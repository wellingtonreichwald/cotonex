package com.tonex.controller.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.tonex.controller.tonex.Codec
import com.tonex.controller.tonex.Frame
import com.tonex.controller.tonex.Message
import com.tonex.controller.tonex.TonexMessage
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Sessão USB com o TONEX One.
 *
 * O pedal não é MIDI: é um CDC ACM proprietário (VID 0x1963, PID 0x00D1) com
 * uma porta bulk IN 0x87 e uma bulk OUT 0x07. O mesmo driver `usb.host` do
 * Android o enxerga, então `claimInterface` + `bulkTransfer` bastam — não
 * precisa de permissão extra nem de driver nativo.
 *
 * Estrutura espelha a do firmware (`usb_tonex_one_task`):
 *  - [send] monta o payload com [Codec], aplica CRC e stuffing com [Frame],
 *    e joga o frame no bulk OUT;
 *  - a thread de leitura lê o bulk IN, corta nos flags `0x7E` com
 *    [Frame.locateEnd], e só entrega a [TonexMessage] depois do CRC bater.
 *
 * O que aqui é **hipótese até o M0**: o tamanho de leitura (4096) e o timeout
 * (500 ms). O firmware lê em pedaços e acumula, exatamente como fazemos com o
 * `pending`. Timeout no bulk IN é o estado ocioso normal e não é tratado como
 * erro — o watchdog de verdade (correlacionar silêncio com pedido pendente)
 * só entra no M1, quando houver uma captura USB real para conferir.
 */
class TonexUsbSession(
    private val usb: UsbManager,
    device: UsbDevice,
    private val onMessage: (TonexMessage) -> Unit,
    private val onError: (String) -> Unit,
    private val onLog: (String) -> Unit,
) : AutoCloseable {

    private val iface: UsbInterface? =
        device.interfaceCount.let { n -> (0 until n).mapNotNull { device.getInterface(it) } }
            .firstOrNull { intf ->
                (0 until intf.endpointCount).any { i ->
                    val e = intf.getEndpoint(i)
                    e.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                }
            }

    private val epIn: UsbEndpoint? = iface?.let { i ->
        (0 until i.endpointCount).map(i::getEndpoint).firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
    }
    private val epOut: UsbEndpoint? = iface?.let { i ->
        (0 until i.endpointCount).map(i::getEndpoint).firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
    }

    private val conn: UsbDeviceConnection? = iface?.let { i ->
        val c = usb.openDevice(device) ?: return@let null
        if (c.claimInterface(i, true)) c else { c.close(); null }
    }

    private val tx = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = false
    private var reader: Thread? = null
    private var writer: Thread? = null

    val connected: Boolean get() = conn != null && iface != null && epIn != null && epOut != null

    /** Começa as threads de E/S. Chamar depois de construir, se [connected] for `true`. */
    fun start() {
        if (!connected) {
            onError("TONEX One não encontrado (esperado VID 0x1963 / PID 0x00D1)")
            return
        }
        running = true

        writer = thread(name = "tonex-tx", isDaemon = true) {
            while (running) {
                val frame = tx.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                val n = conn!!.bulkTransfer(epOut, frame, frame.size, WRITE_TIMEOUT_MS)
                if (n != frame.size) onError("bulkTransfer OUT devolveu $n de ${frame.size}")
            }
        }

        reader = thread(name = "tonex-rx", isDaemon = true) {
            val buf = ByteArray(READ_SIZE)
            val pending = java.io.ByteArrayOutputStream()  // frame ainda incompleto
            while (running) {
                val n = conn!!.bulkTransfer(epIn, buf, buf.size, READ_TIMEOUT_MS)
                if (n < 0) {
                    // timeout é o estado ocioso normal do bulk IN, não é erro.
                    // O watchdog de verdade só existe no M1, quando dá para
                    // correlacionar a ausência de resposta com um pedido pendente.
                    continue
                }
                pending.write(buf, 0, n)
                drain(pending)
            }
        }

        onLog("USB aberto · IN 0x%02X OUT 0x%02X".format(epIn!!.address, epOut!!.address))
    }

    /**
     * Corta o acumulado nos flags e entrega só o que passou no CRC.
     *
     * É a mesma lógica do loop de receive do firmware: procura o próximo `0x7E`
     * a partir do índice 1, tenta decodificar, e só limpa o buffer quando o
     * frame foi aceito — se o frame estiver incompleto, espera mais bytes.
     */
    private fun drain(pending: java.io.ByteArrayOutputStream) {
        while (true) {
            val data = pending.toByteArray()
            if (data.size < 2) return
            val end = Frame.locateEnd(data)
            if (end < 0) {
                // sobrou lixo sem fechamento: descarta o que precede o primeiro flag
                val first = data.indexOf(Frame.FLAG.toByte())
                if (first > 0) {
                    pending.reset()
                    pending.write(data, first, data.size - first)
                }
                return
            }
            val frame = data.copyOfRange(0, end + 1)
            val payload = Frame.decode(frame)
            if (payload == null) {
                // CRC/corrupção: pula o frame e segue adiante, não aborta a sessão
                onLog("frame descartado (CRC/stuffing): ${frame.toHex()}")
            } else {
                val msg = Message.parse(payload)
                if (msg != null) onMessage(msg) else onLog("payload sem cabeçalho válido: ${payload.toHex()}")
            }
            pending.reset()
            if (end + 1 < data.size) pending.write(data, end + 1, data.size - end - 1)
        }
    }

    /** Envia um payload já montado pelo [Codec]; o CRC e o stuffing são daqui. */
    fun send(payload: ByteArray) = tx.offer(Frame.encode(payload))

    fun hello() = send(Codec.hello())
    fun requestState() = send(Codec.requestState())
    fun requestPreset(index: Int, full: Boolean = false) = send(Codec.requestPresetDetails(index, full))
    fun setParameter(paramIndex: Int, value: Float) = send(Codec.setParameter(paramIndex, value))
    fun setMasterVolume(value: Float) = send(Codec.setMasterVolume(value))

    override fun close() {
        running = false
        writer?.interrupt(); reader?.interrupt()
        iface?.let { conn?.releaseInterface(it) }
        conn?.close()
        onLog("USB fechado")
    }

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }

    private companion object {
        const val READ_SIZE = 4096
        const val READ_TIMEOUT_MS = 500
        const val WRITE_TIMEOUT_MS = 1000
    }
}
