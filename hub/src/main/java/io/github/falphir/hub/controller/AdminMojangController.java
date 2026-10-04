package io.github.falphir.hub.controller;

import io.github.falphir.hub.service.MojangProfiles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the hub's existing Mojang lookup (already used internally by WhitelistService) to
 * admin clients, so the Discord bot resolves usernames through the hub instead of hitting
 * Mojang's API itself -- one implementation of "does this Minecraft account exist" instead of two.
 */
@RestController
@RequestMapping("/api/admin/mojang")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminMojangController {

    private final MojangProfiles mojang;

    public AdminMojangController(MojangProfiles mojang) {
        this.mojang = mojang;
    }

    @GetMapping("/{name}")
    @Operation(summary = "Resolve a Minecraft username", description = "Looks it up via Mojang. 404 if no such account (including an invalid name).")
    public ResponseEntity<ProfileView> lookup(@PathVariable String name) {
        return mojang.lookup(name).map(p -> new ProfileView(p.uuid(), p.name())).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record ProfileView(String uuid, String name) {}
}
