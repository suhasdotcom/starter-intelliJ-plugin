package com.github.suhasdotcom.starterintellijplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RunGitCommandAction extends AnAction {

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

            // Capture and display output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            Messages.showInfoMessage(project, output.toString(), "Git Command Output");
        } catch (Exception ex) {
            Messages.showErrorDialog("Failed to execute Git command: " + ex.getMessage(), "Error");
        }
    }
}
