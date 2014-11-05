package org.cobbzilla.util.io;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class FilesystemWatcher implements Runnable {

    public static final long STOP_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private final Thread thread = new Thread(this);
    private final AtomicBoolean done = new AtomicBoolean(false);
    @Getter private final Path path;

    public FilesystemWatcher(Path path) { this.path = path; }

    public synchronized void start () {
        done.set(false);
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop () {
        done.set(true);
        thread.interrupt();
        long start = System.currentTimeMillis();
        while (thread.isAlive() && System.currentTimeMillis() - start < STOP_TIMEOUT) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IllegalStateException("interrupted while trying to stop " + this + ": " + e, e);
            }
        }
        if (thread.isAlive()) {
            log.warn("Watcher thread did not die, killing it");
            thread.stop();
        }
    }

    // print the events and the affected file
    private void handleEvent(WatchEvent<?> event) {

        WatchEvent.Kind<?> kind = event.kind();
        Path path = event.context() instanceof Path ? (Path) event.context() : null;
        File file = path == null ? null : path.toFile();

        if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
            if (file.isDirectory()) {
                onDirCreated(toFile(path));
            } else {
                onFileCreated(toFile(path));
            }
        } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            if (file.isDirectory()) {
                onDirDeleted(toFile(path));
            } else {
                onFileDeleted(toFile(path));
            }
        } else if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            if (file.isDirectory()) {
                onDirModified(toFile(path));
            } else {
                onFileModified(toFile(path));
            }
        }
    }

    private String abs(Path path) { return path.toFile().getAbsolutePath(); }
    private String abs(File path) { return path.getAbsolutePath(); }

    protected void onDirCreated(File path) { log.info("dir created: "+abs(path)); }
    protected void onFileCreated(File path) { log.info("file created: "+abs(path)); }

    protected void onDirModified(File path) { log.info("dir modified: "+abs(path)); }
    protected void onFileModified(File path) { log.info("file modified: "+abs(path)); }

    protected void onDirDeleted(File path) { log.info("dir deleted: "+abs(path)); }
    protected void onFileDeleted(File path) { log.info("file deleted: "+abs(path)); }

    public File toFile(Path p) { return new File(path.toFile(), p.toFile().getName()); }

    @Override
    public void run() {
        try {
            log.info("Registering watch service on "+path);
            final WatchService watchService = path.getFileSystem().newWatchService();
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            // loop forever to watch directory
            while (!done.get()) {
                final WatchKey watchKey;
                watchKey = watchService.take(); // this call is blocking until events are present

                // poll for file system events on the WatchKey
                log.info("Waiting for FS events on "+path);
                for (final WatchEvent<?> event : watchKey.pollEvents()) {
                    log.info("Handling event: "+event.kind().name()+" "+event.context());
                    handleEvent(event);
                }

                // if the watched directed gets deleted, get out of run method
                if (!watchKey.reset()) {
                    log.warn("watchKey could not be reset, perhaps path ("+path+") was removed?");
                    watchKey.cancel();
                    watchService.close();
                    break;
                }
            }

        } catch (InterruptedException e) {
            throw new IllegalStateException("watch thread interrupted, exiting: "+e, e);

        } catch (Exception e) {
            throw new IllegalStateException("error in watch thread, exiting: "+e, e);
        }
    }
}
