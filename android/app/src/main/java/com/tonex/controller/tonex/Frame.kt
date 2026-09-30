package com.tonex.controller.tonex

/**
 * Enquadramento HDLC-like do TONEX One — port literal de
 * `tonex_common_add_framing()` e `tonex_common_remove_framing()`
 * (`usb_tonex_common.c:104` e `:136`).
 *
 * ```
 * 0x7E | stuffed(payload) | stuffed(crc_lo) | stuffed(crc_hi) | 0x7E
 * ```
 *
 * Stuffing: `0x7E` ou `0x7D` viram `0x7D, byte XOR 0x20`.
 * O CRC é little-endian na linha e é calculado sobre o payload **sem** stuffing.
 */
object Frame {

    const val FLAG = 0x7E
    const val ESC = 0x7D
    const val ESC_XOR = 0x20

    /** Um frame precisa de no mínimo: flag + crc_lo + crc_hi + flag. */
    private const val MIN_FRAME = 4

    /** Codifica payload (+CRC) para a linha, com flags e stuffing. */
    fun encode(payload: ByteArray): ByteArray {
        val out = ByteArray(payload.size * 2 + 8)
        var n = 0
        out[n++] = FLAG.toByte()
        for (b in payload) n += stuff(out, n, b.toInt() and 0xFF)
        val crc = Crc.x25(payload)
        n += stuff(out, n, crc and 0xFF)
        n += stuff(out, n, (crc ushr 8) and 0xFF)
        out[n++] = FLAG.toByte()
        return out.copyOf(n)
    }

    /**
     * Decodifica um frame completo, devolvendo o payload **sem** os 2 bytes de CRC.
     *
     * `null` em qualquer erro — mesma lista de recusas da função em C:
     * tamanho mínimo, flags de início/fim ausentes, escape truncado,
     * flag no meio do frame, frame curto demais para caber CRC, ou CRC divergente.
     *
     * Como na função em C, a leitura **para na primeira flag interna** entre o
     * primeiro e o último `0x7E`.
     */
    fun decode(frame: ByteArray): ByteArray? {
        if (frame.size < MIN_FRAME) return null
        if ((frame[0].toInt() and 0xFF) != FLAG) return null
        if ((frame[frame.size - 1].toInt() and 0xFF) != FLAG) return null

        val out = ByteArray(frame.size)
        var n = 0
        var i = 1
        val last = frame.size - 1

        while (i < last) {
            val b = frame[i].toInt() and 0xFF
            when {
                b == ESC -> {
                    if (i + 1 >= last) return null
                    out[n++] = ((frame[i + 1].toInt() and 0xFF) xor ESC_XOR).toByte()
                    i++
                }
                b == FLAG -> break
                else -> out[n++] = b.toByte()
            }
            i++
        }

        if (n < 2) return null

        val received = ((out[n - 1].toInt() and 0xFF) shl 8) or (out[n - 2].toInt() and 0xFF)
        val payload = out.copyOf(n - 2)
        if (received != Crc.x25(payload)) return null
        return payload
    }

    /** Índice da próxima flag `0x7E` depois da posição 0, ou -1 se não houver. */
    fun locateEnd(data: ByteArray, length: Int = data.size): Int {
        for (i in 1 until length) {
            if ((data[i].toInt() and 0xFF) == FLAG) return i
        }
        return -1
    }

    private fun stuff(out: ByteArray, at: Int, byte: Int): Int =
        if (byte == FLAG || byte == ESC) {
            out[at] = ESC.toByte()
            out[at + 1] = (byte xor ESC_XOR).toByte()
            2
        } else {
            out[at] = byte.toByte()
            1
        }
}
