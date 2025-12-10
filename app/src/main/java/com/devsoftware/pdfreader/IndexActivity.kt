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

        // PDF Reader klasörü
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
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkAndUpdatePermission()
                loadSharedPDFs()
            }
        }

        webView.addJavascriptInterface(AndroidInterface(this), "Android")

        // SADECE index.html
        webView.loadUrl("file:///android_asset/web/index.html")

        loadSharedPDFs()
        handleShareIntent(intent)
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
                }, 500)
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
                "sharedDate": "${escape(p.sharedDate)}"
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

    inner class AndroidInterface(private val act: Activity) {

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
                    Toast.makeText(act, "Ayar açılamadı", Toast.LENGTH_SHORT).show()
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
        fun showToast(msg: String) {
            act.runOnUiThread {
                Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
