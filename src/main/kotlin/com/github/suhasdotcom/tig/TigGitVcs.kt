package com.github.suhasdotcom.tig

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import git4idea.GitVcs

class TigGitVcs(project: Project) : AbstractVcs(project, ID) {

    companion object {
        const val ID = "TigGit" // Unique ID for your VCS
        fun getKey(): VcsKey = createKey(ID);

        val getInstance = GitVcs::getInstance
    }
    private val gitVcs: GitVcs = GitVcs(project)
    override fun getDisplayName(): String = "Tig"

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
