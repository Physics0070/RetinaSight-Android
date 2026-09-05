package com.retinasight.ai.ui.theme

import androidx.compose.ui.graphics.Color
import com.retinasight.ai.core.model.DrGrade

// Brand: a calm clinical teal. Trustworthy without looking like an alarm.
val Teal700 = Color(0xFF00695C)
val Teal500 = Color(0xFF00897B)
val Teal100 = Color(0xFFB2DFDB)
val Teal900 = Color(0xFF004D40)

val Surface = Color(0xFFFAFAFA)
val SurfaceDark = Color(0xFF121212)
val OnSurface = Color(0xFF1A1C1E)
val OnSurfaceDark = Color(0xFFE3E2E6)
val Outline = Color(0xFF74777F)

/**
 * The five-step severity scale, reused everywhere a grade is shown so the colour
 * always means the same thing.
 *
 * Each colour is dark enough to carry white text at AA contrast, because these
 * are used as large filled blocks and the phone is often held in sunlight.
 * Colour is never the ONLY signal - an icon and spoken words always accompany it.
 */
object SeverityPalette {
    val grade0 = Color(0xFF1B7F3B) // no DR        - green
    val grade1 = Color(0xFF6B7F00) // mild         - olive
    val grade2 = Color(0xFFB06E00) // moderate     - amber
    val grade3 = Color(0xFFC1440E) // severe       - orange red
    val grade4 = Color(0xFFA01818) // proliferative - red

    fun colorFor(grade: DrGrade): Color = when (grade) {
        DrGrade.NO_DR -> grade0
        DrGrade.MILD -> grade1
        DrGrade.MODERATE -> grade2
        DrGrade.SEVERE -> grade3
        DrGrade.PROLIFERATIVE -> grade4
    }

    /** Soft tint of the same hue, for backgrounds that carry dark text. */
    fun containerFor(grade: DrGrade): Color = when (grade) {
        DrGrade.NO_DR -> Color(0xFFD7F0DE)
        DrGrade.MILD -> Color(0xFFEAF0C9)
        DrGrade.MODERATE -> Color(0xFFFCEBCC)
        DrGrade.SEVERE -> Color(0xFFFADACE)
        DrGrade.PROLIFERATIVE -> Color(0xFFF7D4D4)
    }
}
