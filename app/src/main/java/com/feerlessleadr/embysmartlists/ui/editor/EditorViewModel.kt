package com.feerlessleadr.embysmartlists.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embysmartlists.AppContainer
import com.feerlessleadr.embysmartlists.data.ApiException
import com.feerlessleadr.embysmartlists.data.Catalog
import com.feerlessleadr.embysmartlists.data.EmbyUser
import com.feerlessleadr.embysmartlists.data.ListDraft
import com.feerlessleadr.embysmartlists.data.Templates
import com.feerlessleadr.embysmartlists.data.ValueOption
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val catalog: Catalog? = null,
    val users: List<EmbyUser> = emptyList(),
    val libraries: List<ValueOption> = emptyList(),
    val draft: ListDraft? = null,
    /** A new list starts by choosing a template. */
    val choosingTemplate: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
)

/** Creates a new list (when [listId] is null) or edits an existing one. */
class EditorViewModel(private val container: AppContainer, private val listId: String?) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState(choosingTemplate = listId == null))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            val api = container.api() ?: return@launch
            try {
                coroutineScope {
                    val catalog = async { api.catalog() }
                    val users = async { runCatching { api.users() }.getOrDefault(emptyList()) }
                    val libraries = async { runCatching { api.libraries() }.getOrDefault(emptyList()) }
                    val doc = listId?.let { id -> async { api.list(id) } }
                    val loadedUsers = users.await()
                    _state.update {
                        it.copy(
                            loading = false,
                            catalog = catalog.await(),
                            users = loadedUsers,
                            libraries = libraries.await(),
                            draft = doc?.await()?.let(ListDraft::from) ?: it.draft,
                        )
                    }
                }
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(loading = false, loadError = e.message) }
            }
        }
    }

    fun chooseTemplate(id: String) {
        val users = _state.value.users
        val me = container.session.value?.userId
        val owner = users.firstOrNull { it.id.equals(me, ignoreCase = true) }?.id ?: users.firstOrNull()?.id
        _state.update { it.copy(draft = Templates.draft(id, owner), choosingTemplate = false) }
    }

    fun update(change: (ListDraft) -> ListDraft) {
        _state.update { s -> s.copy(draft = s.draft?.let(change), saveError = null) }
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val draft = s.draft ?: return
        draft.validate(s.catalog)?.let { problem ->
            _state.update { it.copy(saveError = problem) }
            return
        }
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(saving = true, saveError = null) }
            try {
                val json = draft.toJson()
                if (draft.isNew) api.create(json) else api.update(listId.orEmpty(), json)
                container.notifyListsChanged()
                _state.update { it.copy(saving = false) }
                onSaved()
            } catch (e: ApiException) {
                if (e.isUnauthorized) {
                    container.signOut()
                } else {
                    _state.update { it.copy(saving = false, saveError = e.message) }
                }
            }
        }
    }
}
