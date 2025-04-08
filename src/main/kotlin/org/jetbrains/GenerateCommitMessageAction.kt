package org.jetbrains

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.Refreshable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = Logger.getInstance("GitPlugin")
private val API_TOKEN = BuildConfig.API_TOKEN
private val API_URL = BuildConfig.API_URL
private val REMOTE_API_URL = BuildConfig.REMOTE_API_URL

class GenerateCommitMessageAction : AnAction("Generate Commit Message") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val commitPanel = getCommitPanel(event)
        if (commitPanel == null) {
            showErrorDialog(project, "No commit message document found.")
            return
        }

        commitPanel.setCommitMessage("")
        var msg: String? = null
        repeat(5) {
            msg = runCatching { getChangedMessage(project) }.getOrNull()
            if (msg?.isNotBlank() == true) return@repeat
        }

        if (msg.isNullOrBlank()) {
            showErrorDialog(project, "No changes found.")
        }


        commitPanel.setCommitMessage(msg)
    }

    private fun generateCommitMessage(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        return "chore: Auto-generated commit on $timestamp"
    }

    private fun showErrorDialog(project: Project?, message: String) {
        Messages.showMessageDialog(
            project,
            message,
            "Error",
            Messages.getErrorIcon()
        )
    }

    private fun getCommitPanel(event: AnActionEvent?): CommitMessageI? {
        if (event == null) {
            return null
        }
        val data: Refreshable? = Refreshable.PANEL_KEY.getData(event.dataContext)
        if (data is CommitMessageI) {
            return data
        }
        return VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(event.dataContext)
    }

    private fun getChangedMessage(project: Project): String? {
        val selectedChanges = getIncludedCheckedChangesFromCommit(project) ?: return null

        return selectedChanges
            .asSequence()
            .filterNot { it.shouldIgnoreFile() }
            .mapNotNull { file ->
                val before = file.beforeRevision?.content
                val after = file.afterRevision?.content
                val path = file.virtualFile?.canonicalPath

                if (before == null || after == null || path == null) return@mapNotNull null

                val rawMessage = getDiffMessageRepeat(path, before, after)
                if (!rawMessage.isNullOrBlank()) {
                    return@mapNotNull "$path:\n$rawMessage"
                }

                val (trimmedBefore, trimmedAfter) = trimDiffPair(before, after)

                getDiffMessageRepeat(path, trimmedBefore, trimmedAfter)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$path:\n$it" }
            }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n\n")
    }

    private fun trimDiffPair(before: String, after: String): Pair<String, String> {
        val beforeChunks = splitIntoChunks(before, 100).toMutableList()
        val afterChunks = splitIntoChunks(after, 100).toMutableList()

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
        val message = buildString {
            appendLine("generate a short simple git commit message based on my below code changes:")
            appendLine("my file located at:\n$path")
            appendLine("my code before changes:\n${beforeContent.trim()}")
            appendLine("my code after changes:\n${afterContent.trim()}")
        }
        return completions_remote(message.trim())
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

    fun completions(content: String): String? {
        val client = OkHttpClient()
        val gson = Gson()
        val mediaType = "application/json".toMediaType()

        val requestModel = RequestModel(
            stream = false,
            model = "chatgpt-4o-latest",
            messages = listOf(Message(role = "user", content = content)),
            features = Features(imageGeneration = false, codeInterpreter = false, webSearch = false),
            modelItem = ModelItem(id = "chatgpt-4o-latest", `object` = "model", name = "chatgpt-4o-latest", urlIdx = 0)
        )

        val jsonString = gson.toJson(requestModel)

        val request = Request.Builder()
            .url(API_URL)
            .post(jsonString.toRequestBody(mediaType))
            .addHeader("accept", "application/json")
            .addHeader("content-type", "application/json")
            .addHeader(
                "authorization",
                "Bearer $API_TOKEN"
            )
            .addHeader("content-type", "application/json")
            .addHeader(
                "Cookie",
                "token=$API_TOKEN"
            )
            .build()

        val response = client.newCall(request).execute()
        val jsonResponse = response.body.string()
        val chatResponse = gson.fromJson(jsonResponse, ChatResponse::class.java)
        return chatResponse?.choices?.firstOrNull()?.message?.content
    }

    fun completions_remote(content: String): String? {
        val client = OkHttpClient()
        val gson = Gson()
        val mediaType = "application/json".toMediaType()

        data class RequestBody(
            val content: String,
            val api_url: String,
            val api_token: String
        )

        data class ApiResponse(
            val response: String?,
            val error: String?
        )

        val requestBody = RequestBody(
            content = content,
            api_url = API_URL,
            api_token = API_TOKEN
        )
        val jsonString = gson.toJson(requestBody)
        try {
            val request = Request.Builder()
                .url(REMOTE_API_URL)
                .post(jsonString.toRequestBody(mediaType))
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val apiResponse: ApiResponse? = gson.fromJson(response.body.string(), ApiResponse::class.java)
            return apiResponse?.response
        } catch (e: Exception) {
            return null
        }
    }
}

fun splitIntoChunks(text: String, chunkSize: Int = 50): List<String> {
    return text.lines().chunked(chunkSize).map { it.joinToString("\n") }
}

