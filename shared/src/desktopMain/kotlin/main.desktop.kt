import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.myapplication.shared.ui.app.App

/**
 * Desktop 平台入口：渲染共享 [App]；[AppPreview] 供 IDE 预览面板
 * （@Preview 是 desktop 专属注解，Android 端不需要）。
 */
@Composable fun MainView(
    launchTarget: String? = null,
    launchNonce: Int = 0,
) = App(launchTarget, launchNonce)

@Preview
@Composable
fun AppPreview() {
    App()
}
