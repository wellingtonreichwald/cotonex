package com.tonex.controller.tonex

/**
 * Leitura do cabeçalho das mensagens RECEBIDAS (pedal → app).
 *
 * Port de `tonex_common_parse_value()` (`usb_tonex_common.c:251`) e do
 * `switch` em `usb_tonex_one_parse()` (`usb_tonex_one.c:1067-1128`).
 *
 * Formato após o unescape:
 * ```
 * [0]=0xB9  [1]=0x03  type(varint)  size(varint)  unknown(varint)  corpo[size]
 * ```
 *
 * Varint:
 *  - `0x81` ou `0x82` → lê 2 bytes, LE16 → consome 3
 *  - `0x80`           → lê 1 byte            → consome 2
 *  - outro            → é o próprio valor    → consome 1
 */
object Message {

    /** Varint lido em `at`. Devolve o valor, ou `null` se faltar byte. */
    fun readValue(data: ByteArray, at: Int): Int? {
        if (at >= data.size) return null
        return when (data[at].toInt() and 0xFF) {
            0x81, 0x82 -> {
                if (at + 2 >= data.size) return null
                ((data[at + 2].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
            }
            0x80 -> {
                if (at + 1 >= data.size) return null
                data[at + 1].toInt() and 0xFF
            }
            else -> data[at].toInt() and 0xFF
        }
    }

    /** Quantos bytes o varint em `at` ocupa. */
    fun valueWidth(data: ByteArray, at: Int): Int = when {
        at >= data.size -> 0
        else -> when (data[at].toInt() and 0xFF) {
            0x81, 0x82 -> if (at + 2 < data.size) 3 else 0
            0x80 -> if (at + 1 < data.size) 2 else 0
            else -> 1
        }
    }

    fun kindOf(type: Int): MessageKind = when (type) {
        0x02 -> MessageKind.HELLO
        0x0306 -> MessageKind.STATE_UPDATE
        0x0304 -> MessageKind.PRESET_DETAILS
        0x0303 -> MessageKind.PRESET_DETAILS_FULL
        0x0309 -> MessageKind.PARAM_CHANGED
        else -> MessageKind.UNKNOWN
    }

    /**
     * @param payload payload sem CRC (saída de [Frame.decode])
     * @param strict quando `true`, exige `corpo.size == size` como o firmware
     *   faz. O padrão é `false`: ver a nota de `Codec` sobre o byte extra —
     *   enquanto isso não for resolvido, um parser estrito derrubaria mensagens
     *   legítimas.
     * @return `null` se o payload for curto demais ou o magic for errado.
     */
    fun parse(payload: ByteArray, strict: Boolean = false): TonexMessage? {
        if (payload.size < 5) return null
        if ((payload[0].toInt() and 0xFF) != 0xB9) return null
        if ((payload[1].toInt() and 0xFF) != 0x03) return null

        var pos = 2
        val type = readValue(payload, pos) ?: return null
        pos += valueWidth(payload, pos)
        val size = readValue(payload, pos) ?: return null
        pos += valueWidth(payload, pos)
        val unknown = readValue(payload, pos) ?: return null
        pos += valueWidth(payload, pos)

        val body = if (pos <= payload.size) payload.copyOfRange(pos, payload.size) else ByteArray(0)
        val mismatch = body.size != size
        if (strict && mismatch) return null

        return TonexMessage(
            type = type,
            kind = kindOf(type),
            size = size,
            unknown = unknown,
            body = body,
            sizeMismatch = mismatch,
        )
    }
}

enum class MessageKind { HELLO, STATE_UPDATE, PRESET_DETAILS, PRESET_DETAILS_FULL, PARAM_CHANGED, UNKNOWN }

data class TonexMessage(
    /** Valor cru do varint `type` (0x02, 0x0306, 0x0304, 0x0303, 0x0309…). */
    val type: Int,
    val kind: MessageKind,
    /** Varint `size` declarado pelo pedal. */
    val size: Int,
    val unknown: Int,
    /** Bytes após o cabeçalho. */
    val body: ByteArray,
    /** `true` quando `body.size != size` — esperado no primeiro build (ver `Codec`). */
    val sizeMismatch: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is TonexMessage && other.type == type && other.size == size &&
            other.unknown == unknown && other.body.contentEquals(body) &&
            other.sizeMismatch == sizeMismatch

    override fun hashCode(): Int = (type * 31 + size) * 31 + body.contentHashCode()
}
