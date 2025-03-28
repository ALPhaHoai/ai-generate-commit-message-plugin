package org.jetbrains

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.Refreshable
import com.jetbrains.rd.util.first
import com.jetbrains.rd.util.firstOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = Logger.getInstance("GitPlugin")
private val API_TOKEN = ""
private val API_URL = "https://chatgpt.com/"

class GenerateCommitMessageAction : AnAction("Generate Commit Message") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project

        val commitPanel = getCommitPanel(event)
        if (commitPanel == null) {
            showErrorDialog(project, "No commit message document found.")
            return
        }
        val msg = getChangedMessage(project)
        if (msg == null) {
            showErrorDialog(project, "No changes  found.")
            return
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

    /** Retrieves all changed file paths from the selected commit files */
    private fun getChangedMessage(project: Project?): String? {
        project ?: return null
        val changeListManager = ChangeListManager.getInstance(project)

        val file = changeListManager.allChanges.firstOrNull() ?: return null
        val beforeContent = file.beforeRevision?.content  // Old file content
        val afterContent = file.afterRevision?.content  // New file content
        if (beforeContent != null && afterContent != null) {
            return getDiffMessage(beforeContent, afterContent)
        }


//        changeListManager.allChanges.forEachIndexed { index, file ->
//            val beforeContent = file.beforeRevision?.content  // Old file content
//            val afterContent = file.afterRevision?.content  // New file content
//
//            if (beforeContent != null && afterContent != null) {
//                println("File $index: BEFORE CHANGE:\n$beforeContent")
//                println("File $index: AFTER CHANGE:\n$afterContent")
//                println("------------------------------")
//            }
//        }

        return null

//        return changeListManager.allChanges
//            .mapNotNull { it.virtualFile?.path }
    }

    private fun getDiffMessage(beforeContent: String, afterContent: String): String? {
        return completions(
            "generate a short simple git commit message based on my below code changes:  \n  \n" +
                    "\n\n\n\n\n\nbefore changes:  \n${beforeContent.trim()}" +
                    "\n\n\n\nafter changes:  \n${afterContent.trim()}"
        )
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
        return chatResponse.choices.firstOrNull()?.message?.content
    }
}