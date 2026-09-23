package io.github.falphir.hub.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Info", description = "General information about the hub")
public class InfoController {

    private final String appName;

    public InfoController(@Value("${spring.application.name}") String appName) {
        this.appName = appName;
    }

    @GetMapping("/info")
    @Operation(summary = "Basic hub information",
            description = "Public endpoint returning the hub's name, status and current server time.")
    public InfoResponse info() {
        return new InfoResponse(appName, "online", Instant.now());
    }

    public record InfoResponse(String name, String status, Instant serverTime) {}
}