package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

                // intent:// linklerini yakala
                if (url.startsWith("intent://")) {
                    return try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        startActivity(intent)
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        true
                    }
                }

                // Tüm dosya izni ayarına git
                if (url == "settings://all_files") {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                            // 🔥 Uygulamaya özel tüm dosya erişimi ekranına gider
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)

                        } else {

                            // Android 10 ve öncesi – uygulama ayarları
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        }

                    } catch (e: Exception) {
                        // Ekstra garanti → Genel ayarlara git
                        val fallback = Intent(Settings.ACTION_SETTINGS)
                        startActivity(fallback)
                    }

                    return true
                }

                return false
            }
        }

        // assets içinden HTML yükle
        webView.loadUrl("file:///android_asset/web/index.html")
    }
}
