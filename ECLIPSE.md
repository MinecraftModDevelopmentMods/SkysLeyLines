# Eclipse setup

Import the inner `SkysLeyLines` directory as an existing Gradle project. The outer `skysleylines 121NEO` directory is the Eclipse workspace, not the Gradle project.

In project **Properties > Gradle**, enable **Override workspace settings** and use:

- Gradle wrapper (9.2.1).
- Gradle user home: `D:\MinecraftMineralogy\.gradle-verify-cache`.
- Java home: `C:\Users\johnb\.jdks\temurin-21.0.7+6`.

These settings are specific to this project. Keep other workspaces' Gradle homes and Java versions unchanged. A Gradle launch that does not override settings inherits this build's configuration from its working directory.

Right-click the project and choose **Gradle > Refresh Gradle Project**. Wait for synchronization and its preparation tasks to finish. The production source roots are `src/main/java` and processed `build/resources/main`; tests and the validation consumer are available through their explicit Gradle tasks.

To run the client, use **Run > Run Configurations > Gradle Task > SkysLeyLines - runClient**, with task `runClient` and working directory `${workspace_loc:/SkysLeyLines}`. The same task is available in the Gradle Tasks view. Run `runServer` explicitly for a dedicated server. Do not launch the mod entry-point class as a standalone Java application.

NeoGradle also generates its own prepared Java/group launches under `.eclipse/configurations` during import. Keep those generated rather than hand-maintaining Forge-era launch files. No `genEclipseRuns` task is used.

Windows x64 client launches now select the verified project GLFW runtime automatically. Keep `runtime/windows-x86_64` with this checkout. Refresh the Gradle project after updating the build, or run both setup commands below; the Tooling API check regenerates and verifies the prepared Java launch's native-library argument. [GLFW.md](GLFW.md) explains the native window-ownership fix and external-launcher setup.

The build's Eclipse model coalesces duplicate library paths while retaining source attachments and sets the project encoding to UTF-8. `verifyEclipseProductionClasspath` rejects duplicate entries, missing NeoForge sources, raw resource roots, and test/probe leakage. To regenerate and check from PowerShell:

```powershell
$env:JAVA_HOME='C:\Users\johnb\.jdks\temurin-21.0.7+6'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME='D:\MinecraftMineralogy\.gradle-verify-cache'
.\gradlew.bat eclipse verifyEclipseProductionClasspath --offline --no-daemon --max-workers=2
.\scripts\Verify-EclipseModel.ps1
```

The second check queries the actual Eclipse Tooling API model, with NeoGradle's IDE-import hooks enabled. It runs separately from Gradle tasks to avoid starting a nested build against the same project/cache. Use one Gradle operation at a time, including Eclipse synchronization.

Before future cleans, preserve the existing runtime evidence and copied worlds. Eclipse setup itself does not require cleaning the build, deleting `.metadata`, or resetting world data.

If import or refresh appears to do nothing, check **Window > Show View > Error Log**. Buildship can report a missing root-project location there without creating a Problems marker. The `.settings/org.eclipse.buildship.core.prefs` file must contain `connection.project.dir=` (an empty value is correct for this root project). The setup task now preserves Buildship's existing preferences and supplies that key when missing; rerun the commands above, refresh the project files with F5, then refresh Gradle. Keep this project's Java and Gradle-home overrides as listed above when importing again.
