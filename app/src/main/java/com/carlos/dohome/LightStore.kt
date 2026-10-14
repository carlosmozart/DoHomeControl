package com.carlos.dohome

import android.content.Context
import androidx.compose.ui.graphics.Color

/** Estado da lâmpada guardado em disco, compartilhado entre o app e o widget. */
object LightStore {
    private const val PREFS = "dohome"

    fun load(context: Context): UiState {
        val p = context.getSharedPreferences(PREFS, 0)
        return UiState(
            ip = p.getString("ip", "") ?: "",
            isOn = p.getBoolean("on", false),
            mode = runCatching { Mode.valueOf(p.getString("mode", null) ?: "") }.getOrDefault(Mode.WHITE),
            hue = p.getFloat("hue", 30f),
            saturation = p.getFloat("sat", 1f),
            brightness = p.getInt("brightness", 255),
            kelvin = p.getInt("kelvin", 4500),
            effect = p.getInt("effect", -1).takeIf { it > 0 },
            model = p.getString("model", "") ?: "",
            sid = p.getString("sid", "") ?: "",
            isRgb = p.getBoolean("rgb", true),
        )
    }

    fun save(context: Context, u: UiState) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("ip", u.ip)
            .putBoolean("on", u.isOn)
            .putString("mode", u.mode.name)
            .putFloat("hue", u.hue)
            .putFloat("sat", u.saturation)
            .putInt("brightness", u.brightness)
            .putInt("kelvin", u.kelvin)
            .putInt("effect", u.effect ?: -1)
            .putString("model", u.model)
            .putString("sid", u.sid)
            .putBoolean("rgb", u.isRgb)
            .apply()
    }
}

/** Canais 0..5000 correspondentes às configurações (efeito cai para a última cor). */
fun UiState.toRaw(): RawState = when (mode) {
    Mode.WHITE -> DoHomeClient.whiteState(kelvin, brightness)
    else -> {
        val c = Color.hsv(hue, saturation, 1f)
        DoHomeClient.rgbState((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), brightness)
    }
}

/** Aplica o estado lido da lâmpada. Desligada: mantém cor/brilho anteriores para religar igual. */
fun UiState.withRaw(s: RawState): UiState {
    var n = copy(isOn = s.isOn)
    if (s.r + s.g + s.b > 0) {
        val max = maxOf(s.r, s.g, s.b)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(s.r * 255 / max, s.g * 255 / max, s.b * 255 / max, hsv)
        n = n.copy(mode = Mode.COLOR, hue = hsv[0], saturation = hsv[1],
            brightness = (max * 255 / DoHome.MAX).coerceIn(1, 255))
    } else if (s.w + s.m > 0) {
        val total = s.w + s.m
        val kelvin = DoHome.KELVIN_MIN + s.w * (DoHome.KELVIN_MAX - DoHome.KELVIN_MIN) / total
        n = n.copy(mode = Mode.WHITE, kelvin = kelvin, brightness = (total * 255 / DoHome.MAX).coerceIn(1, 255))
    }
    return n
}
