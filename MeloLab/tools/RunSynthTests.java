import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Run from project root: java tools/RunSynthTests.java (JDK 17+, no Android SDK). */
class RunSynthTests {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Нужен полный JDK 17 или новее");
        Path output = Files.createTempDirectory("melolab-synth-tests-");
        try {
            int result = compiler.run(null, null, null, "-source", "17", "-target", "17", "-d", output.toString(),
                    "app/src/main/java/com/rvmmv/melolab/SynthCore.java", "tools/SynthCoreTest.java");
            if (result != 0) throw new IllegalStateException("Synthesis tests did not compile");
            try (URLClassLoader classes = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()})) {
                classes.loadClass("com.rvmmv.melolab.SynthCoreTest").getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
