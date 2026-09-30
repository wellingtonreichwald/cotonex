package com.tonex.controller.tonex

/**
 * Construtor das mensagens de SAÍDA (app → pedal).
 *
 * Os bytes são os literais de `usb_tonex_one.c` — não foram reconstruídos a
 * partir da spec. São as mensagens que o firmware ESP32 envia e que o pedal já
 * aceita hoje.
 *
 * ---------------------------------------------------------------------------
 * ATENÇÃO — campo `size` do cabeçalho
 *
 * O cabeçalho é `B9 03 | type(varint) | size(varint) | unknown(varint)` e o
 * corpo vem logo em seguida. O parser do próprio firmware valida
 * `corpo_len == size` (`usb_tonex_one.c:1118`), mas **as mensagens que ele
 * transmite têm um byte a mais que esse cálculo daria**: o cabeçalho fixo tem
 * 11 bytes e o corpo 10, enquanto `size` declarado é 10.
 *
 * Ou seja: ou `size` está errado, ou existe um 4º byte de cabeçalho que o
 * parser não lê. Não dá para decidir lendo código — precisa de captura USB.
 *
 * Enquanto isso: **emitimos byte a byte o que o firmware emite**, porque é
 * isso que o pedal comprovadamente aceita. Não "corrigir" o size aqui.
 * ---------------------------------------------------------------------------
 */
object Codec {

    // ---- cabeçalhos fixos, literais de usb_tonex_one.c ----

    /** `usb_tonex_one_hello():204` */
    val HELLO = hex("B9 03 00 82 04 00 80 0B 01 B9 02 02 0B")

    /** `usb_tonex_one_request_state():224` */
    val REQUEST_STATE = hex("B9 03 00 82 06 00 80 0B 03 B9 02 81 06 03 0B")

    /** `usb_tonex_one_request_preset_details():246` — `[15]` = índice, `[16]` = flag full. */
    val REQUEST_PRESET_DETAILS = hex("B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 00 00")

    /** `usb_tonex_one_request_master_volume():347` (corpo já embutido). */
    val REQUEST_MASTER_VOLUME = hex("B9 03 81 0D 03 82 05 00 80 0B 03 B9 03 03 00 00")

    /** `usb_tonex_one_request_tuner():376` — `[12]` = estado. Não funciona no One, só no One Plus. */
    val REQUEST_TUNER = hex("B9 03 81 0F 03 82 03 00 80 19 03 B9 01 00")

    /** `usb_tonex_one_send_single_parameter():272` e `send_master_volume():311`. */
    private val HEADER_SET = hex("B9 03 81 09 03 82 0A 00 80 0B 03")

    // ---- corpo ----

    /** `B9 04 02 00 <index:1 byte> 88 <float32 LE>` — 10 bytes. */
    private fun bodyParam(index: Int, value: Float): ByteArray {
        require(index in 0..255) { "índice de parâmetro fora do byte: $index" }
        val b = ByteArray(10)
        b[0] = 0xB9.toByte(); b[1] = 0x04; b[2] = 0x02; b[3] = 0x00
        b[4] = index.toByte()
        b[5] = 0x88
        writeFloatLe(b, 6, value)
        return b
    }

    /** `B9 04 03 00 00 88 <float32 LE>` — 10 bytes. Byte[2] = 0x03 marca o global. */
    private fun bodyGlobal(value: Float): ByteArray {
        val b = ByteArray(10)
        b[0] = 0xB9.toByte(); b[1] = 0x04; b[2] = 0x03; b[3] = 0x00; b[4] = 0x00
        b[5] = 0x88
        writeFloatLe(b, 6, value)
        return b
    }

    // ---- mensagens ----

    fun hello() = HELLO.copyOf()
    fun requestState() = REQUEST_STATE.copyOf()

    /** @param index 0..19 no TONEX One. @param full `true` = ~30 kB, quase inutilizável. */
    fun requestPresetDetails(index: Int, full: Boolean = false): ByteArray {
        require(index in 0..255) { "índice de preset fora do byte: $index" }
        val m = REQUEST_PRESET_DETAILS.copyOf()
        m[15] = index.toByte()
        m[16] = if (full) 1 else 0
        return m
    }

    fun requestMasterVolume() = REQUEST_MASTER_VOLUME.copyOf()

    /** `usb_tonex_one_send_single_parameter` — só para parâmetros 0..108 (`TONEX_PARAM_*`). */
    fun setParameter(paramIndex: Int, value: Float): ByteArray = HEADER_SET + bodyParam(paramIndex, value)

    /** `usb_tonex_one_send_master_volume` — o valor vai como `TONEX_GLOBAL_MASTER_VOLUME`. */
    fun setMasterVolume(value: Float): ByteArray = HEADER_SET + bodyGlobal(value)

    fun requestTuner(state: Boolean): ByteArray {
        val m = REQUEST_TUNER.copyOf()
        m[13] = if (state) 1 else 0  // `usb_tonex_one_request_tuner():376`
        return m
    }

    // ---- utilidades ----

    /** Float32 little-endian, como `memcpy(&value)` faz no ESP32 (little-endian). */
    fun writeFloatLe(dst: ByteArray, at: Int, v: Float) {
        val bits = java.lang.Float.floatToIntBits(v)
        dst[at] = (bits and 0xFF).toByte()
        dst[at + 1] = ((bits ushr 8) and 0xFF).toByte()
        dst[at + 2] = ((bits ushr 16) and 0xFF).toByte()
        dst[at + 3] = ((bits ushr 24) and 0xFF).toByte()
    }

    fun readFloatLe(src: ByteArray, at: Int): Float {
        val bits = (src[at].toInt() and 0xFF) or
            ((src[at + 1].toInt() and 0xFF) shl 8) or
            ((src[at + 2].toInt() and 0xFF) shl 16) or
            ((src[at + 3].toInt() and 0xFF) shl 24)
        return java.lang.Float.intBitsToFloat(bits)
    }

    /** `"B9 03 00"` → `[0xB9, 0x03, 0x00]`. Espaços e quebras de linha são ignorados. */
    fun hex(s: String): ByteArray =
        s.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
}
