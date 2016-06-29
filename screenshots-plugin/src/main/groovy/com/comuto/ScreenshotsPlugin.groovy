package com.comuto

import com.mounacheikhna.capture.CaptureRunnerTask
import com.mounacheikhna.jsongenerator.GenerateJsonTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.StopExecutionException

/**
 * Top-level plugin for managing task for running tests that generate screenshots and copying them
 * to src/main/play folder.
 **/
public class ScreenshotsPlugin implements Plugin<Project> {

  private static final String GROUP_SCREENSHOTS = "screenshots"
  public static final String DEFAULT_PRODUCT_FLAVOR = "defaultConfig"
  public static final String DEFAULT_BUILD_TYPE = "debug"

  @Override
  void apply(Project project) {
    project.extensions.add("screenshots", ScreenshotsExtension)

    sanitizeInput(project)

    project.afterEvaluate {
      File configFile = new File("${project.projectDir}/${project.screenshots.configFilePath}")

      if (!configFile.exists()) {
        throw new StopExecutionException(
            "ConfigFile of path ${configFile.getPath()} doesn't exist ")
      }

/*
      Task apksPathsTask = project.task("apksPathsTask") << {
        String apkPath = getApkPath(project)
        String testAppPath = getTestApkPath(project)
        String testPackage = getTestPackage(project)
        println " apkPath : $apkPath & testAppPath : $testAppPath & testPackage : $testPackage"
      }
*/

      Task cleanFoldersTask = createCleanTask(project)
      Map<String, String> configValues = Utils.valuesFromFile(configFile)
      Task downloadTranslationsTask = createDownloadTranslationsTask(project)
      Task copyPlayTask = createCopyPlayTask(project, configValues)
      Task screenshotsTask = createScreenshotsTasks(project, configValues)

      Task frameTask = project.tasks.getByName("FrameScreenshots")
      if (frameTask != null) {
        createScreenshotsWorkflowTask(project, cleanFoldersTask, downloadTranslationsTask,
            screenshotsTask, frameTask, copyPlayTask)
      } else {
        createScreenshotsWorkflowTask(project, cleanFoldersTask, downloadTranslationsTask,
            screenshotsTask, copyPlayTask)
      }
    }
  }

  static Task createCleanTask(Project project) {
    return project.task("cleanFoldersTask") {
      final File screenshotsDir = new File(
          "${project.projectDir}/${project.screenshots.screenshotsDir}")
      if (screenshotsDir.exists()) {
        screenshotsDir.eachFileRecurse { it.delete() }
      }
      File finalOutputDir = new File("${project.projectDir}/${project.screenshots.finalOutputDir}")
      if (finalOutputDir.exists()) {
        finalOutputDir.eachFileRecurse { it.delete() }
      }
    }
  }

  Task createDownloadTranslationsTask(Project project) {
    def transyncDirName = "${project.projectDir}/${project.screenshots.configFolder}"
    File transyncWorkingDir = new File(transyncDirName)
    def args = ["transync", "pull", "--locale=all"]
    Task downloadTask = project.task("DownloadTranslations", type: Exec) {
      workingDir transyncWorkingDir
      commandLine args
    }

    Task copyTask = project.task("copyTranslations", type: Copy) {
      from "${project.projectDir}/${project.screenshots.translationsFolder}" //TODO: don't hard code translations folder
      into "${project.projectDir}/src/${project.screenshots.productFlavor}/assets/"
    }

    Task transyncTask = project.task("PullTranslations", group: GROUP_SCREENSHOTS)
    dependsOnOrdered(transyncTask, downloadTask, copyTask)
    return transyncTask
  }

  private static void createScreenshotsWorkflowTask(Project project, Task... tasks) {
    Task screenshotsWorkflowTask = project.task("ScreenshotsWorkflow",
        group: GROUP_SCREENSHOTS,
        description: "Run the complete screenshot pipeline.")
    dependsOnOrdered(screenshotsWorkflowTask, tasks)
  }

  private Task createScreenshotsTasks(Project project, Map<String, String> configValues) {
    String screenshotsDirName = "${project.projectDir}/${project.screenshots.screenshotsDir}"

    Task takeAllScreenshots = project.task("Screenshots",
        group: GROUP_SCREENSHOTS,
        description: "Takes screenshots generated by spoon on all the connected devices.")

    List<Task> localesTasks = createTestsRunTasks(project, screenshotsDirName, configValues)
    String productFlavor = project.screenshots.productFlavor
    def flavorTaskName = productFlavor.capitalize()

    Task assembleTask = project.tasks.findByName("assemble$flavorTaskName")
    Task assembleTestTask = project.tasks.findByName("assembleAndroidTest")

    if (localesTasks.isEmpty()) {
      return
    }
    localesTasks.get(0).dependsOn assembleTask
    localesTasks.get(0).dependsOn assembleTestTask
    int size = localesTasks.size();
    for (int i = 1; i < size; i++) {
      localesTasks.get(i).dependsOn localesTasks.get(i - 1)
    }
    takeAllScreenshots.dependsOn localesTasks.get(size - 1)
    return takeAllScreenshots
  }

  private Task createCopyPlayTask(Project project, Map<String, String> configValues) {
    String[] localesStr
    if (configValues.containsKey("locales") && configValues.get("locales") != null) {
      String strl = configValues.get("locales")
      localesStr = strl.split(",");
    }
    return project.task("CopyToPlayFolders",
        type: ProcessScreenshotsTask,
        group: GROUP_SCREENSHOTS,
        description: "Copy generated screenshots into play folder each in the right place.") {
      localesValues localesStr
      screenshotsOutputDir project.screenshots.finalOutputDir
      phoneSerialNo project.screenshots.phone
      sevenInchDeviceSerialNo project.screenshots.sevenInchDevice
      tenInchDeviceSerialNo project.screenshots.tenInchDevice
    }
  }

  private static void dependsOnOrdered(Task task, Task... others) {
    task.dependsOn(others)
    for (int i = 0; i < others.size() - 1; i++) {
      if (others[i] != null) {
        others[i + 1].mustRunAfter(others[i])
      }
    }
  }

  private static void sanitizeInput(Project project) {
    //first lets check that at least one serial nb is provided
    if ([project.screenshots.phone, project.screenshots.sevenInchDevice, project.screenshots.tenInchDevice]
        .every { it?.trim() == false }) {
      throw new IllegalArgumentException("You must provide a serial number of a phone or seven " +
          "inch or tablet device. Use adb devices command to find the serial number for the connected device.")
    }
    project.screenshots.productFlavor = project.screenshots.productFlavor ?: DEFAULT_PRODUCT_FLAVOR
    project.screenshots.buildType = project.screenshots.buildType ?: DEFAULT_BUILD_TYPE
  }

  private List<Task> createTestsRunTasks(Project project, String screenshotOutputDirName,
      Map<String, String> values) {
    String apkPath = getApkPath(project)
    String testAppPath = getTestApkPath(project)
    String testPackage = getTestPackage(project)

    println " apkPath: $apkPath & testAppPath: $testAppPath & testPackage : $testPackage"

    String localesStr = values.get("locales")
    if (localesStr == null) {
      throw new StopExecutionException("Illegal Argument locales.")
    }
    def locales = localesStr.split(",")
    List<Task> localesTasks = new ArrayList<>()
    locales.each {
      String currentLocale = it
      def localeFileName = values.get(currentLocale)
      println "***  localeFileName : $localeFileName "
      Task testRunTask = createTestRunTask(project, currentLocale, values, apkPath, testAppPath,
          testPackage, screenshotOutputDirName)
      Task generateJsonTask = createJsonGenerateTask(project, currentLocale, localeFileName)
      testRunTask.dependsOn generateJsonTask
      testRunTask.mustRunAfter generateJsonTask
      localesTasks.add(testRunTask)
    }
    localesTasks
  }

  private Task createJsonGenerateTask(Project project, String currentLocale, localeFileName) {
    String screenshotProductFlavor = project.screenshots.productFlavor

    //TODO: maybe better just to have a folder with these placeholders that we can take from
    Map<String, String> files = new HashMap<>()
    project.screenshots.dataPlaceholdersFiles.each {
      files.put(it, "${currentLocale}_${it.replace("placeholder", "generated")}".toLowerCase())
    }

    println " generate json with translations in ${project.projectDir}/${project.screenshots.translationsFolder} " +
        " and custom in ${project.projectDir}/${project.screenshots.customJsonValuesFolder} "
    Task generateJsonTask = project.task("${currentLocale}CreateJson",
        type: GenerateJsonTask,
        group: GROUP_SCREENSHOTS,
        description: "Generates Json files from the templates and replaces the placeholders with the provided values.") {
      locale currentLocale
      addFirstPassConfig "${project.projectDir}/${project.screenshots.translationsFolder}/$localeFileName",
          "##", "##", false

      addFirstPassConfig "${project.projectDir}/${project.screenshots.customJsonValuesFolder}/$localeFileName",
          "##", "##", false
      productFlavor screenshotProductFlavor
      outputFileNamesMappings files
    }
    generateJsonTask
  }

  private Task createTestRunTask(Project project, String currentLocale, Map<String, String> values,
      String apkPath,
      String testAppPath, String testPackage, String screenshotOutputDirName) {
    println "create test run task for local $currentLocale & for values $values & for testAppPath " +
        "$testAppPath & for testPackage $testPackage & for output dir $screenshotOutputDirName"
    def args = [:]
    args.put("locale", currentLocale)
    values.findAll { k, v -> k.contains(currentLocale) }
        .each { key, val -> args.put(key, val)
    }

    return project.task("${currentLocale}TestRunTask", type: CaptureRunnerTask) {
      appApkPath apkPath
      testApkPath testAppPath
      testPackageName testPackage
      serialNumber project.screenshots.phone
      outputPath "$screenshotOutputDirName"
      instrumentationArgs args
      testClassName project.screenshots.screenshotClass
      taskPrefix "${currentLocale}"
    }
  }

  private static String getTestPackage(Project project) {
    def buildType = project.screenshots.buildType
    if (buildType?.trim()) {
      buildType = buildType.replace("-", "")
      return "${project.screenshots.appPackageName}" + "." + "${buildType}" + ".test"
    } else {
      return "${project.screenshots.appPackageName}" + ".test"
    }
  }

  private static String getTestApkPath(Project project) {
    String screenshotProductFlavor = project.screenshots.productFlavor
    return "${project.buildDir}/outputs/apk/${project.name}-$screenshotProductFlavor-${project.screenshots.buildType}-androidTest-unaligned.apk"
  }

  private static String getApkPath(Project project) {
    String screenshotProductFlavor = project.screenshots.productFlavor
    if (project.screenshots.hasApkSplit) {
      return "${project.buildDir}/outputs/apk/${project.name}-$screenshotProductFlavor-universal-${project.screenshots.buildType}-unaligned.apk"
    } else {
      return "${project.buildDir}/outputs/apk/${project.name}-$screenshotProductFlavor-${project.screenshots.buildType}-unaligned.apk"
    }
  }
}