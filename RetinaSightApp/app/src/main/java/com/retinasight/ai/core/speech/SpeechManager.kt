package com.retinasight.ai.core.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.retinasight.ai.core.lang.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Speaks results aloud using the phone's own offline text-to-speech engine.
 *
 * Voice is not a nice-to-have here: a user who cannot read still has to receive
 * the result and the referral advice, so every result screen speaks.
 *
 * Android's TTS works offline once the voice data for a language is installed.
 * When a language has no installed voice, [isLanguageSupported] reports false so
 * the UI can say so honestly instead of silently doing nothing. That is the
 * hook where the AI4Bharat ONNX fallback will plug in later.
 */
class SpeechManager(context: Context) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            _isReady.value = status == TextToSpeech.SUCCESS
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Required override; the newer overload delegates here.")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
        }
    }

    /** True when this phone actually has an installed voice for the language. */
    fun isLanguageSupported(language: AppLanguage): Boolean {
        val result = engine?.isLanguageAvailable(language.locale) ?: return false
        return result == TextToSpeech.LANG_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    /**
     * Speaks [text] in [language], replacing anything currently being spoken so
     * a user tapping Listen twice never hears two overlapping voices.
     *
     * @return false if the language has no voice on this phone, so the caller
     *         can show a clear message rather than failing silently.
     */
    fun speak(text: String, language: AppLanguage): Boolean {
        val tts = engine ?: return false
        if (!_isReady.value) return false
        if (text.isBlank()) return false

        val langResult = tts.setLanguage(language.locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            // Fall back to the default locale rather than staying silent.
            tts.setLanguage(Locale.getDefault())
            return false
        }

        // Slightly slower than default: clearer for older listeners.
        tts.setSpeechRate(0.92f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        return true
    }

    /**
     * Intent that opens the TTS engine's voice-download screen.
     *
     * Android ships Google TTS but downloads voice data per language on demand,
     * so a fresh phone has no Marathi or Tamil voice even though the engine
     * supports them. Without this the app can only report the gap; with it the
     * health worker can actually close it, once, over any connection.
     */
    fun installVoiceDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun stop() {
        engine?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        _isReady.value = false
        _isSpeaking.value = false
    }

    private companion object {
        const val UTTERANCE_ID = "retinasight_utterance"
    }
}
