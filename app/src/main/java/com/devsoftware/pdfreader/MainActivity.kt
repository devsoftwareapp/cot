package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // Web Ayarları
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.allowUniversalAccessFromFileURLs = true

        // JavaScript Arayüzünü Ekle (Adı: Android)
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // HTML'deki "Ayarlardan Erişim Ver" butonuna tıklanınca burası çalışır
                if (url == "settings://all_files") {
                    openAllFilesPermission()
                    return true
                }
                return false
            }
        }

        // HTML Dosyasını Yükle
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    /** İzin Ekranını Açan Fonksiyon */
    private fun openAllFilesPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    /** Uygulamaya geri dönüldüğünde (Resume) HTML'i tetikle */
    override fun onResume() {
        super.onResume()
        // Sayfa tam yüklenmemiş olabilir diye ufak bir kontrol/gecikme eklenebilir ama
        // direkt çağrı genellikle yeterlidir.
        webView.postDelayed({
            webView.evaluateJavascript("if(window.onAndroidResume) { window.onAndroidResume(); }", null)
        }, 500)
    }

    /** JavaScript ile konuşan köprü sınıfı */
    inner class AndroidBridge {

        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                // Android 10 ve altı için basit okuma izni kontrolü (ContextCompat gerekebilir ama şimdilik true varsayalım)
                true
            }
        }

        @JavascriptInterface
        fun listPDFs(): String {
            val pdfList = ArrayList<String>()
            
            // Taranacak kök dizinler
            val roots = listOf(
                Environment.getExternalStorageDirectory() // /storage/emulated/0 genellikle
            )

            roots.forEach { root ->
                if (root.exists()) {
                    scanPDFs(root, pdfList)
                }
            }
            // Listeyi string olarak birleştirip gönderiyoruz (path1||path2||path3)
            return pdfList.joinToString("||")
        }

        private fun scanPDFs(folder: File, output: MutableList<String>) {
            // Gizli klasörleri ve gereksizleri atla
            if (!folder.exists()) return
            if (folder.name.startsWith(".")) return 
            if (folder.name.equals("Android", ignoreCase = true)) return // Android klasörünü atla (performans için)

            val files = folder.listFiles() ?: return

            for (file in files) {
                if (file.isDirectory) {
                    scanPDFs(file, output)
                } else {
                    if (file.name.lowercase().endsWith(".pdf")) {
                        output.add(file.absolutePath)
                    }
                }
            }
        }
    }
}
