import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.gradle.tooling.*;
import org.gradle.tooling.model.eclipse.EclipseProject;

/** Uses the Eclipse Tooling model consumed by Buildship, including NeoGradle's IDE-import hooks. */
public final class VerifyEclipseModel {
    public static void main(String[] args) throws Exception {
        var projectDir = new File(args[0]);
        verifyBuildshipRoot(projectDir);
        var connector = GradleConnector.newConnector().forProjectDirectory(new File(args[0]))
                .useGradleUserHomeDir(new File(args[1])).useInstallation(new File(args[3]));
        try (var connection = connector.connect()) {
            var model = connection.model(EclipseProject.class).setJavaHome(new File(args[2]))
                    .withArguments("--offline", "--max-workers=2", "-Declipse.application=org.eclipse.ui.ide.workbench")
                    .setStandardOutput(System.out).setStandardError(System.err).get();
            var seen = new HashSet<String>();
            int libraries = 0, neo = 0;
            for (var dependency : model.getClasspath()) {
                String name = dependency.getFile().getCanonicalPath().replace('\\', '/');
                String key = System.getProperty("os.name").startsWith("Windows") ? name.toLowerCase(Locale.ROOT) : name;
                if (!seen.add(key)) throw new IllegalStateException("Duplicate Eclipse Tooling library: " + name);
                if (key.matches(".*(?:junit|opentest4j|mockito|bytebuddy|runtimeprobe).*")) throw new IllegalStateException("Test dependency in Eclipse model: " + name);
                if (name.endsWith("/neoforge-21.1.247.jar")) neo++;
                libraries++;
            }
            if (neo != 1) throw new IllegalStateException("Expected exactly one mapped NeoForge jar, found " + neo);
            var sources = new TreeSet<String>();
            for (var source : model.getSourceDirectories()) sources.add(source.getPath().replace('\\', '/'));
            if (!sources.equals(Set.of("src/main/java", "build/resources/main"))) throw new IllegalStateException("Unexpected Eclipse sources: " + sources);
            verifyBuildshipRoot(projectDir);
            System.out.println("ECLIPSE_TOOLING_MODEL_PASS libraries=" + libraries + " neoforge=" + neo + " sources=" + sources);
        }
    }

    private static void verifyBuildshipRoot(File projectDir) throws Exception {
        var preferences = new Properties();
        try (var input = Files.newInputStream(projectDir.toPath().resolve(".settings/org.eclipse.buildship.core.prefs"))) {
            preferences.load(input);
        }
        String root = preferences.getProperty("connection.project.dir");
        if (root == null || !new File(projectDir, root).getCanonicalFile().equals(projectDir.getCanonicalFile())) {
            throw new IllegalStateException("Buildship root-project location is missing or points outside this build");
        }
    }
}
