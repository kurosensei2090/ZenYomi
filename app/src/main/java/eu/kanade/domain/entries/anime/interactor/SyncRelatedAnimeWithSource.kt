package eu.kanade.domain.entries.anime.interactor

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRelationRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.time.Instant
import kotlin.time.Duration.Companion.days

class SyncRelatedAnimeWithSource(
    private val sourceManager: AnimeSourceManager,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val relationRepository: AnimeRelationRepository,
) {

    suspend fun await(anime: Anime, forceRefresh: Boolean = false) {
        val source = sourceManager.get(anime.source) ?: return

        val now = Instant.now().toEpochMilli()
        if (!forceRefresh) {
            val lastFetchedAt = relationRepository.getLastFetchedAt(anime.id)
            if (lastFetchedAt != null && now - lastFetchedAt < TTL) return
        }

        val sourceRelations = if (source.supportsRelatedAnime) {
            try {
                source.getRelatedAnimeList(anime.toSAnime())
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val rawRelations = if (sourceRelations.isNotEmpty()) {
            sourceRelations
        } else if (source is AnimeCatalogueSource) {
            discoverSameSourceSeasons(source, anime)
        } else {
            emptyList()
        }

        val groups = rawRelations
            .map { relation ->
                relation.name to relation.animes
                    .distinctBy { it.url }
                    .filterNot { it.url == anime.url }
                    .take(MAX_PER_GROUP)
                    .map { networkToLocalAnime.await(it.toDomainAnime(anime.source)).id }
            }
            .filter { (_, ids) -> ids.isNotEmpty() }

        relationRepository.replaceRelations(anime.id, groups, now)
    }

    /**
     * Searches the same [source] for other seasons/parts of [anime] when the extension does not
     * implement [AnimeCatalogueSource.getRelatedAnimeList].
     */
    private suspend fun discoverSameSourceSeasons(
        source: AnimeCatalogueSource,
        anime: Anime,
    ): List<AnimeRelation> {
        val currentSeason = extractSeasonOrder(anime.title)
        val baseTitle = extractBaseTitle(anime.title)
        if (baseTitle.length < 3) return emptyList()

        val searchResults = try {
            withTimeoutOrNull(10_000L) {
                source.getSearchAnime(1, baseTitle, AnimeFilterList()).animes
            }.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }

        if (searchResults.isEmpty()) return emptyList()

        val normalizedBase = normalizeForMatch(baseTitle)
        val candidates = searchResults
            .filter { it.url != anime.url }
            .filter { candidate ->
                val candidateBase = normalizeForMatch(extractBaseTitle(candidate.title))
                candidateBase == normalizedBase ||
                    (normalizedBase.length >= 6 && (candidateBase.startsWith(normalizedBase) || normalizedBase.startsWith(candidateBase)))
            }
            .distinctBy { it.url }

        if (candidates.isEmpty()) return emptyList()

        val previousSeasons = mutableListOf<Pair<Float, SAnime>>()
        val nextSeasons = mutableListOf<Pair<Float, SAnime>>()
        val otherRelated = mutableListOf<SAnime>()

        for (candidate in candidates) {
            val candOrder = extractSeasonOrder(candidate.title)
            if (currentSeason != null && candOrder != null) {
                when {
                    candOrder < currentSeason -> previousSeasons.add(candOrder to candidate)
                    candOrder > currentSeason -> nextSeasons.add(candOrder to candidate)
                    else -> otherRelated.add(candidate)
                }
            } else if (currentSeason != null && currentSeason > 1f && candOrder == null) {
                // Unnumbered base title (e.g. "Title") is usually Season 1 when viewing Season 2+
                previousSeasons.add(1f to candidate)
            } else if (candOrder != null && candOrder > 1f && currentSeason == null) {
                // Numbered sequel (e.g. "Title II" / "Season 2") when viewing Season 1
                nextSeasons.add(candOrder to candidate)
            } else {
                otherRelated.add(candidate)
            }
        }

        val relations = mutableListOf<AnimeRelation>()
        if (previousSeasons.isNotEmpty()) {
            relations.add(
                AnimeRelation(
                    "Previous Seasons",
                    previousSeasons.sortedBy { it.first }.map { it.second },
                ),
            )
        }
        if (nextSeasons.isNotEmpty()) {
            relations.add(
                AnimeRelation(
                    "Next Seasons",
                    nextSeasons.sortedBy { it.first }.map { it.second },
                ),
            )
        }
        if (otherRelated.isNotEmpty()) {
            relations.add(AnimeRelation("Related Seasons", otherRelated))
        }
        return relations
    }

    companion object {
        private val TTL = 7.days.inWholeMilliseconds
        private const val MAX_PER_GROUP = 10

        private val SEASON_STRIP_PATTERNS = listOf(
            Regex("""\s*[:\-–—]?\s*(?:season|series|cour|part|s)\s*\d+.*$""", RegexOption.IGNORE_CASE),
            Regex("""\s*[:\-–—]?\s*\d+(?:st|nd|rd|th)\s+season.*$""", RegexOption.IGNORE_CASE),
            Regex("""\s+(?:II|III|IV|V|VI|VII|VIII)\s*$"""),
            Regex("""\s+(?:2nd|3rd|4th|5th)\s*$""", RegexOption.IGNORE_CASE),
            Regex("""\s*الموسم\s+.*$"""),
            Regex("""\s*الجزء\s+.*$"""),
        )

        private val SEASON_ORDER_PATTERNS = listOf(
            Regex("""(?:season|s)\s*(\d+)""", RegexOption.IGNORE_CASE) to 1f,
            Regex("""(\d+)(?:st|nd|rd|th)\s+season""", RegexOption.IGNORE_CASE) to 1f,
            Regex("""part\s*(\d+)""", RegexOption.IGNORE_CASE) to 0.1f,
            Regex("""cour\s*(\d+)""", RegexOption.IGNORE_CASE) to 0.1f,
        )

        private val ROMAN_SUFFIX_REGEX = Regex("""\s+(II|III|IV|V|VI|VII|VIII)\b""")

        internal fun extractBaseTitle(title: String): String {
            var result = title.trim()
            for (pattern in SEASON_STRIP_PATTERNS) {
                result = result.replace(pattern, "").trim()
            }
            return result.trimEnd(':', '-', '–', '—', ' ')
        }

        internal fun extractSeasonOrder(title: String): Float? {
            var baseSeason: Float? = null
            for ((regex, scale) in SEASON_ORDER_PATTERNS) {
                val match = regex.find(title)
                val num = match?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: continue
                if (scale == 1f) {
                    baseSeason = (baseSeason ?: 0f) + num
                } else {
                    baseSeason = (baseSeason ?: 1f) + num * scale
                }
            }
            if (baseSeason != null) return baseSeason

            val roman = ROMAN_SUFFIX_REGEX.find(title)?.groupValues?.getOrNull(1)
            return when (roman) {
                "II" -> 2f
                "III" -> 3f
                "IV" -> 4f
                "V" -> 5f
                "VI" -> 6f
                "VII" -> 7f
                "VIII" -> 8f
                else -> when {
                    title.contains("الموسم الثاني") || title.contains("الجزء الثاني") -> 2f
                    title.contains("الموسم الثالث") || title.contains("الجزء الثالث") -> 3f
                    title.contains("الموسم الرابع") || title.contains("الجزء الرابع") -> 4f
                    title.contains("الموسم الأول") || title.contains("الجزء الأول") -> 1f
                    else -> null
                }
            }
        }

        private fun normalizeForMatch(text: String): String {
            return text.lowercase()
                .replace(Regex("""[^\p{L}\p{N}]"""), "")
        }
    }
}
