package io.github.falphir.hub.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;

/**
 * Backs off password guessing, per client IP and per username. Spring's own authentication events feed it,
 * so it counts every place a password is checked: Basic on any endpoint, and the /login form.
 * {@code config/LoginThrottleFilter} asks it before the password is even looked at.
 * <p>
 * The username lock only holds back IPs that have failed recently themselves, so someone guessing the
 * bot's name from elsewhere can't lock the bot out of its own clean loopback address.
 * <p>
 * In memory on purpose: a restart forgets everything, which the issue (#39) accepts.
 */
@Service
public class LoginThrottle {

    static final int IP_FREE_FAILURES = 5;
    static final int USER_FREE_FAILURES = 20;
    private static final Duration FIRST_LOCK = Duration.ofMinutes(1);
    private static final Duration MAX_LOCK = Duration.ofMinutes(15);
    /** A key with no failure for this long is forgotten. */
    private static final Duration MEMORY = Duration.ofMinutes(15);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final AuditLog audit;

    public LoginThrottle(AuditLog audit) {
        this.audit = audit;
    }

    /** How long {@code ip} must wait before trying {@code username}; zero when it may try now. */
    public Duration retryAfter(String ip, String username) {
        Instant now = Instant.now();
        Attempts byIp = recent(ipKey(ip), now);
        if (byIp == null) return Duration.ZERO; // a clean IP is never held back
        Duration wait = byIp.remaining(now);
        Attempts byUser = recent(userKey(username), now);
        if (byUser != null && byUser.remaining(now).compareTo(wait) > 0) wait = byUser.remaining(now);
        return wait;
    }

    @EventListener
    public void failed(AuthenticationFailureBadCredentialsEvent event) {
        Authentication auth = event.getAuthentication();
        if (!(auth instanceof UsernamePasswordAuthenticationToken)) return;
        String ip = ipOf(auth);
        String username = clean(auth.getName());
        if (fail(ipKey(ip), IP_FREE_FAILURES)) {
            audit.record("login.lockout", "IP " + ip, IP_FREE_FAILURES + " failed sign-ins, the last as " + username);
        }
        if (fail(userKey(username), USER_FREE_FAILURES)) {
            audit.record("login.lockout", username, USER_FREE_FAILURES + " failed sign-ins, the last from " + ip);
        }
    }

    /** Clears the IP, not the username: one good sign-in shouldn't wipe a guessing run from elsewhere. */
    @EventListener
    public void succeeded(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken) attempts.remove(ipKey(ipOf(auth)));
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    void forgetOld() {
        Instant now = Instant.now();
        attempts.values().removeIf(a -> a.stale(now));
    }

    /** Counts a failure; true when this one first locks the key. */
    private boolean fail(String key, int free) {
        Instant now = Instant.now();
        Attempts updated = attempts.compute(key, (k, a) -> {
            Attempts next = a == null || a.stale(now) ? new Attempts() : a;
            next.failures++;
            next.lastFailure = now;
            if (next.failures >= free) {
                int doublings = Math.min(next.failures - free, 10);
                Duration lock = FIRST_LOCK.multipliedBy(1L << doublings);
                next.lockedUntil = now.plus(lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock);
            }
            return next;
        });
        return updated.failures == free;
    }

    private Attempts recent(String key, Instant now) {
        Attempts a = attempts.get(key);
        return a == null || a.stale(now) ? null : a;
    }

    private static String ipOf(Authentication auth) {
        return auth.getDetails() instanceof WebAuthenticationDetails details ? details.getRemoteAddress() : "unknown";
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String userKey(String username) {
        return "user:" + clean(username);
    }

    /** Usernames here are whatever a stranger typed: no control characters, and short. */
    private static String clean(String username) {
        String s = username == null ? "" : username.replaceAll("\\p{Cntrl}", "").trim();
        return s.length() <= 64 ? s : s.substring(0, 64);
    }

    private static final class Attempts {
        int failures;
        Instant lastFailure = Instant.EPOCH;
        Instant lockedUntil = Instant.EPOCH;

        Duration remaining(Instant now) {
            return now.isBefore(lockedUntil) ? Duration.between(now, lockedUntil) : Duration.ZERO;
        }

        boolean stale(Instant now) {
            return lastFailure.plus(MEMORY).isBefore(now) && !now.isBefore(lockedUntil);
        }
    }
}
