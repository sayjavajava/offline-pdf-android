package com.offgridpdf.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.offgridpdf.android.ui.navigation.OffGridNavHost
import com.offgridpdf.android.ui.theme.OffGridPdfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OffGridPdfTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OffGridNavHost()
                }
            }
        }
    }
}
