package com.github.saikanya.languageinterface.api

import com.intellij.openapi.project.ProjectManager
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.HttpRequestHandler

/**
 * 获取当前 IDEA 中所有已打开的工作区目录
 *
 * GET /api/language-interface/workspaces
 *
 * @author saika
 */
class GetWorkspacesHandler : HttpRequestHandler() {

    companion object {
        private const val PATH_PREFIX = "/api/language-interface/workspaces"
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

        val projects = ProjectManager.getInstance().openProjects.filter { !it.isDefault }

        if (projects.isEmpty()) {
            ApiResponse.error(context, ApiResponse.PROJECT_NOT_FOUND, "No open project found")
            return true
        }

        val workspaces = projects.map { project ->
            mapOf(
                "name" to project.name,
                "path" to (project.basePath ?: ""),
            )
        }

        ApiResponse.success(context, mapOf("total" to workspaces.size, "workspaces" to workspaces))
        return true
    }
}
