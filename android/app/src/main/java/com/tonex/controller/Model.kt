package com.tonex.controller

/*
 * Gerado por outputs/gen_model.py a partir de preview-4-deitado.html.
 * NÃO edite à mão: mude o preview e rode o gerador de novo.
 *
 * É a mesma tabela que a tela mostra — índice USB (pn), CC do Chocolate,
 * tipo (switch / select / range), limites e padrão tirados de
 * source/main/tonex_params.c.
 */

data class Preset(val n: Int, val name: String, val skin: String, val color: Long)

/** Um dos 8 botões da cadeia. [cc] é sempre numérico: Amp e Cab ganharam CC novo. */
data class BlockCfg(
    val id: String,
    val label: String,
    val cc: Int,
    val pn: Int,
    val on: Boolean,
    val vOn: String,
    val vOff: String,
    val pnName: String,
)

/** Ação de navegação (não tem parâmetro contínuo). */
data class NavAction(val key: String, val label: String, val cc: Int, val note: String?)

enum class RowKind { HD, SW, SEL, SL }

/**
 * Uma linha da aba Parâmetros.
 *
 * @property kind HD = subtítulo de tipo, SW = liga/desliga, SEL = menu, SL = fader.
 * @property pn índice do enum `TonexParameters` — é o que viaja no frame USB.
 * @property pnList quando o modelo escolhido troca o conjunto de parâmetros
 *   (Reverb): o índice real é `pnList[selIndex]`.
 */
data class RowCfg(
    val kind: RowKind,
    val label: String,
    val pn: Int,
    val cc: Int? = null,
    val blk: String? = null,
    val on: Boolean = false,
    val min: Float = 0f,
    val max: Float = 0f,
    val def: Float = 0f,
    val unit: String = "",
    val opts: List<String> = emptyList(),
    val selIndex: Int = 0,
    val pnList: List<Int> = emptyList(),
) {
    /** Índice real do parâmetro, já com o deslocamento do modelo escolhido. */
    fun effectivePn(sel: Int): Int = if (pnList.isEmpty()) pn else pnList[sel.coerceIn(pnList.indices)]
}

data class PaneCfg(val id: String, val label: String, val hint: String, val rows: List<RowCfg>)

data class SkinGroup(val label: String, val keys: List<String>)

/** Predefinições do preview: mesmos 20 nomes, cores e skins. */
val PRESETS = listOf(
    Preset(1, "Clean Room", "fndrtwin", 0xFF5AA9E6L),
    Preset(2, "Boutique Clean", "slvrface", 0xFF8FB8DEL),
    Preset(3, "Crunch Stack", "jcm", 0xFFFB9230L),
    Preset(4, "Plexi Rhythm", "mdnbkplx", 0xFFE0B341L),
    Preset(5, "Brown Lead", "evh", 0xFFE85D4CL),
    Preset(6, "5150 Crunch", "5150", 0xFFD1A60CL),
    Preset(7, "Mark Clean", "mesamkv", 0xFF7FC8A9L),
    Preset(8, "Dual Rect", "msbogdul", 0xFFC4553BL),
    Preset(9, "Fried Rhythm", "friedman", 0xFFB05FD6L),
    Preset(10, "Dumble Style", "jbdumble", 0xFFC9924AL),
    Preset(11, "Chime 30", "ac30", 0xFF6FD3C7L),
    Preset(12, "Tweed Combo", "fndrtwdg", 0xFFD8A24BL),
    Preset(13, "Hot Rod Clean", "fndrhtrd", 0xFF4FA3D1L),
    Preset(14, "Jazz Clean", "roljazz", 0xFF9BB2C7L),
    Preset(15, "Supro Lo", "supro", 0xFFA56BFFL),
    Preset(16, "Diezel VH4", "diezel", 0xFF5FCB7AL),
    Preset(17, "Orange Crunch", "orngr120", 0xFFFF7A2FL),
    Preset(18, "Ampeg SVT", "ampgchrm", 0xFFC0C4C8L),
    Preset(19, "Jet Clean", "jetcity", 0xFF7FD1FFL),
    Preset(20, "Wood Amp", "woodamp", 0xFFB98A56L),
)

val BLOCKS = listOf(
    BlockCfg("gate", "Gate", cc = 14, pn = 1, on = true,
        vOn = "1", vOff = "0", pnName = "NOISE_GATE_ENABLE"),
    BlockCfg("comp", "Comp", cc = 18, pn = 6, on = false,
        vOn = "1", vOff = "0", pnName = "COMP_ENABLE"),
    BlockCfg("amp", "Amp", cc = 56, pn = 18, on = true,
        vOn = "1", vOff = "0", pnName = "MODEL_AMP_ENABLE"),
    BlockCfg("cab", "Cab", cc = 57, pn = 24, on = true,
        vOn = "1 (VIR)", vOff = "0 (desligado)", pnName = "CABINET_TYPE"),
    BlockCfg("mod", "Mod", cc = 32, pn = 64, on = false,
        vOn = "1", vOff = "0", pnName = "MODULATION_ENABLE"),
    BlockCfg("dly", "Delay", cc = 2, pn = 95, on = false,
        vOn = "1", vOff = "0", pnName = "DELAY_ENABLE"),
    BlockCfg("rev", "Reverb", cc = 75, pn = 37, on = true,
        vOn = "1", vOff = "0", pnName = "REVERB_ENABLE"),
    BlockCfg("eq", "EQ", cc = 30, pn = 10, on = true,
        vOn = "Pós", vOff = "Pré", pnName = "EQ_POST"),
)

val NAV_ACTIONS = listOf(
    NavAction("preset", "Selecionar preset", cc = 127, note = "valor 0–19"),
    NavAction("preset_next", "Próximo preset", cc = 126, note = null),
    NavAction("preset_prev", "Preset anterior", cc = 125, note = null),
    NavAction("bank_next", "Banco +", cc = 124, note = null),
    NavAction("bank_prev", "Banco −", cc = 123, note = null),
    NavAction("tuner", "Afinador", cc = 121, note = null),
)

val PANES = listOf(
    PaneCfg("gate", "Gate",
        hint = "Só o básico: liga, limiar, release, profundidade e se entra antes ou depois da cadeia.",
        rows = listOf(
        RowCfg(RowKind.HD, "Ligar/Desligar"),
        RowCfg(RowKind.SW, "Gate", pn = 1,
            cc = 14, blk = "gate", on = true),
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Pré/Pós", pn = 0,
            cc = 13, opts = listOf("Pré", "Pós"), selIndex = 0),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "Limiar", pn = 2,
            cc = 15, min = -100.0f, max = 0.0f,
            def = -64.0f, unit = " db"),
        RowCfg(RowKind.SL, "Release", pn = 3,
            cc = 16, min = 5.0f, max = 500.0f,
            def = 20.0f, unit = " ms"),
        RowCfg(RowKind.SL, "Profundidade", pn = 4,
            cc = 17, min = -100.0f, max = -20.0f,
            def = -60.0f, unit = " db")
        )),
    PaneCfg("comp", "Comp",
        hint = "Ataque vai de 1 a 51. MAKE_UP é o ganho de reposição.",
        rows = listOf(
        RowCfg(RowKind.HD, "Ligar/Desligar"),
        RowCfg(RowKind.SW, "Compressor", pn = 6,
            cc = 18, blk = "comp", on = false),
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Pré/Pós", pn = 5,
            cc = 22, opts = listOf("Pré", "Pós"), selIndex = 1),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "Limiar", pn = 7,
            cc = 19, min = -40.0f, max = 0.0f,
            def = -14.0f, unit = " db"),
        RowCfg(RowKind.SL, "Make-up", pn = 8,
            cc = 20, min = -30.0f, max = 10.0f,
            def = -12.0f, unit = " db"),
        RowCfg(RowKind.SL, "Ataque", pn = 9,
            cc = 21, min = 1.0f, max = 51.0f,
            def = 14.0f, unit = "")
        )),
    PaneCfg("eq", "EQ",
        hint = "EQ completo: 3 faixas, a frequência de cada uma e o Q do médio. Não existe EQ_ON — só Pré/Pós.",
        rows = listOf(
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Pré/Pós", pn = 10,
            cc = 30, blk = "eq", opts = listOf("Pré", "Pós"),
            selIndex = 0),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "Graves", pn = 11,
            cc = 23, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = ""),
        RowCfg(RowKind.SL, "Freq. graves", pn = 12,
            min = 75.0f, max = 600.0f, def = 300.0f,
            unit = " Hz"),
        RowCfg(RowKind.SL, "Médios", pn = 13,
            cc = 25, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = ""),
        RowCfg(RowKind.SL, "Q médios", pn = 14,
            min = 0.2f, max = 3.0f, def = 0.7f,
            unit = ""),
        RowCfg(RowKind.SL, "Freq. médios", pn = 15,
            min = 150.0f, max = 5000.0f, def = 750.0f,
            unit = " Hz"),
        RowCfg(RowKind.SL, "Agudos", pn = 16,
            cc = 28, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = ""),
        RowCfg(RowKind.SL, "Freq. agudos", pn = 17,
            min = 1000.0f, max = 4000.0f, def = 1900.0f,
            unit = " Hz")
        )),
    PaneCfg("amp", "Amp",
        hint = "O que dá para mexer de verdade: gabinete, VIR, mic 1, blend, ganho, volume, presença e profundidade. Os internos (SW1, CABU) ficaram de fora.",
        rows = listOf(
        RowCfg(RowKind.HD, "Ligar/Desligar"),
        RowCfg(RowKind.SW, "Amp", pn = 18,
            cc = 56, blk = "amp", on = true),
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Gabinete", pn = 24,
            opts = listOf("Tone Model", "VIR", "Desligado"), selIndex = 0),
        RowCfg(RowKind.SEL, "Modelo VIR", pn = 25,
            opts = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), selIndex = 5),
        RowCfg(RowKind.SEL, "Mic 1", pn = 27,
            opts = listOf("0", "1", "2"), selIndex = 0),
        RowCfg(RowKind.SL, "Blend dos mics", pn = 33,
            min = -100.0f, max = 100.0f, def = 0.0f,
            unit = "%"),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "Ganho", pn = 20,
            cc = 102, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = ""),
        RowCfg(RowKind.SL, "Volume", pn = 21,
            cc = 103, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = ""),
        RowCfg(RowKind.SL, "Mix", pn = 22,
            cc = 104, min = 0.0f, max = 100.0f,
            def = 100.0f, unit = "%"),
        RowCfg(RowKind.SL, "Mic 1 · X", pn = 28,
            min = 0.0f, max = 10.0f, def = 0.0f,
            unit = ""),
        RowCfg(RowKind.SL, "Mic 1 · Z", pn = 29,
            min = 0.0f, max = 10.0f, def = 0.0f,
            unit = ""),
        RowCfg(RowKind.SL, "Presença", pn = 34,
            cc = 106, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = ""),
        RowCfg(RowKind.SL, "Profundidade", pn = 35,
            cc = 107, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = "")
        )),
    PaneCfg("rev", "Reverb",
        hint = "Tempo, Pré-delay, Cor e Mix seguem o modelo escolhido — trocar o modelo troca os quatro no pedal.",
        rows = listOf(
        RowCfg(RowKind.HD, "Ligar/Desligar"),
        RowCfg(RowKind.SW, "Reverb", pn = 37,
            cc = 75, blk = "rev", on = true),
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Modelo", pn = 38,
            cc = 85, opts = listOf("Mola 1", "Mola 2", "Mola 3", "Mola 4", "Sala", "Placa"), selIndex = 0),
        RowCfg(RowKind.SEL, "Pré/Pós", pn = 36,
            cc = 84, opts = listOf("Pré", "Pós"), selIndex = 0),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "Tempo", pn = 39,
            cc = 59, min = 0.0f, max = 10.0f,
            def = 5.0f, unit = "", pnList = listOf(39, 43, 47, 51, 55, 59)),
        RowCfg(RowKind.SL, "Pré-delay", pn = 40,
            min = 0.0f, max = 500.0f, def = 0.0f,
            unit = " ms", pnList = listOf(40, 44, 48, 52, 56, 60)),
        RowCfg(RowKind.SL, "Cor", pn = 41,
            min = -10.0f, max = 10.0f, def = 0.0f,
            unit = "", pnList = listOf(41, 45, 49, 53, 57, 61)),
        RowCfg(RowKind.SL, "Mix", pn = 42,
            cc = 62, min = 0.0f, max = 100.0f,
            def = 0.0f, unit = "%", pnList = listOf(42, 46, 50, 54, 58, 62))
        )),
    PaneCfg("mod", "Mod",
        hint = "Ajustes do Chorus (o modelo padrão). Cada modelo tem os seus no firmware.",
        rows = listOf(
        RowCfg(RowKind.HD, "Ligar/Desligar"),
        RowCfg(RowKind.SW, "Modulação", pn = 64,
            cc = 32, blk = "mod", on = false),
        RowCfg(RowKind.SW, "Sync", pn = 66,
            on = false),
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Modelo", pn = 65,
            cc = 33, opts = listOf("Chorus", "Tremolo", "Phaser", "Flanger", "Rotary"), selIndex = 0),
        RowCfg(RowKind.SEL, "Divisão", pn = 67,
            opts = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17"), selIndex = 0),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "Taxa", pn = 68,
            cc = 35, min = 0.1f, max = 10.0f,
            def = 0.5f, unit = ""),
        RowCfg(RowKind.SL, "Profundidade", pn = 69,
            cc = 36, min = 0.0f, max = 100.0f,
            def = 0.0f, unit = "%"),
        RowCfg(RowKind.SL, "Nível", pn = 70,
            cc = 37, min = 0.0f, max = 10.0f,
            def = 0.0f, unit = "")
        )),
    PaneCfg("dly", "Delay",
        hint = "Tempo, Feedback e Mix do Digital. Trocar para Fita muda os pn no pedal.",
        rows = listOf(
        RowCfg(RowKind.HD, "Ligar/Desligar"),
        RowCfg(RowKind.SW, "Delay", pn = 95,
            cc = 2, blk = "dly", on = false),
        RowCfg(RowKind.SW, "Sync", pn = 97,
            on = false),
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Modelo", pn = 96,
            cc = 3, opts = listOf("Digital", "Fita"), selIndex = 0),
        RowCfg(RowKind.SEL, "Divisão", pn = 98,
            opts = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17"), selIndex = 0),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "Tempo", pn = 99,
            cc = 5, min = 0.0f, max = 1000.0f,
            def = 0.0f, unit = " ms"),
        RowCfg(RowKind.SL, "Feedback", pn = 100,
            cc = 6, min = 0.0f, max = 100.0f,
            def = 0.0f, unit = "%"),
        RowCfg(RowKind.SL, "Mix", pn = 102,
            cc = 8, min = 0.0f, max = 100.0f,
            def = 0.0f, unit = "%")
        )),
    PaneCfg("glob", "Geral",
        hint = "Globais NÃO vão por send_single_parameter: exigem eco do StateData.",
        rows = listOf(
        RowCfg(RowKind.HD, "Escolhes"),
        RowCfg(RowKind.SEL, "Cab sim", pn = 112,
            cc = 117, opts = listOf("Ligado", "Bypass"), selIndex = 0),
        RowCfg(RowKind.SEL, "Bypass do preset", pn = 115,
            cc = 12, opts = listOf("Normal", "Bypass"), selIndex = 0),
        RowCfg(RowKind.SEL, "Monitor direto", pn = 117,
            opts = listOf("Desligado", "Ligado"), selIndex = 0),
        RowCfg(RowKind.HD, "Ajustes"),
        RowCfg(RowKind.SL, "BPM", pn = 110,
            cc = 88, min = 40.0f, max = 240.0f,
            def = 80.0f, unit = ""),
        RowCfg(RowKind.SL, "Trim de entrada", pn = 111,
            cc = 116, min = -15.0f, max = 15.0f,
            def = 0.0f, unit = " db"),
        RowCfg(RowKind.SL, "Afinação de referência", pn = 114,
            min = 415.0f, max = 465.0f, def = 440.0f,
            unit = " Hz"),
        RowCfg(RowKind.SL, "Volume master", pn = 116,
            cc = 122, min = -40.0f, max = 3.0f,
            def = 0.0f, unit = " db")
        )),
)

val SKIN_GROUPS = listOf(
    SkinGroup("Amplificadores", listOf(
        "5150", "ac30", "ampgchrm", "ba500", "diezel", "elgntblu", "evh", "fndrhtrd",
        "fndrtwdg", "fndrtwin", "friedman", "jbdumble", "jcm", "jetcity", "jtm", "mdnbkplx",
        "mdnwhplx", "mesamkv", "msamkwd", "msbogdul", "orngr120", "roljazz", "slvrface", "supro",
        "tnxablk", "tnxared", "whtmdrn", "woodamp",
    )),
    SkinGroup("Pedais", listOf(
        "bigmuff", "bossblk", "bossslvr", "bossyel", "fuzzred", "fuzzslvr", "ibnzblue", "ibnzdblu",
        "ibnzgrn", "ibnzred", "klongld", "lifepdl", "mngglry", "mxrdbbl", "mxrdblrd", "mxrsgorg",
        "mxrsngbk", "mxrsnggd", "mxrsnggn", "mxrsngwh", "mxrsngyl", "ratyell",
    )),
)
