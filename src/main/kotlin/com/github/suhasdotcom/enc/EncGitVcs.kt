package com.github.suhasdotcom.enc

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import git4idea.GitVcs

class EncGitVcs(project: Project) : AbstractVcs(project, ID) {

    companion object {
        const val ID = "EncGit" // Unique ID for your VCS
        fun getKey(): VcsKey = createKey(ID);
    }
    private val gitVcs: GitVcs = GitVcs(project)
    override fun getDisplayName(): String = "Enc"

    // Delegate overridden methods to gitVcs
    override fun getCheckinEnvironment() = gitVcs.checkinEnvironment
    override fun getDiffProvider() = gitVcs.diffProvider
    override fun getMergeProvider() = gitVcs.mergeProvider
    override fun getAnnotationProvider() = gitVcs.annotationProvider
    override fun getCommittedChangesProvider() = gitVcs.committedChangesProvider
    override fun getVcsHistoryProvider() = gitVcs.vcsHistoryProvider
    override fun getUpdateEnvironment() = gitVcs.updateEnvironment
    override fun getStatusEnvironment() = gitVcs.statusEnvironment

    override fun getType(): VcsType = gitVcs.type

    // Delegate any other methods as required
}
