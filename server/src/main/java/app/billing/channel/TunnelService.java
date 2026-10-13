package app.billing.channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import app.billing.config.AppProperties;
import jakarta.annotation.PreDestroy;

/**
 * Public access through Cloudflare Tunnel (cloudflared). No router or port-forwarding needed,
 * works behind CGNAT.
 */
@Service
public class TunnelService {

    public enum State { STOPPED, CONNECTING, RUNNING, RECONNECTING, ERROR }

    private static final Logger log = LoggerFactory.getLogger(TunnelService.class);
    private static final Pattern QUICK_URL = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    private final AppProperties props;
    private final TunnelRetryPolicy retryPolicy;
    private final ScheduledExecutorService scheduler;

    private Process process;
    private volatile boolean wantRunning;
    private volatile String pendingToken;
    private volatile String pendingFixedUrl;
    private volatile State state = State.STOPPED;
    private volatile String url;
    private volatile String error;
    private ScheduledFuture<?> retryTask;

    @Autowired
    public TunnelService(AppProperties props) {
        this(props, new TunnelRetryPolicy(), defaultScheduler());
    }

    /** Package-private for tests. */
    TunnelService(AppProperties props, TunnelRetryPolicy retryPolicy, ScheduledExecutorService scheduler) {
        this.props = props;
        this.retryPolicy = retryPolicy;
        this.scheduler = scheduler;
    }

    public State state() {
        return state;
    }

    public String url() {
        return url;
    }

    public String error() {
        return error;
    }

    public synchronized void start(String tunnelToken, String fixedPublicUrl) {
        wantRunning = true;
        pendingToken = tunnelToken;
        pendingFixedUrl = fixedPublicUrl;
        retryPolicy.reset();
        cancelRetry();
        launchNow();
    }

    @PreDestroy
    public synchronized void stop() {
        wantRunning = false;
        cancelRetry();
        destroyProcess();
        state = State.STOPPED;
        url = null;
        error = null;
    }

    private void launchNow() {
        destroyProcess();
        String exe = CloudflaredLocator.find(props);
        if (exe == null) {
            fail("cloudflared not found. Reinstall the app or place cloudflared in " + props.dataDir() + "/bin.");
            scheduleRetryIfWanted();
            return;
        }

        boolean named = pendingToken != null && !pendingToken.isBlank();
        List<String> cmd = new ArrayList<>(List.of(exe, "tunnel", "--no-autoupdate"));
        if (named) {
            cmd.addAll(List.of("run", "--token", pendingToken.trim()));
        } else {
            cmd.addAll(List.of("--url", "http://127.0.0.1:" + props.publicPort()));
        }

        try {
            if (state != State.RECONNECTING) {
                state = State.CONNECTING;
                error = null;
            }
            url = named ? blankToNull(pendingFixedUrl) : null;
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            Process p = process;
            Thread reader = new Thread(() -> readOutput(p, named), "cloudflared-output");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            fail("Could not start cloudflared: " + e.getMessage());
            scheduleRetryIfWanted();
        }
    }

    private void readOutput(Process p, boolean named) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.debug("cloudflared: {}", line);
                if (isNoInternetLine(line)) {
                    synchronized (TunnelService.this) {
                        if (process == p) {
                            fail("No internet connection");
                            scheduleRetryIfWanted();
                        }
                    }
                }
                if (!named) {
                    Matcher m = QUICK_URL.matcher(line);
                    if (m.find()) {
                        url = m.group();
                    }
                }
                if (line.contains("Registered tunnel connection")) {
                    state = State.RUNNING;
                    error = null;
                    retryPolicy.reset();
                }
            }
        } catch (IOException ignored) {
            // process ended
        }
        synchronized (this) {
            if (process == p) {
                process = null;
                if (wantRunning) {
                    state = State.RECONNECTING;
                    error = "Reconnecting…";
                    scheduleRetryIfWanted();
                } else {
                    fail("Tunnel stopped.");
                }
            }
        }
    }

    static boolean isNoInternetLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("failed to lookup")
                || lower.contains("no such host")
                || lower.contains("network is unreachable")
                || lower.contains("i/o timeout")
                || lower.contains("connection refused")
                || lower.contains("dial tcp")
                || lower.contains("unable to reach")
                || lower.contains("dns: server misbehaving");
    }

    private void scheduleRetryIfWanted() {
        if (!wantRunning) {
            return;
        }
        cancelRetry();
        long delayMs = retryPolicy.scheduleNextRetryMs();
        state = State.RECONNECTING;
        if (error == null || error.isBlank()) {
            error = "Reconnecting…";
        }
        retryTask = scheduler.schedule(this::retryTick, delayMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void retryTick() {
        if (!wantRunning) {
            return;
        }
        error = "Reconnecting…";
        state = State.RECONNECTING;
        launchNow();
    }

    private void cancelRetry() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }

    private void destroyProcess() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    private void fail(String message) {
        log.warn(message);
        state = State.ERROR;
        error = message;
        url = null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ScheduledExecutorService defaultScheduler() {
        return java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tunnel-retry");
            t.setDaemon(true);
            return t;
        });
    }
}
