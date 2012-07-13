package org.jetbrains.jps

import org.apache.tools.ant.BuildException
import org.codehaus.gant.GantBinding
import org.jetbrains.jps.artifacts.ArtifactBuilder
import org.jetbrains.jps.idea.OwnServiceLoader
import org.jetbrains.jps.listeners.BuildInfoPrinter
import org.jetbrains.jps.listeners.BuildStatisticsListener
import org.jetbrains.jps.listeners.DefaultBuildInfoPrinter
import org.jetbrains.jps.listeners.JpsBuildListener
import org.jetbrains.jps.builders.*

/**
 * @author max
 */
class ProjectBuilder {
  private final Set<ModuleChunk> compiledChunks = [] as Set
  private final Set<ModuleChunk> compiledTestChunks = [] as Set
  private ProjectChunks productionChunks
  private ProjectChunks testChunks

  final Project project;
  final GantBinding binding;
  final ArtifactBuilder artifactBuilder

  final List<ModuleBuilder> sourceGeneratingBuilders = []
  final List<ModuleBuilder> sourceModifyingBuilders = []
  final List<ModuleBuilder> translatingBuilders = []
  final List<ModuleBuilder> weavingBuilders = []
  final CustomTasksBuilder preTasksBuilder = new CustomTasksBuilder()
  final CustomTasksBuilder postTasksBuilder = new CustomTasksBuilder()
  static final OwnServiceLoader<ModuleBuilderService> moduleBuilderLoader = OwnServiceLoader.load(ModuleBuilderService.class)

  final List<JpsBuildListener> listeners = [new BuildStatisticsListener()]
  BuildInfoPrinter buildInfoPrinter = new DefaultBuildInfoPrinter()
  boolean useInProcessJavac
  boolean compressJars = true
  boolean arrangeModuleCyclesOutputs

  private ProjectPaths projectPaths
  private String targetFolder = null
  String tempFolder = null

  private final TempFileContainer tempFileContainer
  boolean dryRun = false

  def ProjectBuilder(GantBinding binding, Project project) {
    this.project = project
    this.binding = binding
    artifactBuilder = new ArtifactBuilder(this)
    tempFileContainer = new TempFileContainer(this, "__build_temp__")
    sourceGeneratingBuilders << new GroovyStubGenerator(this)
    translatingBuilders << new JavacBuilder()
    translatingBuilders << new GroovycBuilder(this)
    translatingBuilders << new ResourceCopier()
    weavingBuilders << new JetBrainsInstrumentations(this)
    productionChunks = new ProjectChunks(project, ClasspathKind.PRODUCTION_COMPILE)
    testChunks = new ProjectChunks(project, ClasspathKind.TEST_COMPILE)

    moduleBuilderLoader*.registerBuilders(this)
  }

  String getTargetFolder() {
    return targetFolder
  }

  void setTargetFolder(String targetFolder) {
    this.targetFolder = targetFolder
    projectPaths = null
  }

  public ProjectPaths getProjectPaths() {
    if (projectPaths == null) {
      projectPaths = new ProjectPaths(project, targetFolder != null ? new File(targetFolder) : null)
      if (!arrangeModuleCyclesOutputs) {
        setOutputPathsForModuleCycles(projectPaths, productionChunks, false)
        setOutputPathsForModuleCycles(projectPaths, testChunks, true)
      }
    }
    return projectPaths
  }

  void setArrangeModuleCyclesOutputs(boolean arrangeModuleCyclesOutputs) {
    this.arrangeModuleCyclesOutputs = arrangeModuleCyclesOutputs
    projectPaths = null
  }

  private def setOutputPathsForModuleCycles(ProjectPaths projectPaths, final ProjectChunks chunks, final boolean forTests) {
    chunks.chunkList.each { ModuleChunk chunk ->
      if (chunk.elements.size() > 1) {
        File outputDir
        if (targetFolder != null) {
          def basePath = forTests ? new File(targetFolder, "test").absolutePath : new File(targetFolder, "production").absolutePath
          def name = chunk.name
          if (name.length() > 100) {
            name = name.substring(0, 100) + "_etc"
          }
          outputDir = new File(basePath, name)
        }
        else {
          outputDir = new File(forTests ? chunk.representativeModule().testOutputPath : chunk.representativeModule().outputPath)
        }
        chunk.elements.each {
          projectPaths.setCustomModuleOutputDir(it, forTests, outputDir)
        }
      }
    }
  }

  def ProjectChunks getChunks(boolean includeTests) {
    return includeTests ? testChunks : productionChunks
  }

  private def List<ModuleBuilder> builders() {
    [preTasksBuilder, sourceGeneratingBuilders, sourceModifyingBuilders, translatingBuilders, weavingBuilders, postTasksBuilder].flatten()
  }

  public def clean() {
    if (!dryRun) {
      if (targetFolder != null) {
        stage("Cleaning $targetFolder")
        BuildUtil.deleteDir(this, targetFolder)
      }
      else {
        stage("Cleaning output folders for ${project.modules.size()} modules")
        project.modules.values().each {
          cleanModule(it)
        }
        stage("Cleaning output folders for ${project.artifacts.size()} artifacts")
        project.artifacts.values().each {
          artifactBuilder.cleanOutput(it)
        }
      }
    }
    else {
      stage("Cleaning skipped as we're running dry")
    }
    compiledChunks.clear()
    compiledTestChunks.clear()
  }

  def cleanModule(Module m) {
    BuildUtil.deleteDir(this, getModuleOutputFolder(m, false))
    BuildUtil.deleteDir(this, getModuleOutputFolder(m, true))
  }

  public def buildAll() {
    buildAllModules(true)
  }

  public def buildSelected(Collection<Module> modules, boolean tests) {
    buildModules(modules, tests)
  }

  public def buildProduction() {
    buildAllModules(false)
  }

  private def buildAllModules(boolean includeTests) {
    buildModules(project.modules.values(), includeTests)
  }

  public def buildArtifact(String artifactName) {
    def artifact = project.artifacts[artifactName]
    if (artifact == null) {
      error("Artifact '$artifactName' not found")
    }
    artifactBuilder.buildArtifact(artifact)
  }

  public def buildArtifacts() {
    artifactBuilder.buildArtifacts()
  }

  private def clearChunks(Collection<Module> modules) {
    getChunks(true).getChunkList().each {
      if (!modules.intersect(it.modules).isEmpty()) {
        clearChunk(it, null, null)
      }
    }
  }

  def error(String message) {
    throw new BuildException(message)
  }

  def warning(String message) {
    binding.ant.project.log(message, org.apache.tools.ant.Project.MSG_WARN)
  }

  def stage(String message) {
    buildInfoPrinter.printProgressMessage(this, message)
  }

  def info(String message) {
    binding.ant.project.log(message, org.apache.tools.ant.Project.MSG_INFO)
  }

  def debug(String message) {
    binding.ant.project.log(message, org.apache.tools.ant.Project.MSG_DEBUG)
  }

  def buildStart() {
    listeners*.onBuildStarted(this)
  }

  def buildStop() {
    listeners*.onBuildFinished(this)
  }

  private def buildModules(Collection<Module> modules, boolean includeTests) {
    buildStart()
    buildChunks(modules, false)
    if (includeTests) {
      buildChunks(modules, true)
    }
    buildStop()
  }

  private def buildChunks(Collection<Module> modules, boolean tests) {
    getChunks(tests).getChunkList().each {
      if (!modules.intersect(it.modules).isEmpty()) {
        buildChunk(it, tests)
      }
    }
  }

  def preModuleBuildTask(String moduleName, Closure task) {
    preTasksBuilder.registerTask(moduleName, task)
  }

  def postModuleBuildTask(String moduleName, Closure task) {
    postTasksBuilder.registerTask(moduleName, task)
  }

  private ModuleChunk chunkForModule(Module m, boolean tests) {
    return getChunks(tests).findChunk(m)
  }

  def makeModule(Module module) {
    makeModuleWithDependencies(module, false);
  }

  def makeModuleTests(Module module) {
    makeModuleWithDependencies(module, true);
  }

  def deleteTempFiles() {
    tempFileContainer.clean()
  }

  String getTempDirectoryPath(String name) {
    return tempFileContainer.getTempDirPath(name)
  }

  private def makeModuleWithDependencies(Module module, boolean includeTests) {
    def chunk = chunkForModule(module, includeTests)
    Set<Module> dependencies = new HashSet<Module>()
    Set<Module> runtimeDependencies = new HashSet<Module>()
    chunk.modules.each {
      collectModulesFromClasspath(it, ClasspathKind.compile(includeTests), dependencies)
      collectModulesFromClasspath(it, ClasspathKind.runtime(includeTests), runtimeDependencies)
    }
    dependencies.addAll(runtimeDependencies)

    buildModules(dependencies, includeTests)
  }

  def clearChunk(ModuleChunk chunk) {
    if (!dryRun) {
      stage("Cleaning module ${chunk.name}")
      chunk.modules.each {cleanModule it}
    }
  }

  def buildChunk(ModuleChunk chunk, boolean tests) {
    Set<ModuleChunk> compiledSet = tests ? compiledTestChunks : compiledChunks
    if (compiledSet.contains(chunk)) return
    compiledSet.add(chunk)

    stage("Making${tests ? ' tests for' : ''} module ${chunk.name}")
    if (targetFolder == null && !arrangeModuleCyclesOutputs && chunk.modules.size() > 1) {
      warning("Modules $chunk.modules with cyclic dependencies will be compiled to output of ${chunk.modules.toList().first()} module")
    }

    compile(chunk, tests)
  }

  private String getModuleOutputFolder(Module module, boolean tests) {
    return getProjectPaths().getModuleOutputDir(module, tests)?.absolutePath
  }

  private def compile(ModuleChunk chunk, boolean tests) {
    List<String> chunkSources = filterNonExistingFiles(tests ? chunk.testRoots : chunk.sourceRoots, true)
    if (chunkSources.isEmpty()) return

    if (!dryRun) {
      List<String> chunkClasspath = ProjectPaths.getPathsList(getProjectPaths().getClasspathFiles(chunk, ClasspathKind.compile(tests), true))

      List sourceRootsWithDependencies = ProjectPaths.getPathsList(getProjectPaths().getSourcePathsWithDependents(chunk, tests))
      Map<ModuleBuildState, ModuleChunk> states = new HashMap<ModuleBuildState, ModuleChunk>()
      def chunkState = new ModuleBuildState(
              loader: null,
              formInstrumenter: null,
              tests: tests,
              sourceRoots: chunkSources,
              excludes: computeExcludes(chunk.elements, chunkSources),
              classpath: chunkClasspath,
              sourceRootsFromModuleWithDependencies: sourceRootsWithDependencies,
      )
      if (arrangeModuleCyclesOutputs) {
        chunk.modules.each {
          List<String> sourceRoots = filterNonExistingFiles(tests ? it.testRoots : it.sourceRoots, false)
          if (!sourceRoots.isEmpty()) {
            def state = new ModuleBuildState(
                    loader: null,
                    formInstrumenter: null,
                    tests: tests,
                    sourceRoots: sourceRoots,
                    excludes: computeExcludes([it], sourceRoots),
                    classpath: chunkClasspath,
                    targetFolder: createOutputFolder(it.name, it, tests),
                    sourceRootsFromModuleWithDependencies: sourceRootsWithDependencies
            )
            states[state] = new ModuleChunk(it)
          }
        }
        if (chunk.modules.size() > 1) {
          chunkState.targetFolder = getTempDirectoryPath(chunk.name + (tests ? "_tests" : ""))
          binding.ant.mkdir(dir: chunkState.targetFolder)
          chunkClasspath.add(0, chunkState.targetFolder)
          chunkState.tempRootsToDelete << chunkState.targetFolder
        }
      }
      else {
        chunkState.targetFolder = createOutputFolder(chunk.name, chunk.representativeModule(), tests)
        states[chunkState] = chunk
      }

      listeners*.onCompilationStarted(chunk)

      builders().each {ModuleBuilder builder ->
        listeners*.onModuleBuilderStarted(builder, chunk)
        if (arrangeModuleCyclesOutputs && chunk.modules.size() > 1 && builder instanceof ModuleCycleBuilder) {
          ((ModuleCycleBuilder) builder).preprocessModuleCycle(chunkState, chunk, this)
        }
        states.keySet().each {
          builder.processModule(it, states[it], this)
        }
        listeners*.onModuleBuilderFinished(builder, chunk)
      }

      states.keySet().each {
        it.tempRootsToDelete.each {
          BuildUtil.deleteDir(this, it)
        }
      }
      chunkState.tempRootsToDelete.each {
        BuildUtil.deleteDir(this, it)
      }
      listeners*.onCompilationFinished(chunk)
    }

    chunk.modules.each {
      exportProperty("module.${it.name}.output.${tests ? "test" : "main"}", getModuleOutputFolder(it, tests))
    }
  }

  private List<String> computeExcludes(Collection<Module> modules, List<String> sourceRoots) {
    Set<String> excludes = [] as Set
    modules.each {Module module ->
      excludes.addAll(module.excludes)
    }
    return excludes.asList()
  }

  private Set<String> getContentRootsUnder(Set<String> root, Set<Module> modules) {
    Set<String> result = [] as Set
    modules.each {
      it.contentRoots.each {String contentRoot ->
        if (PathUtil.isUnder(root, contentRoot)) {
          result << contentRoot
        }
      }
    }
    return result
  }

  private String createOutputFolder(String name, Module module, boolean tests) {
    def dst = getProjectPaths().getModuleOutputDir(module, tests)
    if (dst == null) {
      error("${tests ? 'Test output' : 'Output'} path for module $name is not specified")
    }
    def ant = binding.ant
    ant.mkdir(dir: dst.absolutePath)
    return dst.absolutePath
  }

  List<String> moduleClasspath(Module module, ClasspathKind classpathKind) {
    return getProjectPaths().getClasspath(chunkForModule(module, classpathKind.isTestsIncluded()), classpathKind)
  }

  List<String> moduleRuntimeClasspath(Module module, boolean test) {
    return getProjectPaths().getClasspath(chunkForModule(module, test), ClasspathKind.runtime(test))
  }

  private def collectModulesFromClasspath(Module module, ClasspathKind kind, Set<Module> result) {
    if (result.contains(module)) return
    result << module
    module.getClasspath(kind).each {
      if (it instanceof Module) {
        collectModulesFromClasspath(it, kind, result)
      }
    }
  }

  String moduleOutput(Module module) {
    return getModuleOutputFolder(module, false)
  }

  String moduleTestsOutput(Module module) {
    return getModuleOutputFolder(module, true)
  }

  List<String> filterNonExistingFiles(List<String> list, boolean showWarnings) {
    List<String> answer = new ArrayList<String>()
    for (path in list) {
      if (new File(path).exists()) {
        answer.add(path)
      }
      else if (showWarnings) {
        warning("'$path' does not exist!")
      }
    }

    answer
  }

  def exportProperty(String name, String value) {
    binding.ant.project.setProperty(name, value)
  }
}