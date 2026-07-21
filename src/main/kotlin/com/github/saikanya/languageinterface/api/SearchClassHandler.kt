package com.github.saikanya.languageinterface.api

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.HttpRequestHandler

/**
 * @author saika
 * @date 2024/6/30 17:00
 */
class SearchClassHandler : HttpRequestHandler() {

    companion object {
        private const val PATH_PREFIX = "/api/language-interface/search-class"
        private const val DEFAULT_LIMIT = 200
        private const val MAX_LIMIT = 200
        private val LOG = logger<SearchClassHandler>()
    }

    override fun isAccessible(request: HttpRequest): Boolean = true

    override fun isSupported(request: FullHttpRequest): Boolean {
        return request.method() == HttpMethod.POST && request.uri().contains(PATH_PREFIX)
    }

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): Boolean {
        if (!urlDecoder.path().startsWith(PATH_PREFIX)) return false

        val body = try {
            val content = request.content().toString(Charsets.UTF_8)
            JsonParser.parseString(content).asJsonObject
        } catch (e: Exception) {
            ApiResponse.error(context, ApiResponse.INVALID_JSON, "Invalid JSON body")
            return true
        }

        val name = body.get("name")?.takeIf { !it.isJsonNull }?.asString
        if (name.isNullOrBlank()) {
            ApiResponse.error(context, ApiResponse.MISSING_PARAM, "Missing required parameter: name")
            return true
        }

        val projectName = body.get("project")?.takeIf { !it.isJsonNull }?.asString
        val rawLimit = body.get("limit")?.takeIf { !it.isJsonNull }?.asInt ?: DEFAULT_LIMIT
        val limit = rawLimit.coerceIn(1, MAX_LIMIT)

        val matchModeRaw = body.get("matchMode")?.takeIf { !it.isJsonNull }?.asString
        val matchMode = when {
            matchModeRaw.isNullOrBlank() -> SearchClassMatchMode.STRICT
            matchModeRaw.equals("strict", ignoreCase = true) -> SearchClassMatchMode.STRICT
            matchModeRaw.equals("fuzzy", ignoreCase = true) -> SearchClassMatchMode.FUZZY
            else -> {
                ApiResponse.error(
                    context,
                    ApiResponse.MISSING_PARAM,
                    "Invalid matchMode, expected strict or fuzzy",
                )
                return true
            }
        }

        val hasOpenProject = ProjectManager.getInstance().openProjects.any { !it.isDefault }
        if (!hasOpenProject) {
            ApiResponse.error(context, ApiResponse.PROJECT_NOT_FOUND, "No open project found")
            return true
        }
        val project = PathUtils.findProject(projectName)
        if (project == null) {
            ApiResponse.error(
                context,
                ApiResponse.SPECIFIED_PROJECT_NOT_FOUND,
                "Specified project not found: $projectName",
            )
            return true
        }

        if (DumbService.isDumb(project)) {
            ApiResponse.error(context, ApiResponse.INDEXING, "Indexing in progress, please try again later")
            return true
        }

        val results = searchClasses(project, name, limit, matchMode)
        ApiResponse.success(context, mapOf("query" to name, "total" to results.size, "results" to results))
        return true
    }

    private fun searchClasses(
        project: Project,
        query: String,
        limit: Int,
        matchMode: SearchClassMatchMode,
    ): List<Map<String, Any>> {
        val resultList = mutableListOf<Map<String, Any>>()

        ApplicationManager.getApplication().runReadAction {
            val scope = GlobalSearchScope.allScope(project)
            val cache = PsiShortNamesCache.getInstance(project)
            val allNames = cache.allClassNames
            val matchingNames = when (matchMode) {
                SearchClassMatchMode.STRICT -> allNames.filter { it.equals(query, ignoreCase = true) }
                SearchClassMatchMode.FUZZY -> allNames.filter { it.contains(query, ignoreCase = true) }
            }

            val fqnToPaths = LinkedHashMap<String, MutableList<String>>()
            for (shortName in matchingNames) {
                val classes = cache.getClassesByName(shortName, scope)
                for (psiClass in classes) {
                    val fqn = psiClass.qualifiedName ?: continue
                    val path = psiClass.containingFile?.virtualFile?.path
                    val paths = fqnToPaths.getOrPut(fqn) { mutableListOf() }
                    if (path != null && path !in paths) {
                        paths.add(path)
                    }
                }
                if (fqnToPaths.size >= limit) break
            }

            for ((fqn, paths) in fqnToPaths.entries.take(limit)) {
                val entry = mutableMapOf<String, Any>("fqn" to fqn)
                if (paths.size > 1) {
                    entry["paths"] = paths
                }
                resultList.add(entry)
            }
        }

        return resultList
    }

}
