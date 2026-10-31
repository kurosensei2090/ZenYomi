package eu.kanade.tachiyomi.ui.browse.cloudstream

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamManager
import eu.kanade.tachiyomi.extension.anime.cloudstream.CloudStreamPluginItem
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.csExtensionsTab(): TabContent {
    val context = LocalContext.current
    val manager = remember { CloudStreamManager.getInstance(context) }
    val badgePlugins by manager.availablePluginsFlow.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    return TabContent(
        titleRes = AYMR.strings.label_cs_extensions,
        badgeNumber = if (badgePlugins.isNotEmpty()) badgePlugins.count { !it.isInstalled } else null,
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
            val scope = rememberCoroutineScope()
            val repos by manager.repositoriesFlow.collectAsState()
            val plugins by manager.availablePluginsFlow.collectAsState()
            val isLoading by manager.isLoadingFlow.collectAsState()
            val statusMessage by manager.statusMessageFlow.collectAsState()

            var searchQuery by remember { mutableStateOf("") }
            var selectedRepoUrl by remember { mutableStateOf<String?>(null) }
            var installingInternalName by remember { mutableStateOf<String?>(null) }

            val filteredPlugins = remember(plugins, searchQuery, selectedRepoUrl) {
                plugins.filter { plugin ->
                    val matchesRepo = selectedRepoUrl == null ||
                        plugin.repoUrl == selectedRepoUrl ||
                        (selectedRepoUrl!!.isNotBlank() && (plugin.repoUrl.contains(selectedRepoUrl!!) || selectedRepoUrl!!.contains(plugin.repoUrl)))
                    val matchesSearch = searchQuery.isBlank() ||
                        plugin.name.contains(searchQuery, ignoreCase = true) ||
                        plugin.internalName.contains(searchQuery, ignoreCase = true) ||
                        plugin.tvTypes.any { type -> type.contains(searchQuery, ignoreCase = true) }
                    matchesRepo && matchesSearch
                }
            }

            tachiyomi.presentation.core.components.material.PullRefresh(
                refreshing = isLoading,
                enabled = true,
                onRefresh = { manager.refreshRepositories() },
                indicatorPadding = contentPadding,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isLoading) {
                        item {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

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
                            val isFiltering = selectedRepoUrl == repo.url
                            val repoPluginCount = plugins.count { it.repoUrl == repo.url || (it.repoUrl.isNotBlank() && (repo.url.contains(it.repoUrl) || it.repoUrl.contains(repo.url))) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedRepoUrl = if (isFiltering) null else repo.url
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFiltering) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                ),
                                border = if (isFiltering) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else null,
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = repo.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (isFiltering) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                ) {
                                                    Text(
                                                        text = "Active Filter",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$repoPluginCount available providers • ${repo.url}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
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

                    // Repository Filter Bar (when multiple repositories connected)
                    if (repos.size > 1) {
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedRepoUrl == null,
                                        onClick = { selectedRepoUrl = null },
                                        label = { Text("All (${plugins.size})") },
                                    )
                                }
                                items(repos, key = { it.url }) { repo ->
                                    val count = plugins.count { it.repoUrl == repo.url || (it.repoUrl.isNotBlank() && (repo.url.contains(it.repoUrl) || it.repoUrl.contains(repo.url))) }
                                    FilterChip(
                                        selected = selectedRepoUrl == repo.url,
                                        onClick = {
                                            selectedRepoUrl = if (selectedRepoUrl == repo.url) null else repo.url
                                        },
                                        label = { Text("${repo.name} ($count)") },
                                    )
                                }
                            }
                        }
                    }

                    // Available Providers Section Header
                    item {
                        val activeRepo = repos.find { it.url == selectedRepoUrl }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (activeRepo != null) {
                                    "${activeRepo.name} (${filteredPlugins.size})"
                                } else {
                                    "Available CS Providers (${filteredPlugins.size})"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (selectedRepoUrl != null) {
                                TextButton(onClick = { selectedRepoUrl = null }) {
                                    Text("Show All")
                                }
                            }
                        }
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
                                    } else if (selectedRepoUrl != null) {
                                        "No matching providers found in this repository."
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
                            val repoName = remember(plugin.repoUrl, repos) {
                                repos.find { it.url == plugin.repoUrl }?.name
                                    ?: repos.find { plugin.repoUrl.isNotBlank() && (it.url.contains(plugin.repoUrl) || plugin.repoUrl.contains(it.url)) }?.name
                            }
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
                                            if (!repoName.isNullOrBlank() && repos.size > 1) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                                    modifier = Modifier.padding(bottom = 3.dp),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Folder,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(11.dp),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = repoName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
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
                                                    context.toast("Uninstalled ${plugin.name}")
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
                                                        val success = manager.installPlugin(plugin)
                                                        if (success) {
                                                            context.toast("Installed ${plugin.name} successfully")
                                                        } else {
                                                            val err = manager.lastErrorFlow.value ?: "Failed to load providers from ${plugin.name}"
                                                            context.toast(err, Toast.LENGTH_LONG)
                                                        }
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

            if (showAddDialog) {
                var nameInput by remember { mutableStateOf("") }
                var urlInput by remember { mutableStateOf("") }

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
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Repository Name (Optional)") },
                                placeholder = { Text("e.g. 3rabi عربي") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Repository URL") },
                                placeholder = { Text("https://.../repo.json") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (urlInput.isNotBlank() || nameInput.isNotBlank()) {
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
