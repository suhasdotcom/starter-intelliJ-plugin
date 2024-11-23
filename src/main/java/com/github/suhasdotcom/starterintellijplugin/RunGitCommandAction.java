package com.github.suhasdotcom.starterintellijplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class RunGitCommandAction extends AnAction {
    static class TigGitHandler extends GitLineHandler {
        public TigGitHandler(@Nullable Project project, @NotNull File directory, @NotNull GitCommand command) {
            super(project, directory, command);
        }

        @Override
        public @NlsSafe String printableCommandLine() {
            return this.getExecutable().isLocal() ? this.unescapeCommandLine(this.myCommandLine.getCommandLineString("tig")) : this.unescapeCommandLine(this.myCommandLine.getCommandLineString((String)null));
        }

        private @NotNull String unescapeCommandLine(@NotNull String commandLine) {
            if (escapeNeeded(commandLine)) {
                return commandLine.replaceAll("\\^\\^\\^\\^", "^");
            }
            return commandLine;
        }

        private boolean escapeNeeded(@NotNull @NonNls String parameter) {
            return SystemInfo.isWindows && isCmd() && parameter.contains("^");
        }

        private boolean isCmd() {
            return StringUtil.toLowerCase(myCommandLine.getExePath()).endsWith("cmd"); //NON-NLS
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
            TigGitHandler gh = new TigGitHandler(project, new java.io.File(project.getBasePath()), GitCommand.STATUS);
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
