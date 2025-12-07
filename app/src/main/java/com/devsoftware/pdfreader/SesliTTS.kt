package com.devsoftware.pdfreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.webkit.WebView
import android.webkit.JavascriptInterface
import java.util.Locale

class SesliTTS(private val context: Context, private val webView: WebView) :
    TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val handler = Handler(Looper.getMainLooper())

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

            // ===== UTTERANCE LISTENER =====
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    handler.post {
                        try {
                            webView.evaluateJavascript(
                                "window.onAndroidSpeechDone && window.onAndroidSpeechDone();",
                                null
                            )
                        } catch (e: Exception) {
                            Log.e("SesliTTS", "JS callback error: $e")
                        }
                    }
                }

                override fun onError(utteranceId: String?) {}
            })
        } else {
            isReady = false
        }
    }

    @JavascriptInterface
    fun speakTextWithRate(text: String, lang: String, rate: Float) {
        if (!isReady) return
        try {
            tts?.language = Locale.forLanguageTag(lang)
            tts?.setSpeechRate(rate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_progress")
        } catch (e: Exception) {
            Log.e("SesliTTS", "speakTextWithRate error: $e")
        }
    }

    @JavascriptInterface
    fun speakText(text: String, lang: String) {
        if (!isReady) return
        try {
            tts?.language = Locale.forLanguageTag(lang)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_progress")
        } catch (e: Exception) {
            Log.e("SesliTTS", "speakText error: $e")
        }
    }

    @JavascriptInterface
    fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun isReady(): Boolean = isReady

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
