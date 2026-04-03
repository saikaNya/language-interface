package com.github.saikanya.languageinterface.api

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.HttpRequestHandler

/**
 * 通过 FQN 或文件路径获取类文件源代码内容
 *
 * GET /api/language-interface/class-content?fqn=com.example.MyClass&project=myProject
 * GET /api/language-interface/class-content?path=/path/to/MyClass.java&project=myProject
 * GET /api/language-interface/class-content?fqn=com.example.MyClass&methods=getName,setName
 *
 * @author saika
 */
class GetClassContentHandler : HttpRequestHandler() {

    companion object {
        private const val PATH_PREFIX = "/api/language-interface/class-content"
        private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        private val LOG = logger<GetClassContentHandler>()
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
        val fqn = params["fqn"]?.firstOrNull()
        val path = params["path"]?.firstOrNull()

        if (fqn.isNullOrBlank() && path.isNullOrBlank()) {
            sendJson(
                context,
                mapOf("error" to "Missing required parameter: fqn or path (at least one required)"),
                HttpResponseStatus.BAD_REQUEST,
            )
            return true
        }

        val projectName = params["project"]?.firstOrNull()
        val project = findProject(projectName)
        if (project == null) {
            sendJson(context, mapOf("error" to "No open project found"), HttpResponseStatus.NOT_FOUND)
            return true
        }

        if (DumbService.isDumb(project)) {
            sendJson(
                context,
                mapOf("error" to "Indexing in progress, please try again later"),
                HttpResponseStatus.SERVICE_UNAVAILABLE,
            )
            return true
        }

        val methodNames = params["methods"]?.firstOrNull()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }

        val result = if (!fqn.isNullOrBlank()) {
            getContentByFqn(project, fqn, methodNames)
        } else {
            getContentByPath(project, path!!, methodNames)
        }

        if (result == null) {
            val query = if (!fqn.isNullOrBlank()) "fqn=$fqn" else "path=$path"
            sendJson(context, mapOf("error" to "Class not found: $query"), HttpResponseStatus.NOT_FOUND)
            return true
        }

        sendJson(context, result)
        return true
    }

    private fun getContentByFqn(project: Project, fqn: String, methodNames: List<String>?): Map<String, Any>? {
        var result: Map<String, Any>? = null

        ApplicationManager.getApplication().runReadAction {
            val scope = GlobalSearchScope.allScope(project)
            val psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, scope) ?: return@runReadAction
            val file = psiClass.containingFile ?: return@runReadAction
            val virtualFile = file.virtualFile

            if (!methodNames.isNullOrEmpty()) {
                val (content, details) = filterContentByMethods(file.text, psiClass, methodNames)
                result = buildResultMap(fqn = psiClass.qualifiedName ?: fqn, path = virtualFile?.path, content = content, methods = details)
            } else {
                result = buildResultMap(fqn = psiClass.qualifiedName ?: fqn, path = virtualFile?.path, content = file.text)
            }
        }

        return result
    }

    private fun getContentByPath(project: Project, path: String, methodNames: List<String>?): Map<String, Any>? {
        var result: Map<String, Any>? = null

        ApplicationManager.getApplication().runReadAction {
            val url = if (path.contains("!/")) "jar://$path" else "file://$path"
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl(url) ?: return@runReadAction
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@runReadAction

            if (!methodNames.isNullOrEmpty()) {
                val psiClass = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java).firstOrNull()
                if (psiClass != null) {
                    val (content, details) = filterContentByMethods(psiFile.text, psiClass, methodNames)
                    result = buildResultMap(fqn = null, path = virtualFile.path, content = content, methods = details)
                } else {
                    result = buildResultMap(fqn = null, path = virtualFile.path, content = psiFile.text)
                }
            } else {
                result = buildResultMap(fqn = null, path = virtualFile.path, content = psiFile.text)
            }
        }

        return result
    }

    private data class MethodRange(val name: String, val startLine: Int, val endLine: Int, val isRequested: Boolean)

    private fun filterContentByMethods(
        text: String,
        psiClass: PsiClass,
        requestedMethods: List<String>,
    ): Pair<String, List<Map<String, Any>>> {
        val lines = text.split("\n")
        val requestedSet = requestedMethods.toSet()

        val lineStartOffsets = IntArray(lines.size)
        var offset = 0
        for (i in lines.indices) {
            lineStartOffsets[i] = offset
            offset += lines[i].length + 1
        }

        fun offsetToLine(off: Int): Int {
            for (i in lines.indices.reversed()) {
                if (off >= lineStartOffsets[i]) return i
            }
            return 0
        }

        fun collectMethods(cls: PsiClass): List<PsiMethod> {
            val result = mutableListOf<PsiMethod>()
            result.addAll(cls.methods)
            result.addAll(cls.constructors)
            for (inner in cls.innerClasses) {
                result.addAll(collectMethods(inner))
            }
            return result
        }

        val allMethods = collectMethods(psiClass)
            .distinctBy { it.textRange }
            .map { method ->
                MethodRange(
                    name = method.name,
                    startLine = offsetToLine(method.textRange.startOffset),
                    endLine = offsetToLine(maxOf(method.textRange.endOffset - 1, method.textRange.startOffset)),
                    isRequested = method.name in requestedSet,
                )
            }
            .sortedBy { it.startLine }

        val hiddenLines = mutableSetOf<Int>()
        val placeholders = mutableMapOf<Int, String>()

        for (method in allMethods) {
            if (!method.isRequested) {
                for (line in method.startLine..method.endLine) {
                    hiddenLines.add(line)
                }
                placeholders[method.startLine] =
                    "    // ... ${method.name}() [lines ${method.startLine + 1}-${method.endLine + 1}]"
            }
        }

        val methodDetails = allMethods
            .filter { it.isRequested }
            .map { mapOf<String, Any>("name" to it.name, "startLine" to (it.startLine + 1), "endLine" to (it.endLine + 1)) }

        val sb = StringBuilder()
        for (i in lines.indices) {
            if (i in hiddenLines) {
                if (i in placeholders) {
                    sb.appendLine(placeholders[i])
                }
            } else {
                sb.appendLine("${String.format("%4d", i + 1)}| ${lines[i]}")
            }
        }

        return sb.toString().trimEnd() to methodDetails
    }

    private fun buildResultMap(
        fqn: String?,
        path: String?,
        content: String,
        methods: List<Map<String, Any>>? = null,
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any>("content" to content)
        if (fqn != null) map["fqn"] = fqn
        if (path != null) map["path"] = path
        if (methods != null) map["methods"] = methods
        return map
    }

    private fun findProject(projectName: String?): Project? {
        val projects = ProjectManager.getInstance().openProjects.filter { !it.isDefault }
        if (projects.isEmpty()) return null
        if (projectName.isNullOrBlank()) return projects.first()
        return projects.find { it.name == projectName } ?: projects.first()
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
