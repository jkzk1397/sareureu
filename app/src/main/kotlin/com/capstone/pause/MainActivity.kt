package com.capstone.pause

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.service.MonitoringController
import com.capstone.pause.navigation.PauseAppShell
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 단일 액티비티. 온보딩 게이트와 하단 탭은 [PauseAppShell]이 맡는다. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var monitoringController: MonitoringController

    /** 알림에서 열 때 고를 탭("coach" 등). 한 번 쓰면 비운다. */
    private val openTab = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()
        openTab.value = intent?.getStringExtra(EXTRA_OPEN_TAB)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = uiState as? MainUiState.Ready ?: return@setContent
            val dark = when (ready.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 앱 테마가 시스템과 달라도 상태 표시줄 아이콘 색이 바탕과 맞게.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            PauseTheme(darkTheme = dark) {
                val tabRequest by openTab.collectAsStateWithLifecycle()
                PauseAppShell(
                    needsOnboarding = ready.needsOnboarding,
                    openTabRequest = tabRequest,
                    onTabRequestHandled = { openTab.value = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 켜 둔 감지가 제조사 절전 등으로 멈춰 있으면, 앱을 연 김에 다시 켠다(앱이 화면에 있을 때만 가능).
        lifecycleScope.launch { monitoringController.ensureRunning() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTab.value = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    private companion object {
        /** `core:intervention`의 PauseNotifier.EXTRA_OPEN_TAB과 같은 값. */
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
