package org.cobbzilla.util.daemon;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public abstract class SimpleDaemon implements Runnable {

    protected static final DateTimeFormatter DFORMAT = DateTimeFormat.forPattern("yyyy-MMM-dd HH:mm:ss");

    public SimpleDaemon () {
        this.name = getClass().getName();
    }

    public SimpleDaemon (String name) {
        this.name = name;
    }

    @Getter private String name;
    @Getter private long lastProcessTime = 0;

    private volatile Thread mainThread = null;
    private final Object lock = new Object();
    private volatile boolean isDone = false;

    public void start() {
        log.info(name+": Starting daemon");
        synchronized (lock) {
            if (mainThread != null) {
                log.warn(name+": daemon is already running, not starting it again");
                return;
            }
            mainThread = new Thread(this);
        }
        mainThread.setDaemon(true);
        mainThread.start();
    }

    private boolean alreadyStopped() {
        if (mainThread == null) {
            log.warn(name+": daemon is already stopped");
            return true;
        }
        return false;
    }

    public void stop() {
        if (alreadyStopped()) return;
        isDone = true;
        mainThread.interrupt();
        // Let's leave it at that, this thread is a daemon anyway.
    }

    public void interrupt() {
        if (alreadyStopped()) return;
        mainThread.interrupt();
    }

    /**
     * @deprecated USE WITH CAUTION -- calls Thread.stop() !!
     */
    private void kill() {
        if (alreadyStopped()) return;
        isDone = true;
        mainThread.stop();
    }

    /**
     * Tries to stop the daemon.  If it doesn't stop within "wait" millis,
     * it gets killed.
     */
    public void stopWithPossibleKill(long wait) {
        stop();
        long start = System.currentTimeMillis();
        while (getIsAlive()
                && (System.currentTimeMillis() - start < wait)) {
            sleep(25, "stopWithPossibleKill");
        }
        if (getIsAlive()) {
            kill();
        }
    }

    protected void init() throws Exception {}

    public void run() {

        long delay = getStartupDelay();
        if (delay > 0) {
            log.debug(name+": Delaying daemon startup for " + delay + "ms...");
            sleep(delay, "run[startup-delay]");
        }
        log.debug(name+": Daemon thread now running");

        try {
            log.debug(name+": Daemon thread invoking init");
            init();

            while (!isDone) {
                log.debug(name+": Daemon thread invoking process");
                process();
                lastProcessTime = System.currentTimeMillis();
                if (isDone) return;
                sleep(getSleepTime(), "run[post-processing]");
            }
        } catch (Exception e) {
            log.error(name+": Error in daemon, exiting: " + e, e);

        } finally {
            _cleanup();
            try {
                cleanup();
            } catch (Exception e) {
                log.error(name+": Error cleaning up, exiting and ignoring error: " + e, e);
            }
        }
    }

    protected long getStartupDelay() { return 0; }

    protected abstract long getSleepTime();

    protected abstract void process();

    protected void cleanup() throws Exception {}

    public boolean getIsDone() { return isDone; }

    public boolean getIsAlive() {
        try {
            return mainThread != null && mainThread.isAlive();
        } catch (NullPointerException npe) {
            return false;
        }
    }

    private void _cleanup() {
        mainThread = null;
        isDone = true;
    }

    public String getStatus() {
        return "isDone=" + getIsDone()
                + "\nlastProcessTime=" + DFORMAT.print(lastProcessTime)
                + "\nsleepTime=" + getSleepTime()+"ms";
    }
}
