package com.retinasight.ai.ui.theme

import androidx.compose.ui.graphics.Color
import com.retinasight.ai.core.model.DrGrade

/**
 * Palette adopted from the AI Studio "Remix RetinaSight AI" design study.
 *
 * The design's clinical identity is kept intact - a deep navy primary, a calming
 * teal secondary and a cool slate neutral ramp, which reads as instrument panel
 * rather than consumer app. Followed exactly: both brand hues, every neutral,
 * and the five severity *tints*.
 *
 * What deliberately departs from it is the severity *fill*. See SeverityPalette.
 */

// Brand. Navy carries the app's chrome; teal is the interactive accent.
val DeepNavy = Color(0xFF0A2463)
val CalmingTeal = Color(0xFF247BA0)
val LaserCyan = Color(0xFF5EEAD4)

// Neutral ramp. A cool slate rather than a warm grey - it stops the retinal
// photograph, which is overwhelmingly orange, from casting the whole UI warm.
val MedicalBg = Color(0xFFF8FAFC)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF475569)
val OutlineBorder = Color(0xFFE2E8F0)

// Label grey for the dark instrument strip. Sits on DarkroomBg, not on the
// light surfaces, so it is defined apart from the light-theme neutrals.
val TextMutedHud = Color(0xFF94A3B8)

// Lesion / alert red, used by the scanner illustration to mark a detected
// microaneurysm. This is decoration on a drawn retina, never a reported grade -
// the grade's colour always comes from SeverityPalette below.
val LesionRed = Color(0xFFDC2626)

// Dark theme. The design calls this the "darkroom" - the ground for capture and
// analysis, where a bright UI would flare against the eyepiece.
val DarkroomBg = Color(0xFF070B14)
val DarkSurface = Color(0xFF0F172A)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkOutline = Color(0xFF334155)
val DarkOnBackground = Color(0xFFF1F5F9)
val DarkOnSurface = Color(0xFFF8FAFC)

/**
 * The five-step severity scale, reused everywhere a grade is shown so the colour
 * always means the same thing.
 *
 * The design supplies both a fill and a tint per grade. Its tints are kept
 * verbatim; its fills are each darkened one step down the same hue ramp, because
 * the fills carry white text and the design's do not survive that.
 *
 * Measured contrast of white text on the design's own fill:
 *
 *   grade             design   ratio   AA     shipped   ratio   AA
 *   0 no DR          #059669    3.77   fail   #047857    5.48   pass
 *   1 mild           #0284C7    4.10   fail   #0369A1    5.93   pass
 *   2 moderate       #F59E0B    2.15   fail   #B45309    5.02   pass
 *   3 severe         #EA580C    3.56   fail   #C2410C    5.18   pass
 *   4 proliferative  #DC2626    4.83   pass   #B91C1C    6.47   pass
 *
 * Four of the five fail AA, moderate worst at 2.15:1. This screen is read
 * outdoors, at arm's length, often by someone who is not the patient, and the
 * colour is how urgency is communicated - so the hue is the design's and the
 * luminance is ours. Each shipped fill is the Tailwind 700 step of the design's
 * 500/600, so the two palettes stay the same family.
 *
 * (The palette this replaces had the same intent but did not meet it: its
 * moderate #B06E00 measured 4.14:1, below AA.)
 *
 * Colour is never the ONLY signal - an icon and spoken words always accompany it.
 */
object SeverityPalette {
    val grade0 = Color(0xFF047857) // no DR         - emerald
    val grade1 = Color(0xFF0369A1) // mild          - sky
    val grade2 = Color(0xFFB45309) // moderate      - amber
    val grade3 = Color(0xFFC2410C) // severe        - orange
    val grade4 = Color(0xFFB91C1C) // proliferative - red

    fun colorFor(grade: DrGrade): Color = when (grade) {
        DrGrade.NO_DR -> grade0
        DrGrade.MILD -> grade1
        DrGrade.MODERATE -> grade2
        DrGrade.SEVERE -> grade3
        DrGrade.PROLIFERATIVE -> grade4
    }

    /**
     * Soft tint of the same hue, for backgrounds that carry dark text. Taken
     * unchanged from the design's `badgeBg`, the Tailwind 50 step.
     */
    fun containerFor(grade: DrGrade): Color = when (grade) {
        DrGrade.NO_DR -> Color(0xFFECFDF5)
        DrGrade.MILD -> Color(0xFFF0F9FF)
        DrGrade.MODERATE -> Color(0xFFFFFBEB)
        DrGrade.SEVERE -> Color(0xFFFFF7ED)
        DrGrade.PROLIFERATIVE -> Color(0xFFFEF2F2)
    }
}
