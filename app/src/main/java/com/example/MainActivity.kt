package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.VoxoraMainScreen
import com.example.ui.VoxoraViewModel
import com.example.ui.theme.VoxoraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VoxoraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxoraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    VoxoraMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

