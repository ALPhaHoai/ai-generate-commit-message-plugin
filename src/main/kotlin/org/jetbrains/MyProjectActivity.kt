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
import java.io.IOException
import java.net.URI
import com.intellij.openapi.components.service

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val url = URI(BuildConfig.API_URL).toURL()
        val baseUrl = "${url.protocol}://${url.host}"

        try {
            // Do HTTP request on IO dispatcher
            val statusCode = withContext(Dispatchers.IO) {
                getStatusCode(baseUrl)
            }

            service<PluginSettingsService>().useLocalModel = (statusCode == 200)

            println("🌐 Website status code for $baseUrl: $statusCode")

            // Show a notification on the UI
            Notifications.Bus.notify(
                Notification(
                    "GitCommitGenerator",
                    "🎯 HTTP Check Complete",
                    "Status code from $baseUrl: $statusCode",
                    NotificationType.INFORMATION
                ),
                project
            )

        } catch (e: IOException) {
            println("❌ Failed to check website: ${e.message}")

            Notifications.Bus.notify(
                Notification(
                    "GitCommitGenerator",
                    "❌ HTTP Request Failed",
                    "Error checking $baseUrl: ${e.message}",
                    NotificationType.ERROR
                ),
                project
            )
        }
    }

    private fun getStatusCode(url: String): Int {
        val request = Request.Builder()
            .url(url)
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            return response.code
        }
    }
}