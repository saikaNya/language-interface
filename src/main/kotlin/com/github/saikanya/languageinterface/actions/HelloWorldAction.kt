package com.github.saikanya.languageinterface.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class HelloWorldAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            e.project,
            "Hello World! 这是一个 IntelliJ 插件 Demo。",
            "Hello World"
        )
    }
}
