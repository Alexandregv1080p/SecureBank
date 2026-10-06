package com.securebank.mobile.ui.pix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.PixScheduleDto
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.PixValidation
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton

@Composable
fun PixSchedulesScreen(viewModel: PixSchedulesViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onBack) { Text("‹ Pix") } }
        item { Text("Pix agendados", style = MaterialTheme.typography.headlineSmall) }
        s.error?.let { item { Banner(it) } }
        when (val items = s.items) {
            Load.Loading -> item { Skeleton(160.dp) }
            is Load.Failed -> item { ErrorState(items.message, onRetry = viewModel::load) }
            is Load.Ready -> if (items.value.isEmpty()) {
                item { EmptyState("Nenhum Pix agendado", "Ao enviar um Pix, ligue \"Agendar para outra data\".") }
            } else {
                item {
                    Panel {
                        Column {
                            items.value.forEachIndexed { i, sch ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                ScheduleRow(sch, onCancel = { viewModel.cancel(sch.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(sch: PixScheduleDto, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Para ${sch.destinationName}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            MoneyText(Money.format(sch.amount.amount), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "${PixValidation.scheduleStatusLabel(sch.status)} · ${PixValidation.displayDate(sch.scheduledFor)}" +
                if (sch.status == "FAILED") " · ${PixValidation.scheduleFailure(sch.failureReason)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (sch.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sch.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (sch.status == "SCHEDULED") TextButton(onClick = onCancel) { Text("Cancelar agendamento") }
    }
}
