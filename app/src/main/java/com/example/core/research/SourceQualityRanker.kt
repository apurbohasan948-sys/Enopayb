package com.example.core.research

data class SourceMetadata(
    val url: String,
    val title: String,
    val sourceDomain: String,
    val snippet: String,
    val qualityScore: Float, // 0.0 to 1.0
    val isOfficialSource: Boolean,
    val retrievedAt: Long = System.currentTimeMillis(),
    val extractedContent: String = "",
    val confidence: Float = 0.85f
)

object SourceQualityRanker {

    private val OFFICIAL_TLDS = listOf(".gov", ".edu", ".mil", ".int")
    private val REPUTABLE_DOMAINS = listOf(
        "wikipedia.org",
        "github.com",
        "android.com",
        "developer.android.com",
        "reuters.com",
        "bbc.com",
        "nature.com",
        "sciencedirect.com",
        "arxiv.org",
        "google.com",
        "ieee.org",
        "who.int",
        "un.org"
    )

    /**
     * Evaluates source domain and assigns a weighted credibility score.
     */
    fun evaluateQuality(url: String, domain: String): Pair<Float, Boolean> {
        val lowerUrl = url.lowercase()
        val lowerDomain = domain.lowercase()

        // 1. Official Government or Educational
        if (OFFICIAL_TLDS.any { lowerDomain.endsWith(it) || lowerUrl.contains(it) }) {
            return 0.98f to true
        }

        // 2. Verified Reputable Research / News / Documentation
        if (REPUTABLE_DOMAINS.any { lowerDomain.contains(it) }) {
            return 0.90f to true
        }

        // 3. Standard HTTPS Websites
        if (lowerUrl.startsWith("https://")) {
            return 0.75f to false
        }

        return 0.50f to false
    }

    /**
     * Sorts sources by quality and credibility.
     */
    fun rankSources(sources: List<SourceMetadata>): List<SourceMetadata> {
        return sources.sortedWith(
            compareByDescending<SourceMetadata> { it.isOfficialSource }
                .thenByDescending { it.qualityScore }
                .thenByDescending { it.confidence }
        )
    }
}
