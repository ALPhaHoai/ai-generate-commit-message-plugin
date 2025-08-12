package org.jetbrains

import com.intellij.openapi.components.*

@State(
    name = "MyPluginSettings",
    storages = [Storage("MyPluginSettings.xml")]
)
@Service(Service.Level.APP)
class PluginSettingsService : PersistentStateComponent<MyPluginState> {
    private var state = MyPluginState()

    override fun getState(): MyPluginState = state

    override fun loadState(state: MyPluginState) {
        this.state = state
    }

    companion object {
        fun getInstance(): PluginSettingsService =
            service()
    }
}

data class MyPluginState(
    var useLocalModel: Boolean = false,
    var apiToken: String? = null,
    var selectedModel: String = "gpt-5-chat-latest",
    var models: List<ModelInfo> = emptyList()
)