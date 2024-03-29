package org.cobbzilla.util.system;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.apache.commons.io.output.TeeOutputStream;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.io.FileUtil;

import java.io.*;
import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;

@Slf4j
public class CommandShell {

    protected static final String EXPORT_PREFIX = "export ";

    public static final String CHMOD = "chmod";
    public static final String CHGRP = "chgrp";
    public static final String CHOWN = "chown";

    private static final int[] DEFAULT_EXIT_VALUES = {0};

    public static Map<String, String> loadShellExports (String userFile) throws IOException {
        File file = new File(System.getProperty("user.home") + File.separator + userFile);
        if (!file.exists()) {
            throw new IllegalArgumentException("file does not exist: "+abs(file));
        }
        return loadShellExports(file);
    }

    public static Map<String, String> loadShellExports (File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return loadShellExports(in);
        }
    }

    public static Map<String, String> loadShellExports (InputStream in) throws IOException {
        final Map<String, String> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line, key, value;
            int eqPos;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) continue;
                if (line.startsWith(EXPORT_PREFIX)) {
                    line = line.substring(EXPORT_PREFIX.length()).trim();
                    eqPos = line.indexOf('=');
                    if (eqPos != -1) {
                        key = line.substring(0, eqPos).trim();
                        value = line.substring(eqPos+1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            // strip quotes if found
                            value = value.substring(1, value.length()-1);
                        }
                        map.put(key, value);
                    }
                }
            }
        }
        return map;
    }

    public static Map<String, String> loadShellExportsOrDie (String f) {
        try { return loadShellExports(f); } catch (Exception e) {
            return die("loadShellExportsOrDie: "+e, e);
        }
    }

    public static Map<String, String> loadShellExportsOrDie (File f) {
        try { return loadShellExports(f); } catch (Exception e) {
            return die("loadShellExportsOrDie: "+e, e);
        }
    }

    public static void replaceShellExport (String f, String name, String value) throws IOException {
        replaceShellExports(new File(f), MapBuilder.build(name, value));
    }

    public static void replaceShellExport (File f, String name, String value) throws IOException {
        replaceShellExports(f, MapBuilder.build(name, value));
    }

    public static void replaceShellExports (String f, Map<String, String> exports) throws IOException {
        replaceShellExports(new File(f), exports);
    }

    public static void replaceShellExports (File f, Map<String, String> exports) throws IOException {

        // validate -- no quote chars allowed for security reasons
        for (String key : exports.keySet()) {
            if (key.contains("\"") || key.contains("\'")) throw new IllegalArgumentException("replaceShellExports: name cannot contain a quote character: "+key);
            String value = exports.get(key);
            if (value.contains("\"") || value.contains("\'")) throw new IllegalArgumentException("replaceShellExports: value for "+key+" cannot contain a quote character: "+value);
        }

        // read entire file as a string
        final String contents = FileUtil.toString(f);

        // walk file line by line and look for replacements to make, overwrite file.
        final Set<String> replaced = new HashSet<>(exports.size());
        try (Writer w = new FileWriter(f)) {
            for (String line : contents.split("\n")) {
                line = line.trim();
                boolean found = false;
                for (String key : exports.keySet()) {
                    if (!line.startsWith("#") && line.matches("^\\s*export\\s+" + key + "\\s*=.*")) {
                        w.write("export " + key + "=\"" + exports.get(key) + "\"");
                        replaced.add(key);
                        found = true;
                        break;
                    }
                }
                if (!found) w.write(line);
                w.write("\n");
            }

            for (String key : exports.keySet()) {
                if (!replaced.contains(key)) {
                    w.write("export "+key+"=\""+exports.get(key)+"\"\n");
                }
            }
        }
    }

    public static MultiCommandResult exec (Collection<String> commands) throws IOException {
        final MultiCommandResult result = new MultiCommandResult();
        for (String c : commands) {
            Command command = new Command(c);
            result.add(command, exec(c));
            if (result.hasException()) return result;
        }
        return result;
    }

    public static CommandResult exec (String command) throws IOException {
        return exec(CommandLine.parse(command));
    }

    public static CommandResult exec (CommandLine command) throws IOException {
        return exec(new Command(command));
    }

    public static CommandResult exec (Command command) throws IOException {

        final DefaultExecutor executor = new DefaultExecutor();

        final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        OutputStream out = command.hasOut() ? new TeeOutputStream(outBuffer, command.getOut()) : outBuffer;
        if (command.isCopyToStandard()) out = new TeeOutputStream(out, System.out);

        final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        OutputStream err = command.hasErr() ? new TeeOutputStream(errBuffer, command.getErr()) : errBuffer;
        if (command.isCopyToStandard()) err = new TeeOutputStream(err, System.err);

        final ExecuteStreamHandler handler = new PumpStreamHandler(out, err, command.getInputStream());
        executor.setStreamHandler(handler);

        if (command.hasDir()) executor.setWorkingDirectory(command.getDir());
        executor.setExitValues(command.getExitValues());

        try {
            final int exitValue = executor.execute(command.getCommandLine(), command.getEnv());
            return new CommandResult(exitValue, outBuffer, errBuffer);

        } catch (Exception e) {
            return new CommandResult(e, outBuffer, errBuffer);
        }
    }

    public static int chmod (File file, String perms) throws IOException {
        return chmod(abs(file), perms, false);
    }

    public static int chmod (File file, String perms, boolean recursive) throws IOException {
        return chmod(abs(file), perms, recursive);
    }

    public static int chmod (String file, String perms) throws IOException {
        return chmod(file, perms, false);
    }

    public static int chmod (String file, String perms, boolean recursive) throws IOException {
        final CommandLine commandLine = new CommandLine(CHMOD);
        if (recursive) commandLine.addArgument("-R");
        commandLine.addArgument(perms);
        commandLine.addArgument(file);
        final Executor executor = new DefaultExecutor();
        return executor.execute(commandLine);
    }

    public static int chgrp(String group, File path) throws IOException {
        return chgrp(group, path, false);
    }

    public static int chgrp(String group, File path, boolean recursive) throws IOException {
        return chgrp(group, abs(path), recursive);
    }

    public static int chgrp(String group, String path) throws IOException {
        return chgrp(group, path, false);
    }

    public static int chgrp(String group, String path, boolean recursive) throws IOException {
        final Executor executor = new DefaultExecutor();
        final CommandLine command = new CommandLine(CHGRP);
        if (recursive) command.addArgument("-R");
        command.addArgument(group).addArgument(path);
        return executor.execute(command);
    }

    public static int chown(String owner, File path) throws IOException { return chown(owner, path, false); }

    public static int chown(String owner, File path, boolean recursive) throws IOException {
        return chown(owner, abs(path), recursive);
    }

    public static int chown(String owner, String path) throws IOException { return chown(owner, path, false); }

    public static int chown(String owner, String path, boolean recursive) throws IOException {
        final Executor executor = new DefaultExecutor();
        final CommandLine command = new CommandLine(CHOWN);
        if (recursive) command.addArgument("-R");
        command.addArgument(owner).addArgument(path);
        return executor.execute(command);
    }

    public static String toString(String command) {
        try {
            return exec(command).getStdout().trim();
        } catch (IOException e) {
            return die("Error executing: "+command+": "+e, e);
        }
    }

    public static String hostname () { return toString("hostname"); }
    public static String domainname() { return toString("hostname -d"); }
    public static String whoami() { return toString("whoami"); }
    public static boolean isRoot() { return "root".equals(whoami()); }

    public static String locale () {
        return execScript("locale | grep LANG= | tr '=.' ' ' | awk '{print $2}'").trim();
    }

    public static String lang () {
        return execScript("locale | grep LANG= | tr '=_' ' ' | awk '{print $2}'").trim();
    }

    public static File tempScript (String contents) {
        contents = "#!/bin/bash\n\n"+contents;
        try {
            final File temp = File.createTempFile("tempScript", ".sh");
            FileUtil.toFile(temp, contents);
            chmod(temp, "700");
            return temp;

        } catch (Exception e) {
            return die("tempScript("+contents+") failed: "+e, e);
        }
    }

    public static String execScript (String contents) { return execScript(contents, null); }

    public static String execScript (String contents, Map<String, String> env) {
        try {
            @Cleanup("delete") final File script = tempScript(contents);
            final Command command = new Command(new CommandLine(script)).setEnv(env);
            return exec(command).getStdout();
        } catch (Exception e) {
            return die("Error executing: "+e);
        }
    }

    public static CommandResult okResult(CommandResult result) {
        if (result == null || !result.isZeroExitStatus()) die("error: "+result);
        return result;
    }

    public static File home(String user) {
        String path = execScript("cd ~" + user + " && pwd");
        if (empty(path)) die("home("+user+"): no home found for user "+user);
        final File f = new File(path);
        if (!f.exists()) die("home("+user+"): home does not exist "+path);
        return f;
    }
}
