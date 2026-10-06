package com.athar.ludo3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.athar.ludo3d.presentation.navigation.LudoNavGraph
import com.athar.ludo3d.presentation.theme.Ludo3DTheme
import dagger.hilt.android.AndroidEntryPoint

// Added by Athar
// Single-activity host. All screens are Compose destinations inside LudoNavGraph.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ludo3DTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LudoNavGraph()
                }
            }
        }
    }
}
