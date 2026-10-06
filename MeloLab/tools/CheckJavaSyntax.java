import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

/** Parse Java source syntax with a JDK; does not compile against Android SDK. */
class CheckJavaSyntax {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                    .sorted().toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK 17 or newer is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-source", "17"), null, files.getJavaFileObjectsFromPaths(sources));
            int count = 0;
            for (var ignored : task.parse()) count++;
            boolean failed = false;
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    failed = true;
                    System.err.println(diagnostic);
                }
            }
            if (failed) throw new IllegalStateException("Java syntax errors found");
            System.out.println("Parsed " + count + " Java sources: no syntax errors.");
            System.out.println("Syntax only; Android API resolution and APK build were not performed.");
        }
    }
}
