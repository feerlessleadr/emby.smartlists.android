package com.feerlessleadr.embysmartlists.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embysmartlists.AppContainer
import com.feerlessleadr.embysmartlists.data.ApiClient
import com.feerlessleadr.embysmartlists.data.ApiException
import com.feerlessleadr.embysmartlists.data.Catalog
import com.feerlessleadr.embysmartlists.data.SmartListDoc
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListsUiState(
    val lists: List<SmartListDoc> = emptyList(),
    val catalog: Catalog? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Lists with a call in flight: their buttons are disabled. */
    val busyIds: Set<String> = emptySet(),
    val error: String? = null,
)

class ListsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ListsUiState())
    val state: StateFlow<ListsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load(initial = true)
    }

    fun reload() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = initial && it.lists.isEmpty(), refreshing = !initial, error = null) }
            val api = container.api() ?: return@launch
            try {
                val known = _state.value.catalog
                val (lists, catalog) = coroutineScope {
                    val l = async { api.lists() }
                    val c = async { known ?: api.catalog() }
                    l.await() to c.await()
                }
                _state.update {
                    it.copy(lists = lists.sortedBy { doc -> doc.name.lowercase() }, catalog = catalog, loading = false, refreshing = false)
                }
            } catch (e: ApiException) {
                failed(e) { msg -> _state.update { it.copy(loading = false, refreshing = false, error = msg) } }
            }
        }
    }

    fun refreshList(doc: SmartListDoc) = act(doc.id, "Refresh started for ${doc.name}") { refresh(doc.id) }.also { reloadSoon() }

    fun refreshAll() {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                api.refreshAll()
                _messages.emit("Refresh started for all lists")
                reloadSoon()
            } catch (e: ApiException) {
                failed(e) { _messages.tryEmit(it) }
            }
        }
    }

    fun setEnabled(doc: SmartListDoc, enabled: Boolean) {
        act(doc.id, if (enabled) "${doc.name} enabled" else "${doc.name} disabled") {
            if (enabled) enable(doc.id) else disable(doc.id)
        }.also { reloadSoon() }
    }

    fun delete(doc: SmartListDoc, alsoDeleteEmbyList: Boolean) {
        act(doc.id, "${doc.name} deleted") { delete(doc.id, alsoDeleteEmbyList) }
    }

    /** Runs one API call for a list, shows the outcome as a message and reloads the lists. */
    private fun act(id: String, success: String, call: suspend ApiClient.() -> Unit) {
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(busyIds = it.busyIds + id) }
            try {
                api.call()
                _messages.emit(success)
            } catch (e: ApiException) {
                failed(e) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - id) }
            }
            load(initial = false)
        }
    }

    /** A refresh runs in the background on the server: look again after a few seconds. */
    private fun reloadSoon() {
        viewModelScope.launch {
            delay(3_000)
            load(initial = false)
            delay(6_000)
            load(initial = false)
        }
    }

    private fun failed(e: ApiException, show: (String) -> Unit) {
        if (e.isUnauthorized) {
            container.signOut()
        } else {
            show(e.message ?: "Something went wrong.")
        }
    }

    fun signOut() = container.signOut()
}
