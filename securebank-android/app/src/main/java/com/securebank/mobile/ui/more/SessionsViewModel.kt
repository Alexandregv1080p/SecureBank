package com.securebank.mobile.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.SessionInfo
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.data.SecurityRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsState(
    val sessions: Load<List<SessionInfo>> = Load.Loading,
    val revokingId: String? = null,
    val error: String? = null,
)

class SessionsViewModel(private val security: SecurityRepository) : ViewModel() {
    private val _state = MutableStateFlow(SessionsState())
    val state: StateFlow<SessionsState> = _state.asStateFlow()

    init {
        load()
        // trocar a senha encerra as outras sessões: a lista acompanha
        viewModelScope.launch { security.changes.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            val result = attempt { security.sessions() }
            _state.update { it.copy(sessions = result.toLoad()) }
        }
    }

    fun revoke(id: String) {
        if (_state.value.revokingId != null) return
        _state.update { it.copy(revokingId = id, error = null) }
        viewModelScope.launch {
            val result = attempt { security.revokeSession(id) }
            _state.update { it.copy(revokingId = null, error = result.exceptionOrNull()?.let(::messageFor)) }
        }
    }
}
