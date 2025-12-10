package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IndexActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var pdfReaderFolder: File
    private val sharedPDFsFolder by lazy { File(pdfReaderFolder, "Paylaşılanlar") }
    private val sharedPDFs = mutableListOf<SharedPDF>()

    companion object {
        const val FILE_PROVIDER_AUTHORITY = "com.devsoftware.pdfreader.fileprovider"
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Direkt WebView oluştur
        webView = WebView(this)
        setContentView(webView)

        // 2. PDF Reader klasörü
        pdfReaderFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PDF Reader"
        )
        if (!pdfReaderFolder.exists()) pdfReaderFolder.mkdirs()
        if (!sharedPDFsFolder.exists()) sharedPDFsFolder.mkdirs()

        // 3. WebView ayarları - KRİTİK
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // 4. WebView client'ları - ALERT'LERİ ENGELLE
        webView.webChromeClient = object : WebChromeClient() {
            // ALERT'leri sustur - BU KRİTİK!
            override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                // Alert'leri sessizce kapat, gösterme
                result.confirm()
                return true // Alert'i WebView'in göstermesini engelle
            }
            
            override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                result.confirm()
                return true
            }
            
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: android.webkit.JsPromptResult): Boolean {
                result.confirm()
                return true
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkAndUpdatePermission()
                loadSharedPDFs()
                
                // JavaScript alert fonksiyonunu override et
                injectAlertOverride()
            }
            
            // file:// URL'lerini engelle
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                
                // file:// ile başlıyorsa ve PDF ise
                if (url.startsWith("file://") && url.endsWith(".pdf", true)) {
                    try {
                        val filePath = url.substringAfter("file://")
                        val file = File(filePath)
                        if (file.exists()) {
                            openPDFWithAndroidViewer(file)
                            return true
                        }
                    } catch (e: Exception) {
                        Log.e("PDFReader", "PDF açma hatası", e)
                    }
                }
                return false
            }
        }

        // 5. JavaScript Interface - KRİTİK
        webView.addJavascriptInterface(PdfAndroidInterface(this), "Android")

        // 6. HTML yükle
        webView.loadUrl("file:///android_asset/web/index.html")

        // 7. Paylaşılan PDF'leri yükle
        loadSharedPDFs()
        
        // 8. İntent'i işle
        handleShareIntent(intent)
    }
    
    // ALERT fonksiyonlarını override eden JavaScript enjekte et
    private fun injectAlertOverride() {
        val jsCode = """
            // Tüm alert fonksiyonlarını sustur
            window.alert = function(message) {
                console.log("Alert engellendi: " + message);
                return;
            };
            
            window.confirm = function(message) {
                console.log("Confirm engellendi: " + message);
                return true;
            };
            
            window.prompt = function(message, defaultValue) {
                console.log("Prompt engellendi: " + message);
                return defaultValue || "";
            };
            
            // Toast benzeri basit bildirim (isteğe bağlı)
            window.showToast = function(message) {
                if (Android && Android.showToast) {
                    Android.showToast(message);
                }
            };
        """
        
        webView.postDelayed({
            webView.evaluateJavascript(jsCode, null)
        }, 1000)
    }

    // Android PDF viewer ile aç
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
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Sessiz hata - alert gösterme
            Log.e("PDFReader", "PDF görüntüleyici bulunamadı", e)
        } catch (e: Exception) {
            Log.e("PDFReader", "PDF açma hatası", e)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermission()
        loadSharedPDFs()
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
                    if (uri.toString().lowercase().endsWith(".pdf")) saveSharedPDF(uri)
                }
            }
        }
    }

    private fun saveSharedPDF(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val name = "Paylaşılan_${timestamp}.pdf"
                val out = File(sharedPDFsFolder, name)

                FileOutputStream(out).use { output -> input.copyTo(output) }

                val pdf = SharedPDF(
                    name = name,
                    path = out.absolutePath,
                    size = formatSize(out.length()),
                    date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                    sharedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                )

                sharedPDFs.add(0, pdf)

                Handler(Looper.getMainLooper()).postDelayed({
                    updateSharedPDFsList()
                    // Toast yerine sessiz çalış
                    Log.i("PDFReader", "PDF Paylaşılanlar'a eklendi: $name")
                }, 300)
            }
        } catch (e: Exception) {
            Log.e("PDFReader", "PDF Kaydetme Hatası", e)
        }
    }

    private fun loadSharedPDFs() {
        sharedPDFs.clear()
        if (sharedPDFsFolder.exists()) {
            sharedPDFsFolder.listFiles { f -> f.isFile && f.name.endsWith(".pdf") }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { f ->
                    sharedPDFs.add(
                        SharedPDF(
                            name = f.name,
                            path = f.absolutePath,
                            size = formatSize(f.length()),
                            date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(f.lastModified())),
                            sharedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
                        )
                    )
                }
        }
        updateSharedPDFsList()
    }

    private fun updateSharedPDFsList() {
        val json = sharedPDFs.joinToString(", ", "[", "]") { p ->
            """
            {
                "name": "${escape(p.name)}",
                "path": "${escape(p.path)}",
                "size": "${escape(p.size)}",
                "date": "${escape(p.date)}",
                "sharedDate": "${escape(p.sharedDate)}",
                "id": ${sharedPDFs.indexOf(p) + 10000}
            }
            """.trimIndent()
        }

        webView.post {
            webView.evaluateJavascript("updateSharedPDFsList($json)", null)
        }
    }

    private fun escape(t: String): String =
        t.replace("\\", "\\\\").replace("\"", "\\\"")

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
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    data class SharedPDF(
        val name: String,
        val path: String,
        val size: String,
        val date: String,
        val sharedDate: String
    )

    inner class PdfAndroidInterface(private val act: Activity) {

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
                    // Sessiz hata
                    Log.e("PDFReader", "Ayarlar açılamadı", e)
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
                scan(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), result)
                scan(pdfReaderFolder, result)

                if (result.isEmpty()) "EMPTY" else result.distinct().joinToString("||")

            } catch (e: Exception) {
                "ERROR"
            }
        }

        private fun scan(dir: File, list: MutableList<String>) {
            if (!dir.exists()) return
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) scan(f, list)
                else if (f.name.endsWith(".pdf", true)) list.add(f.absolutePath)
            }
        }

        @JavascriptInterface
        fun deleteFile(path: String): Boolean =
            try {
                val f = File(path)
                f.exists() && f.delete()
            } catch (e: Exception) {
                false
            }

        @JavascriptInterface
        fun renameFile(oldPath: String, newName: String): String {
            val old = File(oldPath)
            if (!old.exists()) return "ERROR"
            val newFile = File(old.parent, newName)
            return if (old.renameTo(newFile)) newFile.absolutePath else "ERROR"
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

        // ============ YAZDIRMA ============
        @JavascriptInterface
        fun printPDF(path: String) {
            act.runOnUiThread {
                try {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        val uri = FileProvider.getUriForFile(
                            act,
                            FILE_PROVIDER_AUTHORITY,
                            file
                        )
                        
                        // DİREKT YAZDIRMA İNTENT'İ - alert YOK
                        val printIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        
                        // Chooser'sız direkt intent - sistem yazdırma seçeneğini açar
                        try {
                            act.startActivity(printIntent)
                        } catch (e: ActivityNotFoundException) {
                            // Chooser ile fallback
                            act.startActivity(Intent.createChooser(printIntent, "Yazdır: ${file.name}"))
                        }
                        
                    } else {
                        // Sessiz hata
                        Log.e("PDFReader", "PDF bulunamadı: $path")
                    }
                } catch (e: Exception) {
                    Log.e("PDFReader", "Yazdırma hatası", e)
                }
            }
        }

        // ============ PAYLAŞMA ============
        @JavascriptInterface
        fun shareFile(path: String) {
            act.runOnUiThread {
                try {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        val uri = FileProvider.getUriForFile(
                            act,
                            FILE_PROVIDER_AUTHORITY,
                            file
                        )
                        
                        // Android paylaşım menüsü - alert YOK
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        
                        // Direkt paylaşım menüsünü aç
                        try {
                            act.startActivity(shareIntent)
                        } catch (e: ActivityNotFoundException) {
                            // Chooser ile fallback
                            act.startActivity(Intent.createChooser(shareIntent, "Paylaş: ${file.name}"))
                        }
                        
                    } else {
                        Log.e("PDFReader", "Dosya bulunamadı: $path")
                    }
                } catch (e: Exception) {
                    Log.e("PDFReader", "Paylaşım hatası", e)
                }
            }
        }

        @JavascriptInterface
        fun shareFiles(paths: String) {
            act.runOnUiThread {
                try {
                    val pathList = paths.split("||")
                    val uris = ArrayList<Uri>()

                    pathList.forEach { path ->
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            val uri = FileProvider.getUriForFile(
                                act,
                                FILE_PROVIDER_AUTHORITY,
                                file
                            )
                            uris.add(uri)
                        }
                    }

                    if (uris.isNotEmpty()) {
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "application/pdf"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        // Direkt aç
                        try {
                            act.startActivity(shareIntent)
                        } catch (e: ActivityNotFoundException) {
                            act.startActivity(Intent.createChooser(shareIntent, 
                                if (uris.size == 1) "PDF Paylaş" else "${uris.size} PDF Dosyası Paylaş"))
                        }
                    } else {
                        Log.e("PDFReader", "Paylaşılacak dosya bulunamadı")
                    }
                } catch (e: Exception) {
                    Log.e("PDFReader", "Çoklu paylaşım hatası", e)
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
        fun showToast(msg: String) {
            act.runOnUiThread {
                // İsteğe bağlı: Toast göstermek isterseniz
                // Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
                Log.i("PDFReader", "Toast mesajı: $msg")
            }
        }
    }
}
