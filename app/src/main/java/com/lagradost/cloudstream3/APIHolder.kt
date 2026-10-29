package com.lagradost.cloudstream3

object APIHolder {
    val apis = mutableListOf<MainAPI>()
    val allProviders = mutableListOf<MainAPI>()

    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L

    val unixTimeMS: Long
        get() = System.currentTimeMillis()

    fun getApiFromName(name: String): MainAPI? {
        return allProviders.find { it.name.equals(name, ignoreCase = true) }
            ?: apis.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getApiFromNameNull(name: String?): MainAPI? {
        if (name == null) return null
        return getApiFromName(name)
    }

    fun addPlugin(api: MainAPI) {
        if (allProviders.none { it.name.equals(api.name, ignoreCase = true) }) {
            allProviders.add(api)
        }
        if (apis.none { it.name.equals(api.name, ignoreCase = true) }) {
            apis.add(api)
        }
    }

    fun removePlugin(api: MainAPI) {
        allProviders.removeAll { it.name.equals(api.name, ignoreCase = true) }
        apis.removeAll { it.name.equals(api.name, ignoreCase = true) }
    }
}
