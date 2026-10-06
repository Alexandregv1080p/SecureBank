package com.securebank.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.PixKey
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

data class PixKeysState(
    val keys: Load<List<PixKey>> = Load.Loading,
    val accounts: Load<List<Account>> = Load.Loading,
    val accountId: String? = null,
    val working: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

class PixKeysViewModel(private val pix: PixRepository, private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PixKeysState())
    val state: StateFlow<PixKeysState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { loadKeys() } }
    }

    fun load() {
        loadKeys()
        viewModelScope.launch {
            val result = attempt { banking.accounts() }
            _state.update { s ->
                val load = result.toLoad()
                s.copy(accounts = load, accountId = s.accountId ?: (load as? Load.Ready)?.value?.firstOrNull()?.id)
            }
        }
    }

    private fun loadKeys() {
        viewModelScope.launch {
            val result = attempt { pix.keys() }
            _state.update { it.copy(keys = result.toLoad()) }
        }
    }

    fun onAccount(id: String) = _state.update { it.copy(accountId = id, error = null) }

    fun register(type: String) {
        val s = _state.value
        val account = s.accountId ?: return
        if (s.working) return
        act("Chave cadastrada.") { pix.registerKey(account, type) }
    }

    fun delete(id: String) {
        if (_state.value.working) return
        act("Chave removida.") { pix.deleteKey(id) }
    }

    private fun act(done: String, block: suspend () -> Unit) {
        _state.update { it.copy(working = true, error = null, done = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(done = done) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }
}
