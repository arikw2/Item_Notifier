package com.arikw.itemnotifier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.arikw.itemnotifier.data.ItemRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddItemScreen(
    initialUrl: String?,
    onDone: () -> Unit,
    viewModel: AddItemViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val selectedSizes by viewModel.selectedSizes.collectAsState()
    val saved by viewModel.saved.collectAsState()

    var urlText by rememberSaveable { mutableStateOf(initialUrl ?: "") }
    var tab by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(saved) {
        if (saved) onDone()
    }
    // A link shared into the app loads immediately.
    LaunchedEffect(Unit) {
        if (!initialUrl.isNullOrBlank() && state is AddItemUiState.Idle) {
            viewModel.loadProduct(initialUrl)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "Track an item" else "Watch a search") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Track item") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Watch search") })
            }
            if (tab == 0) {
                TrackItemTab(
                    urlText = urlText,
                    onUrlChange = { urlText = it },
                    state = state,
                    selectedColor = selectedColor,
                    selectedSizes = selectedSizes,
                    viewModel = viewModel,
                )
            } else {
                WatchSearchTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun TrackItemTab(
    urlText: String,
    onUrlChange: (String) -> Unit,
    state: AddItemUiState,
    selectedColor: Int?,
    selectedSizes: Set<Int>,
    viewModel: AddItemViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            OutlinedTextField(
                value = urlText,
                onValueChange = onUrlChange,
                label = { Text("Product link") },
                placeholder = { Text("https://…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { viewModel.loadProduct(urlText) },
                enabled = urlText.isNotBlank() && state !is AddItemUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load product")
            }

            when (val s = state) {
                is AddItemUiState.Idle -> {
                    Text(
                        "Paste or share a product link, then pick the sizes to watch.\n\n" +
                            "Works with Terminal X and any Shopify-based shop — Fox, " +
                            "Foot Locker IL, Laline, Fox Home and many more stores " +
                            "that accept BuyMe. Other shops usually work too, with " +
                            "whole-product tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                is AddItemUiState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AddItemUiState.Error -> {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is AddItemUiState.Loaded -> {
                    ProductDetails(
                        state = s,
                        selectedColor = selectedColor,
                        selectedSizes = selectedSizes,
                        onColorSelect = viewModel::selectColor,
                        onSizeToggle = viewModel::toggleSize,
                        onSave = viewModel::save,
                        onSaveWholeProduct = viewModel::saveWholeProduct,
                    )
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchSearchTab(viewModel: AddItemViewModel) {
    val watchState by viewModel.watchState.collectAsState()
    var queryText by rememberSaveable { mutableStateOf("") }
    var shopIndex by rememberSaveable { mutableStateOf(0) }
    var customHost by rememberSaveable { mutableStateOf("") }

    val shops = AddItemViewModel.WATCHABLE_SHOPS
    val isCustom = shopIndex == shops.size
    val shop = if (isCustom) AddItemViewModel.customShop(customHost) else shops[shopIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Get notified when a new product matching your search shows up — " +
                "e.g. watch \"novablast 6\" to hear the moment it lands.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Text("Shop", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shops.forEachIndexed { index, s ->
                FilterChip(
                    selected = shopIndex == index,
                    onClick = { shopIndex = index },
                    label = { Text(s.name) }
                )
            }
            FilterChip(
                selected = isCustom,
                onClick = { shopIndex = shops.size },
                label = { Text("Other shop…") }
            )
        }
        if (isCustom) {
            OutlinedTextField(
                value = customHost,
                onValueChange = { customHost = it },
                label = { Text("Shop address (Shopify-based)") },
                placeholder = { Text("shop.co.il") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            label = { Text("Search for") },
            placeholder = { Text("novablast 6") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = { viewModel.previewWatch(shop, queryText) },
            enabled = queryText.isNotBlank() &&
                (!isCustom || customHost.isNotBlank()) &&
                watchState !is WatchUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search now")
        }

        when (val s = watchState) {
            is WatchUiState.Idle -> {}
            is WatchUiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
            is WatchUiState.Error -> {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is WatchUiState.Previewed -> {
                Text(
                    if (s.matches.isEmpty())
                        "No matching products right now — perfect for watching. " +
                            "You'll be notified when the first one arrives."
                    else
                        "${s.matches.size} matching product(s) exist today. They won't " +
                            "trigger alerts — only products that appear from now on will.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                s.matches.take(5).forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = p.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            p.price?.let {
                                Text(
                                    ItemRepository.formatPrice(it, p.currency),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = viewModel::saveWatch,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Watch \"${s.query}\" on ${s.shop.name}")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductDetails(
    state: AddItemUiState.Loaded,
    selectedColor: Int?,
    selectedSizes: Set<Int>,
    onColorSelect: (Int) -> Unit,
    onSizeToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onSaveWholeProduct: () -> Unit,
) {
    val snapshot = state.snapshot

    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = snapshot.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                snapshot.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                snapshot.siteName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            snapshot.price?.let { price ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ItemRepository.formatPrice(price, snapshot.currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    snapshot.compareAtPrice?.let { was ->
                        Text(
                            ItemRepository.formatPrice(was, snapshot.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            snapshot.promoText?.let { promo ->
                Text(
                    promo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (snapshot.colors.size > 1) {
        Text("Color", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            snapshot.colors.forEach { color ->
                FilterChip(
                    selected = color.valueIndex == selectedColor,
                    onClick = { onColorSelect(color.valueIndex) },
                    label = { Text(color.label) }
                )
            }
        }
    }

    if (snapshot.sizes.isEmpty()) {
        // No per-size data (e.g. generic sites) — offer whole-product tracking.
        val available = snapshot.anyAvailable()
        Text(
            when (available) {
                true -> "This product is currently in stock."
                false -> "This product is currently out of stock — you'll be " +
                    "notified when it's back."
                null -> "This shop doesn't report availability up front; the app " +
                    "will still watch the page for changes."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onSaveWholeProduct,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Track this product")
        }
    } else {
        Text("Sizes to watch", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            snapshot.sizesForColor(selectedColor).forEach { (size, inStock) ->
                FilterChip(
                    selected = size.valueIndex in selectedSizes,
                    onClick = { onSizeToggle(size.valueIndex) },
                    label = {
                        Text(
                            size.label,
                            textDecoration = if (inStock) TextDecoration.None
                            else TextDecoration.LineThrough
                        )
                    }
                )
            }
        }
        Text(
            "Struck-through sizes are currently out of stock — those are the ones " +
                "worth watching. You'll get a notification when one comes back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Button(
            onClick = onSave,
            enabled = selectedSizes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (selectedSizes.size <= 1) "Track this item"
                else "Track ${selectedSizes.size} sizes"
            )
        }
    }
}
