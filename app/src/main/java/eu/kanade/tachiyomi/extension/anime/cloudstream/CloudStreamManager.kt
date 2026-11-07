@file:OptIn(com.lagradost.cloudstream3.UnsafeSSL::class)
package eu.kanade.tachiyomi.extension.anime.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.nicehttp.ignoreAllSSLErrors
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@Serializable
private data class PluginManifest(
    val pluginClassName: String = "",
    val name: String = "",
    val version: Int = 1,
    val requiresResources: Boolean = false,
)

@Serializable
data class CloudStreamRepo(
    val name: String,
    val url: String,
) {
    val cleanName: String
        get() = CloudStreamManager.extractCleanRepoName(url, name)
}

@Serializable
data class CloudStreamPluginItem(
    val name: String,
    val internalName: String,
    val url: String,
    val iconUrl: String? = null,
    val version: Int = 1,
    val tvTypes: List<String> = emptyList(),
    val language: String = "en",
    val repoUrl: String = "",
    val isInstalled: Boolean = false,
    val installedVersion: Int? = null,
    val installedRepoUrl: String? = null,
    val hasUpdate: Boolean = false,
)

class CloudStreamManager(
    private val context: Context,
) {
    companion object {
        private const val PREF_REPOS = "zenyomi.cloudstream.repos"
        private const val PREF_PLUGIN_REPOS = "zenyomi.cloudstream.plugin_repos_map"
        private const val PREF_PLUGIN_VERSIONS = "zenyomi.cloudstream.plugin_versions_map"
        private const val PREF_PLUGIN_ICONS = "zenyomi.cloudstream.plugin_icons_map"
        private const val PLUGINS_DIR = "cloudstream_plugins"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; ZenYomi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        private val GENERIC_REPO_NAMES = setOf(
            "main", "master", "builds", "build", "repo", "plugins", "release", "releases",
            "cloudstream repo", "cloudstream repo.", "cloudstream", "cs repo", "cs3"
        )

        private val URL_IGNORE_SEGMENTS = setOf(
            "http:", "https:", "raw.githubusercontent.com", "github.com", "gitlab.com",
            "repo.json", "plugins.json", "builds", "main", "master", "build", "release",
            "releases", "raw", "pages", "gh-pages", "refs", "heads", "tree", "blob"
        )

        fun isUrlLike(s: String): Boolean {
            val trimmed = s.trim()
            if (trimmed.isBlank() || trimmed.contains(" ")) return false
            return trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("cloudstreamrepo://", ignoreCase = true) ||
                trimmed.contains("raw.githubusercontent.com", ignoreCase = true) ||
                trimmed.contains("github.com", ignoreCase = true) ||
                trimmed.endsWith(".json", ignoreCase = true) ||
                trimmed.endsWith("/repo", ignoreCase = true)
        }

        fun extractCleanRepoName(url: String, rawName: String): String {
            val firstLine = rawName.lineSequence().firstOrNull()?.trim().orEmpty()
            val stripped = firstLine.split(Regex("\\s+https?://")).first().trim()

            // If a custom or detected name was provided and is not a generic branch or URL, keep it!
            if (stripped.isNotBlank() && !isUrlLike(stripped) && stripped.lowercase() !in GENERIC_REPO_NAMES) {
                return stripped
            }

            // Otherwise extract the meaningful repository/extension name from the URL
            val clean = url.substringBefore('?').substringBefore('#').trimEnd('/')
            val parts = clean.split('/').filter { it.isNotBlank() }

            val meaningful = parts.map { it.removeSuffix(".json") }.filter { part ->
                val lower = part.lowercase()
                lower !in URL_IGNORE_SEGMENTS &&
                    !lower.endsWith(".com") &&
                    !lower.endsWith(".io") &&
                    !lower.endsWith(".org") &&
                    !lower.endsWith(".net")
            }

            if (meaningful.isNotEmpty()) {
                val candidate = meaningful.last()
                if (candidate.isNotBlank() && candidate.lowercase() !in GENERIC_REPO_NAMES) {
                    return candidate
                }
            }

            return stripped.ifBlank { "CloudStream Repo" }
        }

        @Volatile
        private var instance: CloudStreamManager? = null

        fun getInstance(context: Context): CloudStreamManager {
            return instance ?: synchronized(this) {
                instance ?: CloudStreamManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("cloudstream_manager", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        try {
            Injekt.get<NetworkHelper>().client
        } catch (_: Exception) {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    private val pluginsFolder: File by lazy {
        File(context.filesDir, PLUGINS_DIR).apply { mkdirs() }
    }

    private val _repositoriesFlow = MutableStateFlow<List<CloudStreamRepo>>(emptyList())
    val repositoriesFlow: StateFlow<List<CloudStreamRepo>> = _repositoriesFlow.asStateFlow()

    private val _availablePluginsFlow = MutableStateFlow<List<CloudStreamPluginItem>>(emptyList())
    val availablePluginsFlow: StateFlow<List<CloudStreamPluginItem>> = _availablePluginsFlow.asStateFlow()

    private val _installedSourcesFlow = MutableStateFlow<List<CloudStreamAnimeSource>>(emptyList())
    val installedSourcesFlow: StateFlow<List<CloudStreamAnimeSource>> = _installedSourcesFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(false)
    val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow.asStateFlow()

    private val _statusMessageFlow = MutableStateFlow<String?>(null)
    val statusMessageFlow: StateFlow<String?> = _statusMessageFlow.asStateFlow()

    private val _lastErrorFlow = MutableStateFlow<String?>(null)
    val lastErrorFlow: StateFlow<String?> = _lastErrorFlow.asStateFlow()

    init {
        com.lagradost.cloudstream3.AcraApplication.context = context.applicationContext
        try {
            com.lagradost.cloudstream3.app.baseClient = client
            com.lagradost.cloudstream3.insecureApp.baseClient = client.newBuilder().ignoreAllSSLErrors().build()
        } catch (t: Throwable) {
            logcat(LogPriority.WARN, t) { "Failed to bind baseClient to CloudStream app" }
        }
        com.lagradost.cloudstream3.extractors.registerCommonExtractors()
        loadSavedRepositories()
        scope.launch {
            loadInstalledPlugins()
            refreshRepositories()
        }
    }

    private fun loadSavedRepositories() {
        val raw = prefs.getString(PREF_REPOS, null)
        val repos = if (!raw.isNullOrBlank()) {
            try {
                json.decodeFromString<List<CloudStreamRepo>>(raw)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Auto-repair any swapped, messy, or generic branch names saved previously (e.g. main, builds, master, URLs)
        var hasRepairs = false
        val repaired = repos.map { repo ->
            var newName = repo.name.lines().firstOrNull()?.trim().orEmpty()
            if (newName.isBlank() || isUrlLike(newName) || newName.lowercase() in GENERIC_REPO_NAMES) {
                newName = extractCleanRepoName(repo.url, "")
            }
            val stripped = newName.split(Regex("\\s+https?://")).first().trim()
            if (stripped.isNotBlank() && stripped.lowercase() !in GENERIC_REPO_NAMES) {
                newName = stripped
            }

            var newUrl = repo.url
            if (!isUrlLike(newUrl) && isUrlLike(repo.name)) {
                newUrl = normalizeRepoUrl(repo.name)
            }

            if (newName != repo.name || newUrl != repo.url) {
                hasRepairs = true
                CloudStreamRepo(name = newName, url = newUrl)
            } else {
                repo
            }
        }

        _repositoriesFlow.value = repaired
        if (hasRepairs) {
            saveRepositories(repaired)
        }
    }

    private fun saveRepositories(repos: List<CloudStreamRepo>) {
        prefs.edit().putString(PREF_REPOS, json.encodeToString(repos)).apply()
        _repositoriesFlow.value = repos
    }

    private fun getPluginRepoMap(): MutableMap<String, String> {
        val raw = prefs.getString(PREF_PLUGIN_REPOS, null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, String>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun savePluginRepoMap(map: Map<String, String>) {
        prefs.edit().putString(PREF_PLUGIN_REPOS, json.encodeToString(map)).apply()
    }

    private fun associatePluginWithRepo(pluginName: String, repoUrl: String) {
        if (repoUrl.isBlank()) return
        val map = getPluginRepoMap()
        map[pluginName] = repoUrl
        savePluginRepoMap(map)
    }

    private fun removePluginRepoAssociation(pluginName: String) {
        val map = getPluginRepoMap()
        if (map.remove(pluginName) != null) {
            savePluginRepoMap(map)
        }
    }

    private fun getPluginVersionMap(): MutableMap<String, Int> {
        val raw = prefs.getString(PREF_PLUGIN_VERSIONS, null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, Int>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun savePluginVersionMap(map: Map<String, Int>) {
        prefs.edit().putString(PREF_PLUGIN_VERSIONS, json.encodeToString(map)).apply()
    }

    private fun recordPluginVersion(pluginName: String, version: Int) {
        if (pluginName.isBlank() || version <= 0) return
        val map = getPluginVersionMap()
        map[pluginName] = version
        savePluginVersionMap(map)
    }

    private fun removePluginVersion(pluginName: String) {
        val map = getPluginVersionMap()
        if (map.remove(pluginName) != null) {
            savePluginVersionMap(map)
        }
    }

    private fun getPluginIconMap(): MutableMap<String, String> {
        val raw = prefs.getString(PREF_PLUGIN_ICONS, null) ?: return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, String>>(raw).toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun savePluginIconMap(map: Map<String, String>) {
        prefs.edit().putString(PREF_PLUGIN_ICONS, json.encodeToString(map)).apply()
    }

    fun getPluginIconUrl(pluginName: String): String? {
        if (pluginName.isBlank()) return null
        val clean = pluginName.removeSuffix(".cs3").removeSuffix(" [CS]").trim()
        val map = getPluginIconMap()
        return map[pluginName] ?: map[clean] ?: map["$clean.cs3"]
    }

    fun recordPluginIcon(pluginName: String, rawIconUrl: String?) {
        if (pluginName.isBlank() || rawIconUrl.isNullOrBlank()) return
        val iconUrl = rawIconUrl.replace("%size%", "128")
        val map = getPluginIconMap()
        map[pluginName] = iconUrl
        val clean = pluginName.removeSuffix(".cs3").removeSuffix(" [CS]").trim()
        map[clean] = iconUrl
        map["$clean.cs3"] = iconUrl
        savePluginIconMap(map)
    }

    private fun getPluginsForRepo(repoUrl: String): List<String> {
        val map = getPluginRepoMap()
        return map.filter { it.value.equals(repoUrl, ignoreCase = true) }.map { it.key }
    }

    fun getRepoForPlugin(pluginName: String): CloudStreamRepo? {
        val map = getPluginRepoMap()
        val repoUrl = map[pluginName]
            ?: _availablePluginsFlow.value.find { it.internalName.equals(pluginName, ignoreCase = true) || it.name.equals(pluginName, ignoreCase = true) }?.repoUrl
            ?: map.entries.find { pluginName.contains(it.key, ignoreCase = true) || it.key.contains(pluginName, ignoreCase = true) }?.value
        if (repoUrl != null) {
            val normTarget = normalizeRepoUrl(repoUrl).trimEnd('/')
            return _repositoriesFlow.value.find { normalizeRepoUrl(it.url).trimEnd('/') == normTarget }
                ?: _repositoriesFlow.value.find { it.url == repoUrl }
                ?: _repositoriesFlow.value.find { it.url.contains(repoUrl) || repoUrl.contains(it.url) }
        }
        return null
    }

    fun addRepository(url: String, name: String = "") {
        var rawUrl = url.trim()
        var rawName = name.lines().firstOrNull()?.trim().orEmpty()

        // Auto-swap if user put the URL in the Name field and the Name in the URL field
        if (!isUrlLike(rawUrl) && isUrlLike(rawName)) {
            val temp = rawUrl
            rawUrl = rawName
            rawName = if (temp.startsWith("https://") || temp.startsWith("http://")) {
                temp.substringAfter("://")
            } else {
                temp
            }
        }

        if (isUrlLike(rawName)) {
            rawName = ""
        }

        val normalized = normalizeRepoUrl(rawUrl)
        if (normalized.isBlank()) return
        val current = _repositoriesFlow.value.toMutableList()
        if (current.none { it.url == normalized }) {
            val displayName = extractCleanRepoName(normalized, rawName)
            current.add(CloudStreamRepo(name = displayName, url = normalized))
            saveRepositories(current)
            refreshRepositories()
        }
    }

    fun renameRepository(url: String, newName: String) {
        val trimmed = newName.lines().firstOrNull()?.trim().orEmpty()
        if (trimmed.isBlank()) return
        val current = _repositoriesFlow.value.toMutableList()
        val index = current.indexOfFirst {
            it.url.equals(url, ignoreCase = true) || normalizeRepoUrl(it.url) == normalizeRepoUrl(url)
        }
        if (index != -1) {
            current[index] = current[index].copy(name = trimmed)
            saveRepositories(current)
        }
    }

    fun removeRepository(url: String) {
        scope.launch(Dispatchers.IO) {
            val normalizedTarget = normalizeRepoUrl(url).trimEnd('/')
            val current = _repositoriesFlow.value.toMutableList()
            current.removeAll {
                val norm = normalizeRepoUrl(it.url).trimEnd('/')
                norm == normalizedTarget || it.url.equals(url, ignoreCase = true)
            }
            saveRepositories(current)

            if (current.isEmpty()) {
                // All repos removed: wipe plugins folder and caches completely
                pluginsFolder.listFiles()?.forEach { file ->
                    try {
                        file.setWritable(true)
                        file.delete()
                    } catch (_: Throwable) {}
                }
                context.codeCacheDir.listFiles { f -> f.extension == "jar" || f.extension == "dex" }?.forEach { f ->
                    try {
                        f.setWritable(true)
                        f.delete()
                    } catch (_: Throwable) {}
                }
                com.lagradost.cloudstream3.APIHolder.allProviders.clear()
                com.lagradost.cloudstream3.APIHolder.apis.clear()
                savePluginRepoMap(emptyMap())
                _installedSourcesFlow.value = emptyList()
                _availablePluginsFlow.value = emptyList()
                _lastErrorFlow.value = null
                refreshRepositories()
                return@launch
            }

            // Some repos remain. Find which plugins belong to remaining repos.
            val remainingRepoUrls = current.map { normalizeRepoUrl(it.url).trimEnd('/') }.toSet()
            val pluginsToKeep = _availablePluginsFlow.value
                .filter { normalizeRepoUrl(it.repoUrl).trimEnd('/') in remainingRepoUrls }
                .flatMap { listOf(it.internalName, it.name, it.internalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")) }
                .toSet()

            // Delete any installed plugin file that does NOT belong to any remaining repo
            pluginsFolder.listFiles { f -> f.extension.equals("cs3", ignoreCase = true) }?.forEach { file ->
                val baseName = file.nameWithoutExtension
                val isKeep = pluginsToKeep.any { it.equals(baseName, ignoreCase = true) }
                if (!isKeep) {
                    try {
                        file.setWritable(true)
                        file.delete()
                    } catch (_: Throwable) {}
                    val jarFile = File(context.codeCacheDir, "$baseName.jar")
                    if (jarFile.exists()) {
                        jarFile.setWritable(true)
                        jarFile.delete()
                    }
                    removePluginRepoAssociation(baseName)
                }
            }

            loadInstalledPlugins()
            refreshRepositories()
        }
    }

    private data class RepoResolveResult(
        val pluginUrls: List<String>,
        val detectedName: String? = null,
    )

    fun refreshRepositories() {
        scope.launch {
            _isLoadingFlow.value = true
            _statusMessageFlow.value = "Fetching repository plugins..."
            try {
                val pluginsList = mutableListOf<CloudStreamPluginItem>()
                val repos = _repositoriesFlow.value
                val updatedRepos = repos.toMutableList()
                var reposChanged = false

                for ((index, repo) in repos.withIndex()) {
                    try {
                        val result = resolvePluginUrls(repo.url)
                        val cleanDetected = result.detectedName?.lines()?.firstOrNull()?.trim().orEmpty()
                        val isGenericName = repo.name.isBlank() || isUrlLike(repo.name) || repo.name.lowercase() in GENERIC_REPO_NAMES
                        if (cleanDetected.isNotBlank() && isGenericName) {
                            updatedRepos[index] = repo.copy(name = cleanDetected)
                            reposChanged = true
                        } else if (isGenericName) {
                            val cleanExtracted = extractCleanRepoName(repo.url, "")
                            if (cleanExtracted != repo.name && cleanExtracted.lowercase() !in GENERIC_REPO_NAMES) {
                                updatedRepos[index] = repo.copy(name = cleanExtracted)
                                reposChanged = true
                            }
                        }
                        for (pluginListUrl in result.pluginUrls) {
                            val items = fetchPluginList(pluginListUrl, repo.url)
                            pluginsList.addAll(items)
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to fetch CloudStream repo: ${repo.url}" }
                    }
                }

                if (reposChanged) {
                    saveRepositories(updatedRepos)
                }

                val installedNames = pluginsFolder.listFiles { file -> file.extension.equals("cs3", ignoreCase = true) }
                    ?.map { it.nameWithoutExtension }
                    ?.toSet() ?: emptySet()

                val repoMap = getPluginRepoMap()
                val versionMap = getPluginVersionMap()
                val installedSources = _installedSourcesFlow.value

                val marked = pluginsList.distinctBy { "${it.repoUrl}_${it.internalName}" }.map { item ->
                    val safe = item.internalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val isFilePresent = installedNames.contains(item.internalName) ||
                        installedNames.contains(safe) ||
                        installedNames.contains(item.name) ||
                        installedNames.contains(item.name.replace(Regex("[^a-zA-Z0-9._-]"), "_"))

                    if (!isFilePresent) {
                        item.copy(
                            isInstalled = false,
                            installedVersion = null,
                            installedRepoUrl = null,
                            hasUpdate = false,
                        )
                    } else {
                        val installedRepo = repoMap[safe] ?: repoMap[item.internalName] ?: repoMap[item.name]
                        val installedVer = versionMap[safe] ?: versionMap[item.internalName] ?: versionMap[item.name]
                            ?: installedSources.find {
                                it.pluginName.equals(item.name, ignoreCase = true) ||
                                    it.pluginName.equals(item.internalName, ignoreCase = true) ||
                                    it.pluginName.equals(safe, ignoreCase = true)
                            }?.pluginVersion

                        val matchesRepo = installedRepo != null && (
                            installedRepo.equals(item.repoUrl, ignoreCase = true) ||
                                (item.repoUrl.isNotBlank() && (installedRepo.contains(item.repoUrl) || item.repoUrl.contains(installedRepo)))
                        )

                        val hasUpdate = installedVer != null && item.version > installedVer
                        val isInstalledForThis = (matchesRepo || installedRepo == null) && !hasUpdate

                        item.copy(
                            isInstalled = isInstalledForThis,
                            installedVersion = installedVer,
                            installedRepoUrl = installedRepo,
                            hasUpdate = hasUpdate,
                        )
                    }
                }
                _availablePluginsFlow.value = marked
            } finally {
                kotlinx.coroutines.delay(500)
                _statusMessageFlow.value = null
                _isLoadingFlow.value = false
            }
        }
    }

    fun normalizeRepoUrl(raw: String): String {
        var url = raw.trim()
        if (url.startsWith("cloudstreamrepo://", ignoreCase = true)) {
            url = url.substring("cloudstreamrepo://".length)
        }
        if (url.startsWith("https://cs.repo/?", ignoreCase = true) || url.startsWith("http://cs.repo/?", ignoreCase = true)) {
            url = url.substringAfter("?")
        } else if (url.startsWith("https://cs.repo/", ignoreCase = true) || url.startsWith("http://cs.repo/", ignoreCase = true)) {
            url = url.substringAfter("cs.repo/")
        }
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        // GitHub tree/blob URL normalization to raw content
        val baseGithubRegex = "^https?://github\\.com/([^/]+)/([^/]+)/?$".toRegex(RegexOption.IGNORE_CASE)
        val baseGithubMatch = baseGithubRegex.matchEntire(url)
        if (baseGithubMatch != null) {
            val (owner, repo) = baseGithubMatch.destructured
            url = "https://raw.githubusercontent.com/$owner/$repo/master"
        }
        val treeRegex = "^https?://github\\.com/([^/]+)/([^/]+)/tree/([^/]+)/?(.*)$".toRegex(RegexOption.IGNORE_CASE)
        val treeMatch = treeRegex.matchEntire(url)
        if (treeMatch != null) {
            val (owner, repo, branch, subpath) = treeMatch.destructured
            url = "https://raw.githubusercontent.com/$owner/$repo/$branch/$subpath".trimEnd('/')
        }
        val blobRegex = "^https?://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/?(.*)$".toRegex(RegexOption.IGNORE_CASE)
        val blobMatch = blobRegex.matchEntire(url)
        if (blobMatch != null) {
            val (owner, repo, branch, subpath) = blobMatch.destructured
            url = "https://raw.githubusercontent.com/$owner/$repo/$branch/$subpath"
        }
        return url
    }

    private fun resolveUrl(baseUrl: String, target: String): String {
        val trimmed = target.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return trimmed
        return httpUrl.resolve(trimmed)?.toString() ?: trimmed
    }

    private fun resolvePluginUrls(repoUrl: String): RepoResolveResult {
        val candidates = if (repoUrl.endsWith(".json", ignoreCase = true)) {
            listOf(repoUrl)
        } else {
            val clean = repoUrl.trimEnd('/')
            listOf(
                "$clean.json",
                "$clean/repo.json",
                "$clean/plugins.json",
                clean,
            )
        }

        for (candidate in candidates) {
            try {
                val request = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", USER_AGENT)
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue
                val body = response.body.string()
                if (body.isBlank()) continue

                try {
                    val parsed = json.parseToJsonElement(body)
                    if (parsed is JsonObject) {
                        val detectedName = parsed["name"]?.jsonPrimitive?.contentOrNull
                        val lists = parsed["pluginLists"]?.jsonArray?.mapNotNull {
                            val path = it.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                            resolveUrl(candidate, path)
                        }
                        if (!lists.isNullOrEmpty()) {
                            return RepoResolveResult(lists, detectedName)
                        }
                        if (parsed.containsKey("plugins")) {
                            return RepoResolveResult(listOf(candidate), detectedName)
                        }
                    } else if (parsed is JsonArray) {
                        // It was directly an array of plugins
                        return RepoResolveResult(listOf(candidate))
                    }
                } catch (_: Exception) {
                }

                return RepoResolveResult(listOf(candidate))
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Candidate $candidate failed" }
            }
        }
        return RepoResolveResult(emptyList())
    }

    private fun fetchPluginList(url: String, repoUrl: String): List<CloudStreamPluginItem> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body.string()
            if (body.isBlank()) return emptyList()

            val parsed = try {
                json.parseToJsonElement(body)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to parse JSON from $url" }
                return emptyList()
            }

            val jsonObjects: List<JsonObject> = when (parsed) {
                is JsonArray -> parsed.mapNotNull { it as? JsonObject }
                is JsonObject -> {
                    if (parsed.containsKey("plugins") && parsed["plugins"] is JsonArray) {
                        (parsed["plugins"] as JsonArray).mapNotNull { it as? JsonObject }
                    } else {
                        parsed.values.mapNotNull { it as? JsonObject }
                    }
                }
                else -> emptyList()
            }

            val results = mutableListOf<CloudStreamPluginItem>()
            for (obj in jsonObjects) {
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val internalName = obj["internalName"]?.jsonPrimitive?.contentOrNull ?: name
                val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull
                    ?: obj["cs3Url"]?.jsonPrimitive?.contentOrNull
                    ?: obj["downloadUrl"]?.jsonPrimitive?.contentOrNull
                    ?: obj["jarUrl"]?.jsonPrimitive?.contentOrNull
                    ?: continue
                val pluginUrl = resolveUrl(url, rawUrl)
                val rawIcon = obj["iconUrl"]?.jsonPrimitive?.contentOrNull?.let { resolveUrl(url, it) }
                val iconUrl = rawIcon?.replace("%size%", "128")
                val version = obj["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: "en"
                val tvTypes = obj["tvTypes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                if (!iconUrl.isNullOrBlank()) {
                    recordPluginIcon(internalName, iconUrl)
                    recordPluginIcon(name, iconUrl)
                }

                results.add(
                    CloudStreamPluginItem(
                        name = name,
                        internalName = internalName,
                        url = pluginUrl,
                        iconUrl = iconUrl,
                        version = version,
                        tvTypes = tvTypes,
                        language = lang,
                        repoUrl = repoUrl,
                    ),
                )
            }
            results
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error fetching plugin list from $url" }
            emptyList()
        }
    }

    suspend fun installPlugin(plugin: CloudStreamPluginItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeName = plugin.internalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(pluginsFolder, "$safeName.cs3")
            if (targetFile.exists()) {
                targetFile.setWritable(true)
                targetFile.delete()
            }
            val downloadUrl = plugin.url.trim().replace(" ", "%20")
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "HTTP ${response.code} downloading plugin from ${plugin.url}"
                logcat(LogPriority.ERROR) { err }
                _lastErrorFlow.value = err
                return@withContext false
            }
            val bytes = response.body.bytes()
            if (bytes.isEmpty()) {
                _lastErrorFlow.value = "Downloaded plugin ${plugin.name} was empty"
                return@withContext false
            }
            FileOutputStream(targetFile).use { it.write(bytes) }
            try {
                targetFile.setReadOnly()
            } catch (_: Throwable) {}

            val newSources = loadSinglePluginFile(targetFile)
            if (newSources.isEmpty()) {
                targetFile.setWritable(true)
                targetFile.delete()
                val jar = File(context.codeCacheDir, "$safeName.jar")
                if (jar.exists()) {
                    jar.setWritable(true)
                    jar.delete()
                }
                val errMsg = _lastErrorFlow.value ?: "No providers found in ${plugin.name}"
                logcat(LogPriority.WARN) { errMsg }
                _lastErrorFlow.value = errMsg
                refreshRepositories()
                return@withContext false
            }

            associatePluginWithRepo(safeName, plugin.repoUrl)
            if (safeName != plugin.internalName) {
                associatePluginWithRepo(plugin.internalName, plugin.repoUrl)
            }
            associatePluginWithRepo(plugin.name, plugin.repoUrl)

            recordPluginVersion(safeName, plugin.version)
            recordPluginVersion(plugin.internalName, plugin.version)
            recordPluginVersion(plugin.name, plugin.version)

            if (!plugin.iconUrl.isNullOrBlank()) {
                recordPluginIcon(safeName, plugin.iconUrl)
                recordPluginIcon(plugin.internalName, plugin.iconUrl)
                recordPluginIcon(plugin.name, plugin.iconUrl)
            }

            val current = _installedSourcesFlow.value.toMutableList()
            current.removeAll {
                it.pluginName == plugin.name ||
                    it.pluginName == plugin.internalName ||
                    it.pluginName == safeName
            }
            current.addAll(newSources)
            _installedSourcesFlow.value = current.distinctBy { it.id }

            _availablePluginsFlow.value = _availablePluginsFlow.value.map { item ->
                if (item.internalName == plugin.internalName || item.internalName == safeName || item.name == plugin.name) {
                    val matchesRepo = item.repoUrl.equals(plugin.repoUrl, ignoreCase = true)
                    item.copy(
                        isInstalled = matchesRepo,
                        installedVersion = plugin.version,
                        installedRepoUrl = plugin.repoUrl,
                        hasUpdate = item.version > plugin.version,
                    )
                } else {
                    item
                }
            }

            refreshRepositories()
            return@withContext true
        } catch (e: Exception) {
            val errMsg = "Failed to install ${plugin.name}: ${e.message}"
            logcat(LogPriority.ERROR, e) { errMsg }
            _lastErrorFlow.value = errMsg
            return@withContext false
        }
    }

    suspend fun uninstallPlugin(internalName: String) = withContext(Dispatchers.IO) {
        val safeName = internalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val targetFile = File(pluginsFolder, "$safeName.cs3")
        if (targetFile.exists()) {
            targetFile.setWritable(true)
            targetFile.delete()
        }
        val rawFile = File(pluginsFolder, "$internalName.cs3")
        if (rawFile.exists()) {
            rawFile.setWritable(true)
            rawFile.delete()
        }
        val jarFile = File(context.codeCacheDir, "$safeName.jar")
        if (jarFile.exists()) {
            jarFile.setWritable(true)
            jarFile.delete()
        }

        removePluginRepoAssociation(internalName)
        removePluginRepoAssociation(safeName)
        removePluginVersion(internalName)
        removePluginVersion(safeName)

        val currentSources = _installedSourcesFlow.value.toMutableList()
        currentSources.removeAll {
            it.pluginName.equals(internalName, ignoreCase = true) ||
                it.pluginName.equals(safeName, ignoreCase = true) ||
                it.api.name.equals(internalName, ignoreCase = true)
        }
        _installedSourcesFlow.value = currentSources

        _availablePluginsFlow.value = _availablePluginsFlow.value.map { item ->
            if (item.internalName.equals(internalName, ignoreCase = true) ||
                item.internalName.equals(safeName, ignoreCase = true) ||
                item.name.equals(internalName, ignoreCase = true)
            ) {
                item.copy(
                    isInstalled = false,
                    installedVersion = null,
                    installedRepoUrl = null,
                    hasUpdate = false,
                )
            } else {
                item
            }
        }

        refreshRepositories()
    }

    fun reloadInstalledPlugins() {
        scope.launch {
            _isLoadingFlow.value = true
            try {
                loadInstalledPlugins()
            } finally {
                kotlinx.coroutines.delay(500)
                _isLoadingFlow.value = false
            }
        }
    }

    fun loadInstalledPlugins() {
        val cs3Files = pluginsFolder.listFiles { file -> file.extension.equals("cs3", ignoreCase = true) }
        if (cs3Files.isNullOrEmpty()) {
            _installedSourcesFlow.value = emptyList()
            return
        }
        com.lagradost.cloudstream3.APIHolder.allProviders.clear()
        com.lagradost.cloudstream3.APIHolder.apis.clear()
        com.lagradost.cloudstream3.APIHolder.apiMap.clear()
        val allSources = mutableListOf<CloudStreamAnimeSource>()
        for (file in cs3Files) {
            val sources = loadSinglePluginFile(file)
            allSources.addAll(sources)
        }
        _installedSourcesFlow.value = allSources.distinctBy { it.id }
        if (allSources.isNotEmpty()) {
            _lastErrorFlow.value = null
        }
    }

    private fun loadSinglePluginFile(file: File): List<CloudStreamAnimeSource> {
        val sources = mutableListOf<CloudStreamAnimeSource>()
        try {
            try {
                file.setReadOnly()
            } catch (_: Throwable) {}

            val parentLoader = context.classLoader ?: javaClass.classLoader
            val loader = try {
                dalvik.system.PathClassLoader(file.absolutePath, parentLoader)
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) { "Direct PathClassLoader failed for ${file.name}, trying jar copy" }
                null
            } ?: run {
                try {
                    val safeName = file.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val jarFile = File(context.codeCacheDir, "$safeName.jar")
                    if (!jarFile.exists() || jarFile.length() != file.length()) {
                        jarFile.setWritable(true)
                        file.inputStream().use { input ->
                            jarFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        try {
                            jarFile.setReadOnly()
                        } catch (_: Throwable) {}
                    }
                    dalvik.system.PathClassLoader(jarFile.absolutePath, parentLoader)
                } catch (_: Throwable) {
                    null
                }
            } ?: run {
                val err = "Failed to create ClassLoader for ${file.name}"
                _lastErrorFlow.value = err
                return emptyList()
            }

            var manifestText: String? = null
            try {
                loader.getResourceAsStream("manifest.json")?.bufferedReader()?.use {
                    manifestText = it.readText()
                }
            } catch (_: Throwable) {}

            if (manifestText.isNullOrBlank()) {
                try {
                    ZipFile(file).use { zip ->
                        val entry = zip.entries().asSequence().find {
                            it.name.equals("manifest.json", ignoreCase = true) || it.name.endsWith("/manifest.json", ignoreCase = true)
                        }
                        if (entry != null) {
                            manifestText = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        }
                        val iconEntry = zip.entries().asSequence().find {
                            it.name.equals("icon.png", ignoreCase = true) ||
                                it.name.equals("icon.webp", ignoreCase = true) ||
                                it.name.endsWith("/icon.png", ignoreCase = true) ||
                                it.name.endsWith("/icon.webp", ignoreCase = true)
                        }
                        if (iconEntry != null) {
                            try {
                                val safeName = file.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                val iconDir = File(context.filesDir, "cs_icons").apply { mkdirs() }
                                val iconFile = File(iconDir, "$safeName.png")
                                if (!iconFile.exists() || iconFile.length() != iconEntry.size) {
                                    zip.getInputStream(iconEntry).use { input ->
                                        iconFile.outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                                val localPath = iconFile.absolutePath
                                recordPluginIcon(file.nameWithoutExtension, localPath)
                                recordPluginIcon(file.name, localPath)
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            }

            if (manifestText.isNullOrBlank()) {
                val err = "manifest.json missing in ${file.name}"
                _lastErrorFlow.value = err
                return emptyList()
            }

            val manifest = runCatching {
                json.decodeFromString<PluginManifest>(manifestText.orEmpty())
            }.getOrNull()
            val pluginClassName = manifest?.pluginClassName?.trim().orEmpty().ifBlank {
                Regex(""""pluginClassName"\s*:\s*"([^"]+)"""").find(manifestText.orEmpty())?.groupValues?.getOrNull(1)?.trim().orEmpty()
            }
            val pluginVersion = manifest?.version ?: (
                Regex(""""version"\s*:\s*(\d+)""").find(manifestText.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            )
            val pluginName = manifest?.name?.takeIf { it.isNotBlank() } ?: (
                Regex(""""name"\s*:\s*"([^"]+)"""").find(manifestText.orEmpty())?.groupValues?.getOrNull(1)?.trim().orEmpty()
            ).ifBlank { file.nameWithoutExtension }
            val requiresResources = manifest?.requiresResources ?: (
                Regex(""""requiresResources"\s*:\s*(true|false)""").find(manifestText.orEmpty())?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: false
            )

            recordPluginVersion(file.nameWithoutExtension, pluginVersion)
            recordPluginVersion(pluginName, pluginVersion)
            if (pluginClassName.isNotBlank()) {
                recordPluginVersion(pluginClassName, pluginVersion)
            }

            val manifestIcon = Regex(""""iconUrl"\s*:\s*"([^"]+)"""").find(manifestText.orEmpty())?.groupValues?.getOrNull(1)?.trim()
            if (!manifestIcon.isNullOrBlank()) {
                recordPluginIcon(file.nameWithoutExtension, manifestIcon)
                recordPluginIcon(pluginName, manifestIcon)
            }

            if (pluginClassName.isBlank()) {
                _lastErrorFlow.value = "pluginClassName empty in manifest of ${file.name}"
                return emptyList()
            }

            val clazz = try {
                loader.loadClass(pluginClassName)
            } catch (t: Throwable) {
                val errMsg = "Failed to load class $pluginClassName: ${t::class.java.simpleName}: ${t.message}"
                logcat(LogPriority.ERROR, t) { errMsg }
                _lastErrorFlow.value = errMsg
                return emptyList()
            }

            val constructor = try {
                clazz.getDeclaredConstructor()
            } catch (_: NoSuchMethodException) {
                try {
                    clazz.getDeclaredConstructor(Context::class.java)
                } catch (_: NoSuchMethodException) {
                    clazz.constructors.firstOrNull() ?: return emptyList()
                }
            } catch (t: Throwable) {
                logcat(LogPriority.ERROR, t) { "Error inspecting constructor of $pluginClassName: ${t.message}" }
                val cause = t.cause ?: t
                _lastErrorFlow.value = "[${file.nameWithoutExtension}] ${cause::class.java.simpleName}: ${cause.message}"
                return emptyList()
            }
            constructor.isAccessible = true
            val instance = try {
                if (constructor.parameterTypes.isEmpty()) {
                    constructor.newInstance()
                } else if (constructor.parameterTypes.size == 1 && constructor.parameterTypes[0] == Context::class.java) {
                    constructor.newInstance(context)
                } else {
                    constructor.newInstance()
                }
            } catch (t: Throwable) {
                val cause = t.cause ?: t
                val fullMsg = cause.message?.takeIf { it.isNotBlank() } ?: cause.toString()
                val methodRegex = Regex("""No (?:virtual|static|direct) method ([^(\s]+)(\([^)]*\)[^(\s]+) in class (L[^;]+;)""")
                val matched = methodRegex.find(fullMsg)
                val cleanDetail = if (matched != null) {
                    val mName = matched.groupValues[1]
                    val mSig = matched.groupValues[2]
                    val mCls = matched.groupValues[3].substringAfterLast('/').removeSuffix(";")
                    "Missing: $mCls.$mName$mSig"
                } else {
                    "${cause::class.java.simpleName}: ${cause.message?.take(50)}"
                }
                val errMsg = "[${file.nameWithoutExtension}] $cleanDetail"
                logcat(LogPriority.ERROR, cause) { errMsg }
                _lastErrorFlow.value = errMsg
                return emptyList()
            }

            if (requiresResources) {
                try {
                    val assets = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                    val addAssetPath = android.content.res.AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                    addAssetPath.invoke(assets, file.absolutePath)
                    val res = android.content.res.Resources(assets, context.resources.displayMetrics, context.resources.configuration)
                    if (instance is com.lagradost.cloudstream3.plugins.Plugin) {
                        instance.resources = res
                    } else {
                        try {
                            val f = clazz.getDeclaredField("resources")
                            f.isAccessible = true
                            f.set(instance, res)
                        } catch (_: Throwable) {}
                    }
                } catch (t: Throwable) {
                    logcat(LogPriority.WARN, t) { "Failed to setup resources for $pluginName" }
                }
            }

            val apisBefore = com.lagradost.cloudstream3.APIHolder.allProviders.toList()

            val activeAct = com.lagradost.cloudstream3.CommonActivity.activity
            val loadContext = activeAct ?: context
            try {
                if (instance is com.lagradost.cloudstream3.plugins.Plugin) {
                    instance.load(loadContext)
                } else if (instance is com.lagradost.cloudstream3.plugins.BasePlugin) {
                    instance.load()
                } else {
                    try {
                        val m = clazz.getMethod("load", Context::class.java)
                        m.isAccessible = true
                        m.invoke(instance, loadContext)
                    } catch (_: NoSuchMethodException) {
                        try {
                            val m = clazz.getMethod("load")
                            m.isAccessible = true
                            m.invoke(instance)
                        } catch (_: NoSuchMethodException) {}
                    }
                }
            } catch (t: Throwable) {
                val cause = t.cause ?: t
                logcat(LogPriority.WARN, cause) { "Error calling load() on $pluginName: ${cause.message}" }
                val fullMsg = cause.message?.takeIf { it.isNotBlank() } ?: cause.toString()
                val methodRegex = Regex("""No (?:virtual|static|direct) method ([^(\s]+)(\([^)]*\)[^(\s]+) in class (L[^;]+;)""")
                val matched = methodRegex.find(fullMsg)
                val cleanDetail = if (matched != null) {
                    val mName = matched.groupValues[1]
                    val mSig = matched.groupValues[2]
                    val mCls = matched.groupValues[3].substringAfterLast('/').removeSuffix(";")
                    "Missing: $mCls.$mName$mSig"
                } else {
                    val fallbackRegex = Regex("""(No (?:virtual|static|direct) method [^\s]+)""")
                    fallbackRegex.find(fullMsg)?.groupValues?.getOrNull(1)
                        ?: "${cause::class.java.simpleName}: ${cause.message?.take(50)}"
                }
                _lastErrorFlow.value = "[${file.nameWithoutExtension}] $cleanDetail"
            }

            fun createSource(api: MainAPI): CloudStreamAnimeSource {
                val apiName = runCatching { api.name }.getOrDefault("")
                val resolvedIcon = getPluginIconUrl(file.nameWithoutExtension)
                    ?: getPluginIconUrl(pluginName)
                    ?: getPluginIconUrl(apiName)
                return CloudStreamAnimeSource(
                    api = api,
                    pluginName = pluginName,
                    pluginVersion = pluginVersion,
                    iconUrl = resolvedIcon,
                )
            }

            try {
                if (instance is com.lagradost.cloudstream3.plugins.BasePlugin) {
                    for (api in instance.registeredAPIs) {
                        try {
                            val apiName = runCatching { api.name }.getOrNull() ?: ""
                            if (sources.none { runCatching { it.api.name }.getOrDefault("") == apiName }) {
                                sources.add(createSource(api))
                            }
                        } catch (t: Throwable) {
                            logcat(LogPriority.WARN, t) { "Failed to create source for API in registeredAPIs of $pluginName" }
                        }
                    }
                }
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) { "Error accessing registeredAPIs on $pluginName" }
            }

            try {
                var c: Class<*>? = clazz
                while (c != null && c != Any::class.java) {
                    try {
                        val field = c.getDeclaredField("registeredAPIs")
                        field.isAccessible = true
                        val list = field.get(instance) as? List<*>
                        list?.forEach { item ->
                            if (item is MainAPI) {
                                val itemApiName = runCatching { item.name }.getOrNull() ?: ""
                                if (sources.none { runCatching { it.api.name }.getOrDefault("") == itemApiName }) {
                                    sources.add(createSource(item))
                                }
                            }
                        }
                        break
                    } catch (_: NoSuchFieldException) {
                        c = c.superclass
                    }
                }
            } catch (_: Throwable) {}

            try {
                val newApis = com.lagradost.cloudstream3.APIHolder.allProviders.filter { afterApi ->
                    val afterName = runCatching { afterApi.name }.getOrNull() ?: ""
                    apisBefore.none { runCatching { it.name.equals(afterName, ignoreCase = true) }.getOrDefault(false) }
                }
                for (api in newApis) {
                    val apiName = runCatching { api.name }.getOrNull() ?: ""
                    if (sources.none { runCatching { it.api.name }.getOrDefault("") == apiName }) {
                        sources.add(createSource(api))
                    }
                }
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) { "Error reading new providers from APIHolder for $pluginName" }
            }

            try {
                if (instance is MainAPI) {
                    val instName = runCatching { instance.name }.getOrNull() ?: ""
                    if (sources.none { runCatching { it.api.name }.getOrDefault("") == instName }) {
                        sources.add(createSource(instance))
                    }
                }
            } catch (t: Throwable) {
                logcat(LogPriority.WARN, t) { "Error checking instance as MainAPI for $pluginName" }
            }

            try {
                var c: Class<*>? = clazz
                while (c != null && c != Any::class.java) {
                    for (field in c.declaredFields) {
                        try {
                            field.isAccessible = true
                            val obj = field.get(instance)
                            if (obj is MainAPI) {
                                val objName = runCatching { obj.name }.getOrNull() ?: ""
                                if (sources.none { runCatching { it.api.name }.getOrDefault("") == objName }) {
                                    sources.add(createSource(obj))
                                }
                            } else if (obj is Collection<*>) {
                                obj.filterIsInstance<MainAPI>().forEach { api ->
                                    val apiName = runCatching { api.name }.getOrNull() ?: ""
                                    if (sources.none { runCatching { it.api.name }.getOrDefault("") == apiName }) {
                                        sources.add(createSource(api))
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    c = c.superclass
                }
            } catch (_: Throwable) {}

            if (sources.isNotEmpty()) {
                _lastErrorFlow.value = null
                logcat(LogPriority.INFO) { "Loaded ${sources.size} CloudStream sources from $pluginName" }
            } else if (_lastErrorFlow.value == null) {
                _lastErrorFlow.value = "No MainAPI providers registered by $pluginName"
            }
        } catch (e: Throwable) {
            val cause = e.cause ?: e
            val fullMsg = cause.message?.takeIf { it.isNotBlank() } ?: cause.toString()
            val methodRegex = Regex("""(No (?:virtual|static|direct) method [^(\s]+\([^)]*\)[^(\s]+)""")
            val matched = methodRegex.find(fullMsg)?.groupValues?.getOrNull(1)
            val cleanMsg = matched ?: if (fullMsg.length > 100) fullMsg.take(97) + "..." else fullMsg
            val errMsg = "Failed to load ${file.name}: ${cause::class.java.simpleName}: $cleanMsg"
            logcat(LogPriority.ERROR, e) { errMsg }
            _lastErrorFlow.value = errMsg
        }
        return sources
    }
}
