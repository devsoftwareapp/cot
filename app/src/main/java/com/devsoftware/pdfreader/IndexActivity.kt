package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IndexActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var pdfReaderFolder: File
    
    // File picker için değişkenler
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_RESULT_CODE = 100
    private val TAG = "PDFReader"

    companion object {
        const val FILE_PROVIDER_AUTHORITY = "com.devsoftware.pdfreader.fileprovider"
        const val REQUEST_CODE_SHARE = 101
        const val REQUEST_CODE_PRINT = 102
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // WebView oluştur
        webView = WebView(this)
        setContentView(webView)

        // PDF Reader klasörü
        pdfReaderFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PDF Reader"
        )
        if (!pdfReaderFolder.exists()) {
            pdfReaderFolder.mkdirs()
        }
        Log.d(TAG, "PDF Reader klasörü: ${pdfReaderFolder.absolutePath}")

        // WebView ayarları
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        
        // WebView client'ları
        webView.webChromeClient = object : WebChromeClient() {
            // Alert'leri engelle
            override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                Log.d(TAG, "Alert: $message")
                result.confirm()
                return true
            }
            
            override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                Log.d(TAG, "Confirm: $message")
                result.confirm()
                return true
            }
            
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: android.webkit.JsPromptResult): Boolean {
                Log.d(TAG, "Prompt: $message")
                result.confirm()
                return true
            }
            
            // File picker
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@IndexActivity.filePathCallback?.onReceiveValue(null)
                this@IndexActivity.filePathCallback = filePathCallback
                
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf"))
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                }
                
                try {
                    startActivityForResult(
                        Intent.createChooser(intent, "PDF Dosyası Seçin"),
                        FILE_CHOOSER_RESULT_CODE
                    )
                    return true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback?.onReceiveValue(null)
                    this@IndexActivity.filePathCallback = null
                    return false
                }
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkAndUpdatePermission()
                injectJavaScriptOverrides()
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                
                Log.d(TAG, "URL: $url")
                
                if (url.startsWith("file://") && url.endsWith(".pdf", true)) {
                    try {
                        val filePath = url.substringAfter("file://")
                        val file = File(filePath)
                        if (file.exists()) {
                            openPDFWithAndroidViewer(file)
                            return true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "PDF açma hatası", e)
                    }
                }
                else if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Harici link açma hatası", e)
                    }
                }
                
                return false
            }
        }

        // JavaScript Interface
        webView.addJavascriptInterface(PdfAndroidInterface(this), "Android")

        // HTML yükle
        webView.loadUrl("file:///android_asset/web/index.html")

        // İntent'i işle
        handleShareIntent(intent)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            FILE_CHOOSER_RESULT_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val uri = data.data
                    if (uri != null) {
                        filePathCallback?.onReceiveValue(arrayOf(uri))
                        handleSelectedFile(uri)
                    } else {
                        filePathCallback?.onReceiveValue(null)
                    }
                } else {
                    filePathCallback?.onReceiveValue(null)
                }
                filePathCallback = null
            }
        }
    }
    
    private fun handleSelectedFile(uri: Uri) {
        runOnUiThread {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val fileName = getFileName(uri)
                    val cleanFileName = if (fileName.endsWith(".pdf", true)) {
                        fileName
                    } else {
                        "$fileName.pdf"
                    }
                    
                    val outFile = File(pdfReaderFolder, cleanFileName)
                    
                    // Dosya zaten varsa, timestamp ekle
                    val finalFile = if (outFile.exists()) {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val nameWithoutExt = cleanFileName.substringBeforeLast(".")
                        val ext = cleanFileName.substringAfterLast(".", "pdf")
                        File(pdfReaderFolder, "${nameWithoutExt}_$timestamp.$ext")
                    } else {
                        outFile
                    }
                    
                    FileOutputStream(finalFile).use { output ->
                        input.copyTo(output)
                    }
                    
                    Log.i(TAG, "PDF import edildi: ${finalFile.name}")
                    
                    // WebView'e bildir
                    webView.postDelayed({
                        val jsCode = """
                            if (window.onPDFImported) {
                                window.onPDFImported({
                                    name: "${finalFile.name}",
                                    path: "${finalFile.absolutePath}",
                                    size: "${formatSize(finalFile.length())}",
                                    date: "${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}",
                                    id: ${System.currentTimeMillis()}
                                });
                            }
                            setTimeout(function() {
                                if (window.scanDeviceForPDFs) {
                                    window.scanDeviceForPDFs();
                                }
                            }, 500);
                        """.trimIndent()
                        
                        webView.evaluateJavascript(jsCode, null)
                    }, 300)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Dosya import hatası", e)
            }
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result = ""
        
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex("_display_name")
                    if (displayNameIndex != -1) {
                        result = cursor.getString(displayNameIndex)
                    }
                }
            }
        }
        
        if (result.isEmpty()) {
            result = uri.path?.substringAfterLast('/') ?: "imported_${System.currentTimeMillis()}"
        }
        
        // Özel karakterleri temizle
        result = result.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        
        return result
    }
    
    private fun injectJavaScriptOverrides() {
        val jsCode = """
            // JavaScript override'ları
            console.log("JavaScript override'ları enjekte edildi");
        """
        
        webView.postDelayed({
            webView.evaluateJavascript(jsCode, null)
        }, 1000)
    }

    // PDF görüntüleyici ile aç
    private fun openPDFWithAndroidViewer(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                FILE_PROVIDER_AUTHORITY,
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "PDF görüntüleyici bulunamadı", e)
        } catch (e: Exception) {
            Log.e(TAG, "PDF açma hatası", e)
        }
    }

    // PAYLAŞMA FONKSİYONU - GÜNCEL
    fun shareFile(path: String) {
        Log.d(TAG, "Paylaşma başlatılıyor: $path")
        
        try {
            val file = File(path)
            if (!file.exists()) {
                Log.e(TAG, "Dosya bulunamadı: $path")
                return
            }
            
            val uri = FileProvider.getUriForFile(
                this,
                FILE_PROVIDER_AUTHORITY,
                file
            )
            
            Log.d(TAG, "URI oluşturuldu: $uri")
            
            // PAYLAŞIM INTENT'İ - TÜM SEÇENEKLER GÖSTERİLECEK
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TEXT, "PDF Dosyası: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // CHOOSER KULLAN - Tüm paylaşım seçenekleri göster
            val chooserIntent = Intent.createChooser(shareIntent, "PDF'yi Paylaş: ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            Log.d(TAG, "Paylaşım intent'i hazır: ${file.name}")
            
            // Intent'i başlat
            startActivity(chooserIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Paylaşım hatası", e)
        }
    }

    // YAZDIRMA FONKSİYONU - GÜNCEL
    fun printFile(path: String) {
        Log.d(TAG, "Yazdırma başlatılıyor: $path")
        
        try {
            val file = File(path)
            if (!file.exists()) {
                Log.e(TAG, "Dosya bulunamadı: $path")
                return
            }
            
            val uri = FileProvider.getUriForFile(
                this,
                FILE_PROVIDER_AUTHORITY,
                file
            )
            
            Log.d(TAG, "Yazdırma URI: $uri")
            
            // YAZDIRMA INTENT'İ - DOĞRUDAN YAZICI
            val printIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // DOĞRUDAN YAZDIR - CHOOSER YOK
            try {
                startActivity(printIntent)
                Log.d(TAG, "Yazdırma intent'i başlatıldı")
            } catch (e: ActivityNotFoundException) {
                // Fallback: Chooser ile
                Log.d(TAG, "Direct print failed, trying chooser")
                val chooser = Intent.createChooser(printIntent, "Yazdır: ${file.name}")
                startActivity(chooser)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Yazdırma hatası", e)
        }
    }

    // ÇOKLU PAYLAŞMA
    fun shareMultipleFiles(paths: List<String>) {
        Log.d(TAG, "Çoklu paylaşma: ${paths.size} dosya")
        
        try {
            val uris = ArrayList<Uri>()
            
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        this,
                        FILE_PROVIDER_AUTHORITY,
                        file
                    )
                    uris.add(uri)
                }
            }
            
            if (uris.isEmpty()) {
                Log.e(TAG, "Paylaşılacak dosya bulunamadı")
                return
            }
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "application/pdf"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "${uris.size} PDF Dosyası")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "${uris.size} PDF Dosyasını Paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(chooserIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Çoklu paylaşım hatası", e)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermission()
        
        if (filePathCallback != null) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "application/pdf") {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    uri?.let { saveSharedPDF(it) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.forEach { uri ->
                    if (uri.toString().lowercase().endsWith(".pdf")) {
                        saveSharedPDF(uri)
                    }
                }
            }
        }
    }

    private fun saveSharedPDF(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val name = "Shared_${timestamp}.pdf"
                val outFile = File(pdfReaderFolder, name)

                FileOutputStream(outFile).use { output -> input.copyTo(output) }

                Log.i(TAG, "PDF kaydedildi: $name")
                
                webView.postDelayed({
                    val jsCode = """
                        if (window.scanDeviceForPDFs) {
                            window.scanDeviceForPDFs();
                        }
                    """.trimIndent()
                    webView.evaluateJavascript(jsCode, null)
                }, 500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF Kaydetme Hatası", e)
        }
    }

    private fun formatSize(s: Long): String =
        when {
            s >= 1024 * 1024 -> "${(s / 1024f / 1024f).format(1)} MB"
            s >= 1024 -> "${(s / 1024f).format(0)} KB"
            else -> "$s B"
        }

    private fun Float.format(d: Int) = "%.${d}f".format(this)

    private fun checkAndUpdatePermission() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else true

        val js = if (granted) "onPermissionGranted()" else "onPermissionDenied()"

        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ============ ANDROID JAVASCRIPT INTERFACE ============
    inner class PdfAndroidInterface(private val act: Activity) {

        @JavascriptInterface
        fun logMessage(message: String) {
            Log.d(TAG, "JS: $message")
        }

        @JavascriptInterface
        fun checkPermission(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true

        @JavascriptInterface
        fun openAllFilesSettings() {
            act.runOnUiThread {
                try {
                    val uri = Uri.parse("package:${act.packageName}")
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = uri
                    act.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Ayarlar açılamadı", e)
                }
            }
        }

        @JavascriptInterface
        fun openFilePicker() {
            act.runOnUiThread {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf"))
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                }
                
                try {
                    act.startActivityForResult(
                        Intent.createChooser(intent, "PDF Dosyası Seçin"),
                        FILE_CHOOSER_RESULT_CODE
                    )
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "Dosya yöneticisi bulunamadı", e)
                }
            }
        }

        @JavascriptInterface
        fun listPDFs(): String {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager())
                        return "PERMISSION_DENIED"
                }

                val result = mutableListOf<String>()
                
                // Sadece PDF Reader klasörünü tara
                if (pdfReaderFolder.exists()) {
                    scanDirectory(pdfReaderFolder, result)
                }
                
                // Downloads klasörünü de tara
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloads.exists()) {
                    scanDirectory(downloads, result)
                }

                if (result.isEmpty()) "EMPTY" else result.distinct().joinToString("||")

            } catch (e: Exception) {
                Log.e(TAG, "PDF listeleme hatası", e)
                "ERROR"
            }
        }

        private fun scanDirectory(dir: File, list: MutableList<String>) {
            if (!dir.exists()) return
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory && f != pdfReaderFolder) {
                    scanDirectory(f, list)
                } else if (f.isFile && f.name.endsWith(".pdf", true)) {
                    list.add(f.absolutePath)
                }
            }
        }

        @JavascriptInterface
        fun deleteFile(path: String): Boolean =
            try {
                val f = File(path)
                val deleted = f.exists() && f.delete()
                if (deleted) {
                    Log.i(TAG, "Dosya silindi: $path")
                }
                deleted
            } catch (e: Exception) {
                Log.e(TAG, "Dosya silme hatası", e)
                false
            }

        @JavascriptInterface
        fun renameFile(oldPath: String, newName: String): String {
            return try {
                val old = File(oldPath)
                if (!old.exists()) return "ERROR"
                
                val newFile = File(old.parent, newName)
                val success = old.renameTo(newFile)
                
                if (success) {
                    Log.i(TAG, "Dosya yeniden adlandırıldı: $oldPath -> ${newFile.absolutePath}")
                    newFile.absolutePath
                } else {
                    "ERROR"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Dosya yeniden adlandırma hatası", e)
                "ERROR"
            }
        }

        @JavascriptInterface
        fun getFileSize(path: String): String {
            return try {
                val file = File(path)
                if (file.exists()) {
                    file.length().toString()
                } else {
                    "0"
                }
            } catch (e: Exception) {
                "0"
            }
        }

        @JavascriptInterface
        fun getFileDate(path: String): String {
            return try {
                val file = File(path)
                if (file.exists()) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                }
            } catch (e: Exception) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            }
        }

        @JavascriptInterface
        fun createFolder(folderPath: String): String {
            return try {
                val folder = File(folderPath)
                if (folder.exists()) {
                    "EXISTS"
                } else {
                    if (folder.mkdirs()) {
                        "SUCCESS"
                    } else {
                        "FAILED"
                    }
                }
            } catch (e: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun saveBase64PDF(base64Data: String, fileName: String) {
            act.runOnUiThread {
                try {
                    val cleanBase64 = if (base64Data.contains(",")) {
                        base64Data.substringAfter(",")
                    } else {
                        base64Data
                    }
                    
                    val pdfData = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                    val outFile = File(pdfReaderFolder, fileName)
                    
                    FileOutputStream(outFile).use { output ->
                        output.write(pdfData)
                    }
                    
                    Log.i(TAG, "Base64 PDF kaydedildi: ${outFile.absolutePath}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Base64 PDF kaydetme hatası", e)
                }
            }
        }

        // ============ KRİTİK: YAZDIRMA FONKSİYONU ============
        @JavascriptInterface
        fun printPDF(path: String) {
            Log.d(TAG, "JavaScript'ten yazdırma isteği: $path")
            
            act.runOnUiThread {
                try {
                    // Activity'nin printFile fonksiyonunu çağır
                    (act as? IndexActivity)?.printFile(path)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Yazdırma hatası", e)
                }
            }
        }

        // ============ KRİTİK: PAYLAŞMA FONKSİYONU ============
        @JavascriptInterface
        fun shareFile(path: String) {
            Log.d(TAG, "JavaScript'ten paylaşma isteği: $path")
            
            act.runOnUiThread {
                try {
                    // Activity'nin shareFile fonksiyonunu çağır
                    (act as? IndexActivity)?.shareFile(path)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Paylaşım hatası", e)
                }
            }
        }

        @JavascriptInterface
        fun shareFiles(paths: String) {
            Log.d(TAG, "JavaScript'ten çoklu paylaşma isteği: $paths")
            
            act.runOnUiThread {
                try {
                    val pathList = paths.split("||")
                    (act as? IndexActivity)?.shareMultipleFiles(pathList)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Çoklu paylaşım hatası", e)
                }
            }
        }

        @JavascriptInterface
        fun getFileForSharing(path: String): String {
            return try {
                val file = File(path)
                if (file.exists() && file.isFile) {
                    FileProvider.getUriForFile(
                        act,
                        FILE_PROVIDER_AUTHORITY,
                        file
                    ).toString()
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun checkPermissionOnResume() {
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true

            val js = if (granted) "onPermissionGranted()" else "onPermissionDenied()"
            
            webView.post {
                webView.evaluateJavascript(js, null)
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            act.runOnUiThread {
                Log.i(TAG, "Toast: $msg")
            }
        }
    }
}
