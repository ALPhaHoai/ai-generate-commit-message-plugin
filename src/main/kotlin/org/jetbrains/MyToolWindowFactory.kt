package org.jetbrains

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel

class MyToolWindowFactory : ToolWindowFactory {
    private val models = listOf(
        "deepseek-r1:7b",
        "o3-mini",
        "chatgpt-4o-latest",
        "deepseek-r1:1.5b",
        "gpt-4.1",
        "gpt-4.1-mini",
        "gpt-4.1-nano"
    )

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val settings = PluginSettingsService.getInstance()
        val propertyGraph = PropertyGraph()
        val defaultModel = settings.state.selectedModel?.takeIf { it in models } ?: "GPT-4.1"
        val selectedModelProperty = propertyGraph.property(defaultModel)

        // Persist selection
        selectedModelProperty.afterChange {
            settings.state.selectedModel = it
        }

        val contentPanel = panel {
            row {}.resizableRow()
            row {
                text("Multiline code completion")
                comment("<kbd>Alt</kbd> <kbd>Shift</kbd> <kbd>\\</kbd>")
            }
            row {
                text("Code generation in the editor")
                comment("<kbd>Ctrl</kbd> <kbd>\\</kbd>")
            }
            row {
                text("AI actions in the editor's context menu")
            }
            row {
                browserLink("All features", "https://www.jetbrains.com/")
            }
            row {}
            group("Model") {
                row {
                    comboBox(models).bindItem(selectedModelProperty)
                }
            }
        }

        val content = ContentFactory.getInstance().createContent(contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}