import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.*;

/** Rebuild with a full JDK: java tools/BuildWrapper.java (no external tools). */
class BuildWrapper {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK 17 or newer is required");
        Path classes = Files.createTempDirectory("rvmmv-wrapper-classes-");
        try {
            // Source/target work even on a JDK whose optional ct.sym archive was stripped.
            // The bootstrap uses only Java 17 APIs.
            int result = compiler.run(null, null, null, "-source", "17", "-target", "17", "-d", classes.toString(),
                    root.resolve("gradle/wrapper/GradleBootstrap.java").toString());
            if (result != 0) throw new IllegalStateException("Compilation failed: " + result);
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "org.gradle.wrapper.GradleWrapperMain");
            manifest.getMainAttributes().putValue("Implementation-Title", "MeloLab transparent Gradle bootstrap");
            manifest.getMainAttributes().putValue("Implementation-Version", "1.0");
            Path output = root.resolve("gradle/wrapper/gradle-wrapper.jar");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output), manifest);
                 var stream = Files.walk(classes)) {
                for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                    JarEntry entry = new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/'));
                    entry.setTime(0);
                    jar.putNextEntry(entry);
                    Files.copy(file, jar);
                    jar.closeEntry();
                }
            }
            System.out.println("Created " + output + " (" + Files.size(output) + " bytes)");
        } finally {
            try (var stream = Files.walk(classes)) {
                for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
