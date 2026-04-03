package com.github.saikanya.languageinterface.api

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.HttpRequestHandler

/**
 * @author saikanya
 * @date 2024/6/30 17:00
 */
class SearchClassHandler : HttpRequestHandler() {

    companion object {
        private const val PATH_PREFIX = "/api/language-interface/search-class"
        private const val DEFAULT_LIMIT = 200
        private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        private val LOG = logger<SearchClassHandler>()
    }

    override fun isAccessible(request: HttpRequest): Boolean = true

    override fun isSupported(request: FullHttpRequest): Boolean {
        return request.method() == HttpMethod.GET && request.uri().contains(PATH_PREFIX)
    }

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): Boolean {
        if (!urlDecoder.path().startsWith(PATH_PREFIX)) return false

        val params = urlDecoder.parameters()
        val name = params["name"]?.firstOrNull()
        if (name.isNullOrBlank()) {
            sendJson(context, mapOf("error" to "Missing required parameter: name"), HttpResponseStatus.BAD_REQUEST)
            return true
        }

        val projectName = params["project"]?.firstOrNull()
        val limit = params["limit"]?.firstOrNull()?.toIntOrNull() ?: DEFAULT_LIMIT

        val project = findProject(projectName)
        if (project == null) {
            sendJson(context, mapOf("error" to "No open project found"), HttpResponseStatus.NOT_FOUND)
            return true
        }

        if (DumbService.isDumb(project)) {
            sendJson(
                context,
                mapOf("error" to "Indexing in progress, please try again later"),
                HttpResponseStatus.SERVICE_UNAVAILABLE
            )
            return true
        }

        val results = searchClasses(project, name, limit)
        sendJson(context, mapOf("query" to name, "total" to results.size, "results" to results))
        return true
    }

    private fun findProject(projectName: String?): Project? {
        val projects = ProjectManager.getInstance().openProjects.filter { !it.isDefault }
        if (projects.isEmpty()) return null
        if (projectName.isNullOrBlank()) return projects.first()
        return projects.find { it.name == projectName } ?: projects.first()
    }

    private fun searchClasses(project: Project, query: String, limit: Int): List<Map<String, Any>> {
        val resultList = mutableListOf<Map<String, Any>>()

        ApplicationManager.getApplication().runReadAction {
            val scope = GlobalSearchScope.allScope(project)
            val cache = PsiShortNamesCache.getInstance(project)
            val allNames = cache.allClassNames
            val matchingNames = allNames.filter { it.contains(query, ignoreCase = true) }

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

    private fun sendJson(
        context: ChannelHandlerContext,
        data: Any,
        status: HttpResponseStatus = HttpResponseStatus.OK,
    ) {
        val json = gson.toJson(data)
        val bytes = json.toByteArray(Charsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes))
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.size)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        context.channel().writeAndFlush(response)
    }
}
