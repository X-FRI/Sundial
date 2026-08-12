package com.myapplication

import MainView
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.myapplication.shared.data.setAndroidAppContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 起系统强制 edge-to-edge：显式配置状态栏/导航栏样式，
        // 让系统按当前主题（浅/深色）自动选择图标明暗，否则透明状态栏下图标
        // 与 app 背景同色，看起来像系统状态栏"消失"。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setAndroidAppContext(applicationContext)
        setContent {
            MainView()
        }
    }
}