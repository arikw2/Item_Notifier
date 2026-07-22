package com.arikw.itemnotifier.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.arikw.itemnotifier.data.ItemRepository
import com.arikw.itemnotifier.data.Prefs
import com.arikw.itemnotifier.data.db.SearchWatch
import com.arikw.itemnotifier.data.db.StockStatus
import com.arikw.itemnotifier.data.db.TrackedItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onAddItem: () -> Unit,
    viewModel: ItemListViewModel = viewModel(),
) {
    val items by viewModel.items.collectAsState()
    val watches by viewModel.watches.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val intervalMinutes by viewModel.intervalMinutes.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var itemPendingDelete by remember { mutableStateOf<TrackedItem?>(null) }
    var watchPendingDelete by remember { mutableStateOf<SearchWatch?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Notifier") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings")
                    }
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.refreshNow() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Check now")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = "Track a new item")
            }
        }
    ) { padding ->
        if (items.isEmpty() && watches.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp
                )
            ) {
                if (watches.isNotEmpty()) {
                    item(key = "watches-header") {
                        Text(
                            "Search watches",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    items(watches, key = { "w${it.id}" }) { watch ->
                        SearchWatchRow(
                            watch = watch,
                            onDelete = { watchPendingDelete = watch },
                        )
                    }
                    if (items.isNotEmpty()) {
                        item(key = "items-header") {
                            Text(
                                "Tracked items",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                            )
                        }
                    }
                }
                items(items, key = { it.id }) { item ->
                    TrackedItemRow(
                        item = item,
                        onDelete = { itemPendingDelete = item },
                    )
                }
            }
        }
    }

    if (showSettings) {
        IntervalDialog(
            current = intervalMinutes,
            onSelect = {
                viewModel.setIntervalMinutes(it)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }

    watchPendingDelete?.let { watch ->
        AlertDialog(
            onDismissRequest = { watchPendingDelete = null },
            title = { Text("Stop watching?") },
            text = { Text("\"${watch.query}\" on ${watch.siteName}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWatch(watch)
                    watchPendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { watchPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    itemPendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            title = { Text("Stop tracking?") },
            text = {
                Text(
                    if (item.isWholeProduct) item.name
                    else "${item.name} — size ${item.sizeLabel}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item)
                    itemPendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                "Nothing tracked yet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "Tap + and paste a product link from Terminal X,\nFox, Foot Locker, Laline or another shop\nto get notified when your size is back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SearchWatchRow(
    watch: SearchWatch,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(watch.searchUrl)))
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    "\"${watch.query}\"",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(watch.siteName)
                        watch.lastMatchCount?.let { append(" · $it match${if (it == 1) "" else "es"}") }
                        if (watch.lastError) append(" · last check failed")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (watch.lastError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline
                )
                watch.lastCheckedAt?.let { checkedAt ->
                    Text(
                        DateUtils.getRelativeTimeSpanString(checkedAt).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun TrackedItemRow(
    item: TrackedItem,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (item.isWholeProduct) append("Whole product")
                        else append("Size ${item.sizeLabel}")
                        item.colorLabel?.let { append(" · $it") }
                        item.siteName?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    item.lastPrice?.let { price ->
                        Text(
                            ItemRepository.formatPrice(price, item.currency),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item.lastWasPrice?.let { was ->
                        Text(
                            ItemRepository.formatPrice(was, item.currency),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    item.promoText?.let { promo ->
                        Text(
                            promo,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    StatusChip(item.lastStatus)
                    item.lastCheckedAt?.let { checkedAt ->
                        Text(
                            DateUtils.getRelativeTimeSpanString(checkedAt).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: StockStatus) {
    val (label, color) = when (status) {
        StockStatus.IN_STOCK -> "In stock" to Color(0xFF2E7D32)
        StockStatus.OUT_OF_STOCK -> "Out of stock" to Color(0xFFC62828)
        StockStatus.UNKNOWN -> "Not checked yet" to Color(0xFF757575)
        StockStatus.ERROR -> "Check failed" to Color(0xFFE65100)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun IntervalDialog(
    current: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check every…") },
        text = {
            Column {
                Prefs.INTERVAL_CHOICES_MINUTES.forEach { minutes ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                    ) {
                        RadioButton(
                            selected = minutes == current,
                            onClick = { onSelect(minutes) }
                        )
                        Text(
                            if (minutes < 60) "$minutes minutes"
                            else "${minutes / 60} hour${if (minutes > 60) "s" else ""}"
                        )
                    }
                }
                Text(
                    "Android may delay checks to save battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
