package com.arikw.itemnotifier.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arikw.itemnotifier.data.ItemRepository
import com.arikw.itemnotifier.data.Prefs
import com.arikw.itemnotifier.data.db.TrackedItem
import com.arikw.itemnotifier.worker.StockCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ItemRepository(application)

    val items: StateFlow<List<TrackedItem>> = repository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _intervalMinutes = MutableStateFlow(Prefs.checkIntervalMinutes(application))
    val intervalMinutes: StateFlow<Long> = _intervalMinutes

    /** Checks all items right now, in-process, so the list updates immediately. */
    fun refreshNow() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repository.checkAllAndNotify()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun deleteItem(item: TrackedItem) {
        viewModelScope.launch { repository.deleteItem(item.id) }
    }

    fun setIntervalMinutes(minutes: Long) {
        val app = getApplication<Application>()
        Prefs.setCheckIntervalMinutes(app, minutes)
        _intervalMinutes.value = minutes
        StockCheckWorker.schedule(app, minutes)
    }
}
