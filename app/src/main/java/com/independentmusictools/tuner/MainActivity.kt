package com.independentmusictools.tuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.independentmusictools.tuner.settings.TunerSettings
import com.independentmusictools.tuner.state.TunerViewModel
import com.independentmusictools.tuner.ui.TunerScreen
import com.independentmusictools.tuner.ui.theme.TunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TunerTheme {
                val viewModel = remember { TunerViewModel(TunerSettings(applicationContext)) }
                DisposableEffect(Unit) {
                    onDispose { viewModel.dispose() }
                }
                TunerScreen(viewModel)
            }
        }
    }
}
