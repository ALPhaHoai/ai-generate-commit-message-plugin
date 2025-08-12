package org.jetbrains

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val url = URI(BuildConfig.API_URL).toURL()
        val baseUrl = "${url.protocol}://${url.host}"
        val email = BuildConfig.EMAIL
        val password = BuildConfig.PASSWORD
        val settings = PluginSettingsService.getInstance()
        var apiToken = settings.state.apiToken

        val isLocal = withContext(Dispatchers.IO) {
            try {
                getStatusCode(baseUrl) == 200
            } catch (e: Exception) {
                notifyError(project, "Connection Failed", "Could not reach $baseUrl: ${e.message}")
                println("❌ Error checking $baseUrl: ${e.message}")
                false
            }
        }

        settings.state.useLocalModel = isLocal
        println("🌐 Server check: $baseUrl is ${if (isLocal) "available (200)" else "unavailable"}")

        if (isLocal) {
            notifyInfo(project, "Server Check Successful", "$baseUrl responded with status 200")
        }

        val isTokenMissing = apiToken.isNullOrBlank()
        val isTokenValid = !isTokenMissing && withContext(Dispatchers.IO) {
            auth(apiToken!!, !isLocal)
        }

        if (isTokenMissing || !isTokenValid) {
            notifyError(
                project,
                if (isTokenMissing) "Missing Token" else "Invalid Token",
                "Current token is ${if (isTokenMissing) "missing" else "invalid"}. Requesting a new one..."
            )

            val newToken = withContext(Dispatchers.IO) {
                login(email, password, !isLocal)
            }

            if (!newToken.isNullOrBlank()) {
                apiToken = newToken
                settings.state.apiToken = newToken
                notifyInfo(project, "Token Refreshed", "A new API token was successfully obtained.")
            } else {
                notifyError(project, "Token Error", "Failed to acquire a new API token.")
            }
        } else {
            notifyInfo(project, "Token Verified", "Your API token is valid and active.")
        }

        if (!apiToken.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                getModels(apiToken!!, !isLocal)
            }?.let { models ->
                if (models != settings.state.models) {
                    settings.state.models = models
                    project.messageBus.syncPublisher(MODELS_CHANGED_TOPIC).modelsChanged(models)
                }
            }
        }
    }

    private fun getStatusCode(url: String): Int {
        val request = Request.Builder().url(url).build()
        OkHttpClient().newCall(request).execute().use { response ->
            return response.code
        }
    }

    private fun notifyInfo(project: Project, title: String, content: String) {
        Notifications.Bus.notify(
            Notification("GitCommitGenerator", title, content, NotificationType.INFORMATION),
            project
        )
    }

    private fun notifyError(project: Project, title: String, content: String) {
        Notifications.Bus.notify(
            Notification("GitCommitGenerator", title, content, NotificationType.ERROR),
            project
        )
    }

    private fun setUpIcon(project: Project) {
        val activeIcon = IconLoader.getIcon("/icons/auto_generate_commit.svg", javaClass)
        val disabledIcon = IconLoader.getIcon("/icons/auto_generate_commit_disabled.svg", javaClass)

        val action = ActionManager.getInstance().getAction("com.example.autocommit.Action")

        val vcsManager = ProjectLevelVcsManager.getInstance(project)

        vcsManager.runAfterInitialization {
            val connection = project.messageBus.connect()
            var alreadyTriggered = false

            connection.subscribe(
                ToolWindowManagerListener.TOPIC,
                object : ToolWindowManagerListener {
                    override fun toolWindowShown(toolWindow: com.intellij.openapi.wm.ToolWindow) {
                        if (!alreadyTriggered && toolWindow.id == "Commit") {
                            alreadyTriggered = true
                            connection.disconnect()
                            addChangeListListener(project) { changes ->
                                println(changes?.size)
                                val presentation = action.templatePresentation
                                presentation.icon = if (changes.isNullOrEmpty()) disabledIcon else activeIcon
                                presentation.isEnabled = changes.isNullOrEmpty()
                            }
                        }
                    }
                }
            )
        }
    }
}