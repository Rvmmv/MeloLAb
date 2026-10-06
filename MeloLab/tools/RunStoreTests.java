import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Uses production store/codec/domain; only Android preferences/context are in-memory doubles. */
public final class RunStoreTests {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Use JDK 17+");
        Path temp = Files.createTempDirectory("melolab-store-tests-");
        Path context = temp.resolve("android/content/Context.java");
        Path prefs = temp.resolve("android/content/SharedPreferences.java");
        Files.createDirectories(context.getParent());
        Files.writeString(context, "package android.content; public abstract class Context { public static final int MODE_PRIVATE=0; public Context getApplicationContext(){return this;} public abstract SharedPreferences getSharedPreferences(String name,int mode); }");
        Files.writeString(prefs, "package android.content; public interface SharedPreferences { String getString(String key,String fallback); Editor edit(); interface Editor { Editor putString(String key,String value); boolean commit(); } }");
        int result = compiler.run(null, null, null, "-encoding", "UTF-8", "-d", temp.toString(),
                context.toString(), prefs.toString(), "app/src/main/java/com/rvmmv/melolab/MusicData.java",
                "app/src/main/java/com/rvmmv/melolab/MusicTheory.java", "app/src/main/java/com/rvmmv/melolab/ProjectStore.java",
                "tools/ProjectStoreTests.java");
        if (result != 0) throw new IllegalStateException("Store test compilation failed");
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()})) {
            loader.loadClass("com.rvmmv.melolab.ProjectStoreTests").getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        }
    }
}
