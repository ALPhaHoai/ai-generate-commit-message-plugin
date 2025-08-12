package org.jetbrains

import com.google.gson.annotations.SerializedName


// Define data classes to match the JSON structure
data class RequestModel(
    val stream: Boolean,
    val model: String,
    val messages: List<Message>,
    val features: Features,
    @SerializedName("model_item") val modelItem: ModelItem
)

data class Message(
    val role: String,
    val content: String
)

data class Features(
    @SerializedName("image_generation") val imageGeneration: Boolean,
    @SerializedName("code_interpreter") val codeInterpreter: Boolean,
    @SerializedName("web_search") val webSearch: Boolean
)

data class ModelItem(
    val id: String,
    val `object`: String,
    val name: String,
    val urlIdx: Int
)


// Define data classes to match the JSON structure
data class ChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage,
    @SerializedName("service_tier") val serviceTier: String,
    @SerializedName("system_fingerprint") val systemFingerprint: String
)

data class Choice(
    val index: Int,
    val message: Message,
    val logprobs: Any?,
    @SerializedName("finish_reason") val finishReason: String
)

data class ChatCompletionChunk(
    val choices: List<ChunkChoice>
)

data class ChunkChoice(
    val delta: DeltaContent?
)

data class DeltaContent(
    val content: String?
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int,
    @SerializedName("prompt_tokens_details") val promptTokensDetails: TokenDetails,
    @SerializedName("completion_tokens_details") val completionTokensDetails: TokenDetails
)

data class TokenDetails(
    @SerializedName("cached_tokens") val cachedTokens: Int?,
    @SerializedName("audio_tokens") val audioTokens: Int?,
    @SerializedName("reasoning_tokens") val reasoningTokens: Int?,
    @SerializedName("accepted_prediction_tokens") val acceptedPredictionTokens: Int?,
    @SerializedName("rejected_prediction_tokens") val rejectedPredictionTokens: Int?
)

data class SignInRequest(
    val email: String,
    val password: String
)

data class SignInResponse(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("name") val name: String,
    @SerializedName("role") val role: String,
    @SerializedName("profile_image_url") val profileImageUrl: String,
    @SerializedName("token") val token: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_at") val expiresAt: String?,
    @SerializedName("permissions") val permissions: Permissions
)

data class Permissions(
    @SerializedName("workspace") val workspace: Workspace,
    @SerializedName("chat") val chat: Chat,
    @SerializedName("features") val features: Features
)

data class Workspace(
    @SerializedName("models") val models: Boolean,
    @SerializedName("knowledge") val knowledge: Boolean,
    @SerializedName("prompts") val prompts: Boolean,
    @SerializedName("tools") val tools: Boolean
)

data class Chat(
    @SerializedName("controls") val controls: Boolean,
    @SerializedName("file_upload") val fileUpload: Boolean,
    @SerializedName("delete") val delete: Boolean,
    @SerializedName("edit") val edit: Boolean,
    @SerializedName("temporary") val temporary: Boolean
)

data class AuthResponse(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    @SerializedName("profile_image_url") val profileImageUrl: String,
    val token: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_at") val expiresAt: String?, // null in your case
    val permissions: Permissions
)

data class RemoteRequestBody(
    val action: String? = null,
    val content: String? = null,
    val api_url: String? = null,
    val api_token: String? = null,
    val email: String? = null,
    val password: String? = null,
)
data class RemoteApiResponse(
    val response: String?,
    val error: String?
)

data class ErrorResponse(val detail: String)

data class ModelsResponse(
    val data: List<ModelInfo>
)

data class ModelInfo(
    val id: String,
    val name: String,
    val owned_by: String?,
    val created: Long?,
    val info: ModelDetail?
)

data class ModelDetail(
    val id: String,
    val name: String,
    val meta: ModelMeta?
)

data class ModelMeta(
    val description: String?,
    val capabilities: Capabilities?
)

data class Capabilities(
    val vision: Boolean?,
    val citations: Boolean?
)