package com.looktube.model

import java.net.URI

data class VideoSummary(
    val id: String,
    val title: String,
    val description: String,
    val isPremium: Boolean,
    val feedCategory: String,
    val playbackUrl: String?,
    val seriesTitle: String? = null,
    val thumbnailUrl: String? = null,
    val publishedAtEpochMillis: Long? = null,
    val durationSeconds: Long? = null,
)

val VideoSummary.displaySeriesTitle: String
    get() = sequenceOf(
        seriesTitle.toHeuristicShowTitleOrNull(),
        title.toHeuristicShowTitleOrNull(),
        feedCategory.toHeuristicShowTitleOrNull(),
    ).filterNotNull().firstOrNull() ?: feedCategory.ifBlank { "More videos" }

val VideoSummary.seriesGroupingKey: String
    get() = displaySeriesTitle.normalizedGroupingKey()

val VideoSummary.castGroupingTitle: String
    get() {
        val haystack = listOf(title, description.stripHtml())
            .joinToString(" ")
            .lowercase()
        val firstMatchedName = HEURISTIC_CAST_NAMES
            .mapNotNull { name ->
                haystack.indexOf(name.lowercase())
                    .takeIf { it >= 0 }
                    ?.let { it to name }
            }
            .minByOrNull { it.first }
            ?.second
        return firstMatchedName ?: when {
            displaySeriesTitle.contains("bombcast", ignoreCase = true) -> "Bombcast crew"
            displaySeriesTitle.contains("game mess", ignoreCase = true) -> "Jeff Grubb"
            displaySeriesTitle.contains("blight club", ignoreCase = true) -> "Blight Club crew"
            displaySeriesTitle.contains("dump truck", ignoreCase = true) -> "Dump Truck crew"
            else -> "Mixed cast"
        }
    }

val VideoSummary.castGroupingKey: String
    get() = castGroupingTitle.normalizedGroupingKey()

val VideoSummary.topicGroupingTitle: String
    get() {
        val subtitle = title.removeShowPrefix(displaySeriesTitle)
        return subtitle.extractTopicLabel()
            ?: description.stripHtml().extractTopicLabel()
            ?: displaySeriesTitle
    }

val VideoSummary.topicGroupingKey: String
    get() = topicGroupingTitle.normalizedGroupingKey()

fun VideoSummary.bestDurationSeconds(progress: PlaybackProgress?): Long? =
    durationSeconds ?: progress?.durationSeconds?.takeIf { it > 0 }

fun String?.toHeuristicShowTitleOrNull(): String? {
    val raw = this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    if (raw.isGenericFeedCategoryLabel()) {
        return null
    }
    KNOWN_SHOW_TITLE_PATTERNS.firstNotNullOfOrNull { (pattern, showTitle) ->
        pattern.matchEntire(raw)?.let { showTitle }
    }?.let { return it }
    val candidate = TITLE_PREFIX_SEPARATORS
        .firstNotNullOfOrNull { separator ->
            raw.substringBefore(separator, missingDelimiterValue = raw)
                .takeIf { it != raw }
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }
        ?: raw
    return candidate
        .stripTrailingEpisodeMarkers()
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() && it.length <= 48 && !it.isGenericFeedCategoryLabel() }
}

fun String?.toHeuristicShowTitleFromUrlOrNull(): String? {
    val raw = this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    val pathSegments = uri.path
        ?.split('/')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        .orEmpty()
    val slugCandidates = buildList {
        if (pathSegments.size >= 2 && pathSegments.first() in GIANT_BOMB_SHOW_PATH_PREFIXES) {
            add(pathSegments[1])
        }
        addAll(pathSegments)
    }
    return slugCandidates
        .asSequence()
        .mapNotNull(String::toKnownShowTitleFromSlugOrNull)
        .firstOrNull()
}

private fun String.toKnownShowTitleFromSlugOrNull(): String? {
    val slug = lowercase().trim()
    if (slug in GIANT_BOMB_SHOW_PATH_PREFIXES) {
        return null
    }
    KNOWN_SHOW_SLUGS[slug]?.let { return it }
    KNOWN_SHOW_SLUGS.entries.firstOrNull { (knownSlug, _) ->
        slug.startsWith("$knownSlug-")
    }?.value?.let { return it }
    if (!slug.matches(Regex("[a-z0-9-]{3,48}")) || slug.firstOrNull()?.isDigit() == true) {
        return null
    }
    return slug.split('-')
        .filter(String::isNotBlank)
        .joinToString(" ") { token -> token.replaceFirstChar(Char::uppercaseChar) }
        .takeIf { it.isNotBlank() && !it.isGenericFeedCategoryLabel() }
}

private fun String.normalizedGroupingKey(): String = trim()
    .lowercase()
    .replace("&", "and")
    .replace("'", "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .replace(Regex("\\b(the|premium|show|series)\\b"), " ")
    .replace(Regex("\\blooks\\b"), "look")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun String.stripTrailingEpisodeMarkers(): String {
    var result = trim()
    SHOW_TITLE_TRAILING_PATTERNS.forEach { pattern ->
        pattern.matchEntire(result)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { result = it }
    }
    return result.trimEnd(':', '-', '—', '#', ' ')
}

private fun String.isGenericFeedCategoryLabel(): Boolean = equals("Premium", ignoreCase = true) ||
    equals("Latest Premium", ignoreCase = true) ||
    equals("Premium Video", ignoreCase = true) ||
    equals("Premium Audio", ignoreCase = true) ||
    equals("Video", ignoreCase = true) ||
    equals("Videos", ignoreCase = true) ||
    equals("Show", ignoreCase = true) ||
    equals("Shows", ignoreCase = true) ||
    equals("Podcast", ignoreCase = true) ||
    equals("Podcasts", ignoreCase = true) ||
    equals("Uncategorized", ignoreCase = true)

private fun String.stripHtml(): String = replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun String.removeShowPrefix(showTitle: String): String {
    val withoutShowPrefix = if (startsWith(showTitle, ignoreCase = true)) {
        drop(showTitle.length)
    } else {
        this
    }
    return withoutShowPrefix
        .replace(Regex("^[\\s:#—-]+"), "")
        .replace(Regex("^\\d+[a-z]?(?=\\b|:)\\s*[:—-]?\\s*"), "")
        .trim()
}

private fun String.extractTopicLabel(): String? {
    val cleaned = trim()
        .replace(Regex("\\s+"), " ")
        .trim()
    if (cleaned.isBlank()) {
        return null
    }
    val phraseCandidates = TOPIC_PHRASE_REGEX.findAll(cleaned)
        .map { it.value.trim(' ', '.', ',', ':', ';', '!', '?', '"', '\'') }
        .filter { candidate ->
            candidate.length in 4..40 &&
                candidate.lowercase() !in GENERIC_TOPIC_LABELS
        }
        .toList()
    phraseCandidates.firstOrNull { ' ' in it }?.let { return it }
    phraseCandidates.firstOrNull()?.let { return it }
    val firstKeyword = cleaned
        .lowercase()
        .split(Regex("[^a-z0-9+]+"))
        .firstOrNull { token ->
            token.length >= 4 &&
                token !in GENERIC_TOPIC_LABELS &&
                token.toIntOrNull() == null
        }
        ?: return null
    return firstKeyword.split('-')
        .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }
}

private val TITLE_PREFIX_SEPARATORS = listOf(": ", " - ", " — ", " #")

private val KNOWN_SHOW_TITLE_PATTERNS = listOf(
    Regex("""^\d{3,4}\s*:\s+.+$""") to "Giant Bombcast",
    Regex("""^Giant Bombcast\s+\d{3,4}\b.*$""", RegexOption.IGNORE_CASE) to "Giant Bombcast",
    Regex("""^Voicemail Dump Truck\b.*$""", RegexOption.IGNORE_CASE) to "Voicemail Dump Truck",
    Regex("""^.*\b(?:BONUS DUMP|RE-DUMP)\b.*$""", RegexOption.IGNORE_CASE) to "Voicemail Dump Truck",
    Regex("""^.*\.mp3$""", RegexOption.IGNORE_CASE) to "Voicemail Dump Truck",
    Regex("""^Game Mess Mornings\b.*$""", RegexOption.IGNORE_CASE) to "Game Mess Mornings",
)

private val SHOW_TITLE_TRAILING_PATTERNS = listOf(
    Regex("""^(.*?)(?:\s+#?\d{1,4}[a-z]?)$""", RegexOption.IGNORE_CASE),
    Regex("""^(.*?)(?:\s+episode\s+\d{1,4}[a-z]?)$""", RegexOption.IGNORE_CASE),
    Regex("""^(.*?)(?:\s+\(\s*part\s+\d+\s*\))$""", RegexOption.IGNORE_CASE),
    Regex("""^(.*?)(?:\s+\d{1,2}/\d{1,2}/\d{2,4})$"""),
    Regex("""^(.*?)(?:\s+\d{4}-\d{2}-\d{2})$"""),
)

private val GIANT_BOMB_SHOW_PATH_PREFIXES = setOf("shows", "videos", "podcasts")

private val KNOWN_SHOW_SLUGS = mapOf(
    "giant-bombcast" to "Giant Bombcast",
    "voicemail-dump-truck" to "Voicemail Dump Truck",
    "game-mess-mornings" to "Game Mess Mornings",
    "quick-look" to "Quick Look",
    "quick-looks" to "Quick Look",
    "blight-club" to "Blight Club",
    "best-of-giant-bomb" to "Best of Giant Bomb",
    "bomb-a-thon" to "Bomb-A-Thon",
    "giant-bomb-presents" to "Giant Bomb Presents",
    "this-is-the-run" to "This Is the Run",
    "premium-this-is-the-run" to "This Is the Run",
)

private val HEURISTIC_CAST_NAMES = listOf(
    "Jeff Grubb",
    "Jan Ochoa",
    "Dan Ryckert",
    "Jeff Bakalar",
    "Mike Minotti",
    "Lucy James",
    "Tamoor Hussain",
    "Niki Grayson",
    "Jess O'Brien",
    "Shawn McDowell",
    "Jason Oestreicher",
    "Brad Shoemaker",
    "Vinny Caravella",
    "Alex Navarro",
)

private val TOPIC_PHRASE_REGEX = Regex("""\b[A-Z0-9][A-Za-z0-9+.'-]*(?:\s+[A-Z0-9][A-Za-z0-9+.'-]*){0,2}\b""")

private val GENERIC_TOPIC_LABELS = setOf(
    "premium",
    "video",
    "videos",
    "episode",
    "part",
    "show",
    "series",
    "latest",
    "giant",
    "bomb",
    "quick",
    "look",
    "club",
    "cast",
    "crew",
    "with",
    "from",
    "about",
    "that",
    "this",
    "have",
    "your",
    "their",
    "game",
    "games",
    "giant bomb",
)
