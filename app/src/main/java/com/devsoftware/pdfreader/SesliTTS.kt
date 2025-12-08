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
    
    // Dil kodları eşleştirmesi
    private val localeMap = mapOf(
        // Ana diller
        "en-US" to Locale.US,
        "tr-TR" to Locale("tr", "TR"),
        "ku-TR" to Locale("ku", "TR"), // Kurmanci
        "ckb-IQ" to Locale("ckb", "IQ"), // Sorani
        "ku-IR" to Locale("ku", "IR"), // Gorani
        
        // Diğer diller
        "ar-SA" to Locale("ar", "SA"),
        "fr-FR" to Locale.FRANCE,
        "de-DE" to Locale.GERMANY,
        "es-ES" to Locale("es", "ES"),
        "it-IT" to Locale.ITALY,
        "pt-PT" to Locale("pt", "PT"),
        "ru-RU" to Locale("ru", "RU"),
        "zh-CN" to Locale.SIMPLIFIED_CHINESE,
        "zh-TW" to Locale.TRADITIONAL_CHINESE,
        "ja-JP" to Locale.JAPAN,
        "ko-KR" to Locale.KOREA,
        "hi-IN" to Locale("hi", "IN"),
        "fa-IR" to Locale("fa", "IR")
    )

    init {
        try {
            tts = TextToSpeech(context, this)
            Log.d("SesliTTS", "TTS initialized")
        } catch (e: Exception) {
            Log.e("SesliTTS", "Init error: $e")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Birden fazla dil desteği
            val defaultLocale = Locale.US
            val result = tts?.setLanguage(defaultLocale)
            
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                     result != TextToSpeech.LANG_NOT_SUPPORTED
            
            if (isReady) {
                Log.d("SesliTTS", "TTS ready with default locale: $defaultLocale")
            } else {
                // Varsayılan başarısız olursa Türkçe'yi dene
                val trResult = tts?.setLanguage(Locale("tr", "TR"))
                isReady = trResult != TextToSpeech.LANG_MISSING_DATA &&
                         trResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (isReady) {
                    Log.d("SesliTTS", "TTS ready with Turkish locale")
                }
            }

            // ===== UTTERANCE LISTENER =====
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("SesliTTS", "TTS started: $utteranceId")
                    handler.post {
                        webView.evaluateJavascript(
                            "console.log('Android → TTS started');",
                            null
                        )
                    }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("SesliTTS", "TTS finished: $utteranceId")
                    handler.post {
                        try {
                            // JavaScript callback'i çağır
                            webView.evaluateJavascript(
                                "(function() { " +
                                "   console.log('Android → TTS finished, calling window.onAndroidSpeechDone'); " +
                                "   if (typeof window.onAndroidSpeechDone === 'function') { " +
                                "       window.onAndroidSpeechDone(); " +
                                "   } else { " +
                                "       console.error('onAndroidSpeechDone not found'); " +
                                "       // Fallback: Global callback'i dene" +
                                "       if (typeof onAndroidSpeechDone === 'function') { " +
                                "           onAndroidSpeechDone(); " +
                                "       }" +
                                "   } " +
                                "})()",
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
                        // Hata durumunda da callback'i çağır
                        webView.evaluateJavascript(
                            "(function() { " +
                            "   console.error('Android → TTS error'); " +
                            "   if (typeof window.onAndroidSpeechDone === 'function') { " +
                            "       window.onAndroidSpeechDone(); " +
                            "   }" +
                            "})()",
                            null
                        )
                    }
                }
            })
            
            // WebView'a TTS'nin hazır olduğunu bildir
            handler.post {
                webView.evaluateJavascript(
                    "(function() { " +
                    "   console.log('Android → TTS ready'); " +
                    "   if (typeof window.onAndroidTTSReady === 'function') { " +
                    "       window.onAndroidTTSReady(); " +
                    "   }" +
                    "})()",
                    null
                )
            }
            
        } else {
            isReady = false
            Log.e("SesliTTS", "TTS initialization failed")
        }
    }

    @JavascriptInterface
    fun initTTS() {
        Log.d("SesliTTS", "initTTS called from JavaScript")
        // TTS zaten başlatıldı, sadece hazır olup olmadığını kontrol et
        if (isReady) {
            handler.post {
                webView.evaluateJavascript(
                    "if (window.onAndroidTTSReady) window.onAndroidTTSReady();",
                    null
                )
            }
        }
    }

    @JavascriptInterface
    fun speakTextWithRate(text: String, lang: String, rate: Float) {
        Log.d("SesliTTS", "speakTextWithRate called: text='$text', lang='$lang', rate=$rate")
        
        if (!isReady) {
            Log.e("SesliTTS", "TTS not ready")
            handler.post {
                webView.evaluateJavascript(
                    "console.error('Android → TTS not ready');",
                    null
                )
            }
            return
        }
        
        try {
            // Dil kodunu Locale'e çevir
            val locale = localeMap[lang] ?: run {
                // Dil kodundan Locale oluşturmayı dene
                val parts = lang.split("-")
                if (parts.size == 2) {
                    Locale(parts[0], parts[1])
                } else {
                    Locale.US // Varsayılan
                }
            }
            
            // Dil desteğini kontrol et
            val langAvailable = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            val finalLocale = if (langAvailable >= TextToSpeech.LANG_AVAILABLE) {
                locale
            } else {
                Log.w("SesliTTS", "Language $lang not available, using default")
                Locale.US
            }
            
            tts?.language = finalLocale
            tts?.setSpeechRate(rate)
            
            // WebView'a konuşmanın başladığını bildir
            handler.post {
                webView.evaluateJavascript(
                    "if (window.onAndroidTTSStart) window.onAndroidTTSStart();",
                    null
                )
            }
            
            // Konuşmayı başlat
            val utteranceId = "tts_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            Log.d("SesliTTS", "Speaking: '${text.take(50)}...' (lang: $finalLocale, rate: $rate)")
            
        } catch (e: Exception) {
            Log.e("SesliTTS", "speakTextWithRate error: $e")
            handler.post {
                webView.evaluateJavascript(
                    "console.error('Android → TTS error: ${e.message}');",
                    null
                )
            }
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
            handler.post {
                webView.evaluateJavascript(
                    "console.log('Android → TTS stopped');",
                    null
                )
            }
        } catch (e: Exception) {
            Log.e("SesliTTS", "stop error: $e")
        }
    }

    @JavascriptInterface
    fun setPitch(pitch: Float) {
        try {
            tts?.setPitch(pitch)
            Log.d("SesliTTS", "Pitch set to: $pitch")
        } catch (e: Exception) {
            Log.e("SesliTTS", "setPitch error: $e")
        }
    }

    @JavascriptInterface
    fun isReady(): Boolean {
        Log.d("SesliTTS", "isReady called, returning: $isReady")
        return isReady
    }

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
