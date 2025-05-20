package org.jetbrains

import com.intellij.icons.*
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.*
import javax.swing.Icon
import javax.swing.JLabel

data class FilePreview(val name: String, val icon: Icon?)

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
        val defaultModel = settings.state.selectedModel.takeIf { it in models } ?: "gpt-4.1"
        val selectedModelProperty = propertyGraph.property(defaultModel)

        val fileNameProperty = propertyGraph.property("No file selected")
        val fileIconProperty = propertyGraph.property(AllIcons.General.Warning)

        // Listen for editor tab changes
        setupFileChangeListener(project) { filePreview ->
            fileNameProperty.set(filePreview.name)
            fileIconProperty.set(filePreview.icon ?: AllIcons.General.Warning)
        }

        // Persist selection
        selectedModelProperty.afterChange {
            settings.state.selectedModel = it
        }

        val iconLabel = JLabel(fileIconProperty.get())

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

            panel {
                group("File Info") {
                    row {
                        cell(iconLabel)
                        label(fileNameProperty.get())
                            .bindText(fileNameProperty)
                    }
                }
            }
        }

        fileIconProperty.afterChange {
            iconLabel.icon = fileIconProperty.get()
        }

        val content = ContentFactory.getInstance().createContent(contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

fun setupFileChangeListener(project: Project, onChange: (FilePreview) -> Unit) {
    val connection = project.messageBus.connect()
    connection.subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
            override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                val file: VirtualFile? = event.newFile
                onChange(
                    if (file != null)
                        FilePreview(file.name, file.fileType.icon)
                    else
                        FilePreview("No file selected", AllIcons.General.Warning)
                )
            }
        }
    )
}