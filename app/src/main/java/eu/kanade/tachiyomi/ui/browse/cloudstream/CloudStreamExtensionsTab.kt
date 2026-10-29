package eu.kanade.tachiyomi.ui.browse.cloudstream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamManager
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamPluginItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.csExtensionsTab(): TabContent {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { CloudStreamManager.getInstance(context) }

    val repos by manager.repositoriesFlow.collectAsState()
    val plugins by manager.availablePluginsFlow.collectAsState()
    val isLoading by manager.isLoadingFlow.collectAsState()
    val statusMessage by manager.statusMessageFlow.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var installingInternalName by remember { mutableStateOf<String?>(null) }

    return TabContent(
        titleRes = AYMR.strings.label_cs_extensions,
        badgeNumber = if (plugins.isNotEmpty()) plugins.count { !it.isInstalled } else null,
        actions = persistentListOf(
            AppBar.Action(
                title = "Add Repository",
                icon = Icons.Outlined.Add,
                onClick = { showAddDialog = true },
            ),
            AppBar.Action(
                title = "Refresh",
                icon = Icons.Outlined.Refresh,
                onClick = { manager.refreshRepositories() },
            ),
        ),
        content = { contentPadding, _ ->
            val filteredPlugins = if (searchQuery.isBlank()) {
                plugins
            } else {
                plugins.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.internalName.contains(searchQuery, ignoreCase = true) ||
                        it.tvTypes.any { type -> type.contains(searchQuery, ignoreCase = true) }
                }
            }

            tachiyomi.presentation.core.components.material.PullRefresh(
                refreshing = isLoading,
                enabled = true,
                onRefresh = { manager.refreshRepositories() },
                indicatorPadding = contentPadding,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    AnimatedVisibility(visible = isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search CS extensions...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    // Connected Repositories Section
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Connected Repositories (${repos.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Add Repo")
                            }
                        }
                    }

                    if (repos.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "No Repositories Configured",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add a CloudStream repository URL (repo.json or plugins.json) to browse and install providers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = { showAddDialog = true }) {
                                        Text("+ Add Repository")
                                    }
                                }
                            }
                        }
                    } else {
                        items(repos, key = { it.url }) { repo ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = repo.name,
                                            style = MaterialTheme.typography.bodyMedium,
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
                                            contentDescription = "Delete Repo",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Available Providers Section
                    item {
                        Text(
                            text = "Available CS Providers (${filteredPlugins.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    if (filteredPlugins.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (isLoading) {
                                        statusMessage ?: "Loading providers from repositories..."
                                    } else if (repos.isEmpty()) {
                                        "Add a repository above to view available providers."
                                    } else {
                                        "No providers found in connected repositories."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(filteredPlugins, key = { "${it.repoUrl}_${it.internalName}" }) { plugin ->
                            val isCurrentlyInstalling = installingInternalName == plugin.internalName
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (plugin.isInstalled) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (plugin.isInstalled) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.size(12.dp))

                                        Column {
                                            Text(
                                                text = plugin.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "${plugin.language.uppercase()} • v${plugin.version}" +
                                                    if (plugin.tvTypes.isNotEmpty()) " • ${plugin.tvTypes.joinToString()}" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.size(8.dp))

                                    if (isCurrentlyInstalling) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                    } else if (plugin.isInstalled) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    manager.uninstallPlugin(plugin.internalName)
                                                }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error,
                                            ),
                                        ) {
                                            Text("Uninstall")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    installingInternalName = plugin.internalName
                                                    try {
                                                        manager.installPlugin(plugin)
                                                    } finally {
                                                        installingInternalName = null
                                                    }
                                                }
                                            },
                                        ) {
                                            Text("Install")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
            }

            if (showAddDialog) {
                var urlInput by remember { mutableStateOf("") }
                var nameInput by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add CloudStream Repository") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Enter a CloudStream repository URL (repo.json or plugins.json).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Repository URL") },
                                placeholder = { Text("https://.../repo.json") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Display Name (Optional)") },
                                placeholder = { Text("My CloudStream Repo") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    manager.addRepository(urlInput, nameInput)
                                    showAddDialog = false
                                }
                            },
                        ) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        },
    )
}
