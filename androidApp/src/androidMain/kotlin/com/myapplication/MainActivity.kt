package com.myapplication

import MainView
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myapplication.shared.data.setAndroidAppContext

class MainActivity : AppCompatActivity() {
    private var launchTarget by mutableStateOf<String?>(null)
    private var launchNonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLaunchIntent(intent)
        // targetSdk 36 起系统强制 edge-to-edge：显式配置状态栏/导航栏样式，
        // 让系统按当前主题（浅/深色）自动选择图标明暗，否则透明状态栏下图标
        // 与 app 背景同色，看起来像系统状态栏"消失"。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setAndroidAppContext(applicationContext)
        setContent {
            MainView(launchTarget = launchTarget, launchNonce = launchNonce)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
    }

    private fun applyLaunchIntent(intent: Intent?) {
        launchTarget = launchTargetFromIntent(intent)
        launchNonce += 1
    }

    private fun launchTargetFromIntent(intent: Intent?): String? = intent?.getStringExtra(EXTRA_SUNDIAL_TARGET)

    companion object {
        const val EXTRA_SUNDIAL_TARGET = "com.myapplication.extra.SUNDIAL_TARGET"
        const val TARGET_WORKBENCH = "workbench"
        const val TARGET_TODAY = "today"
    }
}
