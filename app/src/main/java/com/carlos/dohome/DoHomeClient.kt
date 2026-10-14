package com.carlos.dohome

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Protocolo "DoIt" usado pelas lâmpadas DoHome (chip W600/ESP).
 *
 *  - Comandos: JSON + "\r\n" via TCP na porta 5555. Ex.: {"cmd":6,"r":5000,"g":0,"b":0,"w":0,"m":0}
 *  - Descoberta: datagrama "cmd=ping" em broadcast UDP na porta 6091; as lâmpadas
 *    respondem "cmd=pong&device_name=...&sta_ip=...".
 *  - Valores de cor vão de 0 a 5000.
 *  - Em modo AP (rede "DoHome_xxxx") a lâmpada fica em 192.168.4.1.
 */
object DoHome {
    const val PORT_TCP = 5555
    const val PORT_UDP = 6091
    const val AP_IP = "192.168.4.1"
    const val MAX = 5000

    const val CMD_SCAN_WIFI = 1
    const val CMD_REBOOT = 3
    const val CMD_DEVICE_INFO = 4
    const val CMD_SET_STATE = 6
    const val CMD_SET_EFFECT = 7
    const val CMD_WIFI_CREDENTIALS = 16
    const val CMD_GET_STATE = 25

    const val KELVIN_MIN = 3000
    const val KELVIN_MAX = 6400
}

class DoHomeException(message: String) : IOException(message)

data class FoundDevice(val name: String, val ip: String, val type: String)

/** Estado bruto da lâmpada (0..5000 por canal). w/m = os dois brancos. */
data class RawState(val r: Int, val g: Int, val b: Int, val w: Int, val m: Int) {
    val isOn get() = r + g + b + w + m > 0
}

/** Rede Wi-Fi atual — necessária quando o Wi-Fi da lâmpada não tem internet e o Android prefere os dados móveis. */
fun wifiNetwork(context: Context): Network? {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    @Suppress("DEPRECATION")
    return cm.allNetworks.firstOrNull {
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

class DoHomeClient(
    private val host: String,
    private val network: Network?,
    private val attempts: Int = 3,
) {

    suspend fun send(cmd: Int, params: Map<String, Any> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val req = JSONObject().put("cmd", cmd)
            params.forEach { (k, v) -> req.put(k, v) }
            val payload = (req.toString() + "\r\n").toByteArray()

            var lastError: IOException? = null
            for (attempt in 1..attempts) {
                try {
                    return@withContext sendOnce(payload, cmd)
                } catch (e: DoHomeException) {
                    throw e
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("Sem resposta de $host")
        }

    private fun sendOnce(payload: ByteArray, cmd: Int): JSONObject {
        val socket = network?.socketFactory?.createSocket() ?: Socket()
        socket.use { s ->
            s.connect(InetSocketAddress(host, DoHome.PORT_TCP), 2000)
            s.soTimeout = 4000
            s.getOutputStream().apply { write(payload); flush() }
            val buf = ByteArray(1024)
            val n = s.getInputStream().read(buf)
            if (n <= 0) throw IOException("Resposta vazia")
            val text = String(buf, 0, n)
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end < start) throw IOException("Resposta inválida: $text")
            val resp = JSONObject(text.substring(start, end + 1))
            val res = resp.optInt("res", 0)
            if (res != 0) throw DoHomeException("A lâmpada recusou o comando $cmd (código $res)")
            return resp
        }
    }

    suspend fun getState(): RawState {
        val r = send(DoHome.CMD_GET_STATE)
        return RawState(r.optInt("r"), r.optInt("g"), r.optInt("b"), r.optInt("w"), r.optInt("m"))
    }

    /**
     * Não mande o campo "on": testado no DT-WY (firmware 1.0.4), com "on":1 a lâmpada
     * ignora os valores e volta ao brilho máximo. Desligar = todos os canais em 0.
     */
    suspend fun setState(s: RawState) {
        send(DoHome.CMD_SET_STATE, mapOf("r" to s.r, "g" to s.g, "b" to s.b, "w" to s.w, "m" to s.m))
    }

    suspend fun setEffect(index: Int) {
        send(DoHome.CMD_SET_EFFECT, mapOf("index" to index))
    }

    suspend fun deviceInfo(): JSONObject = send(DoHome.CMD_DEVICE_INFO)

    suspend fun setWifi(ssid: String, password: String) {
        send(DoHome.CMD_WIFI_CREDENTIALS, mapOf("ssid" to ssid, "pass" to password))
    }

    /** A lâmpada pode reiniciar antes de responder, então erros aqui são esperados. */
    suspend fun reboot() {
        runCatching { send(DoHome.CMD_REBOOT) }
    }

    companion object {
        /** Converte cor 0..255 + brilho 0..255 em canais 0..5000. */
        fun rgbState(r: Int, g: Int, b: Int, brightness: Int): RawState {
            fun ch(v: Int) = v * DoHome.MAX / 255 * brightness / 255
            return RawState(ch(r), ch(g), ch(b), 0, 0)
        }

        /** Branco: kelvin (3000..6400) dividido entre os dois LEDs brancos, somando 5000. */
        fun whiteState(kelvin: Int, brightness: Int): RawState {
            val k = kelvin.coerceIn(DoHome.KELVIN_MIN, DoHome.KELVIN_MAX)
            val pct = (k - DoHome.KELVIN_MIN).toFloat() / (DoHome.KELVIN_MAX - DoHome.KELVIN_MIN)
            val w = (pct * DoHome.MAX).toInt()
            val m = DoHome.MAX - w
            return RawState(0, 0, 0, w * brightness / 255, m * brightness / 255)
        }

        /** Procura lâmpadas na rede local via broadcast UDP. */
        suspend fun discover(context: Context, network: Network?, timeoutMs: Long = 2500): List<FoundDevice> =
            withContext(Dispatchers.IO) {
                val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
                val lock = wifi.createMulticastLock("dohome-discovery").apply {
                    setReferenceCounted(false); acquire()
                }
                val found = linkedMapOf<String, FoundDevice>()
                try {
                    DatagramSocket().use { s ->
                        network?.bindSocket(s)
                        s.broadcast = true
                        s.soTimeout = 400
                        val msg = "cmd=ping\n".toByteArray()
                        val targets = broadcastAddresses(context, network) + InetAddress.getByName("255.255.255.255")
                        repeat(2) {
                            targets.distinct().forEach { addr ->
                                runCatching { s.send(DatagramPacket(msg, msg.size, addr, DoHome.PORT_UDP)) }
                            }
                        }
                        val deadline = System.currentTimeMillis() + timeoutMs
                        val buf = ByteArray(512)
                        while (System.currentTimeMillis() < deadline) {
                            val pkt = DatagramPacket(buf, buf.size)
                            try {
                                s.receive(pkt)
                            } catch (_: SocketTimeoutException) {
                                continue
                            }
                            val fields = String(pkt.data, 0, pkt.length).trim()
                                .split("&").mapNotNull {
                                    val i = it.indexOf('=')
                                    if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
                                }.toMap()
                            if (fields["cmd"] != "pong") continue
                            val ip = fields["sta_ip"]?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                                ?: pkt.address.hostAddress ?: continue
                            found[ip] = FoundDevice(
                                name = fields["device_name"] ?: "DoHome",
                                ip = ip,
                                type = fields["device_type"] ?: "",
                            )
                        }
                    }
                } finally {
                    lock.release()
                }
                found.values.toList()
            }

        /**
         * Plano B quando o broadcast não chega (alguns roteadores bloqueiam): tenta abrir a porta
         * TCP 5555 em todos os IPs da sub-rede do celular (/24) e confirma com o comando de info.
         */
        suspend fun scanSubnet(context: Context, network: Network?): List<FoundDevice> =
            withContext(Dispatchers.IO) {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val own = network?.let { cm.getLinkProperties(it) }?.linkAddresses
                    ?.mapNotNull { it.address as? Inet4Address }?.firstOrNull()
                    ?: return@withContext emptyList()
                val b = own.address
                val prefix = "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}."
                val ownIp = own.hostAddress
                coroutineScope {
                    (1..254).map { i -> "$prefix$i" }.filter { it != ownIp }.map { ip ->
                        async {
                            val open = runCatching {
                                (network.socketFactory.createSocket()).use { s ->
                                    s.connect(InetSocketAddress(ip, DoHome.PORT_TCP), 700)
                                }
                            }.isSuccess
                            if (!open) return@async null
                            val info = runCatching { DoHomeClient(ip, network).deviceInfo() }.getOrNull()
                                ?: return@async null
                            val devId = info.optString("dev_id")
                            FoundDevice(name = "DoHome_" + devId.take(12).takeLast(4), ip = ip,
                                type = devId.substringAfter('_', "").substringBefore('_'))
                        }
                    }.awaitAll().filterNotNull()
                }
            }

        private fun broadcastAddresses(context: Context, network: Network?): List<InetAddress> {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val lp = network?.let { cm.getLinkProperties(it) } ?: return emptyList()
            return lp.linkAddresses.mapNotNull { la ->
                val a = la.address as? Inet4Address ?: return@mapNotNull null
                val ip = a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
                val mask = if (la.prefixLength == 0) 0 else -1 shl (32 - la.prefixLength)
                val bc = ip or mask.inv()
                InetAddress.getByAddress(byteArrayOf((bc shr 24).toByte(), (bc shr 16).toByte(), (bc shr 8).toByte(), bc.toByte()))
            }
        }
    }
}
