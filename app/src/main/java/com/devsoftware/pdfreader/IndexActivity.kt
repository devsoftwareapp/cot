package com.devsoftware.pdfreader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class IndexActivity : AppCompatActivity() {

    private lateinit var wv: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wv = WebView(this)
        setContentView(wv)

        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true

        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = WebViewClient()

        wv.addJavascriptInterface(JS(), "Android")

        wv.loadUrl("file:///android_asset/index.html")
    }

    inner class JS {

        @JavascriptInterface
        fun izinIste() {
            val i = Intent(this@IndexActivity, IzinActivity::class.java)
            startActivityForResult(i, 50)
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)

        if (req == 50) {
            if (res == Activity.RESULT_OK) {
                wv.evaluateJavascript("izinSonucu(true)") {}
            } else {
                wv.evaluateJavascript("izinSonucu(false)") {}
            }
        }
    }
}
