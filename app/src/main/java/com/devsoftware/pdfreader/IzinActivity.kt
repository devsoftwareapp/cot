package com.devsoftware.pdfreader

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class IzinActivity : AppCompatActivity() {

    companion object {
        const val IZIN_KODU = 900
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val izinListesi = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val eksik = izinListesi.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (eksik) {
            ActivityCompat.requestPermissions(this, izinListesi, IZIN_KODU)
        } else {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == IZIN_KODU) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                setResult(Activity.RESULT_OK)
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
        }
        finish()
    }
}
