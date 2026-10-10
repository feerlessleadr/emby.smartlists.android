package com.feerlessleadr.embysmartlists.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feerlessleadr.embysmartlists.AppContainer
import com.feerlessleadr.embysmartlists.data.ApiException
import com.feerlessleadr.embysmartlists.data.Catalog
import com.feerlessleadr.embysmartlists.data.EmbyUser
import com.feerlessleadr.embysmartlists.data.ImageInfo
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
    /** What a "similar to" rule can compare. */
    val similarityFields: List<ValueOption> = emptyList(),
    val draft: ListDraft? = null,
    /** A new list starts by choosing a template. */
    val choosingTemplate: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    /** Custom images of an existing list, and small previews of them by image type. */
    val images: List<ImageInfo> = emptyList(),
    val previews: Map<String, Bitmap> = emptyMap(),
    val imageBusy: Boolean = false,
    val imageMessage: String? = null,
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
                    val similarity = async { runCatching { api.similarityFields() }.getOrDefault(emptyList()) }
                    val doc = listId?.let { id -> async { api.list(id) } }
                    val loadedUsers = users.await()
                    _state.update {
                        it.copy(
                            loading = false,
                            catalog = catalog.await(),
                            users = loadedUsers,
                            libraries = libraries.await(),
                            similarityFields = similarity.await(),
                            draft = doc?.await()?.let(ListDraft::from) ?: it.draft,
                        )
                    }
                }
                if (listId != null) loadImages()
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

    // ----- custom images (existing lists only: they are stored per list id) ------------------------------------

    private fun loadImages() {
        val id = listId ?: return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            try {
                val images = api.images(id)
                _state.update { it.copy(images = images) }
                images.forEach { image ->
                    launch {
                        val bitmap = runCatching { decodePreview(api.imageBytes(id, image.type)) }.getOrNull()
                        if (bitmap != null) _state.update { it.copy(previews = it.previews + (image.type to bitmap)) }
                    }
                }
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(imageMessage = e.message) }
            }
        }
    }

    fun uploadImage(imageType: String, fileName: String, mimeType: String, bytes: ByteArray) {
        val id = listId ?: return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(imageBusy = true, imageMessage = null) }
            try {
                api.uploadImage(id, imageType, fileName, mimeType, bytes)
                _state.update { it.copy(previews = it.previews - imageType) }
                loadImages()
                container.notifyListsChanged()
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(imageMessage = e.message) }
            } finally {
                _state.update { it.copy(imageBusy = false) }
            }
        }
    }

    fun deleteImage(imageType: String) {
        val id = listId ?: return
        viewModelScope.launch {
            val api = container.api() ?: return@launch
            _state.update { it.copy(imageBusy = true, imageMessage = null) }
            try {
                api.deleteImage(id, imageType)
                _state.update { s -> s.copy(images = s.images.filterNot { it.type == imageType }, previews = s.previews - imageType) }
                container.notifyListsChanged()
            } catch (e: ApiException) {
                if (e.isUnauthorized) container.signOut() else _state.update { it.copy(imageMessage = e.message) }
            } finally {
                _state.update { it.copy(imageBusy = false) }
            }
        }
    }

    fun imageProblem(message: String) {
        _state.update { it.copy(imageMessage = message) }
    }

    /** A small version of the image: previews only need to be recognisable. */
    private fun decodePreview(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 600 || bounds.outHeight / sample > 600) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
