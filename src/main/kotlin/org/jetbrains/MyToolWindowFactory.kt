package org.jetbrains

import com.intellij.icons.*
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.*
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JLabel
import com.intellij.openapi.diagnostic.Logger

data class FilePreview(val name: String, val icon: Icon?)

class MyToolWindowFactory : ToolWindowFactory {

    private val logger = Logger.getInstance(MyToolWindowFactory::class.java)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val settings = PluginSettingsService.getInstance()
        val propertyGraph = PropertyGraph()

        val modelsProperty = propertyGraph.property(settings.state.models.map { it.id })

        project.messageBus.connect().subscribe(
            MODELS_CHANGED_TOPIC,
            object : PluginSettingsListener {
                override fun modelsChanged(newModels: List<ModelInfo>) {
                    if (newModels.isNotEmpty()) {
                        modelsProperty.set(newModels.map { it.id })
                    }
                }
            }
        )

        val selectedModelProperty = propertyGraph.property(settings.state.selectedModel)

        val fileNameProperty = propertyGraph.property("No file selected")
        val fileIconProperty = propertyGraph.property(AllIcons.General.Warning)

        // Listen for editor tab changes
        setupFileChangeListener(project) { filePreview ->
            fileNameProperty.set(filePreview.name)
            fileIconProperty.set(filePreview.icon ?: AllIcons.General.Warning)
        }

        // Persist selection
        selectedModelProperty.afterChange { newValue ->
            val oldValue = settings.state.selectedModel
            logger.info("🔍 selectedModel changed from '$oldValue' to '$newValue'",Exception("Change stack trace"))
            settings.state.selectedModel = newValue
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
                    val combo = comboBox(modelsProperty.get()).bindItem(selectedModelProperty)

                    // React to property changes and update comboBox model dynamically
                    modelsProperty.afterChange { newList ->
                        val comboBox = combo.component
                        val model = comboBox.model as DefaultComboBoxModel<String>

                        // Save current selection to restore later
                        val currentSelection = selectedModelProperty.get()

                        // Temporarily remove ItemListener to avoid firing change
                        val listeners = comboBox.itemListeners.toList()
                        listeners.forEach { comboBox.removeItemListener(it) }

                        model.removeAllElements()
                        newList.forEach { model.addElement(it) }

                        // Restore selection if still valid
                        if (currentSelection != null && newList.contains(currentSelection)) {
                            comboBox.selectedItem = currentSelection
                        }

                        // Reattach listeners
                        listeners.forEach { comboBox.addItemListener(it) }
                    }
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

            row {
                button("List Open Files") {
                    val openFiles = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
                    val str = openFiles.joinToString(separator = "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n") { file ->
                        arrayListOf(
                            file.path,
                            (FileDocumentManager.getInstance().getDocument(file)?.text ?: VfsUtilCore.loadText(file)).trim()
                        ).joinToString("\n\n\n\n")
                    }

                    val stringSelection = StringSelection(str)
                    CopyPasteManager.getInstance().setContents(stringSelection)

                    Notifications.Bus.notify(
                        Notification("GitCommitGenerator", "Open Files", "Copied to clipboard", NotificationType.INFORMATION),
                        project
                    )
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