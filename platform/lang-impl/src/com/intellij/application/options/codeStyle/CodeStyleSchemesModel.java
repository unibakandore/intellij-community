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

package com.intellij.application.options.codeStyle;

import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class CodeStyleSchemesModel {

  private final List<CodeStyleScheme> mySchemes = new ArrayList<CodeStyleScheme>();
  private CodeStyleScheme myGlobalSelected;
  private final CodeStyleSchemeImpl myProjectScheme;
  private final CodeStyleScheme myDefault;
  private final Map<CodeStyleScheme, CodeStyleSettings> mySettingsToClone = new HashMap<CodeStyleScheme, CodeStyleSettings>();

  private final EventDispatcher<CodeStyleSettingsListener> myDispatcher = EventDispatcher.create(CodeStyleSettingsListener.class);
  private final Project myProject;
  private boolean myUsePerProjectSettings;
  
  public final static String PROJECT_SCHEME_NAME = "Project";

  public CodeStyleSchemesModel(Project project) {
    myProject = project;
    myProjectScheme = new CodeStyleSchemeImpl(PROJECT_SCHEME_NAME, false, CodeStyleSchemes.getInstance().getDefaultScheme());
    reset();
    myDefault = CodeStyleSchemes.getInstance().getDefaultScheme();
  }

  public void selectScheme(final CodeStyleScheme selected, @Nullable Object source) {
    if (myGlobalSelected != selected && selected != myProjectScheme) {
      myGlobalSelected = selected;
      myDispatcher.getMulticaster().currentSchemeChanged(source);
    }
  }

  public void addScheme(final CodeStyleScheme newScheme, boolean changeSelection) {
    mySchemes.add(newScheme);
    myDispatcher.getMulticaster().schemeListChanged();
    if (changeSelection) {
      selectScheme(newScheme, this);
    }
  }

  public void removeScheme(final CodeStyleScheme scheme) {
    mySchemes.remove(scheme);
    myDispatcher.getMulticaster().schemeListChanged();
    if (myGlobalSelected == scheme) {
      selectScheme(myDefault, this);
    }
  }

  public CodeStyleSettings getCloneSettings(final CodeStyleScheme scheme) {
    if (!mySettingsToClone.containsKey(scheme)) {
      mySettingsToClone.put(scheme, scheme.getCodeStyleSettings().clone());
    }
    return mySettingsToClone.get(scheme);
  }

  public CodeStyleScheme getSelectedScheme(){
    if (myUsePerProjectSettings) {
      return myProjectScheme;
    }
    return myGlobalSelected;
  }

  public void addListener(CodeStyleSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public List<CodeStyleScheme> getSchemes() {
    return Collections.unmodifiableList(mySchemes);
  }

  public void reset() {
    myUsePerProjectSettings = getProjectSettings().USE_PER_PROJECT_SETTINGS;

    CodeStyleScheme[] allSchemes = CodeStyleSchemes.getInstance().getSchemes();
    mySettingsToClone.clear();
    mySchemes.clear();
    ContainerUtil.addAll(mySchemes, allSchemes);
    myGlobalSelected = CodeStyleSchemes.getInstance().getCurrentScheme();

    CodeStyleSettings perProjectSettings = getProjectSettings().PER_PROJECT_SETTINGS;
    if (perProjectSettings != null) {
      myProjectScheme.setCodeStyleSettings(perProjectSettings);
    }


    myDispatcher.getMulticaster().schemeListChanged();
    myDispatcher.getMulticaster().currentSchemeChanged(this);

  }

  public boolean isUsePerProjectSettings() {
    return myUsePerProjectSettings;
  }

  public void setUsePerProjectSettings(final boolean usePerProjectSettings) {
    setUsePerProjectSettings(usePerProjectSettings, false);
  }

  /**
   * Updates 'use per-project settings' value within the current model and optionally at the project settings.
   * 
   * @param usePerProjectSettings  defines whether 'use per-project settings' are in use
   * @param commit                 flag that defines if current project settings should be applied as well
   */
  public void setUsePerProjectSettings(final boolean usePerProjectSettings, final boolean commit) {
    if (commit) {
      final CodeStyleSettingsManager projectSettings = getProjectSettings();
      projectSettings.USE_PER_PROJECT_SETTINGS = usePerProjectSettings;
      projectSettings.PER_PROJECT_SETTINGS = myProjectScheme.getCodeStyleSettings();
    } 
    
    if (myUsePerProjectSettings != usePerProjectSettings) {
      myUsePerProjectSettings = usePerProjectSettings;
      myDispatcher.getMulticaster().usePerProjectSettingsOptionChanged();
      myDispatcher.getMulticaster().currentSchemeChanged(this);
    }
  }

  private CodeStyleSettingsManager getProjectSettings() {
    return CodeStyleSettingsManager.getInstance(myProject);
  }

  public boolean isSchemeListModified() {
    if (getProjectSettings().USE_PER_PROJECT_SETTINGS != myUsePerProjectSettings) return true;
    if (!myUsePerProjectSettings && (getSelectedScheme() != CodeStyleSchemes.getInstance().getCurrentScheme())) return true;
    Set<CodeStyleScheme> configuredSchemesSet = new HashSet<CodeStyleScheme>(getSchemes());
    Set<CodeStyleScheme> savedSchemesSet = new HashSet<CodeStyleScheme>(Arrays.asList(CodeStyleSchemes.getInstance().getSchemes()));
    if (!configuredSchemesSet.equals(savedSchemesSet)) return true;
    return false;
  }

  public void apply() {
    getProjectSettings().USE_PER_PROJECT_SETTINGS = myUsePerProjectSettings;
    getProjectSettings().PER_PROJECT_SETTINGS = myProjectScheme.getCodeStyleSettings();

    final CodeStyleScheme[] savedSchemes = CodeStyleSchemes.getInstance().getSchemes();
    final Set<CodeStyleScheme> savedSchemesSet = new HashSet<CodeStyleScheme>(Arrays.asList(savedSchemes));
    List<CodeStyleScheme> configuredSchemes = getSchemes();

    for (CodeStyleScheme savedScheme : savedSchemes) {
      if (!configuredSchemes.contains(savedScheme)) {
        CodeStyleSchemes.getInstance().deleteScheme(savedScheme);
      }
    }

    for (CodeStyleScheme configuredScheme : configuredSchemes) {
      if (!savedSchemesSet.contains(configuredScheme)) {
        CodeStyleSchemes.getInstance().addScheme(configuredScheme);
      }
    }

    CodeStyleSchemes.getInstance().setCurrentScheme(myGlobalSelected);
    
    // We want to avoid the situation when 'real code style' differs from the copy stored here (e.g. when 'real code style' changes
    // are 'committed' by pressing 'Apply' button). So, we reset the copies here assuming that this method is called on 'Apply'
    // button processing
    mySettingsToClone.clear();
  }

  static SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> getSchemesManager() {
    return ((CodeStyleSchemesImpl) CodeStyleSchemes.getInstance()).getSchemesManager();
  }

  public static boolean cannotBeModified(final CodeStyleScheme currentScheme) {
    return currentScheme.isDefault() || getSchemesManager().isShared(currentScheme);
  }

  public static boolean cannotBeDeleted(final CodeStyleScheme currentScheme) {
    return currentScheme.isDefault();
  }

  public void fireCurrentSettingsChanged() {
    myDispatcher.getMulticaster().currentSettingsChanged();
  }

  public CodeStyleScheme getSelectedGlobalScheme() {
    return myGlobalSelected;
  }

  public void copyToProject(final CodeStyleScheme selectedScheme) {
    myProjectScheme.getCodeStyleSettings().copyFrom(selectedScheme.getCodeStyleSettings());
    myDispatcher.getMulticaster().schemeChanged(myProjectScheme);
    //if (mySettingsToClone.containsKey(myProjectScheme)) {
    //  CodeStyleSettings projectSettings = mySettingsToClone.get(myProjectScheme);
    //  projectSettings.copyFrom(getEditedSchemeSettings(selectedScheme));
    //}
    //else {
    //  mySettingsToClone.put(myProjectScheme, getEditedSchemeSettings(selectedScheme).clone());
    //}
    //myDispatcher.getMulticaster().schemeChanged(myProjectScheme);
  }

  private CodeStyleSettings getEditedSchemeSettings(final CodeStyleScheme selectedScheme) {
    if (mySettingsToClone.containsKey(selectedScheme)) {
      return mySettingsToClone.get(selectedScheme);
    }
    else {
      return selectedScheme.getCodeStyleSettings();
    }
  }

  public CodeStyleScheme exportProjectScheme(final String name) {
    CodeStyleScheme newScheme = createNewScheme(name, myProjectScheme);
    ((CodeStyleSchemeImpl)newScheme).setCodeStyleSettings(getCloneSettings(myProjectScheme));
    addScheme(newScheme, false);

    return newScheme;
  }

  public CodeStyleScheme createNewScheme(final String preferredName, final CodeStyleScheme parentScheme) {
    String name;
    if (preferredName == null) {
      // Generate using parent name
      name = null;
      for (int i = 1; name == null; i++) {
        String currName = parentScheme.getName() + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }
    else {
      name = null;
      for (int i = 0; name == null; i++) {
        String currName = i == 0 ? preferredName : preferredName + " (" + i + ")";
        if (null == findSchemeByName(currName)) {
          name = currName;
        }
      }
    }

    return new CodeStyleSchemeImpl(name, false, parentScheme);
  }

  private CodeStyleScheme findSchemeByName(final String name) {
    for (CodeStyleScheme scheme : mySchemes) {
      if (name.equals(scheme.getName())) return scheme;
    }
    return null;
  }

  public CodeStyleScheme getProjectScheme() {
    return myProjectScheme;
  }

  public boolean isProjectScheme(CodeStyleScheme scheme) {
    return myProjectScheme == scheme;
  }

  public List<CodeStyleScheme> getAllSortedSchemes() {
    List<CodeStyleScheme> schemes = new ArrayList<CodeStyleScheme>();
    schemes.addAll(getSchemes());
    schemes.add(myProjectScheme);
    Collections.sort(schemes, new Comparator<CodeStyleScheme>() {
      @Override
      public int compare(CodeStyleScheme s1, CodeStyleScheme s2) {
        if (isProjectScheme(s1)) return -1;
        if (isProjectScheme(s2)) return 1;
        if (s1.isDefault()) return -1;
        if (s2.isDefault()) return 1;
        return s1.getName().compareToIgnoreCase(s2.getName());
      }
    });
    return schemes;
  }
}
