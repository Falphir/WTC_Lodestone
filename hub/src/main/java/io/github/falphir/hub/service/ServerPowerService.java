package io.github.falphir.hub.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Start, stop, restart and kill for hub servers that run on PufferPanel, sent through the hub so the
 * browser never holds the panel's credentials.
 * <p>
 * PufferPanel 2.7.1 has no restart, so a restart is: stop, poll the status until the process is gone,
 * then start. The stop can take minutes for a modpack, so that runs on its own virtual thread and the
 * request answers straight away. If the server hasn't stopped within the limit, it is left alone rather
 * than started, so it can never end up running twice.
 * <p>
 * Every action goes to the hub's log. ponytail: there's no audit log yet; when there is, these
 * lines become its records.
 */
@Service
public class ServerPowerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerPowerService.class);
    private static final Set<String> ACTIONS = Set.of("start", "stop", "kill", "restart");

    public record PowerStatus(boolean running, boolean restarting) {}

    private final ServerService servers;
    private final PufferPanelClient panel;
    private final ApplicationEventPublisher events;
    private final Duration pollEvery;
    private final Duration stopTimeout;

    private final ExecutorService restarts = Executors.newVirtualThreadPerTaskExecutor();
    /** Panel servers with a restart under way, so a second one can't overlap it. */
    private final Set<String> restarting = ConcurrentHashMap.newKeySet();

    @Autowired
    public ServerPowerService(ServerService servers, PufferPanelClient panel, ApplicationEventPublisher events) {
        this(servers, panel, events, Duration.ofSeconds(2), Duration.ofMinutes(3));
    }

    /** Lets tests restart in milliseconds. */
    ServerPowerService(ServerService servers, PufferPanelClient panel, ApplicationEventPublisher events,
            Duration pollEvery, Duration stopTimeout) {
        this.servers = servers;
        this.panel = panel;
        this.events = events;
        this.pollEvery = pollEvery;
        this.stopTimeout = stopTimeout;
    }

    public PowerStatus status(String hubId) {
        String panelId = panelIdOf(hubId);
        return new PowerStatus(call(() -> panel.isRunning(panelId)), restarting.contains(panelId));
    }

    public void act(String hubId, String action, String admin) {
        if (!ACTIONS.contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown power action '" + action + "'");
        }
        String panelId = panelIdOf(hubId);
        if (restarting.contains(panelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "'" + hubId + "' is already restarting");
        }
        LOGGER.info("{} {} {} (PufferPanel {})", admin, action, hubId, panelId);

        if (action.equals("restart")) {
            if (!restarting.add(panelId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "'" + hubId + "' is already restarting");
            }
            try {
                call(() -> { panel.power(panelId, "stop"); return null; });
            } catch (RuntimeException e) {
                restarting.remove(panelId);
                throw e;
            }
            restarts.submit(() -> finishRestart(hubId, panelId));
        } else {
            call(() -> { panel.power(panelId, action); return null; });
        }
        events.publishEvent(HubEvent.servers(hubId));
    }

    /** On its own thread: wait for the stop to land, then start. */
    private void finishRestart(String hubId, String panelId) {
        try {
            Instant giveUp = Instant.now().plus(stopTimeout);
            while (panel.isRunning(panelId)) {
                if (Instant.now().isAfter(giveUp)) {
                    LOGGER.warn("Restart of {} gave up: still running {} after the stop, so it wasn't started again",
                            hubId, stopTimeout);
                    return;
                }
                Thread.sleep(pollEvery);
            }
            panel.power(panelId, "start");
            LOGGER.info("Restart of {} done: stopped and started again", hubId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOGGER.warn("Restart of {} failed: {}", hubId, e.getMessage());
        } finally {
            restarting.remove(panelId);
            events.publishEvent(HubEvent.servers(hubId));
        }
    }

    private String panelIdOf(String hubId) {
        if (!panel.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PufferPanel isn't configured");
        }
        return servers.pufferpanelIdOf(hubId);
    }

    /** A panel failure becomes a 502 worded for the admin. */
    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (PufferPanelClient.PanelException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
