package com.parental.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parental.shared.Platform
import com.parental.shared.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize platform-specific storage
        Platform.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
