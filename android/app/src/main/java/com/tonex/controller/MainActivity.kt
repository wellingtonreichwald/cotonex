package com.tonex.controller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tonex.controller.midi.MidiScale
import com.tonex.controller.ui.theme.Dim
import com.tonex.controller.ui.theme.Gold
import com.tonex.controller.ui.theme.Line
import com.tonex.controller.ui.theme.Muted
import com.tonex.controller.ui.theme.Orange
import com.tonex.controller.ui.theme.Panel
import com.tonex.controller.ui.theme.Screen
import com.tonex.controller.ui.theme.TonexTheme

/*
 * Quatro telas, a mesma paleta e a mesma estrutura do preview HTML
 * (preview-4-deitado.html / preview-4-de-pe.html).
 *
 * Toda a tabela — presets, blocos, CCs, painéis, tipos e skins — vem de
 * [Model.kt], gerado a partir do preview. Nada de dado duplicado aqui.
 *
 * O que já é real: rótulos, mapa CC → índice USB ([MidiScale]),
 * grade de bancos, separação por tipo e a tela de Config com CC editável.
 * O que ainda não é: sessão USB/BLE (M0) e os PNGs das skins.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TonexTheme { AppShell() }
        }
    }
}

// ---------------------------------------------------------------------------
// Shell
// ---------------------------------------------------------------------------

private enum class Tab(val title: String) {
    CONTROLE("Controle"), PARAMS("Parâmetros"), IMAGEM("Imagem"), CONFIG("Config")
}

@Composable
fun AppShell() {
    var tab by remember { mutableStateOf(Tab.CONTROLE) }
    var current by remember { mutableIntStateOf(3) }
    var bank by remember { mutableIntStateOf(1) }
    var channel by remember { mutableIntStateOf(1) }
    val skinOf = remember { mutableStateOf(PRESETS.associate { it.n to it.skin }) }
    val blockOn = remember {
        mutableStateMapOf<String, Boolean>().apply { BLOCKS.forEach { put(it.id, it.on) } }
    }
    val selOf = remember {
        mutableStateMapOf<String, Int>().apply {
            PANES.forEach { p -> p.rows.forEachIndexed { i, r -> if (r.kind == RowKind.SEL) put(p.id + ":" + i, r.selIndex) } }
        }
    }
    val valOf = remember {
        mutableStateMapOf<String, Float>().apply {
            PANES.forEach { p -> p.rows.forEachIndexed { i, r -> if (r.kind == RowKind.SL) put(p.id + ":" + i, r.def) } }
        }
    }
    /** Texto digitado na aba Config, por referência (`nav:preset`, `blk:gate`, `gate:Limiar`). */
    val ccText = remember { mutableStateMapOf<String, String>() }
    /** Estado dos switches que não são blocos (os blocos usam [blockOn]). */
    val swOf = remember { mutableStateMapOf<String, Boolean>() }
    val log = remember {
        mutableStateOf("Toque num preset, num bloco ou num parâmetro para ver a mensagem que o app enviaria ao pedal.")
    }

    fun effCt(ref: String, base: Int?): Int =
        ((ccText[ref]?.toIntOrNull()) ?: base ?: 0).coerceIn(0, 127)

    fun swGet(pane: PaneCfg, row: RowCfg, idx: Int): Boolean =
        if (row.blk != null) blockOn[row.blk] ?: row.on else swOf[pane.id + ":" + idx] ?: row.on

    /*
     * Permissão de runtime — declarar no manifest NÃO basta desde o API 31.
     * Sem BLUETOOTH_SCAN o scan do Chocolate Plus devolve lista vazia em vez de
     * erro, o que parece bug de hardware e não é. Em API 26–30 o BLE ainda cobra
     * localização, por isso a lista muda com a versão.
     */
    val ctx = LocalContext.current
    var btOk by remember { mutableStateOf<Boolean?>(null) }
    val askBluetooth = rememberLauncherForActivityResult(
        ActivityContracts.RequestMultiplePermissions()
    ) { res -> btOk = res.values.all { it } }

    fun requestBluetooth() {
        val need = (if (Build.VERSION.SDK_INT >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }).filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isEmpty()) btOk = true else askBluetooth.launch(need.toTypedArray())
    }

    LaunchedEffect(Unit) { requestBluetooth() }

    Scaffold(
        containerColor = Screen,
        bottomBar = {
            NavigationBar(containerColor = Panel, modifier = Modifier.height(56.dp)) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.title, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        icon = {},
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Orange,
                            selectedTextColor = Orange,
                            unselectedTextColor = Dim,
                            indicatorColor = Panel,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (btOk == false) {
                Text(
                    "Bluetooth bloqueado — o Chocolate Plus não vai aparecer. Toque para liberar.",
                    color = Orange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3A2418))
                        .clickable { requestBluetooth() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            when (tab) {
                Tab.CONTROLE -> ControleScreen(
                    current = current,
                    bank = bank,
                    channel = channel,
                    skinKey = skinOf.value[current] ?: "",
                    blockOn = blockOn,
                    log = log.value,
                    onPreset = { n ->
                        current = n
                        bank = (n - 1) / 10 + 1
                        log.value = "selecionar preset · CC 127 = ${n - 1} → índice 0-based no pedal"
                    },
                    onBank = { b ->
                        bank = b
                        val first = (b - 1) * 10 + 1
                        if ((current - 1) / 10 + 1 != b) {
                            current = first
                            log.value = "banco $b · preset atual passa a ser $first → CC 127 = ${first - 1}"
                        } else {
                            log.value = "banco $b · presets $first a ${first + 4} em cima, ${first + 5} a ${first + 9} embaixo"
                        }
                    },
                    onBlock = { b ->
                        val now = !(blockOn[b.id] ?: b.on)
                        blockOn[b.id] = now
                        val cc = effCt("blk:${b.id}", b.cc)
                        val v = if (now) b.vOn else b.vOff
                        log.value = "CC $cc = ${if (now) 127 else 0} → USB param[${b.pn}] ${b.label} = $v"
                    },
                )

                Tab.PARAMS -> ParametrosScreen(
                    swGet = { p, r, i -> swGet(p, r, i) },
                    selOf = selOf,
                    valOf = valOf,
                    onSlider = { pane, row, idx, v ->
                        valOf[pane.id + ":" + idx] = v
                        val cc = effCt(pane.id + ":" + row.label, row.cc)
                        val midi = MidiScale.toMidi(v, row.min, row.max)
                        log.value = logLine(
                            cc, midi, row.effectivePn(modelSel(pane, row, selOf)),
                            row.label, "${fmt(v)}${row.unit}",
                        )
                    },
                    onSwitch = { pane, row, idx, on ->
                        if (row.blk != null) blockOn[row.blk] = on
                        else swOf[pane.id + ":" + idx] = on
                        val cc = effCt(pane.id + ":" + row.label, row.cc)
                        log.value = logLine(cc, if (on) 127 else 0, row.pn, row.label, if (on) "1" else "0")
                    },
                    onSelect = { pane, row, idx, opt, optIdx ->
                        selOf[pane.id + ":" + idx] = optIdx
                        // Pré/Pós do EQ é o mesmo parâmetro do tile em Controle
                        if (row.blk != null) blockOn[row.blk] = optIdx == 1
                        val cc = effCt(pane.id + ":" + row.label, row.cc)
                        log.value = logLine(
                            cc, optIdx, row.effectivePn(modelSel(pane, row, selOf)),
                            row.label, opt,
                        )
                    },
                )

                Tab.IMAGEM -> ImagemScreen(
                    preset = current,
                    name = PRESETS.first { it.n == current }.name,
                    selected = skinOf.value[current] ?: "",
                    onPick = { key ->
                        skinOf.value = skinOf.value + (current to key)
                        log.value = "preset $current → imagem $key (guardada no celular; não vai ao pedal)"
                    },
                )

                Tab.CONFIG -> ConfigScreen(
                    channel = channel,
                    ccText = ccText,
                    onChannel = { c ->
                        channel = c
                        log.value = "canal MIDI → o CoToneX passa a escutar só o canal $c"
                    },
                    onCc = { ref, txt -> ccText[ref] = txt },
                )
            }
        }
    }
}

/** Linha de log idêntica à do preview. [cc] nulo = o firmware não tem CC para isso. */
private fun logLine(cc: Int?, midi: Int, pn: Int, label: String, value: String): String =
    if (cc == null) "sem CC de entrada → USB param[$pn] $label = $value"
    else "CC $cc = $midi → USB param[$pn] $label = $value"

/**
 * Qual índice o slider deve comandar quando o bloco tem conjuntos por modelo
 * (Reverb): os quatro ajustes mudam de `pn` junto com o Modelo.
 */
private fun modelSel(pane: PaneCfg, row: RowCfg, selOf: Map<String, Int>): Int {
    if (row.pnList.isEmpty()) return 0
    val i = pane.rows.indexOfFirst { it.kind == RowKind.SEL && it.opts.size == row.pnList.size }
    if (i < 0) return 0
    return selOf[pane.id + ":" + i] ?: 0
}

private fun fmt(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)

private fun rangeOf(bank: Int, meia: Int): IntRange {
    val ini = (bank - 1) * 10 + if (meia == 1) 6 else 1
    return ini..(ini + 4)
}

// ---------------------------------------------------------------------------
// Tela 1 — Controle
// ---------------------------------------------------------------------------

@Composable
private fun ControleScreen(
    current: Int,
    bank: Int,
    channel: Int,
    skinKey: String,
    blockOn: Map<String, Boolean>,
    log: String,
    onPreset: (Int) -> Unit,
    onBank: (Int) -> Unit,
    onBlock: (BlockCfg) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.width(236.dp)) {
                val p = PRESETS.first { it.n == current }
                Text(
                    "${p.n}: ${p.name}",
                    color = Gold, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                // Placeholder da skin: entra no M3 junto com os PNGs de skins_png/.
                Box(
                    Modifier.fillMaxWidth().height(70.dp)
                        .background(Panel, RoundedCornerShape(6.dp))
                        .border(1.dp, Line, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(skinKey, color = Dim, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Metric("Banco", bank.toString())
                    Metric("Canal", channel.toString())
                }
            }

            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ini = rangeOf(bank, 0).first
                    val fim = rangeOf(bank, 1).last
                    Text(
                        "Banco $bank  ·  presets $ini–$fim",
                        color = Muted, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1, 2).forEach { b ->
                            Text(
                                b.toString(),
                                color = if (b == bank) Color(0xFF1A1206) else Muted,
                                fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .background(if (b == bank) Orange else Panel, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (b == bank) Orange else Line, RoundedCornerShape(6.dp))
                                    .clickable { onBank(b) }
                                    .padding(horizontal = 13.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                BankCard(rangeOf(bank, 0), current, onPreset)
                Spacer(Modifier.height(8.dp))
                BankCard(rangeOf(bank, 1), current, onPreset)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BLOCKS.forEach { b ->
                val on = blockOn[b.id] ?: b.on
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (on) Color(0xFF2E2419) else Panel, RoundedCornerShape(6.dp))
                        .border(1.dp, if (on) Orange.copy(alpha = 0.6f) else Line, RoundedCornerShape(6.dp))
                        .clickable { onBlock(b) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(20.dp).height(16.dp)
                            .background(if (on) Orange else Dim, RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        b.label,
                        color = if (on) Orange else Dim,
                        fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Text(
            log,
            color = Color(0xFF8FB8DE),
            fontSize = 9.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .background(Color(0xFF171717), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BankCard(range: IntRange, current: Int, onPreset: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF15212B), RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
    ) {
        Text(
            "presets ${range.first}–${range.last}",
            color = Muted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            range.forEach { n ->
                val p = PRESETS.first { it.n == n }
                val sel = n == current
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (sel) Color(0xFF31261B) else Panel, RoundedCornerShape(7.dp))
                        .border(1.dp, if (sel) Orange else Line, RoundedCornerShape(7.dp))
                        .clickable { onPreset(n) }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(p.color)))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        p.n.toString(),
                        color = if (sel) Orange else Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        p.name,
                        color = if (sel) Color(0xFFE8C9A5) else Muted,
                        fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(key: String, value: String) =
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(key, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Gold, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }

// ---------------------------------------------------------------------------
// Tela 2 — Parâmetros (tipos separados: Ligar/Desligar · Escolhes · Ajustes)
// ---------------------------------------------------------------------------

@Composable
private fun ParametrosScreen(
    swGet: (PaneCfg, RowCfg, Int) -> Boolean,
    selOf: Map<String, Int>,
    valOf: Map<String, Float>,
    onSlider: (PaneCfg, RowCfg, Int, Float) -> Unit,
    onSwitch: (PaneCfg, RowCfg, Int, Boolean) -> Unit,
    onSelect: (PaneCfg, RowCfg, Int, String, Int) -> Unit,
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val pane = PANES[tabIndex]

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PANES.forEachIndexed { i, p ->
                val on = i == tabIndex
                Text(
                    p.label,
                    color = if (on) Color(0xFF1A1206) else Muted,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(if (on) Orange else Panel, RoundedCornerShape(16.dp))
                        .border(1.dp, if (on) Orange else Line, RoundedCornerShape(16.dp))
                        .clickable { tabIndex = i }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            pane.rows.forEachIndexed { idx, row ->
                when (row.kind) {
                    RowKind.HD -> TypeHead(row.label)
                    RowKind.SW -> {
                        SwitchRow(
                            label = row.label,
                            checked = swGet(pane, row, idx),
                            onCheckedChange = { onSwitch(pane, row, idx, it) },
                        )
                    }
                    RowKind.SEL -> {
                        val cur = selOf[pane.id + ":" + idx] ?: row.selIndex
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.label,
                                    color = Color(0xFFE7E7E7), fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                                )
                                Text(
                                    row.opts.getOrElse(cur) { "" },
                                    color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                row.opts.forEachIndexed { k, o ->
                                    val sel = k == cur
                                    Text(
                                        o,
                                        color = if (sel) Color(0xFF1A1206) else Muted,
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(
                                                if (sel) Orange else Panel,
                                                RoundedCornerShape(10.dp),
                                            )
                                            .border(
                                                1.dp, if (sel) Orange else Line,
                                                RoundedCornerShape(10.dp),
                                            )
                                            .clickable { onSelect(pane, row, idx, o, k) }
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                    RowKind.SL -> {
                        val v = valOf[pane.id + ":" + idx] ?: row.def
                        Row(
                            Modifier.fillMaxWidth().padding(top = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                row.label,
                                color = Color(0xFFE7E7E7), fontSize = 11.5.sp,
                                modifier = Modifier.width(96.dp),
                            )
                            Slider(
                                value = v,
                                onValueChange = { onSlider(pane, row, idx, it) },
                                valueRange = row.min..row.max,
                                colors = SliderDefaults.colors(
                                    thumbColor = Orange, activeTrackColor = Orange,
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                            )
                            Text(
                                "${fmt(v)}${row.unit}",
                                color = Orange, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.width(62.dp), textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun TypeHead(label: String) {
    Text(
        label.uppercase(),
        color = Orange,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color(0xFFE7E7E7), fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1A1206),
                checkedTrackColor = Orange,
                checkedBorderColor = Orange,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Color(0xFF424242),
                uncheckedBorderColor = Line,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Tela 3 — Imagem (amplificadores e pedais em grupos separados)
// ---------------------------------------------------------------------------

@Composable
private fun ImagemScreen(preset: Int, name: String, selected: String, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("Imagem do preset", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Escolhida à mão por preset — o TONEX One não guarda imagem nenhuma: a foto fica " +
                "salva só no celular. Editando o preset $preset ($name); na aba Controle é ela " +
                "que aparece embaixo do nome.",
            color = Muted, fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))

        SKIN_GROUPS.forEach { g ->
            Text(
                "${g.label} · ${g.keys.size}",
                color = Orange, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            g.keys.chunked(3).forEach { line ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    line.forEach { key ->
                        val on = key == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .background(if (on) Color(0xFF2C2717) else Panel, RoundedCornerShape(6.dp))
                                .border(1.dp, if (on) Gold else Line, RoundedCornerShape(6.dp))
                                .clickable { onPick(key) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // PNG real entra no M3 ("5150" não é nome válido em res/drawable).
                            Box(
                                Modifier.fillMaxWidth().height(32.dp)
                                    .background(Color(0xFF242424), RoundedCornerShape(3.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text("png", color = Dim, fontSize = 9.sp) }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                key,
                                color = if (on) Gold else Muted,
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Tela 4 — Config (CC de cada ação, agrupado por tipo)
// ---------------------------------------------------------------------------

/** Uma seção da aba Config: navegação, blocos, ou um painel de parâmetros. */
private class CfgSection(val title: String, val groups: List<CfgGroup>)

/** Um grupo de tipo dentro da seção — só existe quando tem linha. */
private class CfgGroup(val title: String, val items: List<CfgItem>)

private class CfgItem(val ref: String, val label: String, val base: Int)

/** Ordem dos grupos, a mesma do preview (`const TIPOS`). */
private val TIPOS = listOf("sw" to "Ligar/Desligar", "sel" to "Escolhes", "sl" to "Ajustes")

@Composable
private fun ConfigScreen(
    channel: Int,
    ccText: Map<String, String>,
    onChannel: (Int) -> Unit,
    onCc: (String, String) -> Unit,
) {
    val sections = remember { buildSections() }
    val eff: (CfgItem) -> Int = { s -> ((ccText[s.ref]?.toIntOrNull()) ?: s.base).coerceIn(0, 127) }
    val counts = sections.flatMap { s -> s.groups.flatMap { g -> g.items } }
        .groupingBy { eff(it) }.eachCount()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            "Comandos do Chocolate Plus",
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Cada ação do CoToneX tem um CC próprio. Como o Chocolate conversa com o celular — " +
                "não direto com o pedal — qualquer CC serve: escolha os que o seu outro " +
                "controlador não usa e não há conflito.",
            color = Muted, fontSize = 11.sp,
        )
        Spacer(Modifier.height(9.dp))

        Text("Canal MIDI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (1..16).forEach { c ->
                val on = c == channel
                Text(
                    c.toString(),
                    color = if (on) Color(0xFF1A1206) else Muted,
                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(if (on) Gold else Panel, RoundedCornerShape(6.dp))
                        .border(1.dp, if (on) Gold else Line, RoundedCornerShape(6.dp))
                        .clickable { onChannel(c) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "Se outro equipamento estiver no canal 1, mexa aqui e o CoToneX passa a escutar " +
                "só o canal escolhido.",
            color = Dim, fontSize = 10.sp,
        )
        Spacer(Modifier.height(11.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                sections.filterIndexed { i, _ -> i % 2 == 0 }.forEach {
                    SectionBlock(it, channel, ccText, eff, counts, onCc)
                }
            }
            Column(Modifier.weight(1f)) {
                sections.filterIndexed { i, _ -> i % 2 == 1 }.forEach {
                    SectionBlock(it, channel, ccText, eff, counts, onCc)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "CC 56 e 57 são novos: Amp e Cab não têm CC no firmware (eram só USB). O Chocolate " +
                "manda o CC que você escolher e o CoToneX converte para param[18] e param[24] " +
                "por USB. CC repetido aparece em vermelho — dois comandos não podem disputar o " +
                "mesmo número.",
            color = Dim, fontSize = 10.sp,
        )
        Spacer(Modifier.height(16.dp))
    }
}

private fun buildSections(): List<CfgSection> {
    val out = mutableListOf<CfgSection>()
    out += CfgSection(
        "Bancos e presets",
        listOf(CfgGroup("", NAV_ACTIONS.map { CfgItem("nav:${it.key}", it.label, it.cc) })),
    )
    out += CfgSection(
        "Blocos",
        listOf(CfgGroup("", BLOCKS.map { CfgItem("blk:${it.id}", it.label, it.cc) })),
    )
    PANES.forEach { p ->
        val rows = p.rows.filter { it.kind != RowKind.HD && it.cc != null && it.blk == null }
        if (rows.isEmpty()) return@forEach
        val groups = TIPOS.mapNotNull { (k, tl) ->
            val kind = when (k) {
                "sw" -> RowKind.SW
                "sel" -> RowKind.SEL
                else -> RowKind.SL
            }
            val rs = rows.filter { it.kind == kind }
            if (rs.isEmpty()) null
            else CfgGroup(tl, rs.map { CfgItem("${p.id}:${it.label}", "${p.label} · ${it.label}", it.cc!!) })
        }
        out += CfgSection(p.label, groups)
    }
    return out
}

@Composable
private fun SectionBlock(
    section: CfgSection,
    channel: Int,
    ccText: Map<String, String>,
    eff: (CfgItem) -> Int,
    counts: Map<Int, Int>,
    onCc: (String, String) -> Unit,
) {
    Text(
        section.title,
        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    section.groups.forEach { g ->
        if (g.title.isNotEmpty()) {
            Text(
                g.title.uppercase(),
                color = Orange, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 3.dp),
            )
        }
        g.items.forEach { item -> CfgRow(item, channel, ccText, eff, counts, onCc) }
    }
}

@Composable
private fun CfgRow(
    item: CfgItem,
    channel: Int,
    ccText: Map<String, String>,
    eff: (CfgItem) -> Int,
    counts: Map<Int, Int>,
    onCc: (String, String) -> Unit,
) {
    val text = ccText[item.ref] ?: item.base.toString()
    val cc = eff(item)
    val conflict = (counts[cc] ?: 0) > 1
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.label,
            color = if (conflict) Color(0xFFE85D4C) else Color(0xFFE7E7E7),
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Ch $channel",
            color = Dim, fontSize = 9.5.sp, modifier = Modifier.padding(end = 6.dp),
        )
        BasicTextField(
            value = text,
            onValueChange = { s ->
                if (s.length <= 3) onCc(item.ref, s.filter { it.isDigit() })
            },
            modifier = Modifier
                .width(46.dp)
                .background(Color(0xFF171717), RoundedCornerShape(5.dp))
                .border(1.dp, if (conflict) Color(0xFFE85D4C) else Line, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            textStyle = TextStyle(
                color = if (conflict) Color(0xFFE85D4C) else Orange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(Orange),
        )
        Text(
            "CC",
            color = Dim, fontSize = 9.sp, modifier = Modifier.padding(start = 5.dp),
        )
    }
}
