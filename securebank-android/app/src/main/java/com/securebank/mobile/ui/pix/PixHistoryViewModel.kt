package com.securebank.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.PixEntry
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.PixRepository
import com.securebank.mobile.ui.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PixHistoryState(
    val items: List<PixEntry> = emptyList(),
    val total: Long = 0,
    val nextPage: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val hasMore: Boolean get() = items.size < total
}

class PixHistoryViewModel(private val pix: PixRepository, banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PixHistoryState())
    val state: StateFlow<PixHistoryState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load(reset = true)
        viewModelScope.launch { banking.changes.collect { load(reset = true) } }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.hasMore && !s.loading && !s.loadingMore) load(reset = false)
    }

    private fun load(reset: Boolean) {
        job?.cancel()
        val page = if (reset) 0 else _state.value.nextPage
        _state.update { if (reset && it.items.isEmpty()) it.copy(loading = true, error = null) else if (reset) it.copy(error = null) else it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            val result = attempt { pix.history(page) }
            _state.update { s ->
                result.fold(
                    onSuccess = { p ->
                        s.copy(items = if (reset) p.items else s.items + p.items, total = p.totalElements, nextPage = page + 1, loading = false, loadingMore = false, refreshing = false)
                    },
                    onFailure = { e -> s.copy(loading = false, loadingMore = false, refreshing = false, error = messageFor(e)) },
                )
            }
        }
    }
}
