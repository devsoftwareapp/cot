package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class IndexActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // WebView ayarları
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // WebView client'ları
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Sayfa yüklendiğinde izin kontrolü yap
                checkAndUpdatePermission()
            }
        }

        // JavaScript Interface EKLEYİN
        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        // Local HTML yükle
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onResume() {
        super.onResume()
        // Aktivite resume olduğunda izin kontrolü
        checkAndUpdatePermission()
    }

    private fun checkAndUpdatePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Android 10 ve altı için true
        }

        // JavaScript'e izin durumunu bildir
        val jsCode = if (hasPermission) {
            "onPermissionGranted()"
        } else {
            "onPermissionDenied()"
        }
        
        webView.evaluateJavascript(jsCode, null)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // JavaScript Interface Class
    inner class WebAppInterface(private val activity: Activity) {

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
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${activity.packageName}")
                        activity.startActivity(intent)
                    } else {
                        // Android 10 ve altı için
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${activity.packageName}")
                        activity.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(activity, "Ayarlar açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun listPDFs(): String {
            return try {
                // İzin kontrolü
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !Environment.isExternalStorageManager()) {
                    return "PERMISSION_DENIED"
                }

                val root = Environment.getExternalStorageDirectory()
                val list = ArrayList<String>()

                // PDF'leri tarama
                scanForPDFs(root, list)

                if (list.isEmpty()) {
                    "EMPTY"
                } else {
                    list.joinToString("||")
                }
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
        }

        private fun scanForPDFs(directory: File, pdfList: ArrayList<String>) {
            try {
                val files = directory.listFiles() ?: return
                
                for (file in files) {
                    if (file.isDirectory) {
                        // Alt dizinleri tarama (derinlik sınırı)
                        if (!file.name.startsWith(".")) { // Gizli dosyaları atla
                            scanForPDFs(file, pdfList)
                        }
                    } else if (file.isFile && file.name.lowercase().endsWith(".pdf")) {
                        pdfList.add(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
