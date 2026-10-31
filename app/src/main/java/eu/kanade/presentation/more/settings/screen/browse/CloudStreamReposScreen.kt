package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamManager
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamPluginItem
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding

class CloudStreamReposScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val manager = remember { CloudStreamManager.getInstance(context) }

        val repos by manager.repositoriesFlow.collectAsState()
        val plugins by manager.availablePluginsFlow.collectAsState()

        val lazyListState = rememberLazyListState()
        var showAddDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    navigateUp = navigator::pop,
                    title = "CloudStream Repositories",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { manager.refreshRepositories() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                CategoryFloatingActionButton(
                    lazyListState = lazyListState,
                    onCreate = { showAddDialog = true },
                )
            },
        ) { paddingValues ->
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Connected Repositories (${repos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (repos.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "No CloudStream Repositories",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the + button below to add a CloudStream repository URL (repo.json or plugins.json).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(repos, key = { it.url }) { repo ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = repo.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = repo.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                IconButton(onClick = { manager.removeRepository(repo.url) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Available CloudStream Providers (${plugins.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                if (plugins.isEmpty() && repos.isNotEmpty()) {
                    item {
                        Text(
                            text = "Loading plugins from repositories...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                items(plugins, key = { "${it.repoUrl}_${it.internalName}" }) { plugin ->
                    PluginItemCard(
                        plugin = plugin,
                        onInstall = {
                            scope.launch { manager.installPlugin(plugin) }
                        },
                        onUninstall = {
                            scope.launch { manager.uninstallPlugin(plugin.internalName) }
                        },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }

            if (showAddDialog) {
                AddRepoDialog(
                    onDismiss = { showAddDialog = false },
                    onAdd = { url, name ->
                        manager.addRepository(url = url, name = name)
                        showAddDialog = false
                    },
                )
            }
        }
    }

    @Composable
    private fun PluginItemCard(
        plugin: CloudStreamPluginItem,
        onInstall: () -> Unit,
        onUninstall: () -> Unit,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text(
                                text = plugin.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "v${plugin.version} • ${plugin.tvTypes.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (plugin.isInstalled) {
                    OutlinedButton(
                        onClick = onUninstall,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Uninstall")
                    }
                } else {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Install")
                    }
                }
            }
        }
    }

    @Composable
    private fun AddRepoDialog(
        onDismiss: () -> Unit,
        onAdd: (url: String, name: String) -> Unit,
    ) {
        var url by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add CloudStream Repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Repository Name (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Repository / plugins.json URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Quick Suggestion:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = {
                            name = "ReCloudStream Extensions"
                            url = "https://raw.githubusercontent.com/recloudstream/extensions/builds/plugins.json"
                        },
                    ) {
                        Text("Use Official ReCloudStream Repository")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (url.isNotBlank() || name.isNotBlank()) {
                            onAdd(url.trim(), name.trim())
                        }
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
        )
    }
}
