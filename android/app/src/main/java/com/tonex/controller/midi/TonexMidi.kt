package com.tonex.controller.midi

/**
 * Mapa de ENTRADA MIDI: o que o controlador (Chocolate Plus) envia por BLE MIDI
 * e como o app converte em ação sobre o TONEX One.
 *
 * ---------------------------------------------------------------------------
 * IMPORTANTE — este arquivo NÃO descreve o que o app envia ao TONEX One.
 *
 * O TONEX One NÃO fala MIDI. Ele é um dispositivo CDC ACM (VID 0x1963,
 * PID 0x00D1) falando protocolo proprietário — ver PROTOCOLO-TONEX-USB.md.
 * O app recebe CC/PC do controlador, traduz para uma ação semântica, e
 * essa ação é materializada como frame USB para o pedal.
 * ---------------------------------------------------------------------------
 *
 * Fonte primária: source/main/midi_helper_tonex.c (função
 * midi_helper_tonex_adjust_param_via_midi) e source/main/midi_helper.h.
 *
 * O MidiCommands.md do repo CONTÉM ERROS em relação ao código real.
 * Cada divergência está marcada com [DOC ERRADO] abaixo.
 *
 * Camada pura, sem Android — testável isoladamente.
 */

// ---------------------------------------------------------------------------
// Conversão de valores
// ---------------------------------------------------------------------------

object MidiBool {
    /** `midi_helper.h:22` — "matches Tonex". */
    const val DISABLE = 0
    /** `midi_helper.h:24` — "matches Tonex". */
    const val ENABLE = 127
    /**
     * `midi_helper.h:23` — "custom for this controller, not natively supported by Tonex".
     * Valor 64 em CC booleano INVERTE o estado atual. Convenção desta plataforma,
     * não do pedal — o app pode aceitar porque quem envia é o controlador.
     */
    const val TOGGLE = 64
}

object MidiScale {
    /**
     * `midi_helper.c:61` — escala 0..127 para o range real do parâmetro.
     *
     *     min + ((midiValue / 127.0f) * (max - min))
     *
     * O divisor é **127.0**, não 127/128 e não 126.
     */
    fun toPhysical(midiValue: Int, min: Float, max: Float): Float =
        min + ((midiValue.coerceIn(0, 127) / 127f) * (max - min))

    /**
     * Inversa. O firmware em C não a tem — está apenas em `web/script.js:4218`
     * como `Math.round((value - min)/(max - min) * 127)`.
     */
    fun toMidi(physical: Float, min: Float, max: Float): Int {
        if (max == min) return 0
        val ratio = ((physical - min) / (max - min)).coerceIn(0f, 1f)
        return (ratio * 127f).toInt()  // trunca, arredondando pra baixo
    }

    /**
     * Booleano. `midi_helper.c:71` — só compara com ENABLE (127).
     * Qualquer outro valor (exceto 64, tratado à parte) vira false.
     */
    fun toBool(midiValue: Int): Boolean = midiValue == MidiBool.ENABLE

    /**
     * Booleano com toggle. `midi_helper_tonex.c:52-81`.
     * Retorna null quando não é toggle — aí use [toBool].
     */
    fun toggleOrNull(midiValue: Int, current: Boolean): Boolean? =
        if (midiValue == MidiBool.TOGGLE) !current else null
}

// ---------------------------------------------------------------------------
// CCs de transporte e comando (ação direta, sem parâmetro contínuo)
// ---------------------------------------------------------------------------

enum class TonexInboundAction(val cc: Int, val doc: String) {
    /** `:212` — "tuner (not supported on all platforms!)". Não funciona no One, só no One Plus (`usb_tonex_one.c:373`). */
    TUNER(9, "doc diz Not Supported — na verdade existe, mas inoperante no One"),

    /** `:236` — `control_trigger_tap_tempo()`, valor ignorado. */
    TAP_TEMPO(10, "ok no doc"),

    /** `:1223` — carrega preset em slot A SEM ativar. Value 0..19. */
    LOAD_SLOT_A(120, "ok no doc"),

    /** `:1239` — carrega preset em slot B SEM ativar. Value 0..19. */
    LOAD_SLOT_B(121, "ok no doc"),

    /** `:1262` — `control_request_preset_index(midiValue)`, value 0..19. */
    SELECT_PRESET(127, "ok no doc"),

    /** `:983` — "A/B slot bank down" → `control_request_ab_bank_down()`. */
    BANK_DOWN(89, "DOC ERRADO: doc diz 'Bank Up / Not supported'. Na verdade é bank DOWN e funciona"),

    /** `:991` — "A/B slot bank up" → `control_request_ab_bank_up()`. */
    BANK_UP(90, "DOC ERRADO: doc diz 'Bank Down / Not supported'. Na verdade é bank UP e funciona"),
}

/**
 * [DOC ERRADO] O doc marca CC 12 como "Preset On / Not supported".
 * O código (`:247`) usa `TONEX_GLOBAL_BYPASS` — é o **bypass do preset**,
 * com toggle em 64 e clamp de valor.
 */
const val CC_PRESET_BYPASS = 12

/** [DOC ERRADO] O doc marca CC 11 como "Expression Pedal / Not Supported" — está correto: `midi_helper_tonex.c:245` só tem o comentário `// 11: expression pedal`, sem case. */
const val CC_EXPRESSION_PEDAL_UNSUPPORTED = 11

/** O firmware só aceita CC (`0xB0`) e PC (`0xC0`). Note on/off e pitch bend são ignorados com log. */
val SUPPORTED_STATUS = setOf(0xB0, 0xC0)

// ---------------------------------------------------------------------------
// Catálogo de parâmetros
// ---------------------------------------------------------------------------

enum class ValueKind { RANGE, ON_OFF, ENUM, ACTION }

/**
 * Um parâmetro manipulável.
 *
 * @property paramIndex índice `TONEX_PARAM_*`/`TONEX_GLOBAL_*` do enum
 *   `tonex_params.h:59-199` — é o que viaja no frame USB (`0x88 + float32`),
 *   não o CC.
 * @property min,max range real em unidade física, lido do pedal em runtime
 *   via `tonex_params_get_min_max()`; os valores aqui são os defaults do repo.
 */
data class TonexParam(
    val id: String,
    val label: String,
    val cc: Int,
    val paramIndex: Int,
    val kind: ValueKind = ValueKind.RANGE,
    val min: Float = 0f,
    val max: Float = 1f,
    val unit: String = "",
    val options: List<String> = emptyList()
)

object TonexParams {
    /**
     * Índices derivados programaticamente de `enum TonexParameters`
     * (`tonex_params.h:59-199`) — não foram contados à mão.
     *
     * CUIDADO com os GLOBAIS: `TONEX_GLOBAL_*` vêm DEPOIS de `TONEX_PARAM_LAST`
     * (índice 109) e "são set differently to the params" (`tonex_params.h:187`).
     * Eles NÃO vão em `usb_tonex_one_send_single_parameter` — vão por
     * `usb_tonex_one_modify_global()`, com eco de StateData.
     */
    val BYPASS = TonexParam("global.bypass", "Bypass", CC_PRESET_BYPASS, 115, ValueKind.ON_OFF)
    val AMP_ENABLE = TonexParam("amp.enable", "Amp", -1, 18, ValueKind.ON_OFF)
    val GATE_ENABLE = TonexParam("gate.enable", "Gate", -1, 1, ValueKind.ON_OFF)
    val COMP_ENABLE = TonexParam("comp.enable", "Comp", -1, 6, ValueKind.ON_OFF)
    val REVERB_ENABLE = TonexParam("reverb.enable", "Reverb", -1, 37, ValueKind.ON_OFF)
    val MOD_ENABLE = TonexParam("mod.enable", "Mod", -1, 64, ValueKind.ON_OFF)
    val DELAY_ENABLE = TonexParam("delay.enable", "Delay", -1, 95, ValueKind.ON_OFF)

    val AMP_GAIN = TonexParam("amp.gain", "Gain", 102, 20, min = 0f, max = 10f, unit = "")
    val AMP_VOLUME = TonexParam("amp.volume", "Volume", 103, 21, min = 0f, max = 10f)
    val AMP_MIX = TonexParam("amp.mix", "Mix", 104, 22, min = 0f, max = 1f)
    val PRESENCE = TonexParam("amp.presence", "Presence", 106, 34)
    val DEPTH = TonexParam("amp.depth", "Depth", 107, 35)

    /**
     * Blocos com toggle e a posição pre/post.
     *
     * Os índices `paramIndex` vêm da ordem do enum em `tonex_params.h`,
     * que é "defined in the same order as they are sent by the Pedal".
     * Os CCs vêm do switch em `midi_helper_tonex.c`.
     */
    val ALL: List<TonexParam> = listOf(
        BYPASS, AMP_ENABLE, GATE_ENABLE, COMP_ENABLE,
        REVERB_ENABLE, MOD_ENABLE, DELAY_ENABLE,
        AMP_GAIN, AMP_VOLUME, AMP_MIX, PRESENCE, DEPTH
    )

    fun byCc(cc: Int): TonexParam? = ALL.firstOrNull { it.cc == cc }
    fun byId(id: String): TonexParam? = ALL.firstOrNull { it.id == id }
}

// ---------------------------------------------------------------------------
// Divergências MidiCommands.md × código real
// ---------------------------------------------------------------------------

object DocErrata {
    /**
     * Lista conferida linha a linha contra `midi_helper_tonex.c`.
     *
     * 1. **CC 89/90** — doc: 89=Bank Up, 90=Bank Down, ambos "Not supported".
     *    Código (`:983`, `:991`): 89=**bank DOWN**, 90=**bank UP**, ambos funcionam.
     *
     * 2. **CC 9 (Tuner)** — doc: "Not Supported".
     *    Código (`:212`): existe e chama `control_request_tuner()`,
     *    mas `usb_tonex_one.c:373` registra que **não funciona no TONEX One**,
     *    só no One Plus.
     *
     * 3. **CC 12 (Preset On)** — doc: "Not supported".
     *    Código (`:247`): é `TONEX_GLOBAL_BYPASS`, com toggle em 64. Funciona.
     *
     * 4. **CC 11 (Expression Pedal)** — doc: "Not Supported". Correto: não há
     *    `case 11`, só o comentário `// 11: expression pedal`.
     *
     * 5. **CC 10 (Tap Tempo)** — doc diz "Value Not used". Correto.
     *
     * 6. **`get_param_for_change_num` diverge de `adjust_param_via_midi`** em
     *    CC 10, 120, 121 e 127: em CC 120/121 a primeira devolve
     *    `MASTER_VOLUME` em vez de disparar o load de slot. Uso errado dessa
     *    função gera comportamento errado — usar sempre a de `adjust`.
     *
     * 7. **Sem clamp** em CC 23, 88, 99, 100 — os demais passam por
     *    `tonex_params_clamp_value()`.
     *
     * 8. **O doc lista "Load Preset to Slot A/B" como 0-19** — correto, mas o
     *    limite real é `usb_get_max_presets_for_connected_modeller()`, e valores
     *    fora da faixa são **rejeitados com warning**, não truncados
     *    (`:1226`, `:1244`, `:1265`).
     */
    val NOTES: List<String> = emptyList()
}
