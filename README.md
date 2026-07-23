# Language Interface for IntelliJ IDEA

<div align="center">
  <a href="#中文说明">中文</a> | <a href="#english">English</a>
</div>

## 中文说明

### 简介

<!-- Plugin description -->
AI Code Agent 通常可以读取当前项目的源码，但无法直接获取外部依赖或 JDK 中的 Java 类源码。因此，AI 在生成代码时经常会出现方法参数数量或类型错误，以及虚构类属性、方法等问题。本项目通过为 Code Agent 提供获取 Java 语法树与类型信息的能力来减少此类问题。

`language-interface` 插件利用 IntelliJ IDEA 的 PSI 语法树和项目索引，为 MCP 客户端提供以下工具：

- `searchJavaTypes`：通过名称或部分名称搜索项目、外部依赖和 JDK 中的 Java 类、接口或枚举。
- `getSourceCodeByFQN`：通过全限定名获取 Java 类型源码，并支持按方法名筛选。
<!-- Plugin description end -->

### 首次安装与设置

#### 1. 安装插件

1. 从 [GitHub Releases](https://github.com/saikaNya/language-interface/releases/latest) 下载最新的插件 ZIP。
2. 在 IntelliJ IDEA 中打开：
   <kbd>Settings/Preferences</kbd> → <kbd>Plugins</kbd> → <kbd>⚙️</kbd> →
   <kbd>Install Plugin from Disk...</kbd>。
3. 选择下载的 ZIP，安装后重启 IDEA。

插件支持 IntelliJ IDEA 2022.3.3 及更高版本。

#### 2. 配置 MCP 客户端

将以下内容添加到 Codex、Claude Code、Cursor、Claude Desktop、Windsurf、Cherry Studio 等客户端的 MCP 配置中。

**仅从 IDEA 获取 Java 类信息：**

```json
{
  "mcpServers": {
    "java-ides": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-server-for-language",
        "--ides",
        "idea"
      ]
    }
  }
}
```

**同时使用 IDEA 和 VSCode 系 IDE：**

如果平时会单独使用 IDEA 或 VSCode 系 IDE，并希望在 IDEA 不可用或未打开目标项目时尝试使用 VSCode，请先在 VSCode 系 IDE 中安装同系列插件
[MCP Server For Java](https://open-vsx.org/extension/saika/mcp-server-for-java)，然后使用以下配置：

```json
{
  "mcpServers": {
    "java-ides": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-server-for-language",
        "--ides",
        "idea",
        "vscode"
      ]
    }
  }
}
```

该配置会优先使用 IDEA；当 IDEA 不可用或未打开目标项目时，再尝试使用 VSCode。

#### 3. 打开 Java 项目

1. 使用 IDEA 打开需要查询的 Java 项目。
2. 等待 IDEA 完成项目加载和索引。
3. 重启或刷新 MCP 客户端，确认已加载以下工具：
   - `searchJavaTypes`
   - `getSourceCodeByFQN`

> 调用工具时，`workspacePath` 必须与 IDEA 中已打开项目的根目录一致。

### 提高工具调用率（推荐）

可以在 AI Agent 的用户规则或系统提示词中添加：

```text
当无法从项目源码中找到 Java 类、类定义或方法实现时，使用 searchJavaTypes 和 getSourceCodeByFQN 搜索依赖库或 JDK 中的类型及源码。
```

### 常见问题

- **提示无法连接 IDEA**：确认 IDEA 正在运行、插件已启用，并已打开 Java 项目。
- **提示 IDEA 正在索引**：等待索引完成后重试。
- **提示找不到工作区**：确认 MCP 客户端打开的目录与 IDEA 项目根目录一致。
- **自定义 IDEA 地址**：默认连接 `http://127.0.0.1:63342`，可在 MCP 参数中添加
  `--idea-base-url http://主机:端口`。

### 工作原理

本插件运行在 IDEA 内，通过 IDEA 的 PSI 和项目索引提供 Java 搜索能力；配套的
[`mcp-server-for-language`](https://github.com/saikaNya/mcp-server-for-java/tree/main/packages/multi-ide-relay)
负责接收 MCP 请求并转发给插件。

```text
MCP 客户端 → mcp-server-for-language → IDEA 插件 → Java 项目索引
```

---

## English

### Overview

AI Code Agents can usually read source code in the current project, but they cannot directly retrieve Java source code from external dependencies or the JDK. As a result, AI-generated code may use the wrong number or type of method arguments or invent class fields and methods. This project helps reduce these issues by giving Code Agents access to Java syntax-tree and type information.

The `language-interface` plugin uses IntelliJ IDEA's PSI syntax tree and project indexes to provide MCP clients with the following tools:

- `searchJavaTypes`: Searches for Java classes, interfaces, and enums by name or partial name across the project, external dependencies, and the JDK.
- `getSourceCodeByFQN`: Retrieves Java source code by fully qualified name, with optional method filtering.

### Installation and Setup

#### 1. Install the Plugin

1. Download the latest plugin ZIP from [GitHub Releases](https://github.com/saikaNya/language-interface/releases/latest).
2. In IntelliJ IDEA, open:
   <kbd>Settings/Preferences</kbd> → <kbd>Plugins</kbd> → <kbd>⚙️</kbd> →
   <kbd>Install Plugin from Disk...</kbd>.
3. Select the downloaded ZIP, install the plugin, and restart IDEA.

IntelliJ IDEA 2022.3.3 or later is required.

#### 2. Configure the MCP Client

Add the following configuration to Codex, Claude Code, Cursor, Claude Desktop, Windsurf, Cherry Studio, or another MCP client.

**Use IDEA only:**

```json
{
  "mcpServers": {
    "java-ides": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-server-for-language",
        "--ides",
        "idea"
      ]
    }
  }
}
```

**Use both IDEA and VSCode-based IDEs:**

If you use IDEA and VSCode-based IDEs separately and want to try VSCode when IDEA is unavailable or has not opened the target project, first install the companion
[MCP Server For Java](https://open-vsx.org/extension/saika/mcp-server-for-java)
extension in the VSCode-based IDE, then use the following configuration:

```json
{
  "mcpServers": {
    "java-ides": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-server-for-language",
        "--ides",
        "idea",
        "vscode"
      ]
    }
  }
}
```

This configuration uses IDEA first. It tries VSCode only when IDEA is unavailable or has not opened the target project.

#### 3. Open a Java Project

1. Open the Java project in IDEA.
2. Wait for IDEA to finish loading and indexing the project.
3. Restart or refresh the MCP client and confirm that the following tools are available:
   - `searchJavaTypes`
   - `getSourceCodeByFQN`

> The `workspacePath` passed to a tool must match the root directory of a project open in IDEA.

### Improve Tool Invocation Rate (Recommended)

Add the following instruction to your AI Agent's user rules or system prompt:

```text
When a Java class, class definition, or method implementation cannot be found in the project source, use searchJavaTypes and getSourceCodeByFQN to search dependencies or the JDK.
```

### Troubleshooting

- **Cannot connect to IDEA**: Make sure IDEA is running, the plugin is enabled, and a Java project is open.
- **IDEA is indexing**: Wait for indexing to finish and try again.
- **Workspace not found**: Make sure the directory open in the MCP client matches the IDEA project root.
- **Custom IDEA address**: The default address is `http://127.0.0.1:63342`. Add
  `--idea-base-url http://host:port` to the MCP arguments to override it.

### How It Works

The plugin runs inside IDEA and uses PSI and project indexes to provide Java search capabilities. The companion
[`mcp-server-for-language`](https://github.com/saikaNya/mcp-server-for-java/tree/main/packages/multi-ide-relay)
receives MCP requests and forwards them to the plugin.

```text
MCP client → mcp-server-for-language → IDEA plugin → Java project indexes
```
