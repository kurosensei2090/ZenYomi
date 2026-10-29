package com.lagradost.cloudstream3.syncproviders.providers

object AniListApi {
    data class CoverImage(val extraLarge: String? = null, val large: String? = null, val medium: String? = null)
    data class Title(val romaji: String? = null, val english: String? = null, val native: String? = null)
    data class SeasonNextAiringEpisode(val episode: Int? = null, val airingAt: Long? = null)
    data class LikePageInfo(val total: Int? = null)
    data class RecommendationConnection(val nodes: List<Any>? = null)
}
