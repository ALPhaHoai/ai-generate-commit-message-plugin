package org.jetbrains

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
}