package com.carlos.dohome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Mode { COLOR, WHITE, EFFECT }

data class UiState(
    val ip: String = "",
    val connected: Boolean = false,
    val busy: Boolean = false,
    val status: String = "",
    val found: List<FoundDevice> = emptyList(),
    val isOn: Boolean = false,
    val mode: Mode = Mode.WHITE,
    val hue: Float = 30f,
    val saturation: Float = 1f,
    val brightness: Int = 255,
    val kelvin: Int = 4500,
    val effect: Int? = null,
    val model: String = "",
    /** Últimos 4 dígitos do MAC, ex.: "a1b2". */
    val sid: String = "",
    /** false para lâmpadas só brancas (DT-WY): esconde Cor e Efeitos. */
    val isRgb: Boolean = true,
)

class LightViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("dohome", 0)
    private val _ui = MutableStateFlow(LightStore.load(app))
    val ui: StateFlow<UiState> = _ui

    /** Fila "conflated": ao arrastar um slider só o comando mais recente é enviado. */
    private val outbox = Channel<suspend (DoHomeClient) -> Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            for (job in outbox) {
                val c = client() ?: continue
                runCatching { job(c) }
                    .onSuccess { _ui.update { it.copy(connected = true) } }
                    .onFailure { e -> _ui.update { it.copy(connected = false, status = "Erro: ${e.message}") } }
                delay(60)
            }
        }
        // Persiste o estado e atualiza o widget da tela inicial (com debounce para não pesar no slider)
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _ui.map { it.copy(busy = false, status = "", found = emptyList(), connected = false) }
                .distinctUntilChanged().drop(1).debounce(400)
                .collect { LightStore.save(app, it); DoHomeWidget.updateAll(app) }
        }
    }

    private fun client(ip: String = _ui.value.ip): DoHomeClient? =
        ip.takeIf { it.isNotBlank() }?.let { DoHomeClient(it, wifiNetwork(getApplication())) }

    private fun status(msg: String) = _ui.update { it.copy(status = msg) }

    fun selectIp(ip: String) {
        val clean = ip.trim()
        prefs.edit().putString("ip", clean).apply()
        _ui.update { it.copy(ip = clean, connected = false) }
        refresh()
    }

    fun discover() = viewModelScope.launch {
        _ui.update { it.copy(busy = true, status = "Procurando lâmpadas na rede…") }
        val app = getApplication<Application>()
        val net = wifiNetwork(app)
        var list = runCatching { DoHomeClient.discover(app, net) }.getOrElse {
            status("Falha na busca: ${it.message}"); emptyList()
        }
        if (list.isEmpty()) {
            status("Broadcast sem resposta. Varrendo a rede IP por IP (uns 10 s)…")
            list = runCatching { DoHomeClient.scanSubnet(app, net) }.getOrElse { emptyList() }
        }
        _ui.update {
            it.copy(
                busy = false, found = list,
                status = if (list.isEmpty()) "Nenhuma lâmpada respondeu. Tente digitar o IP." else "${list.size} encontrada(s)",
            )
        }
        if (list.size == 1 && _ui.value.ip.isBlank()) selectIp(list.first().ip)
    }

    /** Lê o estado atual da lâmpada para sincronizar a tela. */
    fun refresh() = viewModelScope.launch {
        val c = client() ?: return@launch
        _ui.update { it.copy(busy = true, status = "Conectando em ${_ui.value.ip}…") }
        runCatching { c.deviceInfo() to c.getState() }
            .onSuccess { (info, s) ->
                // dev_id = "<mac>_<tipo>_<chip>", ex.: "aabbccdda1b2_DT-WY_W600"
                val model = info.optString("dev_id").split("_").getOrNull(1).orEmpty()
                val isRgb = model.contains("RGB") || model.contains("STRIPE")
                _ui.update { u ->
                    u.copy(
                        busy = false, connected = true, status = "Conectado",
                        model = model, isRgb = isRgb, sid = info.optString("dev_id").take(12).takeLast(4),
                        mode = if (isRgb) u.mode else Mode.WHITE,
                    ).withRaw(s)
                }
            }
            .onFailure { e -> _ui.update { it.copy(busy = false, connected = false, status = "Sem resposta: ${e.message}") } }
    }

    private fun push() {
        val u = _ui.value
        if (u.mode == Mode.EFFECT) return
        val raw = u.toRaw()
        outbox.trySend { it.setState(raw) }
    }

    fun setPower(on: Boolean) {
        _ui.update { it.copy(isOn = on) }
        val u = _ui.value
        if (on) {
            val raw = u.toRaw()
            val effect = u.effect
            outbox.trySend { c ->
                if (u.mode == Mode.EFFECT && effect != null) c.setEffect(effect) else c.setState(raw)
            }
        } else {
            outbox.trySend { it.setState(RawState(0, 0, 0, 0, 0)) }
        }
    }

    fun setColor(hue: Float, saturation: Float) {
        _ui.update { it.copy(mode = Mode.COLOR, hue = hue, saturation = saturation, isOn = true) }
        push()
    }

    fun setBrightness(b: Int) {
        _ui.update { it.copy(brightness = b.coerceIn(1, 255), isOn = true) }
        push()
    }

    fun setKelvin(k: Int) {
        _ui.update { it.copy(mode = Mode.WHITE, kelvin = k, isOn = true) }
        push()
    }

    fun setMode(mode: Mode) {
        _ui.update { it.copy(mode = mode) }
        push()
    }

    fun setEffect(index: Int) {
        _ui.update { it.copy(mode = Mode.EFFECT, effect = index, isOn = true) }
        outbox.trySend { it.setEffect(index) }
    }

    /** Envia a rede Wi-Fi da casa para a lâmpada (celular precisa estar no Wi-Fi DoHome_xxxx). */
    fun provision(ssid: String, password: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, status = "Falando com a lâmpada em ${DoHome.AP_IP}…") }
        val app = getApplication<Application>()
        val c = client(DoHome.AP_IP)!!
        val ok = runCatching {
            c.deviceInfo()
            c.setWifi(ssid, password)
        }.onFailure { e ->
            status("Falhou: ${e.message}. Confira se o celular está conectado ao Wi-Fi DoHome_xxxx.")
        }.isSuccess
        if (ok) {
            // Ainda na rede da lâmpada: o "pong" informa o sta_ip assim que ela pega um IP do roteador.
            status("Rede enviada. Esperando a lâmpada entrar em \"$ssid\"…")
            var newIp: String? = null
            for (i in 1..8) {
                newIp = runCatching { DoHomeClient.discover(app, wifiNetwork(app), 2000) }.getOrNull()
                    ?.firstOrNull { !it.ip.startsWith("192.168.4.") }?.ip
                if (newIp != null) break
            }
            if (newIp == null) c.reboot()

            prefs.edit().putString("ip", newIp ?: "").apply()
            _ui.update {
                it.copy(
                    ip = newIp ?: "", connected = false,
                    status = if (newIp != null)
                        "Pronto! A lâmpada entrou na sua rede com o IP $newIp. Volte o celular para \"$ssid\" e toque em Atualizar."
                    else
                        "Rede enviada e lâmpada reiniciada. Volte o celular para \"$ssid\" e toque em Procurar. " +
                            "Se o Wi-Fi DoHome_xxxx continuar aparecendo, a senha/rede pode estar errada (ou a rede é 5 GHz).",
                )
            }
        }
        _ui.update { it.copy(busy = false) }
        onDone(ok)
    }
}
