package com.github.saikanya.languageinterface.api

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

/**
 * 路径归一化工具，兼容 Windows / WSL / Linux / macOS 格式
 *
 * @author saika
 */
object PathUtils {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private val WSL_MOUNT_PATTERN = Regex("^/mnt/([a-zA-Z])(/.*)?$")
    private val WSL_UNC_PATTERN = Regex("^//wsl\\$/[^/]+(/.*)?$")

    /**
     * 将任意格式的路径归一化为当前操作系统可识别的格式（使用 / 作为分隔符）
     *
     * 支持的输入格式:
     * - Windows:    C:\path\to\file  或  C:/path/to/file
     * - WSL UNC:    \\wsl$\Ubuntu\path  或  //wsl$/Ubuntu/path
     * - WSL mount:  /mnt/c/path/to/file
     * - Linux:      /home/user/path
     * - macOS:      /Users/user/path
     */
    fun normalizePath(path: String): String {
        var p = path.trim()
        if (p.isEmpty()) return p

        // \\wsl$\Distro\xxx  →  //wsl$/Distro/xxx
        if (p.startsWith("\\\\wsl\$") || p.startsWith("\\\\wsl$")) {
            p = p.replace('\\', '/')
        }

        // //wsl$/Distro/xxx: 在非 Windows 环境下提取实际路径
        if (p.startsWith("//wsl\$/") || p.startsWith("//wsl$/")) {
            p = p.replace('\\', '/')
            if (!isWindows) {
                val match = WSL_UNC_PATTERN.matchEntire(p)
                if (match != null) {
                    return match.groupValues[1].ifEmpty { "/" }
                }
            }
            return p
        }

        // /mnt/c/xxx → C:/xxx （仅在 Windows IDE 环境下转换）
        if (isWindows) {
            val wslMatch = WSL_MOUNT_PATTERN.matchEntire(p)
            if (wslMatch != null) {
                val drive = wslMatch.groupValues[1].uppercase()
                val rest = wslMatch.groupValues[2]
                return "$drive:$rest"
            }
        }

        // 统一反斜杠为正斜杠
        p = p.replace('\\', '/')

        return p
    }

    /**
     * 通过项目名称或项目路径查找已打开的项目
     *
     * 匹配优先级（仅当 [projectIdentifier] 非空时）:
     * 1. 精确名称匹配
     * 2. 归一化路径匹配（basePath）
     * 3. 路径末段作为名称匹配
     *
     * [projectIdentifier] 为空或空白时返回第一个已打开的非默认工程；若当前无任何已打开工程则返回 null。
     * 非空但未匹配到任何工程时返回 null（与「未传 project」不同，需由调用方返回错误码 SPECIFIED_PROJECT_NOT_FOUND）。
     */
    fun findProject(projectIdentifier: String?): Project? {
        val projects = ProjectManager.getInstance().openProjects.filter { !it.isDefault }
        if (projects.isEmpty()) return null
        if (projectIdentifier.isNullOrBlank()) return projects.first()

        // 1. 精确名称匹配
        projects.find { it.name == projectIdentifier }?.let { return it }

        // 2. 归一化路径匹配
        val normalized = normalizePath(projectIdentifier)
        projects.find { normalizePath(it.basePath ?: "") == normalized }?.let { return it }

        // 3. 路径末段作为名称匹配（处理传入完整路径但只想匹配名称的情况）
        val lastSegment = normalized.trimEnd('/').substringAfterLast('/')
        if (lastSegment.isNotEmpty() && lastSegment != normalized) {
            projects.find { it.name == lastSegment }?.let { return it }
        }

        return null
    }
}
