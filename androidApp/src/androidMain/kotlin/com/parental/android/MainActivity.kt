package com.parental.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parental.shared.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Platform.init y Koin se inicializan una sola vez en ParentalApplication.
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
