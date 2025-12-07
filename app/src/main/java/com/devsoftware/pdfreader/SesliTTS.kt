package com.devsoftware.pdfreader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale

class SesliTTS(
    private val context: Context,
    private val webView: WebView   // 🔥 WebView referansı EKLENDİ
) : TextToSpeech.OnInitListener {

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
            val result = tts?.setLanguage(Locale.US)
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            // 🔥 TTS CALLBACK → JS OTOMATIK İLERLEME
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {}

                override fun onError(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    // TTS bittikten sonra JS'e haber gönder
                    webView.post {
                        webView.evaluateJavascript(
                            "window.onAndroidSpeechDone && window.onAndroidSpeechDone();",
                            null
                        )
                    }
                }
            })

        } else {
            isReady = false
        }
    }

    // ============================================
    // speakTextWithRate(text, lang, rate)
    // ============================================
    @JavascriptInterface
    fun speakTextWithRate(text: String, lang: String, rate: Float) {
        if (!isReady) return

        try {
            tts?.language = Locale.forLanguageTag(lang)
            tts?.setSpeechRate(rate)

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "tts_done_id"    // 🔥 ID gerekli
            )

        } catch (e: Exception) {
            Log.e("SesliTTS", "speakTextWithRate error: $e")
        }
    }

    // ============================================
    // speakText(text, lang)
    // ============================================
    @JavascriptInterface
    fun speakText(text: String, lang: String) {
        if (!isReady) return

        try {
            tts?.language = Locale.forLanguageTag(lang)

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "tts_done_id"   // 🔥 Aynı ID
            )

        } catch (e: Exception) {
            Log.e("SesliTTS", "speakText error: $e")
        }
    }

    @JavascriptInterface
    fun speak(text: String, lang: String) {
        speakText(text, lang)
    }

    @JavascriptInterface
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun isReady(): Boolean {
        return isReady
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
