package org.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.awt.Container

fun getSelectedChangesInCommitToolWindow(project: Project): List<Change> {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Commit")
        ?: return emptyList()

    val component = toolWindow.component ?: return emptyList()

    // Traverse UI components to find ChangesListView inside the Commit toolWindow
    val changesListView = findChangesListView(component) ?: return emptyList()

    return changesListView.selectedChanges.toList()
}

private fun findChangesListView(component: java.awt.Component): ChangesListView? {
    if (component is ChangesListView) {
        return component
    }
    if (component is java.awt.Container) {
        for (child in component.components) {
            val result = findChangesListView(child)
            if (result != null) return result
        }
    }
    return null
}

fun addChangeListListener(project: Project, onChanged: (changes: List<Change>?) -> Unit) {
    val commitToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Commit") ?: run {
        return
    }
    val rootComponent = commitToolWindow.component
    val changesTree = findChangesTree(rootComponent) ?: run {
        return
    }
    val changesListView = findChangesListView(changesTree) ?: run {
        return
    }
    changesListView.inclusionModel.addInclusionListener(object : InclusionListener {
        override fun inclusionChanged() {
            val inclusion = changesListView.inclusionModel.getInclusion()
            val changes = inclusion.filterIsInstance<Change>()
            onChanged(changes)
        }
    })
}


fun getIncludedCheckedChangesFromCommit(project: Project): List<Change> {
    val commitToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Commit") ?: return emptyList()
    val rootComponent = commitToolWindow.component ?: return emptyList()

    val changesTree = findChangesTree(rootComponent) ?: return emptyList()

    // ✅ Get all included (checked) items and filter for file-level changes
    return changesTree.inclusionModel.getInclusion().filterIsInstance<Change>()
}

private fun findCommitDialogChangesBrowser(component: Component): CommitDialogChangesBrowser? {
    if (component is CommitDialogChangesBrowser) {
        return component
    }
    if (component is Container) {
        for (child in component.components) {
            val result = findCommitDialogChangesBrowser(child)
            if (result != null) return result
        }
    }
    return null
}

fun printComponentTree(component: Component?, level: Int = 0) {
    if (component == null) return

    val indent = " ".repeat(level * 2)
    println("$indent${component::class.java.name}")

    if (component is Container) {
        component.components.forEach { child ->
            printComponentTree(child, level + 1)
        }
    }
}

private fun findChangesTree(component: Component?): ChangesTree? {
    if (component == null) return null

    if (component is ChangesTree) {
        return component
    }

    if (component is Container) {
        for (child in component.components) {
            val result = findChangesTree(child)
            if (result != null) return result
        }
    }

    return null
}

fun Change.shouldIgnoreFile(): Boolean {
    val name = this.virtualFile?.name ?: return false
    return listOf(".map", ".json").any { name.endsWith(it) }
}

public fun trimDiffPair(before: String, after: String): Pair<String, String> {
    val beforeChunks = splitIntoChunks(before.replace("\n\n", "\n"), 100).toMutableList()
    val afterChunks = splitIntoChunks(after.replace("\n\n", "\n"), 100).toMutableList()

    fun removeBlank() {
        // Remove leading blank lines
        while (beforeChunks.firstOrNull()?.isBlank() == true) {
            beforeChunks.removeFirst()
        }
        while (afterChunks.firstOrNull()?.isBlank() == true) {
            afterChunks.removeFirst()
        }

        // Remove trailing blank lines
        while (beforeChunks.lastOrNull()?.isBlank() == true) {
            beforeChunks.removeLast()
        }
        while (afterChunks.lastOrNull()?.isBlank() == true) {
            afterChunks.removeLast()
        }
    }

    removeBlank()

    // Remove common leading lines
    while (
        beforeChunks.isNotEmpty() &&
        afterChunks.isNotEmpty() &&
        beforeChunks.first().trim() == afterChunks.first().trim()
    ) {
        beforeChunks.removeFirst()
        afterChunks.removeFirst()

        removeBlank()
    }

    // Remove common trailing lines
    while (
        beforeChunks.isNotEmpty() &&
        afterChunks.isNotEmpty() &&
        beforeChunks.last().trim() == afterChunks.last().trim()
    ) {
        beforeChunks.removeLast()
        afterChunks.removeLast()

        removeBlank()
    }

    return beforeChunks.joinToString("\n") to afterChunks.joinToString("\n")
}
