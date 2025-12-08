package com.devsoftware.pdfreader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import java.util.*

class SesliTTS(context: Context, private val webView: WebView) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val context = context

    init {
        // TTS'yi başlat
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Türkçe dilini ayarla
            val result = tts?.setLanguage(Locale("tr", "TR"))
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Türkçe yoksa İngilizce kullan
                tts?.setLanguage(Locale.US)
            }
            
            isTtsReady = true
            
            // JavaScript'e TTS'nin hazır olduğunu bildir
            webView.post {
                webView.evaluateJavascript("""
                    try {
                        if (typeof window.onAndroidTTSReady === 'function') {
                            window.onAndroidTTSReady();
                        }
                        // Global init çağır
                        if (typeof initAndroidTTS === 'function') {
                            initAndroidTTS();
                        }
                    } catch(e) {
                        console.log('TTS init error: ' + e);
                    }
                """.trimIndent(), null)
            }
        } else {
            Toast.makeText(context, "TTS başlatılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    /** JavaScript Interface - TTS fonksiyonları */
    @JavascriptInterface
    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
        }
    }

    @JavascriptInterface
    fun speakAdd(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts_id")
        }
    }

    @JavascriptInterface
    fun stop() {
        tts?.stop()
    }

    @JavascriptInterface
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    @JavascriptInterface
    fun setSpeed(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    @JavascriptInterface
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    @JavascriptInterface
    fun isReady(): Boolean {
        return isTtsReady
    }

    /** TTS'yi kapat */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
