package org.cobbzilla.util.io;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.apache.commons.lang3.StringUtils.chop;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class FileUtil {

    public static final File DEFAULT_TEMPDIR = new File(System.getProperty("java.io.tmpdir"));
    private static final File[] EMPTY_ARRAY = {};
    public static final String sep = File.separator;

    public static boolean isReadableNonEmptyFile (File f) {
        return f != null && f.exists() && f.canRead() && f.length() > 0;
    }

    public static File[] list(File dir) {
        final File[] files = dir.listFiles();
        if (files == null) return EMPTY_ARRAY;
        return files;
    }

    public static File[] listFiles(File dir) {
        final File[] files = dir.listFiles(RegularFileFilter.instance);
        if (files == null) return EMPTY_ARRAY;
        return files;
    }

    public static File[] listDirs(File dir) {
        final File[] files = dir.listFiles(DirFilter.instance);
        if (files == null) return EMPTY_ARRAY;
        return files;
    }

    public static String chopSuffix(String path) {
        if (path == null) return null;
        final int lastDot = path.lastIndexOf('.');
        if (lastDot == -1 || lastDot == path.length()-1) return path;
        return path.substring(0, lastDot);
    }

    public static File createTempDir(String prefix) throws IOException {
        return createTempDir(DEFAULT_TEMPDIR, prefix);
    }

    public static File createTempDir(File parentDir, String prefix) throws IOException {
        final Path parent = FileSystems.getDefault().getPath(abs(parentDir));
        return new File(Files.createTempDirectory(parent, prefix).toAbsolutePath().toString());
    }

    public static File createTempDirOrDie(String prefix) {
        return createTempDirOrDie(DEFAULT_TEMPDIR, prefix);
    }

    public static File createTempDirOrDie(File parentDir, String prefix) {
        try {
            return createTempDir(parentDir, prefix);
        } catch (IOException e) {
            return die("createTempDirOrDie: error creating directory with prefix="+abs(parentDir)+"/"+prefix+": "+e, e);
        }
    }

    public static void writeResourceToFile(String resourcePath, File outFile, Class clazz) throws IOException {
        if (!outFile.getParentFile().exists() || !outFile.getParentFile().canWrite() || (outFile.exists() && !outFile.canWrite())) {
            throw new IllegalArgumentException("outFile is not writeable: "+abs(outFile));
        }
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(resourcePath);
             OutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new IllegalArgumentException("null data at resourcePath: "+resourcePath);
            IOUtils.copy(in, out);
        }
    }

    public static List<String> loadResourceAsStringListOrDie(String resourcePath, Class clazz) {
        try {
            return loadResourceAsStringList(resourcePath, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("loadResourceAsStringList error: "+e, e);
        }
    }

    public static List<String> loadResourceAsStringList(String resourcePath, Class clazz) throws IOException {
        @Cleanup final Reader reader = StreamUtil.loadResourceAsReader(resourcePath, clazz);
        return toStringList(reader);
    }

    public static List<String> toStringList(String f) throws IOException {
        return toStringList(new File(f));
    }

    public static List<String> toStringList(File f) throws IOException {
        @Cleanup final Reader reader = new FileReader(f);
        return toStringList(reader);
    }

    public static List<String> toStringList(Reader reader) throws IOException {
        final List<String> strings = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(reader)) {
            String line;
            while ((line = r.readLine()) != null) {
                strings.add(line.trim());
            }
        }
        return strings;
    }

    public static File toFile (List<String> lines) throws IOException {
        final File temp = File.createTempFile(FileUtil.class.getSimpleName()+".toFile", "tmp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp))) {
            for (String line : lines) {
                writer.write(line+"\n");
            }
        }
        return temp;
    }

    public static String toStringOrDie (String f) {
        return toStringOrDie(new File(f));
    }

    public static String toStringOrDie (File f) {
        try {
            return toString(f);
        } catch (FileNotFoundException e) {
            log.warn("toStringOrDie: returning null; file not found: "+abs(f));
            return null;
        } catch (IOException e) {
            final String path = f == null ? "null" : abs(f);
            throw new IllegalArgumentException("Error reading file ("+ path +"): "+e, e);
        }
    }

    public static String toString (String f) throws IOException {
        return toString(new File(f));
    }

    public static String toString (File f) throws IOException {
        StringWriter writer = new StringWriter();
        try (Reader r = new FileReader(f)) {
            IOUtils.copy(r, writer);
        }
        return writer.toString();
    }

    public static byte[] toBytes (File f) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(f)) {
            IOUtils.copy(in, out);
        }
        return out.toByteArray();
    }

    public static Properties toPropertiesOrDie (String f) {
        return toPropertiesOrDie(new File(f));
    }

    private static Properties toPropertiesOrDie(File f) {
        try {
            return toProperties(f);
        } catch (IOException e) {
            final String path = f == null ? "null" : abs(f);
            throw new IllegalArgumentException("Error reading properties file ("+ path +"): "+e, e);
        }
    }

    public static Properties toProperties (String f) throws IOException {
        return toProperties(new File(f));
    }

    public static Properties toProperties (File f) throws IOException {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            props.load(in);
        }
        return props;
    }

    public static Properties resourceToPropertiesOrDie (String path, Class clazz) {
        try {
            return resourceToProperties(path, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error reading resource ("+ path +"): "+e, e);
        }
    }

    public static Properties resourceToProperties (String path, Class clazz) throws IOException {
        Properties props = new Properties();
        try (InputStream in = StreamUtil.loadResourceAsStream(path, clazz)) {
            props.load(in);
        }
        return props;
    }

    public static File toFileOrDie (String file, String data) {
        return toFileOrDie(new File(file), data);
    }

    public static File toFileOrDie(File file, String data) {
        try {
            return toFile(file, data);
        } catch (IOException e) {
            String path = (file == null) ? "null" : abs(file);
            return die("toFileOrDie: error writing to file: "+ path);
        }
    }

    public static File toFile (String file, String data) throws IOException {
        return toFile(new File(file), data);
    }

    public static File toFile(File file, InputStream in) throws IOException {
        return toFile(file, StreamUtil.toString(in));
    }

    public static File toFile(File file, String data) throws IOException {
        if (!ensureDirExists(file.getParentFile())) {
            throw new IOException("Error creating directory: "+file.getParentFile());
        }
        try (OutputStream out = new FileOutputStream(file)) {
            IOUtils.copy(new ByteArrayInputStream(data.getBytes()), out);
        }
        return file;
    }

    public static void renameOrDie (File from, File to) {
        if (!from.renameTo(to)) die("Error renaming "+abs(from)+" -> "+abs(to));
    }

    public static void writeString (File target, String data) throws IOException {
        try (FileWriter w = new FileWriter(target)) {
            w.write(data);
        }
    }

    public static void writeStringOrDie (File target, String data) {
        try {
            writeString(target, data);
        } catch (IOException e) {
            die("Error writing to file ("+abs(target)+"): "+e, e);
        }
    }

    public static void truncate (File file) { _touch(file, false); }

    public static void touch (String file) { _touch(new File(file), true); }

    public static void touch (File file) { _touch(file, true); }

    private static void _touch(File file, boolean append) {
        try (FileWriter ignored = new FileWriter(file, append)) {
            // do nothing -- if append is false, we truncate the file,
            // otherwise just update the mtime/atime, and possible create an empty file if it doesn't already exist
        } catch (IOException e) {
            final String path = (file == null) ? "null" : abs(file);
            throw new IllegalArgumentException("error "+(append ? "touching" : "truncating")+" "+path +": "+e, e);
        }
    }

    public static Path path(File f) {
        return FileSystems.getDefault().getPath(abs(f));
    }

    public static boolean isSymlink(File file) {
        return Files.isSymbolicLink(path(file));
    }

    public static String toStringExcludingLines(File file, String prefix) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().startsWith(prefix)) sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static String dirname(String path) {
        if (empty(path)) throw new NullPointerException("dirname: path was empty");
        int pos = path.lastIndexOf('/');
        if (pos == -1) return ".";
        if (path.endsWith("/")) path = chop(path);
        return path.substring(0, pos);
    }

    public static String basename(String path) {
        if (empty(path)) throw new NullPointerException("basename: path was empty");
        int pos = path.lastIndexOf('/');
        if (pos == -1) return path;
        if (pos == path.length()-1) throw new IllegalArgumentException("basename: invalid path: "+path);
        return path.substring(pos + 1);
    }

    // quick alias for getting an absolute path
    public static String abs(File path) { return path == null ? "null" : path.getAbsolutePath(); }
    public static String abs(Path path) { return path == null ? "null" : abs(path.toFile()); }
    public static String abs(String path) { return path == null ? "null" : abs(new File(path)); }

    public static File mkdirOrDie(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            final String msg = "mkdirOrDie: error creating: " + abs(dir);
            log.error(msg);
            die(msg);
        }
        assertIsDir(dir);
        return dir;
    }

    public static boolean ensureDirExists(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("ensureDirExists: error creating: " + abs(dir));
            return false;
        }
        if (!dir.isDirectory()) {
            log.error("ensureDirExists: not a directory: " + abs(dir));
            return false;
        }
        return true;
    }

    public static void assertIsDir(File dir) {
        if (!dir.isDirectory()) {
            final String msg = "assertIsDir: not a dir: " + abs(dir);
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    public static String extension(File f) { return extension(abs(f)); }

    public static String extension(String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot == -1) return "";
        return name.substring(lastDot);
    }

    public static String removeExtension(File f, String ext) {
        return f.getName().substring(0, f.getName().length() - ext.length());
    }

    /**
     * @param dir The directory to search
     * @return The most recently modified file, or null if the dir does not exist, is not a directory, or does not contain any files
     */
    public static File mostRecentFile(File dir) {
        if (!dir.exists()) return null;
        File newest = null;
        for (File file : list(dir)) {
            if (file.isDirectory()) {
                file = mostRecentFile(file);
                if (file == null) continue;
            }
            if (file.isFile()) {
                if (newest == null) {
                    newest = file;
                } else if (file.lastModified() > newest.lastModified()) {
                    newest = file;
                }
            }
        }
        return newest;
    }

    public static boolean mostRecentFileIsNewerThan(File dir, long time) {
        final File newest = mostRecentFile(dir);
        return newest != null && newest.lastModified() > time;
    }

    public static File mkHomeDir(String subDir) {

        final String homeDir = getUserHomeDir();
        if (empty(homeDir)) die("mkHomeDir: System.getProperty(\"user.home\") returned nothing useful: "+homeDir);

        if (!subDir.startsWith("/")) subDir = "/" + subDir;

        return mkdirOrDie(new File(homeDir + subDir));
    }

    public static String getUserHomeDir() {
        // todo: ensure this works correctly in sandboxed-environments (mac app store)
        return System.getProperty("user.home");
    }

    public static void copyFile(File from, File to) {
        try {
            if (!to.getParentFile().exists() && !to.getParentFile().mkdirs()) {
                die("Error creating parent dir: " + abs(to.getParentFile()));
            }
            FileUtils.copyFile(from, to);
        } catch (IOException e) {
            die("copyFile: "+e, e);
        }
    }

    public static void deleteOrDie(File f) {
        if (f == null) return;
        if (f.exists()) {
            FileUtils.deleteQuietly(f);
            if (f.exists()) die("delete: Error deleting: "+abs(f));
        }
    }
}
