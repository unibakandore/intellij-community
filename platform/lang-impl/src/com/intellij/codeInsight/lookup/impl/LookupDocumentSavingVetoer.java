/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LookupDocumentSavingVetoer implements FileDocumentSynchronizationVetoer {
  @Override
  public boolean maySaveDocument(@NotNull Document document) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (!project.isInitialized() || project.isDisposed()) {
        continue;
      }
      if (LookupManager.getInstance(project).getActiveLookup() != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean mayReloadFileContent(VirtualFile file, @NotNull Document document) {
    return true;
  }
}
