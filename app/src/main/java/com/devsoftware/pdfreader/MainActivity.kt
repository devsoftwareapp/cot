package com.devsoftware.pdfreader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.devsoftware.pdfreader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Index.html'e git
        b.btnIndex.setOnClickListener {
            startActivity(Intent(this, IndexActivity::class.java))
        }
        // Sesli Okuma
        b.btnSesliOkuma.setOnClickListener {
            startActivity(Intent(this, SesliOkumaActivity::class.java))
        }
        // PDF birleştirme
        b.btnBirlestirme.setOnClickListener {
            startActivity(Intent(this, BirlestirmeActivity::class.java))
        }
    }
}
