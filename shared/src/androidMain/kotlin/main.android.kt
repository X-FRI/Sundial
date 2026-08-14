import androidx.compose.runtime.Composable
import com.myapplication.shared.ui.app.App

/**
 * Android 平台入口：由 Activity 等宿主调用的根 Composable。
 * 直接渲染共享的 [App]，平台差异（导航/生命周期）由宿主负责。
 */
@Composable fun MainView(
    launchTarget: String? = null,
    launchNonce: Int = 0,
) = App(launchTarget, launchNonce)
