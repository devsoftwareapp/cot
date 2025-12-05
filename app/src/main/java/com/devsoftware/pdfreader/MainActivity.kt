package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

                return if (url.startsWith("intent://")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                } else {
                    false
                }
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
    }
}
