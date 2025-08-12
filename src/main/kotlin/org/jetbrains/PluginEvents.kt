package org.jetbrains

import com.intellij.util.messages.Topic

/**
 * Listener for when PluginSettingsService.models changes.
 */
interface PluginSettingsListener {
    fun modelsChanged(newModels: List<ModelInfo>)
}

/**
 * Global event topic for model list changes.
 *
 * Listen with:
 *   project.messageBus.connect().subscribe(MODELS_CHANGED_TOPIC, listener)
 *
 * Fire with:
 *   ApplicationManager.getApplication().messageBus.syncPublisher(MODELS_CHANGED_TOPIC).modelsChanged(list)
 */
val MODELS_CHANGED_TOPIC: Topic<PluginSettingsListener> = Topic.create(
    "Models Changed",
    PluginSettingsListener::class.java
)