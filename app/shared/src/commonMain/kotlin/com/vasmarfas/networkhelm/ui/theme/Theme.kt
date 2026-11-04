@file:UseSerializers(HexColor::class, DpNumber::class)

package com.vasmarfas.networkhelm.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.LocalContentColor
import com.composeunstyled.LocalTextStyle
import com.composeunstyled.theme.rememberColoredIndication
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.compose.resources.Font
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.inter_medium
import networkhelm.app.shared.generated.resources.inter_regular
import networkhelm.app.shared.generated.resources.inter_semibold
import networkhelm.app.shared.generated.resources.jetbrains_mono_bold
import networkhelm.app.shared.generated.resources.jetbrains_mono_regular

@Immutable
@Serializable
data class Palette(
    val workspace: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val selection: Color,
    val badge: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val title: Color? = null,
    val grid: Color? = null,
    val frame: Color? = null,
    val inactiveTitle: Color? = null,
    val tab: Color? = null,
    val divider: Color? = null,
    val band: Color? = null,
    val highlight: Color? = null,
    val shadow: Color? = null,
)

val Palette.lit: Color get() = highlight ?: Color.White

val Palette.shaded: Color get() = shadow ?: Color(0xFFE4E4E4)

val DarkPalette = Palette(
    workspace = Color(0xFF23272D),
    surface = Color(0xFF1C2025),
    raised = Color(0xFF2A2F36),
    line = Color(0xFF353B43),
    text = Color(0xFFE4E7EB),
    muted = Color(0xFF9AA3AE),
    faint = Color(0xFF646C76),
    accent = Color(0xFF3D8BFD),
    onAccent = Color(0xFFFFFFFF),
    selection = Color(0xFF24456F),
    badge = Color(0xFF343A42),
    danger = Color(0xFFF0645C),
    success = Color(0xFF4CB782),
    warning = Color(0xFFE5A33A),
    title = Color(0xFF1B3A56),
    grid = Color(0xFF2A2F36),
    inactiveTitle = Color(0xFF343A42),
)

val LightPalette = Palette(
    workspace = Color(0xFFE9ECEF),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFF3F5F7),
    line = Color(0xFFD5DAE0),
    text = Color(0xFF1E2329),
    muted = Color(0xFF5F6873),
    faint = Color(0xFFA3ABB4),
    accent = Color(0xFF1F6FD9),
    onAccent = Color(0xFFFFFFFF),
    selection = Color(0xFFCFE2FC),
    badge = Color(0xFFE3E7EB),
    danger = Color(0xFFD23B33),
    success = Color(0xFF23895A),
    warning = Color(0xFFB97A12),
    grid = Color(0xFFDFE3E7),
)

val LegacyPalette = Palette(
    workspace = Color(0xFFA0A0A0),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFF0F0F0),
    line = Color(0xFFA0A0A0),
    text = Color(0xFF000000),
    muted = Color(0xFF000000),
    faint = Color(0xFF6D6D6D),
    accent = Color(0xFF4D4FCC),
    onAccent = Color(0xFFFFFFFF),
    selection = Color(0xFF9DC0EA),
    badge = Color(0xFFFFFFFF),
    danger = Color(0xFFFF0000),
    success = Color(0xFF008000),
    warning = Color(0xFFB07800),
    grid = Color(0xFFE3E3E3),
    frame = Color(0xFF000000),
    inactiveTitle = Color(0xFFC0C0C0),
    tab = Color(0xFFD8D8D8),
    divider = Color(0xFFDDDDDD),
    band = Color(0xFF3F3FC2),
    highlight = Color(0xFFFFFFFF),
    shadow = Color(0xFFE4E4E4),
)

val LegacyDarkPalette = Palette(
    workspace = Color(0xFF26262A),
    surface = Color(0xFF1E1E21),
    raised = Color(0xFF3C3C40),
    line = Color(0xFF808086),
    text = Color(0xFFF2F2F4),
    muted = Color(0xFFD4D4D8),
    faint = Color(0xFF9C9CA2),
    accent = Color(0xFF98A4FF),
    onAccent = Color(0xFFFFFFFF),
    selection = Color(0xFF34609E),
    badge = Color(0xFF1E1E21),
    danger = Color(0xFFFF7070),
    success = Color(0xFF62D690),
    warning = Color(0xFFF4B544),
    title = Color(0xFF3D3FB5),
    grid = Color(0xFF35353A),
    frame = Color(0xFFB0B0B6),
    inactiveTitle = Color(0xFF5A5A60),
    tab = Color(0xFF2F2F33),
    divider = Color(0xFF505056),
    band = Color(0xFF3D3FB5),
    highlight = Color(0xFF5E5E64),
    shadow = Color(0xFF29292D),
)

@Immutable
@Serializable
data class Metrics(
    val row: Dp = 24.dp,
    val menuItem: Dp = 26.dp,
    val control: Dp = 26.dp,
    val title: Dp = 30.dp,
    val toolbar: Dp = 28.dp,
    val icon: Dp = 16.dp,
    val gap: Dp = 6.dp,
    val padding: Dp = 8.dp,
    val radius: Dp = 4.dp,
)

@Immutable
class Typography(
    val body: TextStyle,
    val strong: TextStyle,
    val small: TextStyle,
    val section: TextStyle,
    val mono: TextStyle,
)

// Inter draws -> and <-> as arrows by default; router values and titles show the characters as typed
private const val LITERAL = "calt 0"

internal expect val textRasterization: PlatformTextStyle?

// a browser canvas has no system fonts, and its fallback fetches fonts from the network
internal expect val systemFonts: Boolean

private fun typography(sans: FontFamily, mono: FontFamily, size: Float = 13f) = Typography(
    body = TextStyle(fontFamily = sans, fontSize = size.sp, lineHeight = (size + 5).sp, fontFeatureSettings = LITERAL, platformStyle = textRasterization),
    strong = TextStyle(fontFamily = sans, fontSize = size.sp, lineHeight = (size + 5).sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = LITERAL, platformStyle = textRasterization),
    small = TextStyle(fontFamily = sans, fontSize = (size - 1).sp, lineHeight = (size + 3).sp, fontFeatureSettings = LITERAL, platformStyle = textRasterization),
    section = TextStyle(
        fontFamily = sans,
        fontSize = (size - 2).sp,
        lineHeight = (size + 1).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = LITERAL,
        platformStyle = textRasterization,
    ),
    mono = TextStyle(fontFamily = mono, fontSize = (size - 1).sp, lineHeight = (size + 4).sp, platformStyle = textRasterization),
)

enum class ThemeFont {
    Inter,
    System,
}

@Serializable
data class ThemeFile(
    val name: String,
    val light: Palette? = null,
    val dark: Palette? = null,
    val metrics: Metrics = Metrics(),
    val fontSize: Float = 13f,
    val font: ThemeFont = ThemeFont.Inter,
    val classic: Boolean = false,
    val icons: Map<String, String> = emptyMap(),
    val followsSystem: Boolean = true,
) {
    fun palette(dark: Boolean): Palette = (if (dark) this.dark ?: light else light ?: this.dark) ?: DarkPalette

    fun isDark(chosen: Boolean?, system: Boolean): Boolean = chosen ?: (followsSystem && system)
}

val Modern = ThemeFile("Modern", LightPalette, DarkPalette)

val Legacy = ThemeFile(
    "Legacy",
    light = LegacyPalette,
    dark = LegacyDarkPalette,
    metrics = Metrics(row = 16.dp, menuItem = 20.dp, control = 18.dp, title = 20.dp, toolbar = 22.dp, icon = 14.dp, gap = 4.dp, padding = 6.dp, radius = 0.dp),
    fontSize = 11f,
    font = ThemeFont.System,
    classic = true,
    followsSystem = false,
)

val BundledThemes = listOf(Modern, Legacy)

fun Color.hex(): String {
    val argb = toArgb().toUInt().toString(16).padStart(8, '0').uppercase()
    return "#" + if (argb.startsWith("FF")) argb.drop(2) else argb
}

fun parseColor(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    val value = digits.toLongOrNull(16) ?: return null
    return Color(if (digits.length == 6) value or 0xFF000000 else value)
}

object HexColor : KSerializer<Color> {
    override val descriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeString(value.hex())

    override fun deserialize(decoder: Decoder): Color {
        val text = decoder.decodeString()
        return parseColor(text) ?: throw SerializationException("$text is not a colour such as #1C2025")
    }
}

object DpNumber : KSerializer<Dp> {
    override val descriptor = PrimitiveSerialDescriptor("Dp", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: Dp) = encoder.encodeFloat(value.value)

    override fun deserialize(decoder: Decoder): Dp = decoder.decodeFloat().dp
}

private val LocalPalette = staticCompositionLocalOf { DarkPalette }
private val LocalMetrics = staticCompositionLocalOf { Metrics() }
private val LocalTypography = staticCompositionLocalOf { typography(FontFamily.SansSerif, FontFamily.Monospace) }
private val LocalClassic = staticCompositionLocalOf { false }
private val LocalIcons = staticCompositionLocalOf { emptyMap<String, ThemeIcon>() }

object Theme {
    val palette: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    val metrics: Metrics
        @Composable @ReadOnlyComposable get() = LocalMetrics.current

    val type: Typography
        @Composable @ReadOnlyComposable get() = LocalTypography.current

    val classic: Boolean
        @Composable @ReadOnlyComposable get() = LocalClassic.current

    val icons: Map<String, ThemeIcon>
        @Composable @ReadOnlyComposable get() = LocalIcons.current
}

fun ThemeFile.drawnIcons(): Map<String, ThemeIcon> =
    (DefaultIcons + (if (classic) ClassicIcons else emptyMap()) + icons).mapNotNull { (key, spec) -> themeIcon(spec)?.let { key to it } }.toMap()

@Composable
fun NetworkHelmTheme(theme: ThemeFile = Modern, dark: Boolean = isSystemInDarkTheme(), scale: Float = 1f, content: @Composable () -> Unit) {
    val palette = theme.palette(dark)
    val sans = if (theme.font == ThemeFont.System && systemFonts) {
        FontFamily.SansSerif
    } else {
        FontFamily(
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semibold, FontWeight.SemiBold),
        )
    }
    val mono = FontFamily(Font(Res.font.jetbrains_mono_regular, FontWeight.Normal), Font(Res.font.jetbrains_mono_bold, FontWeight.Bold))
    val type = typography(sans, mono, theme.fontSize.coerceIn(9f, 20f))
    val density = LocalDensity.current
    val icons = remember(theme) { theme.drawnIcons() }
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * scale.coerceIn(0.67f, 2.5f), density.fontScale),
        LocalPalette provides palette,
        LocalMetrics provides theme.metrics,
        LocalClassic provides theme.classic,
        LocalIcons provides icons,
        LocalTypography provides type,
        LocalContentColor provides palette.text,
        LocalTextStyle provides type.body,
        LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.selection),
        LocalIndication provides rememberColoredIndication(palette.text, hoveredAlpha = 0.1f, pressedAlpha = 0.18f),
        content = content,
    )
}
