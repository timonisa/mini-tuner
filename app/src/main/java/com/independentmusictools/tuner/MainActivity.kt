package com.independentmusictools.tuner

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.independentmusictools.tuner.settings.TunerSettings
import com.independentmusictools.tuner.state.TunerViewModel
import com.independentmusictools.tuner.ui.SettingsScreen
import com.independentmusictools.tuner.ui.TunerScreen
import com.independentmusictools.tuner.ui.theme.TunerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Язык выбирается в настройках и не зависит от языка системы:
        // локаль подменяется до создания интерфейса.
        val tag = TunerSettings(newBase).languageTag
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettings = remember { TunerSettings(applicationContext) }
            var themeMode by remember { mutableStateOf(appSettings.themeMode) }

            TunerTheme(themeMode = themeMode) {
                var screen by remember { mutableStateOf(Screen.TUNER) }
                val viewModel = remember { TunerViewModel(appSettings) }
                DisposableEffect(Unit) {
                    onDispose { viewModel.dispose() }
                }
                when (screen) {
                    Screen.TUNER -> TunerScreen(
                        viewModel = viewModel,
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        themeMode = themeMode,
                        languageTag = TunerSettings(applicationContext).languageTag,
                        onThemeChange = {
                            appSettings.themeMode = it
                            themeMode = it
                        },
                        onLanguageChange = {
                            appSettings.languageTag = it
                            // Язык применяется через пересоздание активити.
                            recreate()
                        },
                        onBack = { screen = Screen.TUNER },
                    )
                }
            }
        }
    }
}

private enum class Screen { TUNER, SETTINGS }
