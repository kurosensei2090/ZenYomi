package eu.kanade.tachiyomi.extension.anime.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.Plugin
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
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@Serializable
data class CloudStreamRepo(
    val name: String,
    val url: String,
)

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
)

class CloudStreamManager(
    private val context: Context,
) {
    companion object {
        private const val PREF_REPOS = "zenyomi.cloudstream.repos"
        private const val PLUGINS_DIR = "cloudstream_plugins"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; ZenYomi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

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

    init {
        com.lagradost.cloudstream3.AcraApplication.context = context.applicationContext
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
        _repositoriesFlow.value = repos
    }

    private fun saveRepositories(repos: List<CloudStreamRepo>) {
        prefs.edit().putString(PREF_REPOS, json.encodeToString(repos)).apply()
        _repositoriesFlow.value = repos
    }

    fun addRepository(url: String, name: String = "") {
        val normalized = normalizeRepoUrl(url)
        if (normalized.isBlank()) return
        val current = _repositoriesFlow.value.toMutableList()
        if (current.none { it.url == normalized }) {
            val displayName = name.ifBlank {
                normalized.substringAfterLast("/").substringBefore(".json").ifBlank { "CloudStream Repo" }
            }
            current.add(CloudStreamRepo(name = displayName, url = normalized))
            saveRepositories(current)
            refreshRepositories()
        }
    }

    fun removeRepository(url: String) {
        val current = _repositoriesFlow.value.toMutableList()
        current.removeAll { it.url == url }
        saveRepositories(current)
        refreshRepositories()
    }

    fun refreshRepositories() {
        scope.launch {
            _isLoadingFlow.value = true
            _statusMessageFlow.value = "Fetching repository plugins..."
            try {
                val pluginsList = mutableListOf<CloudStreamPluginItem>()
                val repos = _repositoriesFlow.value

                for (repo in repos) {
                    try {
                        val pluginUrls = resolvePluginUrls(repo.url)
                        for (pluginListUrl in pluginUrls) {
                            val items = fetchPluginList(pluginListUrl, repo.url)
                            pluginsList.addAll(items)
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to fetch CloudStream repo: ${repo.url}" }
                    }
                }

                val installedNames = pluginsFolder.listFiles { file -> file.extension == "cs3" }
                    ?.map { it.nameWithoutExtension }
                    ?.toSet() ?: emptySet()

                val marked = pluginsList.distinctBy { "${it.repoUrl}_${it.internalName}" }.map { item ->
                    item.copy(isInstalled = installedNames.contains(item.internalName))
                }
                _availablePluginsFlow.value = marked
            } finally {
                kotlinx.coroutines.delay(500)
                _statusMessageFlow.value = null
                _isLoadingFlow.value = false
            }
        }
    }

    private fun normalizeRepoUrl(raw: String): String {
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

    private fun resolvePluginUrls(repoUrl: String): List<String> {
        val candidates = if (repoUrl.endsWith(".json", ignoreCase = true)) {
            listOf(repoUrl)
        } else {
            val clean = repoUrl.trimEnd('/')
            listOf(
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

                if (candidate.endsWith("plugins.json", ignoreCase = true)) {
                    return listOf(candidate)
                }

                try {
                    val parsed = json.parseToJsonElement(body)
                    if (parsed is JsonObject) {
                        val lists = parsed["pluginLists"]?.jsonArray?.mapNotNull {
                            val path = it.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                            resolveUrl(candidate, path)
                        }
                        if (!lists.isNullOrEmpty()) {
                            return lists
                        }
                    } else if (parsed is JsonArray) {
                        // It was directly an array of plugins
                        return listOf(candidate)
                    }
                } catch (_: Exception) {
                }

                return listOf(candidate)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Candidate $candidate failed" }
            }
        }
        return emptyList()
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
                val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                val pluginUrl = resolveUrl(url, rawUrl)
                val iconUrl = obj["iconUrl"]?.jsonPrimitive?.contentOrNull?.let { resolveUrl(url, it) }
                val version = obj["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: "en"
                val tvTypes = obj["tvTypes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

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
            val targetFile = File(pluginsFolder, "${plugin.internalName}.cs3")
            if (targetFile.exists()) {
                targetFile.setWritable(true)
                targetFile.delete()
            }
            if (targetFile.exists()) {
                targetFile.setWritable(true)
            }
            val request = Request.Builder()
                .url(plugin.url)
                .header("User-Agent", USER_AGENT)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                logcat(LogPriority.ERROR) { "HTTP ${response.code} downloading plugin from ${plugin.url}" }
                return@withContext false
            }
            val bytes = response.body.bytes()
            FileOutputStream(targetFile).use { it.write(bytes) }
            targetFile.setReadOnly()

            val newSources = loadSinglePluginFile(targetFile)
            val current = _installedSourcesFlow.value.toMutableList()
            current.removeAll { it.pluginName == plugin.name || it.pluginName == plugin.internalName }
            current.addAll(newSources)
            _installedSourcesFlow.value = current.distinctBy { it.id }

            refreshRepositories()
            return@withContext true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install CloudStream plugin ${plugin.name}" }
            return@withContext false
        }
    }

    suspend fun uninstallPlugin(internalName: String) = withContext(Dispatchers.IO) {
        val targetFile = File(pluginsFolder, "$internalName.cs3")
        if (targetFile.exists()) {
            targetFile.setWritable(true)
            targetFile.delete()
        }
        val dexFile = File(context.codeCacheDir, "$internalName.dex")
        if (dexFile.exists()) {
            dexFile.setWritable(true)
            dexFile.delete()
        }
        val currentSources = _installedSourcesFlow.value.toMutableList()
        currentSources.removeAll { it.pluginName == internalName || it.api.name == internalName }
        _installedSourcesFlow.value = currentSources
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
        val cs3Files = pluginsFolder.listFiles { file -> file.extension == "cs3" } ?: return
        val allSources = mutableListOf<CloudStreamAnimeSource>()
        for (file in cs3Files) {
            val sources = loadSinglePluginFile(file)
            allSources.addAll(sources)
        }
        _installedSourcesFlow.value = allSources.distinctBy { it.id }
    }

    private fun loadSinglePluginFile(file: File): List<CloudStreamAnimeSource> {
        val sources = mutableListOf<CloudStreamAnimeSource>()
        try {
            file.setReadOnly()
            val zip = ZipFile(file)
            val manifestEntry = zip.entries().asSequence().find {
                it.name.equals("manifest.json", ignoreCase = true) || it.name.endsWith("/manifest.json", ignoreCase = true)
            } ?: return emptyList()

            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val manifestJson = JSONObject(manifestText)
            val pluginClassName = manifestJson.optString("pluginClassName", "").trim()
            val pluginVersion = manifestJson.optInt("version", 1)
            val pluginName = manifestJson.optString("name", file.nameWithoutExtension)

            if (pluginClassName.isBlank()) return emptyList()

            val dexEntry = zip.entries().asSequence().find {
                it.name.equals("classes.dex", ignoreCase = true) || it.name.endsWith("/classes.dex", ignoreCase = true)
            } ?: return emptyList()

            val dexFile = File(context.codeCacheDir, "${file.nameWithoutExtension}.dex")
            if (dexFile.exists()) {
                dexFile.setWritable(true)
                dexFile.delete()
            }
            if (dexFile.exists()) {
                dexFile.setWritable(true)
            }
            dexFile.outputStream().use { out ->
                zip.getInputStream(dexEntry).use { it.copyTo(out) }
            }
            dexFile.setReadOnly()

            val pathClassLoader = dalvik.system.PathClassLoader(
                dexFile.absolutePath,
                javaClass.classLoader,
            )

            val clazz = pathClassLoader.loadClass(pluginClassName)
            val constructor = try {
                clazz.getDeclaredConstructor()
            } catch (_: NoSuchMethodException) {
                try {
                    clazz.getDeclaredConstructor(Context::class.java)
                } catch (_: NoSuchMethodException) {
                    clazz.constructors.firstOrNull() ?: return emptyList()
                }
            }
            constructor.isAccessible = true
            val instance = if (constructor.parameterTypes.isEmpty()) {
                constructor.newInstance()
            } else if (constructor.parameterTypes.size == 1 && constructor.parameterTypes[0] == Context::class.java) {
                constructor.newInstance(context)
            } else {
                constructor.newInstance()
            }

            if (instance is com.lagradost.cloudstream3.plugins.BasePlugin) {
                try {
                    instance.load(context)
                } catch (_: NoSuchMethodError) {
                    try {
                        instance.load()
                    } catch (_: Throwable) {}
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) { "Error in plugin.load(context) for $pluginName" }
                }
                for (api in instance.registeredAPIs) {
                    sources.add(CloudStreamAnimeSource(api, pluginName = pluginName, pluginVersion = pluginVersion))
                }
            } else {
                try {
                    clazz.getMethod("load", Context::class.java).invoke(instance, context)
                } catch (_: NoSuchMethodException) {
                    try {
                        clazz.getMethod("load").invoke(instance)
                    } catch (_: Throwable) {}
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) { "Reflection load failed on $pluginClassName" }
                }

                var currClass: Class<*>? = clazz
                while (currClass != null && currClass != Any::class.java) {
                    try {
                        val field = currClass.getDeclaredField("registeredAPIs")
                        field.isAccessible = true
                        val list = field.get(instance) as? List<*>
                        list?.forEach { item ->
                            if (item is MainAPI) {
                                sources.add(CloudStreamAnimeSource(item, pluginName = pluginName, pluginVersion = pluginVersion))
                            }
                        }
                        break
                    } catch (_: NoSuchFieldException) {
                        currClass = currClass.superclass
                    }
                }
            }

            if (instance is MainAPI) {
                sources.add(CloudStreamAnimeSource(instance, pluginName = pluginName, pluginVersion = pluginVersion))
            }

            for (api in com.lagradost.cloudstream3.APIHolder.allProviders) {
                if (sources.none { it.api.name == api.name }) {
                    sources.add(CloudStreamAnimeSource(api, pluginName = pluginName, pluginVersion = pluginVersion))
                }
            }

            if (sources.isEmpty()) {
                var currClass: Class<*>? = clazz
                while (currClass != null && currClass != Any::class.java) {
                    for (field in currClass.declaredFields) {
                        try {
                            field.isAccessible = true
                            val obj = field.get(instance)
                            if (obj is MainAPI) {
                                sources.add(CloudStreamAnimeSource(obj, pluginName = pluginName, pluginVersion = pluginVersion))
                            } else if (obj is Collection<*>) {
                                obj.filterIsInstance<MainAPI>().forEach { api ->
                                    sources.add(CloudStreamAnimeSource(api, pluginName = pluginName, pluginVersion = pluginVersion))
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    currClass = currClass.superclass
                }
            }

            if (sources.isNotEmpty()) {
                logcat(LogPriority.INFO) { "Loaded ${sources.size} CloudStream sources from $pluginName" }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load CloudStream plugin from ${file.name}" }
        }
        return sources
    }
}
