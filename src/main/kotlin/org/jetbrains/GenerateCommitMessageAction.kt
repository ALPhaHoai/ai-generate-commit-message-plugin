package org.jetbrains

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.Refreshable

private val logger = Logger.getInstance("GitCommitMessagePlugin")

class GenerateCommitMessageAction : AnAction("Generate Commit Message") {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val commitPanel = getCommitPanel(event)
        if (commitPanel == null) {
            showErrorDialog(project, "No commit message document found.")
            return
        }

        commitPanel.setCommitMessage("")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Commit Message", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing code changes and generating message..."
                var msg: String? = null
                repeat(5) {
                    msg = runCatching { getChangedMessage(project, indicator) }.getOrNull()
                    if (msg?.isNotBlank() == true) return@repeat
                }

                if (msg.isNullOrBlank()) {
                    showErrorDialog(project, "No changes found.")
                }

                ApplicationManager.getApplication().invokeLater {
                    commitPanel.setCommitMessage(msg)
                }
            }
        })
    }

    private fun showErrorDialog(project: Project?, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showMessageDialog(
                project,
                message,
                "Error",
                Messages.getErrorIcon()
            )
        }
    }

    private fun getCommitPanel(event: AnActionEvent?): CommitMessageI? {
        val data = event?.let { Refreshable.PANEL_KEY.getData(it.dataContext) }
        return when {
            data is CommitMessageI -> data
            else -> event?.let { VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(it.dataContext) }
        }
    }

    private fun getChangedMessage(project: Project, indicator: ProgressIndicator): String? {
        val selectedChanges = getIncludedCheckedChangesFromCommit(project) ?: return null

        val resultMessages = mutableListOf<String>()

        val changes = selectedChanges
            .filterNot { it.shouldIgnoreFile() }

        indicator.text = "Found ${changes.size} file(s) to process..."

        for ((index, file) in changes.withIndex()) {
            val path = file.virtualFile?.canonicalPath
            indicator.text = "Processing file ${index + 1} of ${changes.size}: ${file.virtualFile?.name ?: "Unknown"}"

            val before = file.beforeRevision?.content
            val after = file.afterRevision?.content

            if (before == null || after == null || path == null) {
                continue
            }

            var rawMessage = getDiffMessageRepeat(path, before, after)
            if (rawMessage.isNullOrBlank()) {
                val (trimmedBefore, trimmedAfter) = trimDiffPair(before, after)
                rawMessage = getDiffMessageRepeat(path, trimmedBefore, trimmedAfter)
            }

            if (!rawMessage.isNullOrBlank()) {
                resultMessages.add("$path:\n$rawMessage")
            }
        }

        return if (resultMessages.isNotEmpty()) resultMessages.joinToString("\n\n") else null
    }

    private fun trimDiffPair(before: String, after: String): Pair<String, String> {
        val beforeChunks = splitIntoChunks(before.replace("\n\n", "\n"), 100).toMutableList()
        val afterChunks = splitIntoChunks(after.replace("\n\n", "\n"), 100).toMutableList()

        fun removeBlank() {
            // Remove leading blank lines
            while (beforeChunks.firstOrNull()?.isBlank() == true) {
                beforeChunks.removeFirst()
            }
            while (afterChunks.firstOrNull()?.isBlank() == true) {
                afterChunks.removeFirst()
            }

            // Remove trailing blank lines
            while (beforeChunks.lastOrNull()?.isBlank() == true) {
                beforeChunks.removeLast()
            }
            while (afterChunks.lastOrNull()?.isBlank() == true) {
                afterChunks.removeLast()
            }
        }

        removeBlank()

        // Remove common leading lines
        while (
            beforeChunks.isNotEmpty() &&
            afterChunks.isNotEmpty() &&
            beforeChunks.first().trim() == afterChunks.first().trim()
        ) {
            beforeChunks.removeFirst()
            afterChunks.removeFirst()

            removeBlank()
        }

        // Remove common trailing lines
        while (
            beforeChunks.isNotEmpty() &&
            afterChunks.isNotEmpty() &&
            beforeChunks.last().trim() == afterChunks.last().trim()
        ) {
            beforeChunks.removeLast()
            afterChunks.removeLast()

            removeBlank()
        }

        return beforeChunks.joinToString("\n") to afterChunks.joinToString("\n")
    }


    private fun getDiffMessage(path: String, beforeContent: String, afterContent: String): String? {
        val message = arrayListOf<String>()
        message.add("generate a short simple git commit message based on my below code changes:")
        message.add("my file located at:\n$path")
        message.add("my code before changes:\n${beforeContent.trim()}")
        message.add("my code after changes:\n${afterContent.trim()}")

        val content = message.joinToString("\n\n\n\n\n").trim()
        if (PluginSettingsService.getInstance().state.useLocalModel) {
            logger.info("Using locally hosted ChatGPT model for commit message generation.")
            return completions(content, PluginSettingsService.getInstance().state.apiToken ?: "")
        } else {
            return completions_remote(content, PluginSettingsService.getInstance().state.apiToken ?: "")
        }
    }

    private fun getDiffMessageRepeat(path: String, beforeContent: String, afterContent: String): String? {
        var newCommitMessage: String? = null
        repeat(10) {
            newCommitMessage =
                runCatching { getDiffMessage(path, beforeContent, afterContent) }.getOrNull()
            if (newCommitMessage?.isNotEmpty() == true) {
                return newCommitMessage
            }
        }
        return null
    }


}

fun splitIntoChunks(text: String, chunkSize: Int = 50): List<String> {
    return text.lines().chunked(chunkSize).map { it.joinToString("\n") }
}

