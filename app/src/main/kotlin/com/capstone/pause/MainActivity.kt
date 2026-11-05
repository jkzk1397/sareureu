package com.capstone.pause

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.navigation.PauseAppShell
import dagger.hilt.android.AndroidEntryPoint

/** 단일 액티비티. 온보딩 게이트와 하단 탭은 [PauseAppShell]이 맡는다. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = uiState as? MainUiState.Ready ?: return@setContent
            val dark = when (ready.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PauseTheme(darkTheme = dark) {
                PauseAppShell(needsOnboarding = ready.needsOnboarding)
            }
        }
    }
}
