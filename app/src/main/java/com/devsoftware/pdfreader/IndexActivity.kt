package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
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

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        pdfReaderFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "PDF Reader"
        )
        if (!pdfReaderFolder.exists()) pdfReaderFolder.mkdirs()
        if (!sharedPDFsFolder.exists()) sharedPDFsFolder.mkdirs()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                checkAndUpdatePermission()

                // ❗ SADECE INDEX.HTML ÜZERİNDE PDF LİSTESİ YÜKLE
                if (url?.contains("index.html") == true) {
                    loadSharedPDFs()
                }
            }
        }

        webView.addJavascriptInterface(AndroidInterface(this), "Android")
        webView.loadUrl("file:///android_asset/web/index.html")

        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        checkAndUpdatePermission()

        // Sadece index açıksa tekrar liste yükle
        if (webView.url?.contains("index.html") == true) {
            loadSharedPDFs()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        intent ?: return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "application/pdf") {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) saveSharedPDF(uri)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.forEach { uri ->
                    if (uri.toString().endsWith(".pdf", true)) saveSharedPDF(uri)
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

                FileOutputStream(outputFile).use { output ->
                    inputStream.copyTo(output)
                }

                val sharedPDF = SharedPDF(
                    name = fileName,
                    path = outputFile.absolutePath,
                    size = formatFileSize(outputFile.length()),
                    date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                    sharedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                )

                sharedPDFs.add(0, sharedPDF)

                Handler(Looper.getMainLooper()).postDelayed({
                    updateSharedPDFsList()
                }, 500)

                Toast.makeText(this, "PDF Paylaşılanlar'a kaydedildi", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("PDFReader", "Kaydetme hatası", e)
            Toast.makeText(this, "PDF kaydedilemedi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSharedPDFs() {
        sharedPDFs.clear()

        if (sharedPDFsFolder.exists()) {
            sharedPDFsFolder.listFiles { file -> file.isFile && file.name.endsWith(".pdf") }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    sharedPDFs.add(
                        SharedPDF(
                            name = file.name,
                            path = file.absolutePath,
                            size = formatFileSize(file.length()),
                            date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(file.lastModified())),
                            sharedDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                        )
                    )
                }
        }

        updateSharedPDFsList()
    }

    private fun updateSharedPDFsList() {
        val jsonArray = sharedPDFs.joinToString(", ", "[", "]") { pdf ->
            """
            {
              "name": "${escapeJson(pdf.name)}",
              "path": "${escapeJson(pdf.path)}",
              "size": "${escapeJson(pdf.size)}",
              "date": "${escapeJson(pdf.date)}",
              "sharedDate": "${escapeJson(pdf.sharedDate)}",
              "id": ${sharedPDFs.indexOf(pdf) + 10000}
            }
            """
        }

        webView.post {
            webView.evaluateJavascript("updateSharedPDFsList($jsonArray)", null)
        }
    }

    private fun escapeJson(str: String) =
        str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 -> String.format("%.1f MB", size.toDouble() / 1024 / 1024)
            size >= 1024 -> String.format("%.0f KB", size.toDouble() / 1024)
            else -> "${size} B"
        }
    }

    private fun checkAndUpdatePermission() {
        val hasPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else true

        val js = if (hasPermission) "onPermissionGranted()" else "onPermissionDenied()"

        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    data class SharedPDF(
        val name: String,
        val path: String,
        val size: String,
        val date: String,
        val sharedDate: String
    )

    inner class AndroidInterface(private val activity: Activity) {

        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else true
        }

        @JavascriptInterface
        fun openAllFilesSettings() {
            activity.runOnUiThread {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val uri = Uri.parse("package:${activity.packageName}")
                        val i1 = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = uri }
                        try {
                            activity.startActivity(i1)
                        } catch (_: Exception) {
                            val i2 = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            activity.startActivity(i2)
                        }
                    } else {
                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        i.data = Uri.parse("package:${activity.packageName}")
                        activity.startActivity(i)
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
                when {
                    folder.exists() -> "EXISTS"
                    folder.mkdirs() -> "SUCCESS"
                    else -> "FAILED"
                }
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
        }

        @JavascriptInterface
        fun listPDFs(): String {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Environment.isExternalStorageManager()
                ) return "PERMISSION_DENIED"

                val list = mutableListOf<String>()

                scanForPDFs(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    list,
                    0,
                    3
                )

                scanForPDFs(pdfReaderFolder, list, 0, 3)

                return if (list.isEmpty()) "EMPTY" else list.distinct().joinToString("||")

            } catch (e: Exception) {
                return "ERROR"
            }
        }

        private fun scanForPDFs(dir: File, list: MutableList<String>, depth: Int, maxDepth: Int) {
            if (depth > maxDepth) return
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        scanForPDFs(file, list, depth + 1, maxDepth)
                    } else if (file.name.endsWith(".pdf", true)) {
                        list.add(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                Log.e("PDFReader", "Tarama hatası: ${dir.absolutePath}", e)
            }
        }

        @JavascriptInterface
        fun getFileSize(path: String): String =
            try {
                val f = File(path)
                if (f.exists()) f.length().toString() else "0"
            } catch (e: Exception) {
                "0"
            }

        @JavascriptInterface
        fun getFileDate(path: String): String =
            try {
                val f = File(path)
                val d = if (f.exists()) f.lastModified() else System.currentTimeMillis()
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(d))
            } catch (e: Exception) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            }

        @JavascriptInterface
        fun printPDF(path: String) {
            activity.runOnUiThread {
                try {
                    val file = File(path)
                    if (!file.exists()) {
                        Toast.makeText(activity, "PDF bulunamadı", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
                    else Uri.fromFile(file)

                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "application/pdf"
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    activity.startActivity(Intent.createChooser(intent, "PDF Yazdır"))

                } catch (e: Exception) {
                    Toast.makeText(activity, "Yazdırma açılamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareFile(path: String) {
            activity.runOnUiThread {
                try {
                    val file = File(path)
                    if (!file.exists()) {
                        Toast.makeText(activity, "Dosya yok", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
                    else Uri.fromFile(file)

                    val share = Intent(Intent.ACTION_SEND)
                    share.type = "application/pdf"
                    share.putExtra(Intent.EXTRA_STREAM, uri)
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    activity.startActivity(Intent.createChooser(share, "PDF Paylaş"))

                } catch (e: Exception) {
                    Toast.makeText(activity, "Paylaşım hatası", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareFiles(paths: String) {
            activity.runOnUiThread {
                try {
                    val files = paths.split("||")
                    val uris = ArrayList<Uri>()

                    files.forEach { p ->
                        val f = File(p)
                        if (f.exists()) {
                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                                FileProvider.getUriForFile(activity, "${activity.packageName}.provider", f)
                            else Uri.fromFile(f)

                            uris.add(uri)
                        }
                    }

                    if (uris.isEmpty()) {
                        Toast.makeText(activity, "Dosya yok", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val share = Intent(Intent.ACTION_SEND_MULTIPLE)
                    share.type = "application/pdf"
                    share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    activity.startActivity(Intent.createChooser(share, "PDF Paylaş"))

                } catch (e: Exception) {
                    Toast.makeText(activity, "Paylaşım hatası", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun deleteFile(path: String): Boolean {
            return try {
                val file = File(path)
                file.exists() && file.delete()
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun renameFile(oldPath: String, newName: String): String {
            return try {
                val oldFile = File(oldPath)
                if (!oldFile.exists()) return "ERROR"

                val newFile = File(oldFile.parent, newName)
                if (oldFile.renameTo(newFile)) newFile.absolutePath else "ERROR"

            } catch (e: Exception) {
                "ERROR"
            }
        }

        @JavascriptInterface
        fun getFileForSharing(path: String): String {
            return try {
                val f = File(path)
                if (!f.exists()) return ""

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    FileProvider.getUriForFile(activity, "${activity.packageName}.provider", f).toString()
                else
                    Uri.fromFile(f).toString()

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
