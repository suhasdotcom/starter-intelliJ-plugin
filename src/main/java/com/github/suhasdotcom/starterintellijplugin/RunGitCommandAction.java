package com.github.suhasdotcom.starterintellijplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class RunGitCommandAction extends AnAction {
    static class EncGitHandler extends GitLineHandler {
        public EncGitHandler(@Nullable Project project, @NotNull File directory, @NotNull GitCommand command) {
            super(project, directory, command);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("No active project found.", "Error");
            return;
        }

        try {
            // Example Git command
            ProcessBuilder processBuilder = new ProcessBuilder("git", "status");
            processBuilder.directory(new java.io.File(project.getBasePath()));
            Process process = processBuilder.start();
            EncGitHandler gh = new EncGitHandler(project, new java.io.File(project.getBasePath()), GitCommand.STATUS);
            String output = gh.printableCommandLine();
            // Capture and display output
//            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            StringBuilder output = new StringBuilder();
//            String line;s
//            while ((line = reader.readLine()) != null) {
//                output.append(line).append("\n");
//            }

            Messages.showInfoMessage(project, output.toString(), "Git Command Output");
        } catch (Exception ex) {
            Messages.showErrorDialog("Failed to execute Git command: " + ex.getMessage(), "Error");
        }
    }
}
