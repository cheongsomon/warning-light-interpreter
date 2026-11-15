package ai.pleos.warninglight

import ai.pleos.warninglight.ui.MainScreen
import ai.pleos.warninglight.ui.WarningTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WarningTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                MainScreen(vm, state)
            }
        }
    }
}
