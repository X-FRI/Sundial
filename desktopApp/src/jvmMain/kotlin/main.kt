import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "提醒事项",
        state = rememberWindowState(width = 1000.dp, height = 680.dp),
    ) {
        window.minimumSize = Dimension(720, 500)
        MainView()
    }
}