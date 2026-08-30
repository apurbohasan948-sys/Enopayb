package com.example.core.knowledge

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.AppKnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class AppKnowledgeManager(
    private val dao: JarvisDao
) {
    val allAppKnowledge: Flow<List<AppKnowledgeEntity>> = dao.getAllAppKnowledge()

    suspend fun getAppKnowledge(packageName: String): AppKnowledgeEntity? = withContext(Dispatchers.IO) {
        dao.getAppKnowledgeByPackage(packageName)
    }

    suspend fun recordScreenObservation(
        packageName: String,
        appName: String,
        screenContext: String,
        semanticTarget: String?,
        version: String = "1.0"
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getAppKnowledgeByPackage(packageName)
        if (existing == null) {
            val screens = JSONArray().apply { put(screenContext) }
            val targets = JSONArray().apply { if (!semanticTarget.isNullOrBlank()) put(semanticTarget) }
            val entity = AppKnowledgeEntity(
                appName = appName,
                packageName = packageName,
                version = version,
                knownScreensJson = screens.toString(),
                semanticTargetsJson = targets.toString(),
                lastVerified = System.currentTimeMillis()
            )
            dao.insertAppKnowledge(entity)
        } else {
            val screens = parseJsonList(existing.knownScreensJson).toMutableSet().apply { add(screenContext) }
            val targets = parseJsonList(existing.semanticTargetsJson).toMutableSet().apply {
                if (!semanticTarget.isNullOrBlank()) add(semanticTarget)
            }

            val isVersionChanged = existing.version != version
            val updated = existing.copy(
                appName = appName,
                version = version,
                knownScreensJson = JSONArray(screens.toList()).toString(),
                semanticTargetsJson = JSONArray(targets.toList()).toString(),
                lastVerified = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isStale = isVersionChanged
            )
            dao.updateAppKnowledge(updated)
        }
    }

    suspend fun recordSkillOutcome(
        packageName: String,
        appName: String,
        skillName: String,
        isSuccess: Boolean,
        failedStrategy: String? = null,
        recoveryStrategy: String? = null
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getAppKnowledgeByPackage(packageName) ?: AppKnowledgeEntity(
            appName = appName,
            packageName = packageName,
            lastVerified = System.currentTimeMillis()
        )

        val successfulSkills = parseJsonList(existing.successfulSkillsJson).toMutableSet()
        val failedStrategies = parseJsonList(existing.failedStrategiesJson).toMutableSet()
        val recoveryStrategies = parseJsonList(existing.recoveryStrategiesJson).toMutableSet()

        if (isSuccess) {
            successfulSkills.add(skillName)
        } else {
            if (!failedStrategy.isNullOrBlank()) failedStrategies.add(failedStrategy)
            if (!recoveryStrategy.isNullOrBlank()) recoveryStrategies.add(recoveryStrategy)
        }

        val updated = existing.copy(
            successfulSkillsJson = JSONArray(successfulSkills.toList()).toString(),
            failedStrategiesJson = JSONArray(failedStrategies.toList()).toString(),
            recoveryStrategiesJson = JSONArray(recoveryStrategies.toList()).toString(),
            lastVerified = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        if (existing.id == 0L) {
            dao.insertAppKnowledge(updated)
        } else {
            dao.updateAppKnowledge(updated)
        }
    }

    suspend fun getRecoveryStrategiesForApp(packageName: String): List<String> = withContext(Dispatchers.IO) {
        val app = dao.getAppKnowledgeByPackage(packageName) ?: return@withContext emptyList()
        parseJsonList(app.recoveryStrategiesJson)
    }

    private fun parseJsonList(jsonStr: String): List<String> {
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
