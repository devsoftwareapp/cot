package com.devsoftware.pdfreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // 🔥 PDF input için EKLENEN zorunlu değişken
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // Android 11+ için dosya seçici izni
    private val manageStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowFileAccess = true
        webView.settings.domStorageEnabled = true

        // Javascript arayüzü ekle
        webView.addJavascriptInterface(AndroidInterface(), "Android")

        // Dosya seçim problemini çözen EKLENTİ
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {

                // Eski callback iptal
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val intent = fileChooserParams.createIntent()

                return try {
                    startActivityForResult(intent, 1001)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileChooserCallback = null
                    false
                }
            }
        }

        // WebViewClient - sayfa yükleme
        webView.webViewClient = object : WebViewClient() {}

        // Local HTML yükle
        webView.loadUrl("file:///android_asset/web/sesli_okuma.html")
    }

    // 🔥 Dosya seçiminden dönen sonucu PDF.js'e geri veren kod
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            fileChooserCallback?.onReceiveValue(result)
            fileChooserCallback = null
        }
    }

    // Javascript Interface - Android → JS iletişimi
    inner class AndroidInterface {

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun hasManageStoragePermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        }

        @JavascriptInterface
        fun requestManageStoragePermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    manageStoragePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStoragePermissionLauncher.launch(intent)
                }
            }
        }
    }
}
