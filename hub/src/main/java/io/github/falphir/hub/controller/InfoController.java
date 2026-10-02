package io.github.falphir.hub.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.falphir.hub.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Info", description = "General information about the hub")
public class InfoController {

    private final String appName;
    private final ServerService servers;

    public InfoController(@Value("${spring.application.name}") String appName, ServerService servers) {
        this.appName = appName;
        this.servers = servers;
    }

    @GetMapping("/info")
    @Operation(summary = "Basic hub information",
            description = "Public endpoint returning the hub's name, status, current server time and the heartbeat "
                    + "timing the dashboard uses to draw gaps.")
    public InfoResponse info() {
        return new InfoResponse(appName, "online", Instant.now(), servers.heartbeatInterval().toSeconds(),
                servers.offlineAfter().toSeconds(), ServerService.LAGGING_BELOW_TPS);
    }

    public record InfoResponse(String name, String status, Instant serverTime, long heartbeatIntervalSeconds,
            long offlineAfterSeconds, double laggingBelowTps) {}
}