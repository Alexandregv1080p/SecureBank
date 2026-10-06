package com.securebank.mobile.ui.pix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixHistoryScreen(viewModel: PixHistoryViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value

    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TextButton(onClick = onBack) { Text("‹ Pix") } }
            item { Text("Histórico do Pix", style = MaterialTheme.typography.headlineSmall) }
            when {
                s.loading -> item { Skeleton(192.dp) }
                s.error != null && s.items.isEmpty() -> item { ErrorState(s.error, onRetry = viewModel::refresh) }
                s.items.isEmpty() -> item { EmptyState("Nenhum Pix ainda", "Os Pix que você enviar e receber aparecem aqui.") }
                else -> {
                    item {
                        Panel {
                            Column {
                                s.items.forEachIndexed { i, e ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    PixEntryRow(e)
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
