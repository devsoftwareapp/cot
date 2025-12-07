package com.devsoftware.pdfreader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.JavascriptInterface
import java.util.Locale

class SesliTTS(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("SesliTTS", "Init error: $e")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)  // Default: ENGLISH
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                      result != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            isReady = false
        }
    }

    // ============================================
    // JS → Android: speakTextWithRate(text, lang, rate)
    // ============================================
    @JavascriptInterface
    fun speakTextWithRate(text: String, lang: String, rate: Float) {
        if (!isReady) return

        try {
            tts?.language = Locale.forLanguageTag(lang)
            tts?.setSpeechRate(rate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id_rate")
        } catch (e: Exception) {
            Log.e("SesliTTS", "speakTextWithRate error: $e")
        }
    }

    // ============================================
    // JS → Android: speakText(text, lang)
    // ============================================
    @JavascriptInterface
    fun speakText(text: String, lang: String) {
        if (!isReady) return

        try {
            tts?.language = Locale.forLanguageTag(lang)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
        } catch (e: Exception) {
            Log.e("SesliTTS", "speakText error: $e")
        }
    }

    // Backwards compatible: speak(text, lang)
    @JavascriptInterface
    fun speak(text: String, lang: String) {
        speakText(text, lang)
    }

    // ============================================
    // JS → Android: stop()
    // ============================================
    @JavascriptInterface
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun isReady(): Boolean {
        return isReady
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
