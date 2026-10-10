package com.feerlessleadr.embysmartlists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.feerlessleadr.embysmartlists.ui.EmbySmartListsTheme
import com.feerlessleadr.embysmartlists.ui.ListsScreen
import com.feerlessleadr.embysmartlists.ui.ListsViewModel
import com.feerlessleadr.embysmartlists.ui.SignInScreen
import com.feerlessleadr.embysmartlists.ui.SignInViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        setContent {
            EmbySmartListsTheme {
                Root(container)
            }
        }
    }
}

@Composable
private fun Root(container: AppContainer) {
    val session by container.session.collectAsStateWithLifecycle()
    if (session == null) {
        val vm: SignInViewModel = viewModel(factory = viewModelFactory { initializer { SignInViewModel(container) } })
        SignInScreen(vm)
    } else {
        // A new view model per signed-in session, so signing in as someone else never shows the previous lists.
        val key = session?.token.orEmpty()
        val vm: ListsViewModel = viewModel(key = "lists-$key", factory = viewModelFactory { initializer { ListsViewModel(container) } })
        ListsScreen(vm)
    }
}
