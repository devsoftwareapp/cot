package com.devsoftware.pdfreader

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.devsoftware.pdfreader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ekranda SDK bilgisini göster
        binding.textViewHello.text = "Hello Android SDK 36 🎉"
    }
}
