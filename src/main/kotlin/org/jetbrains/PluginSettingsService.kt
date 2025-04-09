package org.jetbrains

import com.intellij.openapi.components.Service

@Service
class PluginSettingsService {
    var useLocalModel: Boolean = false
}
