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
                title = { Text("Track an item") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Terminal X product link") },
                placeholder = { Text("https://www.terminalx.com/…") },
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
                        "Open a product on terminalx.com (or in their app), copy or " +
                            "share its link here, then pick the sizes you want to watch.",
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
                    )
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
        Text(
            snapshot.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp)
        )
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

    Text("Sizes to watch", style = MaterialTheme.typography.titleSmall)
    if (snapshot.sizes.isEmpty()) {
        Text(
            "This product has no size options — tracking isn't supported for it yet.",
            color = MaterialTheme.colorScheme.error
        )
    } else {
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
