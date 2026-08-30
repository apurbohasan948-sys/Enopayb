package com.example.core.knowledge

import com.example.data.local.dao.JarvisDao
import com.example.data.local.entity.KnowledgeGraphLinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KnowledgeGraph(
    private val dao: JarvisDao
) {
    suspend fun link(
        fromType: String,
        fromId: String,
        relation: String,
        toType: String,
        toId: String,
        weight: Float = 1.0f
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getGraphLinksFrom(fromType, fromId).find {
            it.relation == relation && it.toType == toType && it.toId == toId
        }
        if (existing == null) {
            dao.insertGraphLink(
                KnowledgeGraphLinkEntity(
                    fromType = fromType,
                    fromId = fromId,
                    relation = relation,
                    toType = toType,
                    toId = toId,
                    weight = weight
                )
            )
        }
    }

    suspend fun getRelatedNodes(fromType: String, fromId: String, relation: String? = null): List<KnowledgeGraphLinkEntity> = withContext(Dispatchers.IO) {
        val links = dao.getGraphLinksFrom(fromType, fromId)
        if (relation != null) {
            links.filter { it.relation == relation }
        } else {
            links
        }
    }

    suspend fun recordAppScreenSkillFlow(
        appPackage: String,
        screenContext: String,
        targetElement: String,
        actionName: String,
        skillName: String,
        experienceId: Long
    ) = withContext(Dispatchers.IO) {
        link("APP", appPackage, "CONTAINS_SCREEN", "SCREEN", screenContext)
        link("SCREEN", screenContext, "HAS_TARGET", "TARGET", targetElement)
        link("TARGET", targetElement, "TRIGGERS_ACTION", "ACTION", actionName)
        link("ACTION", actionName, "IMPLEMENTS_SKILL", "SKILL", skillName)
        link("SKILL", skillName, "RECORDED_IN", "EXPERIENCE", experienceId.toString())
    }
}
