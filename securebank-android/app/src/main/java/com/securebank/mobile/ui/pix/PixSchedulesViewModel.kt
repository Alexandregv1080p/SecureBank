package com.securebank.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.PixScheduleDto
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.PixRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PixSchedulesState(val items: Load<List<PixScheduleDto>> = Load.Loading, val error: String? = null)

/** Pix agendados: lista (com o motivo quando não foi realizado) e cancelamento dos ainda pendentes. */
class PixSchedulesViewModel(private val pix: PixRepository, banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PixSchedulesState())
    val state: StateFlow<PixSchedulesState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            val result = attempt { pix.schedules(0, 50).items }.toLoad()
            _state.update { it.copy(items = result) }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            try {
                pix.cancelSchedule(id)
                _state.update { it.copy(error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            }
        }
    }
}
