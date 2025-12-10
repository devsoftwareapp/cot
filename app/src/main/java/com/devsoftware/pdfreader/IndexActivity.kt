package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import android.webkit.MimeTypeMap
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

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // PDF Reader klasörünü oluştur
        pdfReaderFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PDF Reader")
        if (!pdfReaderFolder.exists()) {
            pdfReaderFolder.mkdirs()
        }
        
        // Paylaşılanlar klasörünü oluştur
        if (!sharedPDFsFolder.exists()) {
            sharedPDFsFolder.mkdirs()
        }

        // WebView ayarları
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // WebView client'ları
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkAndUpdatePermission()
                // Paylaşılan PDF'leri yükle
                loadSharedPDFs()
            }
        }

        // JavaScript Interface
        webView.addJavascriptInterface(AndroidInterface(this), "Android")

        // Local HTML yükle
        webView.loadUrl("file:///android_asset/web/index.html")
        
        // Paylaşılan PDF'leri yükle
        loadSharedPDFs()
        
        // onCreate'de intent'i işle
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermission()
        // Resumed'de paylaşılan PDF'leri yeniden yükle
        loadSharedPDFs()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Paylaşım intent'ini işle
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        
        if (intent.action == Intent.ACTION_SEND && intent.type == "application/pdf") {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                saveSharedPDF(uri)
            }
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            uris?.forEach { uri ->
                if (uri.toString().endsWith(".pdf", true)) {
                    saveSharedPDF(uri)
                }
            }
        }
    }

    private fun saveSharedPDF(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "Paylaşılan_${timestamp}.pdf"
                val outputFile = File(sharedPDFsFolder, fileName)
                
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                // Listeye ekle
                val sharedPDF = SharedPDF(
                    name = fileName,
                    path = outputFile.absolutePath,
                    size = formatFileSize(outputFile.length()),
                    date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                    sharedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                )
                
                sharedPDFs.add(0, sharedPDF) // En üste ekle
                
                // WebView'e bildir
                Handler(Looper.getMainLooper()).postDelayed({
                    updateSharedPDFsList()
                }, 500)
                
                Toast.makeText(this, "PDF Paylaşılanlar'a kaydedildi", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("PDFReader", "PDF kaydetme hatası", e)
            Toast.makeText(this, "PDF kaydedilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSharedPDFs() {
        sharedPDFs.clear()
        if (sharedPDFsFolder.exists() && sharedPDFsFolder.isDirectory) {
            sharedPDFsFolder.listFiles { file -> file.isFile && file.name.endsWith(".pdf", true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    val sharedPDF = SharedPDF(
                        name = file.name,
                        path = file.absolutePath,
                        size = formatFileSize(file.length()),
                        date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(file.lastModified())),
                        sharedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                    )
                    sharedPDFs.add(sharedPDF)
                }
        }
        updateSharedPDFsList()
    }

    private fun updateSharedPDFsList() {
        val jsonArray = sharedPDFs.joinToString(", ", "[", "]") { sharedPDF ->
            """
            {
                "name": "${escapeJson(sharedPDF.name)}",
                "path": "${escapeJson(sharedPDF.path)}",
                "size": "${escapeJson(sharedPDF.size)}",
                "date": "${escapeJson(sharedPDF.date)}",
                "sharedDate": "${escapeJson(sharedPDF.sharedDate)}",
                "id": ${sharedPDFs.indexOf(sharedPDF) + 10000}
            }
            """.trimIndent()
        }
        
        val jsCode = "updateSharedPDFsList($jsonArray)"
        webView.post {
            webView.evaluateJavascript(jsCode, null)
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 -> String.format("%.1f MB", size.toDouble() / (1024 * 1024))
            size >= 1024 -> String.format("%.0f KB", size.toDouble() / 1024)
            else -> "$size B"
        }
    }

    private fun checkAndUpdatePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Android 10 ve altı için true
        }

        val jsCode = if (hasPermission) {
            "onPermissionGranted()"
        } else {
            "onPermissionDenied()"
        }
        
        webView.post {
            webView.evaluateJavascript(jsCode, null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Paylaşılan PDF veri sınıfı
    data class SharedPDF(
        val name: String,
        val path: String,
        val size: String,
        val date: String,
        val sharedDate: String
    )

    // JavaScript Interface
    inner class AndroidInterface(private val activity: Activity) {

        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        }

        @JavascriptInterface
        fun openAllFilesSettings() {
            activity.runOnUiThread {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val uri = Uri.parse("package:${activity.packageName}")
                        
                        try {
                            val intent1 = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent1.data = uri
                            activity.startActivity(intent1)
                        } catch (e1: Exception) {
                            try {
                                val intent2 = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                intent2.data = uri
                                activity.startActivity(intent2)
                            } catch (e2: Exception) {
                                val intent3 = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent3.data = uri
                                activity.startActivity(intent3)
                            }
                        }
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${activity.packageName}")
                        activity.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(activity, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show()
                }
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
                "ERROR: ${e.message}"
            }
        }

        @JavascriptInterface
        fun listPDFs(): String {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        return "PERMISSION_DENIED"
                    }
                }

                val list = mutableListOf<String>()
                
                // Download klasörünü tarayın
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                scanForPDFs(downloadsDir, list, 0, 5)
                
                // PDF Reader klasörünü tarayın
                scanForPDFs(pdfReaderFolder, list, 0, 5)

                return if (list.isEmpty()) {
                    "EMPTY"
                } else {
                    list.distinct().joinToString("||")
                }
            } catch (e: Exception) {
                Log.e("PDFReader", "PDF listeleme hatası", e)
                return "ERROR: ${e.message}"
            }
        }

        private fun scanForPDFs(dir: File, list: MutableList<String>, depth: Int, maxDepth: Int) {
            if (depth > maxDepth) return
            
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        scanForPDFs(file, list, depth + 1, maxDepth)
                    } else if (file.isFile && file.name.endsWith(".pdf", true)) {
                        list.add(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                Log.e("PDFReader", "Klasör tarama hatası: ${dir.absolutePath}", e)
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
        fun printPDF(path: String) {
            activity.runOnUiThread {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            FileProvider.getUriForFile(
                                activity,
                                "${activity.packageName}.provider",
                                file
                            )
                        } else {
                            Uri.fromFile(file)
                        }
                        
                        val printIntent = Intent(Intent.ACTION_SEND)
                        printIntent.type = "application/pdf"
                        printIntent.putExtra(Intent.EXTRA_STREAM, uri)
                        printIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        
                        // "Yazdır" seçeneği için özel intent
                        val chooserIntent = Intent.createChooser(printIntent, "PDF Yazdır")
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        ))
                        
                        activity.startActivity(chooserIntent)
                    } else {
                        Toast.makeText(activity, "PDF dosyası bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("PDFReader", "Yazdırma hatası", e)
                    Toast.makeText(activity, "Yazdırma başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareFile(path: String) {
            activity.runOnUiThread {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            FileProvider.getUriForFile(
                                activity,
                                "${activity.packageName}.provider",
                                file
                            )
                        } else {
                            Uri.fromFile(file)
                        }
                        
                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.type = "application/pdf"
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        
                        val chooserTitle = "PDF Paylaş: ${file.name}"
                        activity.startActivity(Intent.createChooser(shareIntent, chooserTitle))
                    } else {
                        Toast.makeText(activity, "Dosya bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("PDFReader", "Paylaşım hatası", e)
                    Toast.makeText(activity, "Paylaşım başlatılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareFiles(paths: String) {
            activity.runOnUiThread {
                try {
                    val pathList = paths.split("||")
                    val uris = ArrayList<Uri>()
                    
                    pathList.forEach { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                FileProvider.getUriForFile(
                                    activity,
                                    "${activity.packageName}.provider",
                                    file
                                )
                            } else {
                                Uri.fromFile(file)
                            }
                            uris.add(uri)
                        }
                    }
                    
                    if (uris.isNotEmpty()) {
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
                        shareIntent.type = "application/pdf"
                        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        
                        val chooserTitle = if (uris.size == 1) {
                            "PDF Paylaş"
                        } else {
                            "${uris.size} PDF Dosyası Paylaş"
                        }
                        
                        activity.startActivity(Intent.createChooser(shareIntent, chooserTitle))
                    } else {
                        Toast.makeText(activity, "Paylaşılacak dosya bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("PDFReader", "Çoklu paylaşım hatası", e)
                    Toast.makeText(activity, "Paylaşım başlatılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun deleteFile(path: String): Boolean {
            return try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("PDFReader", "Dosya silme hatası", e)
                false
            }
        }

        @JavascriptInterface
        fun renameFile(oldPath: String, newName: String): String {
            return try {
                val oldFile = File(oldPath)
                if (oldFile.exists()) {
                    val parent = oldFile.parentFile
                    val newFile = File(parent, newName)
                    
                    if (oldFile.renameTo(newFile)) {
                        newFile.absolutePath
                    } else {
                        "ERROR"
                    }
                } else {
                    "ERROR"
                }
            } catch (e: Exception) {
                Log.e("PDFReader", "Dosya adı değiştirme hatası", e)
                "ERROR"
            }
        }

        @JavascriptInterface
        fun getFileForSharing(path: String): String {
            return try {
                val file = File(path)
                if (file.exists()) {
                    // Base64'e çevir veya file URI döndür
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.provider",
                            file
                        ).toString()
                    } else {
                        Uri.fromFile(file).toString()
                    }
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
