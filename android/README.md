# CoToneX — Android

App Android (Kotlin + Jetpack Compose) que faz o mesmo que o firmware
`TonexOneController` do ESP32:

**Chocolate Plus (BLE MIDI) → app → TONEX One (USB/OTG)**

## Abrir

1. Android Studio (Ladybug ou mais novo) → *Open* nesta pasta `android/`.
2. Deixa o Gradle sincronizar (wrapper 8.11.1, AGP 8.7.3, Kotlin 2.0.21).
3. Roda no aparelho com Android 8.0+ (minSdk 26).

## O que já está implementado

| Camada | Arquivo | Estado |
|---|---|---|
| CRC-16/X-25 | `tonex/Crc.kt` | idêntico ao firmware |
| Framing `7E … 7E` + stuffing | `tonex/Frame.kt` | idêntico ao firmware |
| Mensagens de saída | `tonex/Codec.kt` | literais de `usb_tonex_one.c` |
| Cabeçalho de recepção | `tonex/Message.kt` | port de `tonex_common_parse_value` |
| CC ↔ escala/bool MIDI | `midi/TonexMidi.kt` | port de `midi_helper.c` |
| Transporte USB bulk | `usb/TonexUsbSession.kt` | estrutura pronta, **precisa de hardware** |
| UI (4 abas) | `MainActivity.kt` | fiel ao `preview-4-*.html` |
| Tabela da UI | `Model.kt` | **gerado** do preview (`outputs/gen_model.py`) |
| Tema | `ui/theme/Theme.kt` | paleta do `style.css` do firmware |

As 4 abas são **Controle** (preset atual + 2 bancos de 5+5 + 8 blocos + log),
**Parâmetros** (8 painéis, com subtítulos *Ligar/Desligar* / *Escolhes* /
*Ajustes*), **Imagem** (28 amps + 22 pedais) e **Config** (canal 1–16 e um CC
editável por comando, com conflito destacado). O app roda em **paisagem** —
`android:screenOrientation="landscape"` no manifest.

Ainda **não** tem: sessão BLE do Chocolate (M0/M1), sync de estado no boot,
imagens das skins (o repo tem os 50 PNGs em `skins_png/`, falta empacotar).

## Testes

```bash
cd android && ./gradlew test
```

`FramingTest` não usa valores escritos à mão: foram gerados compilando as
funções **reais** do firmware (`usb_tonex_common.c`) com `gcc` e imprimindo a
saída. Se o teste falhar, o Kotlin divergiu do ESP32 — não é para "consertar"
o teste.

Os vetores de referência (CRC `8C17`, `6644`, `27C8`, `3F7F`, `F426`, `9831`,
`8EA9`…) estão documentados nos comentários de cada `@Test`.

## Ponto aberto

O campo `size` do cabeçalho das mensagens que o app transmite está em **um a
mais** do que o parser do próprio firmware valida (`usb_tonex_one.c:1118`).
Enquanto não houver uma captura USB real, `Codec` emite byte a byte o que o
firmware emite e `Message.parse` roda em modo leniente de propósito. Ver o
bloco de KDoc no topo de `Codec.kt`.

## Nota sobre imagens

`tonenet.com` e `ikmultimedia.com` não são acessíveis deste ambiente (egress
allowlist). Os 50 skins do repo cobrem os amps; imagens que você baixe
manually podem ser dropadas em `app/src/main/res/drawable/` — renomeando, porque
`5150.png` não é nome de recurso válido.
