package com.retinasight.ai.core.model

import androidx.annotation.StringRes
import com.retinasight.ai.R

/**
 * How soon the person should see an eye doctor.
 *
 * This is the single most important thing the app communicates to a rural user,
 * so it is always shown as colour + icon + spoken words, never text alone.
 */
enum class Urgency(@StringRes val labelRes: Int) {
    ROUTINE(R.string.urgency_routine),
    MONITOR(R.string.urgency_monitor),
    SOON(R.string.urgency_soon),
    URGENT(R.string.urgency_urgent),
    IMMEDIATE(R.string.urgency_immediate)
}
