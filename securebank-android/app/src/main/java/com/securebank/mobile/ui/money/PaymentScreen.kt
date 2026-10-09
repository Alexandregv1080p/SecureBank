package com.securebank.mobile.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.PaymentField
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.rememberIdentityConfirmation

@Composable
fun PaymentScreen(container: AppContainer, viewModel: PaymentViewModel, onOpenAccounts: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirm = rememberIdentityConfirmation(container, "Confirme o pagamento")
    val submit = { if (viewModel.validate()) confirm { viewModel.pay() } }

    AuthScaffold("Pagar", "Pague boletos e convênios com o saldo da sua conta.") {
        when (val accounts = s.accounts) {
            Load.Loading -> Skeleton(192.dp)
            is Load.Failed -> ErrorState(accounts.message, onRetry = viewModel::load)
            is Load.Ready -> if (accounts.value.isEmpty()) {
                EmptyState("Você precisa de uma conta para pagar", "Abra uma conta primeiro.") {
                    PrimaryButton("Abrir conta", onClick = onOpenAccounts)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    s.error?.let { Banner(it) }
                    s.ok?.let { Banner(it, isError = false) }
                    AccountPicker("Pagar com", accounts.value, s.accountId, viewModel::onAccount, s.errors[PaymentField.Account])
                    SbTextField("Código de barras ou linha digitável", s.barcode, viewModel::onBarcode, error = s.errors[PaymentField.Barcode], keyboardType = KeyboardType.Number, hint = "44, 47 ou 48 dígitos")
                    SbTextField("Valor (R$)", s.amount, viewModel::onAmount, error = s.errors[PaymentField.Amount], keyboardType = KeyboardType.Decimal, hint = com.securebank.mobile.core.util.Validation.limitHint(s.limit), mask = com.securebank.mobile.core.util.Validation::sanitizeAmount)
                    SbTextField("Descrição (opcional)", s.description, viewModel::onDescription, error = s.errors[PaymentField.Description], imeAction = ImeAction.Done, onDone = { submit() })
                    PrimaryButton("Pagar", onClick = { submit() }, loading = s.loading)

                    Text("Pagamentos recentes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    when (val recent = s.recent) {
                        Load.Loading -> Skeleton(96.dp)
                        is Load.Failed -> ErrorState(recent.message, onRetry = viewModel::load)
                        is Load.Ready -> if (recent.value.isEmpty()) {
                            Text("Nenhum pagamento ainda.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Panel {
                                Column {
                                    recent.value.forEachIndexed { i, p ->
                                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(p.description ?: "Pagamento de boleto", style = MaterialTheme.typography.titleSmall)
                                                Text(Format.dateTime(p.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                MoneyText(Money.format(p.amount.amount), style = MaterialTheme.typography.titleSmall)
                                                Text(if (p.status == "COMPLETED") "Concluído" else p.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
