package com.tonex.controller.tonex

/**
 * CRC-16/X-25 — port literal de `tonex_common_calculate_CRC()` em
 * `source/main/usb_tonex_common.c:46`.
 *
 *  - init `0xFFFF`
 *  - polinômio refletido `0x8408`
 *  - retorno `~crc` (complemento de 1)
 *  - calculado ANTES do byte stuffing
 *
 * Os vetores de teste em [FramingTest] foram gerados compilando a função do
 * firmware com gcc — não foram escritos à mão.
 */
object Crc {

    fun x25(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "intervalo inválido: offset=$offset length=$length size=${data.size}"
        }
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) ((crc ushr 1) xor 0x8408) else (crc ushr 1)
            }
        }
        return crc.inv() and 0xFFFF
    }
}
