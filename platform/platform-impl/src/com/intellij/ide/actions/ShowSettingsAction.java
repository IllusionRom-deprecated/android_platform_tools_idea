/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;

public class ShowSettingsAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    if (SystemInfo.isMac && e.getPlace().equals(ActionPlaces.MAIN_MENU)) {
      // It's called from Preferences in App menu.
      e.getPresentation().setVisible(false);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    final long startTime = System.nanoTime();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final long endTime = System.nanoTime();
        if (ApplicationManagerEx.getApplicationEx().isInternal()) {
          System.out.println("Displaying settings dialog took " + ((endTime - startTime) / 1000000) + " ms");
        }
      }
    });
    ShowSettingsUtil.getInstance().showSettingsDialog(project, new ProjectConfigurablesGroup(project),
                                                      new IdeConfigurablesGroup());
  }
}