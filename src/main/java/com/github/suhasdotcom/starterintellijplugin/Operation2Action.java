package com.github.suhasdotcom.starterintellijplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class Operation2Action extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            Messages.showInfoMessage(project, "Operation 2 executed successfully!", "Info");
        }
    }
}
