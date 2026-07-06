package com.arikw.itemnotifier.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arikw.itemnotifier.data.ItemRepository
import com.arikw.itemnotifier.data.db.StockStatus
import com.arikw.itemnotifier.data.db.TrackedItem
import com.arikw.itemnotifier.data.model.ProductSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AddItemUiState {
    data object Idle : AddItemUiState
    data object Loading : AddItemUiState
    data class Loaded(val snapshot: ProductSnapshot, val url: String) : AddItemUiState
    data class Error(val message: String) : AddItemUiState
}

class AddItemViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ItemRepository(application)

    private val _state = MutableStateFlow<AddItemUiState>(AddItemUiState.Idle)
    val state: StateFlow<AddItemUiState> = _state

    private val _selectedColor = MutableStateFlow<Int?>(null)
    val selectedColor: StateFlow<Int?> = _selectedColor

    private val _selectedSizes = MutableStateFlow<Set<Int>>(emptySet())
    val selectedSizes: StateFlow<Set<Int>> = _selectedSizes

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    fun loadProduct(rawUrl: String) {
        val url = extractUrl(rawUrl)
        if (url == null) {
            _state.value = AddItemUiState.Error("That doesn't look like a product link")
            return
        }
        _state.value = AddItemUiState.Loading
        _selectedSizes.value = emptySet()
        viewModelScope.launch {
            try {
                val snapshot = repository.fetchProduct(url)
                // Preselect the colorway from the URL's ?color= parameter when present.
                val urlColor = runCatching {
                    Uri.parse(url).getQueryParameter("color")?.toIntOrNull()
                }.getOrNull()
                _selectedColor.value = when {
                    urlColor != null && snapshot.colors.any { it.valueIndex == urlColor } -> urlColor
                    else -> snapshot.colors.firstOrNull()?.valueIndex
                }
                _state.value = AddItemUiState.Loaded(snapshot, url)
            } catch (e: Exception) {
                _state.value = AddItemUiState.Error(
                    e.message ?: "Couldn't load that product page"
                )
            }
        }
    }

    fun selectColor(valueIndex: Int) {
        _selectedColor.value = valueIndex
        _selectedSizes.value = emptySet()
    }

    fun toggleSize(valueIndex: Int) {
        _selectedSizes.value = _selectedSizes.value.let {
            if (valueIndex in it) it - valueIndex else it + valueIndex
        }
    }

    fun save() {
        val current = _state.value as? AddItemUiState.Loaded ?: return
        val snapshot = current.snapshot
        val colorIndex = _selectedColor.value
        val colorLabel = snapshot.colors.firstOrNull { it.valueIndex == colorIndex }?.label
        val now = System.currentTimeMillis()

        val items = snapshot.sizes
            .filter { it.valueIndex in _selectedSizes.value }
            .map { size ->
                val variant = snapshot.variantFor(size.valueIndex, size.label, colorIndex)
                TrackedItem(
                    url = current.url,
                    name = snapshot.name,
                    imageUrl = snapshot.imageUrl,
                    colorIndex = colorIndex,
                    colorLabel = colorLabel,
                    sizeLabel = size.label,
                    sizeIndex = size.valueIndex,
                    // Record current availability so we only notify on a real
                    // out-of-stock -> in-stock transition after tracking starts.
                    lastStatus = when (variant?.inStock) {
                        true -> StockStatus.IN_STOCK
                        false -> StockStatus.OUT_OF_STOCK
                        null -> StockStatus.UNKNOWN
                    },
                    lastCheckedAt = now,
                    createdAt = now,
                    siteName = snapshot.siteName,
                    lastPrice = variant?.price ?: snapshot.price,
                    lastWasPrice = variant?.compareAtPrice ?: snapshot.compareAtPrice,
                    currency = snapshot.currency,
                    promoText = snapshot.promoText,
                )
            }

        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.addItems(items)
            _saved.value = true
        }
    }

    /** For sites without per-size data: track availability of the whole product. */
    fun saveWholeProduct() {
        val current = _state.value as? AddItemUiState.Loaded ?: return
        val snapshot = current.snapshot
        val now = System.currentTimeMillis()

        val item = TrackedItem(
            url = current.url,
            name = snapshot.name,
            imageUrl = snapshot.imageUrl,
            colorIndex = null,
            colorLabel = null,
            sizeLabel = "",
            sizeIndex = null,
            lastStatus = when (snapshot.anyAvailable()) {
                true -> StockStatus.IN_STOCK
                false -> StockStatus.OUT_OF_STOCK
                null -> StockStatus.UNKNOWN
            },
            lastCheckedAt = now,
            createdAt = now,
            siteName = snapshot.siteName,
            lastPrice = snapshot.price,
            lastWasPrice = snapshot.compareAtPrice,
            currency = snapshot.currency,
            promoText = snapshot.promoText,
        )

        viewModelScope.launch {
            repository.addItems(listOf(item))
            _saved.value = true
        }
    }

    companion object {
        /** Pulls the first URL out of shared/pasted text. */
        fun extractUrl(text: String): String? {
            val match = Regex("""https?://\S+""")
                .find(text.trim())
                ?: return null
            return match.value.trimEnd('.', ',', ')', ']')
        }
    }
}
