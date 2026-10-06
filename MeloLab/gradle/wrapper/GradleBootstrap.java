package org.gradle.wrapper;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/**
 * Small, auditable bootstrap supplied with this project, not Gradle's official
 * wrapper implementation. Downloads the official distribution from the URL in
 * gradle-wrapper.properties, then delegates all build arguments to Gradle.
 */
class GradleWrapperMain {
    public static void main(String[] args) {
        try {
            int exit = run(args);
            System.exit(exit);
        } catch (Exception failure) {
            System.err.println("Gradle bootstrap: " + failure.getMessage());
            System.err.println("Use Java 17 or newer. First build needs internet. See docs/BUILD.md.");
            System.exit(1);
        }
    }

    private static int run(String[] args) throws Exception {
        Path jar = Paths.get(GradleWrapperMain.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path wrapperDirectory = jar.getParent();
        Path projectDirectory = wrapperDirectory.getParent().getParent();
        Properties properties = readProperties(wrapperDirectory.resolve("gradle-wrapper.properties"));
        String distributionUrl = properties.getProperty("distributionUrl");
        if (distributionUrl == null) throw new IOException("distributionUrl is missing");
        URL url = URI.create(distributionUrl).toURL();
        if (!"https".equals(url.getProtocol()) && !"file".equals(url.getProtocol())) {
            throw new IOException("Only HTTPS or local file distributions are supported");
        }
        if (args.length == 1 && "--wrapper-info".equals(args[0])) {
            System.out.println("MeloLab transparent Gradle bootstrap 1.0 (Java 17)");
            System.out.println("Distribution: " + distributionUrl);
            System.out.println("Source: gradle/wrapper/GradleBootstrap.java");
            return 0;
        }

        Path gradleUserHome = userHome(args);
        applySystemProperties(gradleUserHome.resolve("gradle.properties"));
        applySystemProperties(projectDirectory.resolve("gradle.properties"));
        String archiveName = Paths.get(url.getPath()).getFileName().toString();
        if (!archiveName.endsWith(".zip")) throw new IOException("Distribution must be a ZIP archive");
        String archiveId = archiveName.substring(0, archiveName.length() - 4);
        String hash = new BigInteger(1, MessageDigest.getInstance("MD5")
                .digest(distributionUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8))).toString(36);
        Path installation = gradleUserHome.resolve("wrapper/dists").resolve(archiveId).resolve(hash);
        Files.createDirectories(installation);

        Path gradleDirectory;
        try (FileChannel channel = FileChannel.open(installation.resolve(".bootstrap.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            gradleDirectory = findGradle(installation);
            if (gradleDirectory == null) {
                Path archive = installation.resolve(archiveName);
                if (!Files.isRegularFile(archive)) {
                    System.out.println("Downloading " + distributionUrl);
                    download(url, archive, Integer.parseInt(properties.getProperty("networkTimeout", "120000")));
                }
                String expectedChecksum = properties.getProperty("distributionSha256Sum");
                if (expectedChecksum != null && !expectedChecksum.isBlank()) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (InputStream stream = Files.newInputStream(archive)) {
                        byte[] buffer = new byte[65536];
                        for (int read; (read = stream.read(buffer)) != -1;) digest.update(buffer, 0, read);
                    }
                    if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedChecksum.trim())) {
                        Files.deleteIfExists(archive);
                        throw new IOException("Gradle distribution checksum did not match");
                    }
                }
                Path staging = Files.createTempDirectory(installation, "unpack-");
                try {
                    extract(archive, staging);
                    Path unpacked = findGradle(staging);
                    if (unpacked == null) throw new IOException("Downloaded archive has no Gradle launcher");
                    gradleDirectory = installation.resolve(unpacked.getFileName());
                    if (Files.exists(gradleDirectory)) removeDirectory(gradleDirectory);
                    Files.move(unpacked, gradleDirectory);
                    makeExecutable(gradleDirectory.resolve("bin/gradle"));
                    Files.write(installation.resolve(archiveName + ".ok"), new byte[0]);
                } finally {
                    removeDirectory(staging);
                }
            }
        }

        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
            command.add(gradleDirectory.resolve("bin/gradle.bat").toString());
        } else {
            makeExecutable(gradleDirectory.resolve("bin/gradle"));
            command.add(gradleDirectory.resolve("bin/gradle").toString());
        }
        command.addAll(Arrays.asList(args));
        ProcessBuilder process = new ProcessBuilder(command).directory(projectDirectory.toFile()).inheritIO();
        process.environment().putIfAbsent("GRADLE_USER_HOME", gradleUserHome.toString());
        return process.start().waitFor();
    }

    private static Path userHome(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (("-g".equals(argument) || "--gradle-user-home".equals(argument)) && index + 1 < args.length) {
                return Paths.get(args[index + 1]).toAbsolutePath();
            }
            if (argument.startsWith("--gradle-user-home=")) {
                return Paths.get(argument.substring("--gradle-user-home=".length())).toAbsolutePath();
            }
        }
        String configured = System.getProperty("gradle.user.home");
        if (configured == null || configured.isBlank()) configured = System.getenv("GRADLE_USER_HOME");
        if (configured == null || configured.isBlank()) configured = System.getProperty("user.home") + "/.gradle";
        return Paths.get(configured).toAbsolutePath();
    }

    private static Properties readProperties(Path file) throws IOException {
        Properties result = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream stream = Files.newInputStream(file)) { result.load(stream); }
        }
        return result;
    }

    private static void applySystemProperties(Path file) throws IOException {
        Properties properties = readProperties(file);
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("systemProp.")) System.setProperty(key.substring(11), properties.getProperty(key));
        }
    }

    private static void download(URL url, Path archive, int timeout) throws IOException {
        Path partial = archive.resolveSibling(archive.getFileName() + ".part");
        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            try (InputStream source = connection.getInputStream()) {
                Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(partial); }
    }

    private static void extract(Path archive, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) throw new IOException("Invalid archive path");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static Path findGradle(Path parent) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(parent, "gradle-*")) {
            for (Path child : children) {
                if (Files.isRegularFile(child.resolve("bin/gradle"))
                        && Files.isRegularFile(child.resolve("bin/gradle.bat"))) return child;
            }
        }
        return null;
    }

    private static void makeExecutable(Path file) throws IOException {
        if (!Files.getFileStore(file).supportsFileAttributeView("posix")) return;
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        permissions.add(PosixFilePermission.GROUP_EXECUTE);
        permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(file, permissions);
    }

    private static void removeDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
