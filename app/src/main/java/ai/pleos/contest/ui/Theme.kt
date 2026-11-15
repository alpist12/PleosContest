package ai.pleos.contest.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** UI 시안의 차량용 토큰. 이름은 시안의 Spec-System 보드와 같게 둔다. */
object Tok {
    val bg = Color(0xFF0E1A24)
    val surface = Color(0xFF15242F)
    val ink = Color(0xFFF2F6F7)
    val muted = Color(0xFFB8C7CF)
    val dim = Color(0xFF9FB2BB)
    val accent = Color(0xFFA9D8F0)
    val cta = Color(0xFF8EC9E6)
    val onCta = Color(0xFF0E2233)
    val warn = Color(0xFFE5B55F)
    val warnInk = Color(0xFFF1D9A8)
    val hairline = Color(0x29FFFFFF)
}

/** 차량 화면은 대비와 큰 글자가 먼저다. 장식용 그라데이션은 쓰지 않는다. */
private val CarType = Typography(
    displayLarge = TextStyle(fontSize = 68.sp, lineHeight = 79.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
)

@Composable
fun ChaegimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Tok.bg,
            surface = Tok.surface,
            onBackground = Tok.ink,
            onSurface = Tok.ink,
            primary = Tok.cta,
            onPrimary = Tok.onCta,
        ),
        typography = CarType,
        content = content,
    )
}
