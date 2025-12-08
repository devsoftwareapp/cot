package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
    fun pickPDF() {
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
                }
            }
        }
    }

    /** Seçilen PDF'i işle - FILE PATH gönder */
    private fun handleSelectedPDF(uri: Uri) {
        try {
            // URI'dan dosya yolunu al
            val filePath = getRealPathFromUri(uri)
            
            if (filePath != null && File(filePath).exists()) {
                // JavaScript'e FILE PATH gönder
                webView.post {
                    webView.evaluateJavascript("""
                        (function() {
                            try {
                                console.log('📄 Android PDF path received:', '$filePath');
                                
                                // 1. Önce özel callback'i dene
                                if (typeof window.onAndroidPDFSelected === 'function') {
                                    window.onAndroidPDFSelected('$filePath');
                                    return;
                                }
                                
                                // 2. PDF.js ile yükle
                                if (typeof PDFViewerApplication !== 'undefined') {
                                    // File protocol ile yükle
                                    PDFViewerApplication.open('file://$filePath');
                                    console.log('✅ PDF file:// yüklendi');
                                }
                            } catch(e) {
                                console.error('❌ PDF yükleme hatası:', e);
                            }
                        })();
                    """.trimIndent(), null)
                }
                
                Toast.makeText(context, "PDF seçildi", Toast.LENGTH_SHORT).show()
            } else {
                // Fallback: Base64'e çevir
                handleSelectedPDFAsBase64(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF işlenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // Fallback
            handleSelectedPDFAsBase64(uri)
        }
    }

    /** URI'dan gerçek dosya yolunu al */
    @SuppressLint("Recycle")
    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            // 1. Önce doğrudan path'i dene
            if (uri.path != null && uri.path!!.startsWith("/storage")) {
                return uri.path
            }
            
            // 2. ContentResolver ile dene
            var filePath: String? = null
            
            if (uri.scheme == "content") {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                val cursor = context.contentResolver.query(uri, projection, null, null, null)
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        filePath = it.getString(columnIndex)
                    }
                }
            }
            
            // 3. Eğer hala bulunamadıysa, cache'e kopyala
            if (filePath == null || !File(filePath).exists()) {
                filePath = copyToCache(uri)
            }
            
            filePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** URI'dan cache'e kopyala */
    private fun copyToCache(uri: Uri): String? {
        return try {
            val fileName = getFileNameFromUri(uri) ?: "document_${System.currentTimeMillis()}.pdf"
            val cacheFile = File(context.cacheDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            cacheFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Fallback: Base64'e çevir */
    private fun handleSelectedPDFAsBase64(uri: Uri) {
        try {
            val pdfBytes = getPdfBytesFromUri(uri)
            val fileName = getFileNameFromUri(uri) ?: "document.pdf"
            
            if (pdfBytes != null) {
                // Base64'e çevir
                val base64 = android.util.Base64.encodeToString(pdfBytes, android.util.Base64.DEFAULT)
                
                // JavaScript'e gönder
                webView.post {
                    webView.evaluateJavascript("""
                        (function() {
                            try {
                                console.log('📄 Android PDF Base64 received, length:', ${base64.length});
                                
                                // Base64'ten Blob oluştur
                                const binaryString = atob('$base64');
                                const bytes = new Uint8Array(binaryString.length);
                                for (let i = 0; i < binaryString.length; i++) {
                                    bytes[i] = binaryString.charCodeAt(i);
                                }
                                
                                // Blob URL oluştur
                                const blob = new Blob([bytes], { type: 'application/pdf' });
                                const blobUrl = URL.createObjectURL(blob);
                                
                                // PDF.js ile yükle
                                if (typeof PDFViewerApplication !== 'undefined') {
                                    PDFViewerApplication.open(blobUrl);
                                    console.log('✅ PDF Blob URL ile yüklendi');
                                    
                                    // 5 saniye sonra temizle
                                    setTimeout(() => {
                                        try {
                                            URL.revokeObjectURL(blobUrl);
                                            console.log('🧹 Blob URL temizlendi');
                                        } catch(e) {}
                                    }, 5000);
                                }
                            } catch(e) {
                                console.error('❌ PDF Base64 yükleme hatası:', e);
                            }
                        })();
                    """.trimIndent(), null)
                }
                
                Toast.makeText(context, "$fileName seçildi", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF yüklenemedi", Toast.LENGTH_SHORT).show()
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
                val cursor = context.contentResolver.query(
                    uri, 
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), 
                    null, 
                    null, 
                    null
                )
                
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
                val path = uri.path
                if (path != null) {
                    fileName = path.substring(path.lastIndexOf('/') + 1)
                }
            }
            
            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            "document_${System.currentTimeMillis()}.pdf"
        }
    }

    // ============ MEVCUT TTS KODLARI ============
    
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

    /** TTS'yi kapat */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
