package ai.pleos.warninglight.ui

import ai.pleos.warninglight.data.Severity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val bg = Color(0xFF0E141B)
    val surface = Color(0xFF17202A)
    val surface2 = Color(0xFF212C38)
    val line = Color(0xFF2E3B49)
    val text = Color(0xFFF2F5F8)
    val textDim = Color(0xFF9AA8B6)
    val red = Color(0xFFE5484D)
    val amber = Color(0xFFF5B800)
    val blue = Color(0xFF3E8EF7)
    val green = Color(0xFF30A46C)

    fun of(s: Severity) = when (s) {
        Severity.STOP_NOW -> red
        Severity.SERVICE_SOON -> amber
        Severity.INFO -> blue
    }

    /** 배경색 위 글자색 (황색 위에서는 어두운 글자). */
    fun on(s: Severity) = if (s == Severity.SERVICE_SOON) Color(0xFF1A1300) else Color.White
}

@Composable
fun WarningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.bg,
            surface = Palette.surface,
            primary = Palette.blue,
            onSurface = Palette.text,
            onBackground = Palette.text,
        ),
        content = content,
    )
}
