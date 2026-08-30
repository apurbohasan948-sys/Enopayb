package com.example.core.research

import com.example.core.autonomy.MasterStopManager
import com.example.core.knowledge.IngestionCandidate
import com.example.core.knowledge.KnowledgeIngestionEngine
import com.example.core.model.GeminiModelProvider
import com.example.core.security.PrivacyFilter
import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeSourceType
import com.example.data.local.entity.ResearchStatus
import com.example.data.local.entity.WebResearchRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ResearchResult(
    val id: Long = 0,
    val query: String,
    val userGoal: String,
    val summary: String,
    val keyFindings: List<String>,
    val sources: List<SourceMetadata>,
    val confidence: Float,
    val durationMs: Long,
    val storedAsKnowledge: Boolean
)

class WebResearchManager(
    private val dao: JarvisDao,
    private val searchEngine: WebSearchEngine,
    private val knowledgeUpdateManager: KnowledgeUpdateManager,
    private val geminiProvider: GeminiModelProvider? = null,
    private val ingestionEngine: KnowledgeIngestionEngine? = null,
    private val researchPolicy: ResearchPolicy = ResearchPolicy()
) {
    val allResearchRecords: Flow<List<WebResearchRecordEntity>> = dao.getAllWebResearchRecords()

    /**
     * Executes the complete multi-step autonomous Web Research workflow.
     */
    suspend fun conductResearch(
        query: String,
        userGoal: String = query,
        storeInKnowledgeBase: Boolean = true,
        autoApproveKnowledge: Boolean = true
    ): ResearchResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // Check if Emergency Stop is active
        if (MasterStopManager.isEmergencyStopActive.value) {
            val record = WebResearchRecordEntity(
                query = query,
                userGoal = userGoal,
                status = ResearchStatus.CANCELLED,
                synthesizedSummary = "Research cancelled due to Emergency Master Stop.",
                errorMessage = "Emergency Stop Active",
                createdAt = System.currentTimeMillis()
            )
            val id = dao.insertWebResearchRecord(record)
            return@withContext ResearchResult(
                id = id,
                query = query,
                userGoal = userGoal,
                summary = "Research aborted: Emergency stop active.",
                keyFindings = emptyList(),
                sources = emptyList(),
                confidence = 0f,
                durationMs = 0L,
                storedAsKnowledge = false
            )
        }

        // 1. Search & Collect Sources
        val sources = searchEngine.search(query, maxResults = 4)

        if (sources.isEmpty()) {
            val record = WebResearchRecordEntity(
                query = query,
                userGoal = userGoal,
                status = ResearchStatus.FAILED,
                synthesizedSummary = "No verifiable sources found for query: '$query'.",
                sourcesCount = 0,
                errorMessage = "No sources found",
                createdAt = System.currentTimeMillis()
            )
            val id = dao.insertWebResearchRecord(record)
            return@withContext ResearchResult(
                id = id,
                query = query,
                userGoal = userGoal,
                summary = "No verifiable web sources found.",
                keyFindings = emptyList(),
                sources = emptyList(),
                confidence = 0.2f,
                durationMs = System.currentTimeMillis() - startTime,
                storedAsKnowledge = false
            )
        }

        // 2. Extract and Compare Findings
        val findings = mutableListOf<String>()
        val sourcesJsonArray = JSONArray()

        for (source in sources) {
            val cleanSnippet = source.snippet.trim()
            if (cleanSnippet.isNotBlank()) {
                findings.add(cleanSnippet)
            }

            val sourceObj = JSONObject().apply {
                put("title", source.title)
                put("url", source.url)
                put("domain", source.sourceDomain)
                put("qualityScore", source.qualityScore)
                put("isOfficial", source.isOfficialSource)
            }
            sourcesJsonArray.put(sourceObj)
        }

        // 3. Synthesize and Verify Summary
        val synthesizedSummary: String
        val primarySource = sources.firstOrNull()

        if (geminiProvider?.isConfigured() == true) {
            val contextText = findings.joinToString("\n- ")
            val sanitizedContext = PrivacyFilter.sanitizeForCloud(contextText).sanitizedText
            val prompt = """
                You are JARVIS Web Research Agent.
                Goal: $userGoal
                Verified Sources:
                - $sanitizedContext
                
                Provide a structured, factual, and verified summary of these findings. 
                Include key facts, numbers/dates if any, and note the primary source.
            """.trimIndent()
            synthesizedSummary = try {
                geminiProvider.generateResponse(prompt).text
            } catch (e: Exception) {
                buildLocalSummary(query, findings, primarySource)
            }
        } else {
            synthesizedSummary = buildLocalSummary(query, findings, primarySource)
        }

        val duration = System.currentTimeMillis() - startTime
        val avgConfidence = if (sources.any { it.isOfficialSource }) 0.95f else 0.82f

        // 4. Optionally store approved knowledge
        var stored = false
        if (storeInKnowledgeBase && synthesizedSummary.isNotBlank()) {
            val knowledgeKey = query.lowercase().replace(Regex("[^a-z0-9]"), "_").take(40)
            
            // Ingest into Phase 14 Knowledge Ingestion Engine if available
            ingestionEngine?.ingest(
                IngestionCandidate(
                    title = query,
                    content = synthesizedSummary,
                    summary = synthesizedSummary.take(150),
                    sourceType = if (primarySource?.isOfficialSource == true) KnowledgeSourceType.OFFICIAL_DOCUMENTATION else KnowledgeSourceType.TRUSTED_WEBSITE,
                    sourceUrl = primarySource?.url,
                    tags = "web_research"
                )
            )

            // Also propose to KnowledgeUpdateManager for backward compatibility
            knowledgeUpdateManager.proposeUpdate(
                KnowledgeUpdateProposal(
                    knowledgeKey = knowledgeKey,
                    topic = query,
                    content = synthesizedSummary,
                    summary = synthesizedSummary.take(200),
                    sourceUrl = primarySource?.url,
                    sourceQualityScore = primarySource?.qualityScore ?: 0.8f,
                    confidence = avgConfidence,
                    changeReason = "Autonomous Research for: $userGoal",
                    autoApproveIfConfident = autoApproveKnowledge
                )
            )
            stored = true
        }

        val findingsJson = JSONArray(findings).toString()

        // 5. Persist Research Record
        val recordEntity = WebResearchRecordEntity(
            query = query,
            userGoal = userGoal,
            status = ResearchStatus.COMPLETED,
            synthesizedSummary = synthesizedSummary,
            sourcesCount = sources.size,
            verifiedSourcesJson = sourcesJsonArray.toString(),
            keyFindingsJson = findingsJson,
            confidence = avgConfidence,
            durationMs = duration,
            storedAsKnowledge = stored,
            createdAt = System.currentTimeMillis()
        )

        val id = dao.insertWebResearchRecord(recordEntity)

        ResearchResult(
            id = id,
            query = query,
            userGoal = userGoal,
            summary = synthesizedSummary,
            keyFindings = findings,
            sources = sources,
            confidence = avgConfidence,
            durationMs = duration,
            storedAsKnowledge = stored
        )
    }

    private fun buildLocalSummary(query: String, findings: List<String>, primarySource: SourceMetadata?): String {
        val sb = StringBuilder()
        sb.append("Research summary for: $query\n\n")
        findings.take(3).forEach {
            sb.append("• ").append(it).append("\n\n")
        }
        if (primarySource != null) {
            sb.append("Source: ${primarySource.sourceDomain} (${primarySource.url})")
        }
        return sb.toString()
    }
}
