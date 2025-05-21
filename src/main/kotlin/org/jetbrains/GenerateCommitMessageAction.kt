package org.jetbrains

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.ui.EditorTextField
import javax.swing.JComponent
import javax.swing.text.JTextComponent

private val logger = Logger.getInstance("GitCommitMessagePlugin")

class GenerateCommitMessageAction : AnAction("Generate Commit Message") {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val commitPanel = event.getCommitPanel()
        if (commitPanel == null) {
            showErrorDialog(project, "No commit message document found.")
            return
        }

        commitPanel.commitMessage = ""
        var errorShow = false

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Commit Message", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing code changes and generating message..."

                val selectedChanges = getIncludedCheckedChangesFromCommit(project)
                val commitMessage = StringBuilder()
                val changes = selectedChanges.filterNot { it.shouldIgnoreFile() }

                if(changes.isNotEmpty()){
                    indicator.text = "Processing ${changes.size} file(s)..."
                    val latch = java.util.concurrent.CountDownLatch(1)
                    generateCommitMessageWithContext(
                        files = changes,
                        apiToken = PluginSettingsService.getInstance().state.apiToken ?: "",
                        useProxy = !PluginSettingsService.getInstance().state.useLocalModel,
                        // WHENEVER DATA ARRIVES
                        onMessage = { chunk: String ->
                            commitMessage.append(chunk)
                            ApplicationManager.getApplication().invokeLater {
                                commitPanel.appendCommitMessage(chunk)
                            }
                        },
                        onComplete = { finalResult: String? ->
                            latch.countDown()
                        },
                        onFailure = { error: String ->
                            if (!errorShow) {
                                errorShow = true
                                showErrorDialog(project, error)
                            }
                            latch.countDown()
                        }
                    )
                    latch.await()
                } else {
                    showErrorDialog(project, "No changes found.")
                }
            }
        })
    }

    private fun showErrorDialog(project: Project?, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showMessageDialog(
                project,
                message,
                "Error",
                Messages.getErrorIcon()
            )
        }
    }



}

fun splitIntoChunks(text: String, chunkSize: Int = 50): List<String> {
    return text.lines().chunked(chunkSize).map { it.joinToString("\n") }
}

/** Helper: recursively search for first EditorTextField or JTextComponent */
fun findEditorTextField(comp: JComponent): Any? {
    if (comp is EditorTextField) return comp
    if (comp is JTextComponent) return comp
    for (child in comp.components) {
        if (child is JComponent) {
            val found = findEditorTextField(child)
            if (found != null) return found
        }
    }
    return null
}

fun CheckinProjectPanel.appendCommitMessage(appendText: String) {
    val editorTextField = findEditorTextField(this.component)
    if (editorTextField is EditorTextField) {
        val oldText = editorTextField.text ?: ""
        val newText = oldText + appendText

        // Try to preserve (and restore) selection/caret
        val editor = editorTextField.editor
        val caretOffset: Int
        val selectionStart: Int
        val selectionEnd: Int

        if (editor != null) {
            val caretModel = editor.caretModel
            val selectionModel = editor.selectionModel
            caretOffset = caretModel.offset
            selectionStart = selectionModel.selectionStart
            selectionEnd = selectionModel.selectionEnd
        } else {
            caretOffset = -1
            selectionStart = -1
            selectionEnd = -1
        }

        // Set the new text
        editorTextField.text = newText

        if (editor != null && caretOffset <= oldText.length && selectionStart <= oldText.length && selectionEnd <= oldText.length) {
            // Restore caret and selection - only valid if they were positioned BEFORE the appended area!
            editor.caretModel.moveToOffset(caretOffset)
            if (selectionStart != selectionEnd) {
                editor.selectionModel.setSelection(selectionStart, selectionEnd)
            } else {
                editor.selectionModel.removeSelection()
            }
        } else {
            // Fallback: caret to end, no selection
            editorTextField.setCaretPosition(newText.length)
            editorTextField.removeSelection()
        }
    } else {
        // fallback to public API (will select all)
        this.setCommitMessage(this.commitMessage + appendText)
    }
}

fun AnActionEvent.getCommitPanel(): CheckinProjectPanel? {
    val data = Refreshable.PANEL_KEY.getData(this.dataContext)
    return when {
        data is CheckinProjectPanel -> data
        else -> VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(this.dataContext) as? CheckinProjectPanel
    }
}