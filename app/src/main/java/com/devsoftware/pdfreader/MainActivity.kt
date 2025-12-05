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

        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {

                if (url == "settings://all_files") {
                    openAllFilesPermission()
                    return true
                }

                return false
            }
        }

        webView.loadUrl("file:///android_asset/web/index.html")
    }

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
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun checkPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        }

        @JavascriptInterface
        fun listPDFs(): String {

            val pdfList = ArrayList<String>()

            val roots = listOf(
                File("/storage/emulated/0"),
                File("/sdcard"),
                File("/storage/self/primary")
            )

            roots.forEach { root ->
                if (root.exists()) {
                    scanPDFs(root, pdfList)
                }
            }

            return pdfList.joinToString("||")
        }

        private fun scanPDFs(folder: File, output: MutableList<String>) {
            if (!folder.exists()) return
            if (folder.name.startsWith(".")) return

            folder.listFiles()?.forEach { file ->

                if (file.isDirectory) {
                    scanPDFs(file, output)
                } else if (file.name.lowercase().endsWith(".pdf")) {
                    output.add(file.absolutePath)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        webView.post {
            webView.evaluateJavascript("onAndroidResume()", null)
        }
    }
}
