
package com.devsoftware.pdfreader

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

abstract class BaseWebActivity : AppCompatActivity() {

    protected lateinit var wv: WebView
    abstract fun getAssetPath(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wv = WebView(this)
        setContentView(wv)

        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = true

        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = WebViewClient()

        // placeholder güvenli JS interface
        wv.addJavascriptInterface(Placeholder(), "Android")

        try {
            wv.loadUrl("file:///android_asset/web/" + getAssetPath())
        } catch (e: Exception) {
            wv.loadData("<h2>Placeholder Activity</h2><p>${getAssetPath()} bulunamadı.</p>",
                "text/html", "UTF-8")
        }
    }

    inner class Placeholder {
        @android.webkit.JavascriptInterface
        fun ping() {} // hiçbir şey yapmaz
    }
}
