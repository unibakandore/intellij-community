package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactTypeSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsModuleExtensionLoader;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class JpsModelSerializerExtension {
  public static Iterable<JpsModelSerializerExtension> getExtensions() {
    return JpsServiceManager.getInstance().getExtensions(JpsModelSerializerExtension.class);
  }

  public void loadRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
  }

  public void saveRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
  }

  public void loadProjectRoots(JpsProject project, Element rootManagerElement) {
  }

  public void saveProjectRoots(JpsProject project, Element rootManagerElement) {
  }

  public List<JpsLibraryRootTypeSerializer> getLibraryRootTypeSerializers() {
    return Collections.emptyList();
  }

  @NotNull
  public List<JpsLibraryRootTypeSerializer> getSdkRootTypeSerializers() {
    return Collections.emptyList();
  }

  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
  }

  public void saveModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
  }

  @Nullable
  public JpsElementReference<? extends JpsCompositeElement> createLibraryTableReference(String tableLevel) {
    return null;
  }

  public String getLibraryTableLevelId(JpsElementReference<? extends JpsCompositeElement> reference) {
    return null;
  }

  @NotNull
  public List<? extends JpsModulePropertiesSerializer<?>> getModulePropertiesSerializers() {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends JpsLibraryPropertiesSerializer<?>> getLibraryPropertiesLoaders() {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesLoaders() {
    return Collections.emptyList();
  }

  public List<? extends JpsModuleExtensionLoader<?>> getModuleExtensionLoaders() {
    return Collections.emptyList();
  }

  public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Collections.emptyList();
  }

  public List<JpsArtifactTypeSerializer> getArtifactTypeSerializers() {
    return Collections.emptyList();
  }
}