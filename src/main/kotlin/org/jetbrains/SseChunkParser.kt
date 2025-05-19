package org.jetbrains

import com.google.gson.Gson

class SseChunkParser {
    var responseDone = false
        private set
    private val responseContent = StringBuilder()
    private var previousContent: String? = null

    // Returns true if done, and returns the current response content if new content was added
    fun handleSseChunk(sseString: String): Pair<Boolean, String?> {
        var newContentAppended = false
        val lines = sseString.split('\n')
        for (lineRaw in lines) {

            println(lineRaw)

            var line = lineRaw
            if (line.isBlank()) continue

            if (!line.startsWith("data: ") && previousContent != null) {
                line = previousContent + line
                previousContent = null
            }
            if (!line.startsWith("data: ")) {
                previousContent = line
                continue
            }

            if (line.startsWith("data: [DONE]")) {
                responseDone = true
                break
            } else {
                try {
                    val jsonLine = line.removePrefix("data: ")
                    val chunk = Gson().fromJson(jsonLine, ChatCompletionChunk::class.java)
                    val content = chunk.choices.firstOrNull()?.delta?.content
                    if (!content.isNullOrEmpty()) {
                        responseContent.append(content)
                        newContentAppended = true
                    }
                } catch (e: Exception) {
                    previousContent = line
                }
            }
        }
        return Pair(responseDone, if (newContentAppended) responseContent.toString() else null)
    }

    fun getCompletion(): String = responseContent.toString()
}