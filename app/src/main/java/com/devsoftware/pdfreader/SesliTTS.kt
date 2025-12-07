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
            // İngilizce ve Türkçe dillerini deneyelim
            val usResult = tts?.setLanguage(Locale.US)
            val trResult = tts?.setLanguage(Locale("tr", "TR"))
            
            isReady = (usResult != TextToSpeech.LANG_MISSING_DATA &&
                    usResult != TextToSpeech.LANG_NOT_SUPPORTED) ||
                    (trResult != TextToSpeech.LANG_MISSING_DATA &&
                    trResult != TextToSpeech.LANG_NOT_SUPPORTED)

            // ===== UTTERANCE LISTENER =====
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("SesliTTS", "TTS started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("SesliTTS", "TTS finished: $utteranceId")
                    handler.post {
                        try {
                            // 1. Yöntem: nextSentence() fonksiyonunu doğrudan çağır
                            webView.evaluateJavascript(
                                "(function() { " +
                                "   console.log('Android → TTS finished, calling nextSentence'); " +
                                "   if (typeof nextSentence === 'function') { " +
                                "       nextSentence(); " +
                                "   } else if (typeof moveToNextSentence === 'function') { " +
                                "       moveToNextSentence(); " +
                                "   } else { " +
                                "       console.error('nextSentence or moveToNextSentence not found'); " +
                                "   } " +
                                "})()",
                                null
                            )
                            
                            // 2. Yöntem: window.onAndroidSpeechDone callback
                            webView.evaluateJavascript(
                                "if (window.onAndroidSpeechDone) { " +
                                "   window.onAndroidSpeechDone(); " +
                                "} else { " +
                                "   console.log('onAndroidSpeechDone not defined'); " +
                                "}",
                                null
                            )
                            
                        } catch (e: Exception) {
                            Log.e("SesliTTS", "JS callback error: $e")
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e("SesliTTS", "TTS error: $utteranceId")
                    handler.post {
                        // Hata durumunda da sonraki cümleye geçmeyi dene
                        webView.evaluateJavascript(
                            "(function() { " +
                            "   console.log('Android → TTS error, moving to next'); " +
                            "   if (typeof moveToNextSentence === 'function') { " +
                            "       moveToNextSentence(); " +
                            "   } " +
                            "})()",
                            null
                        )
                    }
                }
            })
        } else {
            isReady = false
            Log.e("SesliTTS", "TTS initialization failed")
        }
    }

    @JavascriptInterface
    fun speakTextWithRate(text: String, lang: String, rate: Float) {
        if (!isReady) {
            Log.e("SesliTTS", "TTS not ready")
            return
        }
        try {
            val locale = if (lang.contains("tr")) {
                Locale("tr", "TR")
            } else {
                Locale.US
            }
            
            tts?.language = locale
            tts?.setSpeechRate(rate)
            
            // Utterance ID'yi belirle
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "android_utterance")
            Log.d("SesliTTS", "Speaking: $text (lang: $lang, rate: $rate)")
            
        } catch (e: Exception) {
            Log.e("SesliTTS", "speakTextWithRate error: $e")
        }
    }

    @JavascriptInterface
    fun speakText(text: String, lang: String) {
        speakTextWithRate(text, lang, 1.0f)
    }

    @JavascriptInterface
    fun speak(text: String, lang: String) {
        speakText(text, lang)
    }

    @JavascriptInterface
    fun stop() {
        try { 
            tts?.stop() 
            Log.d("SesliTTS", "TTS stopped")
        } catch (e: Exception) {
            Log.e("SesliTTS", "stop error: $e")
        }
    }

    @JavascriptInterface
    fun isReady(): Boolean = isReady

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            Log.d("SesliTTS", "TTS shutdown")
        } catch (e: Exception) {
            Log.e("SesliTTS", "shutdown error: $e")
        }
    }
}
