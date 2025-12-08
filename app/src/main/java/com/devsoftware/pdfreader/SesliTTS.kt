package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.*
import android.util.Base64

class SesliTTS(private val context: Context, private val webView: WebView) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    
    init {
        // TTS'yi başlat
        tts = TextToSpeech(context, this)
        
        // File picker'ı başlat (eğer context Activity ise)
        if (context is AppCompatActivity) {
            setupFilePicker(context)
        }
    }

    private fun setupFilePicker(activity: AppCompatActivity) {
        filePickerLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // Seçilen PDF'i işle
                    handleSelectedPDF(uri)
                }
            }
        }
    }

    /** PDF seçiciyi aç */
    @JavascriptInterface
    fun openFilePicker() {
        if (context is AppCompatActivity) {
            val activity = context as AppCompatActivity
            
            activity.runOnUiThread {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/pdf"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                
                try {
                    filePickerLauncher.launch(
                        Intent.createChooser(intent, "PDF Dosyası Seçin")
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Dosya yöneticisi bulunamadı",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Fallback: WebView'deki butonu tetikle
                    webView.post {
                        webView.evaluateJavascript("""
                            (function() {
                                const btn = document.getElementById('secondaryOpenFile');
                                if (btn) btn.click();
                            })();
                        """.trimIndent(), null)
                    }
                }
            }
        }
    }

    /** Seçilen PDF'i işle */
    private fun handleSelectedPDF(uri: Uri) {
        try {
            val pdfBytes = getPdfBytesFromUri(uri)
            val fileName = getFileNameFromUri(uri)
            
            if (pdfBytes != null && fileName != null) {
                // Base64'e çevir
                val base64 = Base64.encodeToString(pdfBytes, Base64.DEFAULT)
                
                // JavaScript'e gönder
                webView.post {
                    webView.evaluateJavascript("""
                        (function() {
                            try {
                                // 1. Önce özel callback'i dene
                                if (typeof window.onAndroidPDFSelected === 'function') {
                                    window.onAndroidPDFSelected('$base64', '$fileName');
                                    return;
                                }
                                
                                // 2. Mevcut callback'i dene
                                if (typeof window.onPDFSelected === 'function') {
                                    window.onPDFSelected('$base64', '$fileName');
                                    return;
                                }
                                
                                // 3. Direkt PDF.js ile yükle
                                const typedarray = new Uint8Array(atob('$base64').split('').map(c => c.charCodeAt(0)));
                                
                                if (typeof PDFViewerApplication !== 'undefined') {
                                    PDFViewerApplication.open(typedarray);
                                    console.log('PDF yüklendi: $fileName');
                                } else {
                                    console.error('PDFViewerApplication bulunamadı');
                                }
                            } catch(e) {
                                console.error('PDF yükleme hatası:', e);
                            }
                        })();
                    """.trimIndent(), null)
                }
                
                // Toast mesajı
                Toast.makeText(context, "$fileName seçildi", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF yüklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** URI'dan PDF bytes'ını al */
    private fun getPdfBytesFromUri(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** URI'dan dosya adını al */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            var fileName: String? = null
            
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            fileName = it.getString(displayNameIndex)
                        }
                    }
                }
            }
            
            if (fileName == null) {
                // URI'dan dosya adını çıkar
                val path = uri.path
                if (path != null) {
                    fileName = path.substring(path.lastIndexOf('/') + 1)
                }
            }
            
            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            "document.pdf"
        }
    }

    // ============ MEVCUT TTS KODLARI (Aynı kalacak) ============
    
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
    
    @JavascriptInterface
    fun pickPDF() {
        openFilePicker()
    }

    /** TTS'yi kapat */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
