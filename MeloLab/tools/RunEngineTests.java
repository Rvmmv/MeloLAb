import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/** Run from project root with JDK17+: java tools/RunEngineTests.java */
public final class RunEngineTests {
    public static void main(String[] args) throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Нужен JDK.");
        Path classes=Files.createTempDirectory("melolab-tests-");
        int result=compiler.run(null,null,null,"-source","17","-target","17","-Xlint:-options","-encoding","UTF-8","-d",classes.toString(),
                "app/src/main/java/com/rvmmv/melolab/MusicData.java","app/src/main/java/com/rvmmv/melolab/MusicTheory.java",
                "app/src/main/java/com/rvmmv/melolab/MidiWriter.java","tools/MusicEngineTests.java");
        if(result!=0)throw new IllegalStateException("Compilation failed: "+result);
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            loader.loadClass("com.rvmmv.melolab.MusicEngineTests").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        }finally {try(var files=Files.walk(classes)){files.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(Exception ignored){}});}}
    }
}
