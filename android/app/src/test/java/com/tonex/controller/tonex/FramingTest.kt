package com.tonex.controller.tonex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vetores de referência gerados compilando as funções **reais** do firmware
 * (`usb_tonex_common.c`) com gcc e imprimindo a saída — não foram escritos à
 * mão. Se este teste falhar, a implementação Kotlin divergiu do ESP32.
 */
class FramingTest {

    private fun b(s: String) = Codec.hex(s)

    // ---------------------------------------------------------------- CRC

    @Test fun crc_of_zero_byte_is_f078() = assertEquals(0xF078, Crc.x25(b("00")))

    @Test fun crc_of_ff_is_ff00() = assertEquals(0xFF00, Crc.x25(b("FF")))

    @Test fun crc_of_flag_byte_is_6a81() = assertEquals(0x6A81, Crc.x25(b("7E")))

    @Test fun crc_of_b903_is_d0aa() = assertEquals(0xD0AA, Crc.x25(b("B9 03")))

    @Test fun crc_of_empty_is_zero() = assertEquals(0x0000, Crc.x25(ByteArray(0)))

    // ------------------------------------------------------------ framing

    private fun assertFrame(name: String, plainHex: String, crc: Int, framedHex: String) {
        val plain = b(plainHex)
        assertEquals("crc de $name", crc, Crc.x25(plain))

        val framed = Frame.encode(plain)
        assertArrayEquals("frame de $name", b(framedHex), framed)

        val back = Frame.decode(framed)
        assertNotNull("round-trip de $name", back)
        assertArrayEquals("payload de $name", plain, back)
    }

    @Test fun hello_frame_matches_firmware() = assertFrame(
        "HELLO",
        "B9 03 00 82 04 00 80 0B 01 B9 02 02 0B",
        0x8C17,
        "7E B9 03 00 82 04 00 80 0B 01 B9 02 02 0B 17 8C 7E",
    )

    @Test fun request_state_frame_matches_firmware() = assertFrame(
        "REQUEST_STATE",
        "B9 03 00 82 06 00 80 0B 03 B9 02 81 06 03 0B",
        0x6644,
        "7E B9 03 00 82 06 00 80 0B 03 B9 02 81 06 03 0B 44 66 7E",
    )

    @Test fun request_preset_details_frame_matches_firmware() = assertFrame(
        "REQUEST_PRESET_DETAILS",
        "B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 00 00",
        0x27C8,
        "7E B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 00 00 C8 27 7E",
    )

    @Test fun set_param_18_one_frame_matches_firmware() = assertFrame(
        "SET_PARAM_18_1.0",
        "B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 02 00 12 88 00 00 80 3F",
        0x3F7F,
        "7E B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 02 00 12 88 00 00 80 3F 7F 3F 7E",
    )

    @Test fun set_master_volume_frame_matches_firmware() = assertFrame(
        "SET_MASTER_1.0",
        "B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 03 00 00 88 00 00 80 3F",
        0xF426,
        "7E B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 03 00 00 88 00 00 80 3F 26 F4 7E",
    )

    @Test fun request_preset_19_frame_matches_firmware() = assertFrame(
        "REQ_PRESET_19",
        "B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 13 00",
        0x9831,
        "7E B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 13 00 31 98 7E",
    )

    @Test fun tuner_on_frame_matches_firmware() = assertFrame(
        "TUNER_ON",
        "B9 03 81 0F 03 82 03 00 80 19 03 B9 01 01",
        0x8EA9,
        "7E B9 03 81 0F 03 82 03 00 80 19 03 B9 01 01 A9 8E 7E",
    )

    @Test fun single_byte_payload_frame() = assertFrame(
        "CRC_EMPTY", "00", 0xF078, "7E 00 78 F0 7E",
    )

    // ----------------------------------------------------------- stuffing

    /** Todos os bytes precisam de escape: 0x7E e 0x7D viram 7D + (b XOR 20). */
    @Test fun stuffing_escapes_both_flag_and_escape() = assertFrame(
        "STUFF_7E", "7E 7D 00 7E", 0xED87,
        "7E 7D 5E 7D 5D 00 7D 5E 87 ED 7E",
    )

    @Test fun stuffing_escapes_leading_escape() = assertFrame(
        "STUFF_7D", "7D 1A", 0xF020, "7E 7D 5D 1A 20 F0 7E",
    )

    // ------------------------------------------------------------- erros

    @Test fun decode_rejects_truncated_escape() {
        // termina com 0x7D logo antes da flag final
        assertNull(Frame.decode(b("7E 00 00 7D")))
    }

    @Test fun decode_rejects_short_frame() = assertNull(Frame.decode(b("7E 00 7E")))

    @Test fun decode_rejects_missing_flags() = assertNull(Frame.decode(b("00 00 00 00")))

    @Test fun decode_rejects_corrupted_crc() {
        val framed = Frame.encode(b("B9 03 00 82 04 00 80 0B 01 B9 02 02 0B"))
        framed[framed.size - 2] = (framed[framed.size - 2].toInt() xor 0xFF).toByte()
        assertNull(Frame.decode(framed))
    }

    @Test fun decode_rejects_untouched_but_wrong_payload() {
        val framed = Frame.encode(b("B9 03 00 82 04 00 80 0B 01 B9 02 02 0B"))
        framed[4] = 0x00  // era 0x82
        assertNull(Frame.decode(framed))
    }

    @Test fun locate_end_finds_next_flag() {
        val framed = Frame.encode(b("B9 03 00"))
        // o laço do firmware começa em 1, então nunca devolve 0
        assertEquals(framed.size - 1, Frame.locateEnd(framed))
        assertEquals(-1, Frame.locateEnd(b("B9 03 00")))
    }

    // -------------------------------------------------------------- codec

    @Test fun set_parameter_emits_the_exact_firmware_bytes() {
        assertArrayEquals(
            b("B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 02 00 12 88 00 00 80 3F"),
            Codec.setParameter(18, 1.0f),
        )
    }

    @Test fun set_master_volume_uses_tag_03_not_02() {
        val m = Codec.setMasterVolume(1.0f)
        assertEquals(0xB9, m[11].toInt() and 0xFF)
        assertEquals(0x04, m[12].toInt() and 0xFF)
        assertEquals(0x03, m[13].toInt() and 0xFF)  // ≠ setParameter, que usa 0x02
        assertEquals(0x88, m[16].toInt() and 0xFF)
        assertEquals(1.0f, Codec.readFloatLe(m, 17), 0f)
        assertArrayEquals(b("B9 03 81 09 03 82 0A 00 80 0B 03 B9 04 03 00 00 88 00 00 80 3F"), m)
    }

    @Test fun preset_details_indices_sit_at_15_and_16() {
        val m = Codec.requestPresetDetails(19, full = false)
        assertEquals(19, m[15].toInt() and 0xFF)
        assertEquals(0, m[16].toInt() and 0xFF)
        assertEquals(17, m.size)
        assertArrayEquals(b("B9 03 81 00 03 82 06 00 80 0B 03 B9 04 0B 01 13 00"), m)
    }

    @Test fun tuner_state_sits_at_13() {
        assertEquals(1, Codec.requestTuner(true)[13].toInt() and 0xFF)
        assertEquals(0, Codec.requestTuner(false)[13].toInt() and 0xFF)
        assertEquals(14, Codec.requestTuner(true).size)
    }

    @Test fun float_is_little_endian_like_memcpy_no_esp32() {
        val dst = ByteArray(4)
        Codec.writeFloatLe(dst, 0, 1.0f)
        assertArrayEquals(b("00 00 80 3F"), dst)
        Codec.writeFloatLe(dst, 0, -0.5f)
        assertEquals(-0.5f, Codec.readFloatLe(dst, 0), 0f)
    }

    // ------------------------------------------------------------- header

    @Test fun varint_width_matches_firmware() {
        val d = b("81 06 03 80 0B 00 FF")
        assertEquals(3, Message.valueWidth(d, 0))   // 0x81
        assertEquals(0x0306, Message.readValue(d, 0) ?: -1)
        assertEquals(2, Message.valueWidth(d, 3))   // 0x80
assertEquals(0x0B, Message.readValue(d, 3) ?: -1)
        assertEquals(1, Message.valueWidth(d, 5))   // valor próprio
assertEquals(0x00, Message.readValue(d, 5) ?: -1)
        assertEquals(1, Message.valueWidth(d, 6))
assertEquals(0xFF, Message.readValue(d, 6) ?: -1)
    }

    @Test fun parse_state_update_header() {
        // type 0x0306 (81 06 03), size 4 (04), unknown 0 (00), corpo 4 bytes
        val msg = Message.parse(b("B9 03 81 06 03 04 00 AA BB CC DD"))
        assertNotNull(msg)
        assertEquals(MessageKind.STATE_UPDATE, msg!!.kind)
        assertEquals(0x0306, msg.type)
        assertEquals(4, msg.size)
        assertEquals(0, msg.unknown)
        assertFalse(msg.sizeMismatch)
        assertArrayEquals(b("AA BB CC DD"), msg.body)
    }

    @Test fun parse_is_lenient_by_default_and_strict_on_demand() {
        // corpo tem 5 bytes mas size declara 4 — o caso real das mensagens do app
        val payload = b("B9 03 81 09 03 82 04 00 80 0B 03 AA BB CC DD EE")
        val lenient = Message.parse(payload)
        assertNotNull(lenient)
        assertTrue(lenient!!.sizeMismatch)
        // começa em [10], que é o 0x03 extra do cabeçalho — ver a nota em Codec
        assertArrayEquals(b("03 AA BB CC DD EE"), lenient.body)

        assertNull(Message.parse(payload, strict = true))
    }

    @Test fun parse_rejects_bad_magic_and_short_payload() {
        assertNull(Message.parse(b("00 03 81 06 03 04 00")))
        assertNull(Message.parse(b("B9 03")))
        assertNull(Message.parse(ByteArray(0)))
    }
}
