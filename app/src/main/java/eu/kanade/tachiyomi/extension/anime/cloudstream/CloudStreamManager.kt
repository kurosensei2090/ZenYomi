package eu.kanade.tachiyomi.extension.anime.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
    private val client: OkHttpClient = OkHttpClient(),
) {
    companion object {
        private const val PREF_REPOS = "zenyomi.cloudstream.repos"
        private const val PLUGINS_DIR = "cloudstream_plugins"

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

    private val pluginsFolder: File by lazy {
        File(context.filesDir, PLUGINS_DIR).apply { mkdirs() }
    }

    private val _repositoriesFlow = MutableStateFlow<List<CloudStreamRepo>>(emptyList())
    val repositoriesFlow: StateFlow<List<CloudStreamRepo>> = _repositoriesFlow.asStateFlow()

    private val _availablePluginsFlow = MutableStateFlow<List<CloudStreamPluginItem>>(emptyList())
    val availablePluginsFlow: StateFlow<List<CloudStreamPluginItem>> = _availablePluginsFlow.asStateFlow()

    private val _installedSourcesFlow = MutableStateFlow<List<CloudStreamAnimeSource>>(emptyList())
    val installedSourcesFlow: StateFlow<List<CloudStreamAnimeSource>> = _installedSourcesFlow.asStateFlow()

    init {
        loadSavedRepositories()
        loadInstalledPlugins()
        refreshRepositories()
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

    fun addRepository(url: String, name: String = "CloudStream Repo") {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val current = _repositoriesFlow.value.toMutableList()
        if (current.none { it.url == cleanUrl }) {
            current.add(CloudStreamRepo(name = name, url = cleanUrl))
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

            val marked = pluginsList.map { item ->
                item.copy(isInstalled = installedNames.contains(item.internalName))
            }
            _availablePluginsFlow.value = marked
        }
    }

    private fun resolvePluginUrls(repoUrl: String): List<String> {
        val request = Request.Builder().url(repoUrl).build()
        val response = client.newCall(request).execute()
        val body = response.body.string()
        if (body.isEmpty()) return emptyList()

        return if (repoUrl.endsWith("plugins.json")) {
            listOf(repoUrl)
        } else {
            // It's a repo.json
            try {
                val element = json.parseToJsonElement(body).jsonObject
                element["pluginLists"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf(repoUrl)
            } catch (e: Exception) {
                listOf(repoUrl)
            }
        }
    }

    private fun fetchPluginList(url: String, repoUrl: String): List<CloudStreamPluginItem> {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body.string()
        if (body.isEmpty()) return emptyList()

        val jsonArray = json.parseToJsonElement(body).jsonArray
        val results = mutableListOf<CloudStreamPluginItem>()

        for (elem in jsonArray) {
            val obj = elem.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: continue
            val internalName = obj["internalName"]?.jsonPrimitive?.content ?: name
            val pluginUrl = obj["url"]?.jsonPrimitive?.content ?: continue
            val iconUrl = obj["iconUrl"]?.jsonPrimitive?.content
            val version = obj["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            val lang = obj["language"]?.jsonPrimitive?.content ?: "en"
            val tvTypes = obj["tvTypes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

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
        return results
    }

    suspend fun installPlugin(plugin: CloudStreamPluginItem) = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(pluginsFolder, "${plugin.internalName}.cs3")
            val request = Request.Builder().url(plugin.url).build()
            val response = client.newCall(request).execute()
            val bytes = response.body.bytes()

            FileOutputStream(targetFile).use { it.write(bytes) }

            loadSinglePluginFile(targetFile)
            refreshRepositories()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to install CloudStream plugin ${plugin.name}" }
        }
    }

    suspend fun uninstallPlugin(internalName: String) = withContext(Dispatchers.IO) {
        val targetFile = File(pluginsFolder, "$internalName.cs3")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val currentSources = _installedSourcesFlow.value.toMutableList()
        currentSources.removeAll { it.pluginName == internalName || it.api.name == internalName }
        _installedSourcesFlow.value = currentSources
        refreshRepositories()
    }

    private fun loadInstalledPlugins() {
        val cs3Files = pluginsFolder.listFiles { file -> file.extension == "cs3" } ?: return
        for (file in cs3Files) {
            loadSinglePluginFile(file)
        }
    }

    private fun loadSinglePluginFile(file: File) {
        try {
            val zip = ZipFile(file)
            val manifestEntry = zip.getEntry("manifest.json") ?: return
            val manifestText = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val manifestJson = JSONObject(manifestText)
            val pluginClassName = manifestJson.optString("pluginClassName", "")
            val pluginVersion = manifestJson.optInt("version", 1)
            val pluginName = manifestJson.optString("name", file.nameWithoutExtension)

            if (pluginClassName.isBlank()) return

            val dexClassLoader = DexClassLoader(
                file.absolutePath,
                context.codeCacheDir.absolutePath,
                null,
                javaClass.classLoader,
            )

            val clazz = dexClassLoader.loadClass(pluginClassName)
            val instance = clazz.getDeclaredConstructor().newInstance()

            val sources = mutableListOf<CloudStreamAnimeSource>()

            if (instance is Plugin) {
                instance.load(context)
                for (api in instance.registeredAPIs) {
                    sources.add(CloudStreamAnimeSource(api, pluginName = pluginName, pluginVersion = pluginVersion))
                }
            } else if (instance is MainAPI) {
                sources.add(CloudStreamAnimeSource(instance, pluginName = pluginName, pluginVersion = pluginVersion))
            }

            if (sources.isNotEmpty()) {
                val current = _installedSourcesFlow.value.toMutableList()
                current.removeAll { it.pluginName == pluginName }
                current.addAll(sources)
                _installedSourcesFlow.value = current
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load CloudStream plugin from ${file.name}" }
        }
    }
}
