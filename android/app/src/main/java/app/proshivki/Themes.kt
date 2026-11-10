// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package app.proshivki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Один набор токенов на тему; компоненты общие (Ui.kt). */
data class Tokens(
    val id: String,
    val name: String,
    val bg: Color,
    val card: Color,
    val line: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val onAccent: Color,
    val radius: Int,
    val font: FontFamily,
    val borderWidth: Int,
    val okBg: Color, val okFg: Color,
    val waitBg: Color, val waitFg: Color,
    val noBg: Color, val noFg: Color,
    val unkBg: Color, val unkFg: Color,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

// Шрифты (ttf, SIL OFL 1.1) лежат в res/font; переменные шрифты берут нужные веса через FontVariation (API 26+).
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)
private val Sans = FontFamily.SansSerif
private val Manrope = variable(R.font.manrope, 400, 500, 700, 800)
private val SpaceGrotesk = variable(R.font.space_grotesk, 400, 700)
private val JetBrains = variable(R.font.jetbrains_mono, 400, 700)
private val Rubik = variable(R.font.rubik, 400, 500, 700)
private val Inter = variable(R.font.inter, 400, 700)
private val Nunito = variable(R.font.nunito, 400, 700)
private val Exo2 = variable(R.font.exo_2, 400, 700)
private val Vt323 = FontFamily(Font(R.font.vt323, FontWeight.Normal))
private val Atkinson = FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal), Font(R.font.atkinson_bold, FontWeight.Bold))

val Themes: List<Tokens> = listOf(
    Tokens("soft", "Мягкий светлый", c(0xF4F3FA), c(0xFFFFFF), c(0xEFEDF7), c(0x1B1830), c(0x6B6785), c(0x5B4CF0), c(0xFFFFFF), 26, Manrope, 0,
        c(0xDDF5E6), c(0x14653A), c(0xFFF0C7), c(0x7A5300), c(0xFFE0DC), c(0x9A2414), c(0xECEBF2), c(0x5B5870)),
    Tokens("navy", "Тёмно-синий", c(0x0B0D11), c(0x14171D), c(0x22262E), c(0xE8EAED), c(0x9AA0A6), c(0x8AB4F8), c(0x0B0D11), 18, Sans, 1,
        c(0x1E3A2A), c(0x81C995), c(0x3A3018), c(0xFDD663), c(0x3A2020), c(0xF28B82), c(0x1D2A3D), c(0x8AB4F8)),
    Tokens("techpass", "Техпаспорт", c(0xF2F1EC), c(0xF2F1EC), c(0x111111), c(0x111111), c(0x555555), c(0x111111), c(0xF2F1EC), 0, SpaceGrotesk, 2,
        c(0xC8F169), c(0x111111), c(0xFFD23F), c(0x111111), c(0xFF5A1F), c(0x111111), c(0xFFFFFF), c(0x111111)),
    Tokens("terminal", "Терминал", c(0x050805), c(0x050805), c(0x1F3A1F), c(0xB7F5B0), c(0x5F8A5A), c(0x39FF6A), c(0x050805), 0, JetBrains, 1,
        c(0x050805), c(0x39FF6A), c(0x050805), c(0xFFD23F), c(0x050805), c(0xFF5F56), c(0x050805), c(0x5F8A5A)),
    Tokens("material", "Material You", c(0x131316), c(0x1F1F25), c(0x2E2E36), c(0xE5E1E9), c(0xC8C5D0), c(0xC6BFFF), c(0x2A1F6E), 28, Rubik, 0,
        c(0x1E4D3A), c(0x9CF2C6), c(0x4D3F12), c(0xF5D97A), c(0x5C1F1A), c(0xFFB4AB), c(0x2E2E36), c(0xC8C5D0)),
    Tokens("amoled", "AMOLED", c(0x000000), c(0x0E0E0E), c(0x1A1A1A), c(0xF5F5F5), c(0x9A9A9A), c(0xFF7A1A), c(0x000000), 20, Inter, 0,
        c(0x0F2E1A), c(0x6BE39A), c(0x33260A), c(0xFFC24A), c(0x3A1010), c(0xFF7B6B), c(0x1A1A1A), c(0x9A9A9A)),
    Tokens("lcd", "Ретро LCD", c(0x9BAE84), c(0xAFC197), c(0x8FA276), c(0x1F2A14), c(0x3E4D2E), c(0x1F2A14), c(0xAFC197), 2, Vt323, 1,
        c(0x1F2A14), c(0xAFC197), c(0xAFC197), c(0x1F2A14), c(0x1F2A14), c(0xE4EED5), c(0xAFC197), c(0x3E4D2E)),
    Tokens("ocean", "Океан", c(0x06212B), c(0x0C3340), c(0x14495A), c(0xE3F6F8), c(0x8FB8C0), c(0x3FD2C7), c(0x06212B), 24, Nunito, 0,
        c(0x0E4A3E), c(0x7EF0C5), c(0x4A3A0E), c(0xFFD98A), c(0x4A1820), c(0xFF9AA6), c(0x14495A), c(0x8FB8C0)),
    Tokens("synth", "Синтвейв", c(0x140A26), c(0x22113D), c(0x2E1A50), c(0xF4E9FF), c(0xB39CD6), c(0xFF3EA5), c(0x140A26), 14, Exo2, 0,
        c(0x0E3A3A), c(0x3EF2E0), c(0x3D2A0A), c(0xFFC857), c(0x4A0E2E), c(0xFF7AC0), c(0x2E1A50), c(0xB39CD6)),
    Tokens("contrast", "Высокий контраст", c(0x000000), c(0x000000), c(0xFFFFFF), c(0xFFFFFF), c(0xE0E0E0), c(0xFFE500), c(0x000000), 6, Atkinson, 2,
        c(0x000000), c(0x7CFF6B), c(0x000000), c(0xFFE500), c(0x000000), c(0xFF8A80), c(0x000000), c(0xFFFFFF)),
)

fun themeById(id: String?): Tokens = Themes.firstOrNull { it.id == id } ?: Themes[0]
