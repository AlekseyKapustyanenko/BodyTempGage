package com.bodytempgage.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material.MaterialTheme
import com.bodytempgage.wear.WearApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = WearApp.container(this)
        setContent {
            MaterialTheme {
                WearAppRoot(container)
            }
        }
    }
}
