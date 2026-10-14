package com.carlos.dohome

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widget da tela inicial: liga/desliga e brilho −/+ (passos de 25%).
 * Usa o mesmo estado salvo do app (LightStore); os comandos vão direto para a lâmpada pela rede local.
 */
class DoHomeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, LightStore.load(context))
        // Sincroniza com a lâmpada (alguém pode ter mexido no interruptor ou no app)
        runAsync { syncFromBulb(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in listOf(ACTION_TOGGLE, ACTION_UP, ACTION_DOWN)) return

        val before = LightStore.load(context)
        if (before.ip.isBlank()) return
        val after = when (action) {
            ACTION_TOGGLE -> before.copy(isOn = !before.isOn)
            ACTION_UP -> before.copy(isOn = true, brightness = stepBrightness(before, +1))
            else -> before.copy(isOn = true, brightness = stepBrightness(before, -1))
        }
        // Resposta imediata na tela; se a lâmpada não responder, volta ao estado anterior
        LightStore.save(context, after)
        render(context, after, busy = true)

        runAsync {
            val client = DoHomeClient(after.ip, wifiNetwork(context), attempts = 1)
            val ok = runCatching {
                if (!after.isOn) client.setState(RawState(0, 0, 0, 0, 0))
                else if (after.mode == Mode.EFFECT && after.effect != null && action == ACTION_TOGGLE) client.setEffect(after.effect)
                else client.setState(after.copy(mode = if (after.mode == Mode.EFFECT) Mode.COLOR else after.mode).toRaw())
            }.isSuccess
            if (ok) {
                render(context, after)
            } else {
                LightStore.save(context, before)
                render(context, before, error = true)
            }
        }
    }

    /** Passos de 25%: 1% → 25% → 50% → 75% → 100%. */
    private fun stepBrightness(u: UiState, dir: Int): Int {
        val steps = listOf(3, 64, 128, 191, 255)
        val current = if (u.isOn) u.brightness else 0
        return if (dir > 0) steps.firstOrNull { it > current } ?: 255
        else steps.lastOrNull { it < current } ?: 3
    }

    private suspend fun syncFromBulb(context: Context) {
        val u = LightStore.load(context)
        if (u.ip.isBlank()) return
        val raw = withTimeoutOrNull(5000) {
            runCatching { DoHomeClient(u.ip, wifiNetwork(context), attempts = 1).getState() }.getOrNull()
        } ?: return
        val synced = u.withRaw(raw)
        LightStore.save(context, synced)
        render(context, synced)
    }

    /** goAsync mantém o receiver vivo enquanto a rede responde (limite do Android: ~10 s). */
    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9000) { block() }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.carlos.dohome.widget.TOGGLE"
        private const val ACTION_UP = "com.carlos.dohome.widget.UP"
        private const val ACTION_DOWN = "com.carlos.dohome.widget.DOWN"

        /** Chamado pelo app quando o estado muda. */
        fun updateAll(context: Context) = render(context, LightStore.load(context))

        private fun render(context: Context, u: UiState, busy: Boolean = false, error: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DoHomeWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context, u, busy, error))
        }

        private fun views(context: Context, u: UiState, busy: Boolean, error: Boolean): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_dohome)
            val lightColor = when (u.mode) {
                Mode.WHITE -> kelvinArgb(u.kelvin)
                Mode.EFFECT -> 0xFFB57BFF.toInt()
                Mode.COLOR -> androidx.compose.ui.graphics.Color.hsv(u.hue, u.saturation, 1f).toArgb()
            }
            v.setInt(R.id.power_bg, "setColorFilter", if (u.isOn) lightColor else context.getColor(R.color.widget_button))
            v.setInt(R.id.power_icon, "setColorFilter",
                if (u.isOn) 0xFF231A00.toInt() else context.getColor(R.color.widget_text_secondary))

            v.setTextViewText(R.id.title, if (u.sid.isNotBlank()) "Luz ${u.sid}" else "DoHome")
            val pct = u.brightness * 100 / 255
            val temp = when {
                u.mode != Mode.WHITE -> ""
                u.kelvin < 3800 -> " · quente"
                u.kelvin < 5400 -> " · neutro"
                else -> " · frio"
            }
            v.setTextViewText(
                R.id.subtitle,
                when {
                    u.ip.isBlank() -> "Toque para configurar"
                    error -> "Sem resposta da lâmpada"
                    busy -> if (u.isOn) "Ligando… $pct%" else "Desligando…"
                    u.isOn -> "Ligada · $pct%$temp"
                    else -> "Desligada"
                },
            )

            // Sem lâmpada configurada, qualquer toque abre o app
            val noBulb = u.ip.isBlank()
            v.setOnClickPendingIntent(R.id.power, if (noBulb) openAppIntent(context) else broadcast(context, ACTION_TOGGLE, 1))
            v.setOnClickPendingIntent(R.id.btn_down, if (noBulb) openAppIntent(context) else broadcast(context, ACTION_DOWN, 2))
            v.setOnClickPendingIntent(R.id.btn_up, if (noBulb) openAppIntent(context) else broadcast(context, ACTION_UP, 3))
            v.setOnClickPendingIntent(R.id.texts, openAppIntent(context))
            return v
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, DoHomeWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun kelvinArgb(k: Int): Int {
            val t = (k - 3000) / 3400f
            return androidx.compose.ui.graphics.Color(red = 1f - 0.12f * t, green = 0.74f + 0.2f * t, blue = 0.40f + 0.6f * t).toArgb()
        }
    }
}
