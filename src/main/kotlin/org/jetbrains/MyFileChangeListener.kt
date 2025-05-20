package org.jetbrains

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class MyFileChangeListener(
    private val onFileSelected: (VirtualFile?) -> Unit
) : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        onFileSelected(event.newFile)
    }
}