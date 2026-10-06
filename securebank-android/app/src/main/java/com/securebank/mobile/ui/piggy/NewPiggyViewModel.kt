package com.securebank.mobile.ui.piggy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.PiggyField
import com.securebank.mobile.core.util.PiggyValidation
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.PiggyRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewPiggyState(
    val accounts: Load<List<Account>> = Load.Loading,
    val accountId: String? = null,
    val name: String = "",
    val goal: String = "",
    val errors: Map<PiggyField, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val createdId: String? = null,
)

class NewPiggyViewModel(private val banking: BankingRepository, private val piggies: PiggyRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewPiggyState())
    val state: StateFlow<NewPiggyState> = _state.asStateFlow()

    init {
        loadAccounts()
    }

    fun loadAccounts() {
        viewModelScope.launch {
            val result = attempt { banking.accounts() }
            _state.update { s ->
                val load = result.toLoad()
                val only = (load as? Load.Ready)?.value?.singleOrNull()?.id
                s.copy(accounts = load, accountId = s.accountId ?: only)
            }
        }
    }

    fun onAccount(id: String) = _state.update { it.copy(accountId = id, errors = it.errors - PiggyField.Account, error = null) }
    fun onName(v: String) = _state.update { it.copy(name = v.take(PiggyValidation.NAME_MAX + 5), errors = it.errors - PiggyField.Name, error = null) }
    fun onGoal(v: String) = _state.update { it.copy(goal = v.filter { c -> c.isDigit() || c == ',' || c == '.' }, errors = it.errors - PiggyField.Goal, error = null) }

    fun create() {
        val s = _state.value
        if (s.loading) return
        val errors = PiggyValidation.validate(s.accountId, s.name, s.goal)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val piggy = piggies.create(s.accountId!!, s.name, PiggyValidation.goalOrNull(s.goal))
                _state.update { it.copy(createdId = piggy.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
