import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() =
    application {
        val appIcon = painterResource("brand/sundial-icon.png")
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sundial",
            icon = appIcon,
            state = rememberWindowState(width = 1000.dp, height = 680.dp),
        ) {
            window.minimumSize = Dimension(720, 500)
            MainView()
        }
    }
