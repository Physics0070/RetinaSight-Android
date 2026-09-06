package com.retinasight.ai.ui.nav

object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val ANALYZING = "analyzing"
    const val RESULT = "result"
    const val HISTORY = "history"

    /** One past screening in full. Takes the record id. */
    const val HISTORY_DETAIL = "history_detail/{recordId}"

    fun historyDetail(recordId: String) = "history_detail/$recordId"
    const val SETTINGS = "settings"
    const val LANGUAGE = "language"
    const val QUALITY = "quality"
    const val CLINIC = "clinic"
    const val PATIENT = "patient"
}
