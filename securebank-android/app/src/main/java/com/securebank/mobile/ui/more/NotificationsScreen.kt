package com.securebank.mobile.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.AppNotification
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value

    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TextButton(onClick = onBack) { Text("‹ Mais") } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Avisos", style = MaterialTheme.typography.headlineSmall)
                    Text("Movimentações e acessos recentes na sua conta.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                s.loading -> item { Skeleton(192.dp) }
                s.error != null && s.items.isEmpty() -> item { ErrorState(s.error, onRetry = viewModel::refresh) }
                s.items.isEmpty() -> item { EmptyState("Nenhum aviso", "Quando algo acontecer na sua conta, você vê aqui.") }
                else -> {
                    item {
                        Panel {
                            Column {
                                s.items.forEachIndexed { i, n ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    NotificationRow(n, marking = s.markingId == n.id, onRead = { viewModel.markRead(n.id) })
                                }
                            }
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            if (s.hasMore) OutlinedButton(onClick = viewModel::loadMore, enabled = !s.loadingMore) { Text(if (s.loadingMore) "Carregando…" else "Ver mais") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, marking: Boolean, onRead: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                n.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (n.read) FontWeight.Normal else FontWeight.SemiBold),
                color = if (n.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(n.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.dateTime(n.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!n.read) TextButton(onClick = onRead, enabled = !marking) { Text(if (marking) "…" else "Marcar como lido") }
    }
}
