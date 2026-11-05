package io.github.falphir.hub.controller;

import java.util.List;

import io.github.falphir.hub.service.CurseForgeClient;
import io.github.falphir.hub.service.ServerProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CurseForge modpacks to create a server from, searched through the hub so its API key never reaches a browser. */
@RestController
@RequestMapping("/api/admin/curseforge")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminCurseForgeController {

    private final CurseForgeClient curseForge;
    private final ServerProvisioningService provisioning;

    public AdminCurseForgeController(CurseForgeClient curseForge, ServerProvisioningService provisioning) {
        this.curseForge = curseForge;
        this.provisioning = provisioning;
    }

    @GetMapping
    @Operation(summary = "CurseForge connection", description = "Whether the hub has a CurseForge API key to search modpacks with.")
    public ConnectionView connection() {
        return new ConnectionView(curseForge.isConfigured());
    }

    public record ConnectionView(@Schema(description = "False until LODESTONE_CURSEFORGE_API_KEY is set") boolean configured) {}

    @GetMapping("/modpacks")
    @Operation(summary = "Search modpacks",
            description = "Minecraft modpacks on CurseForge matching `q`, most popular first. `downloadable` false means the "
                    + "author only allows downloads through the CurseForge app, so its files can't be put on a server from here. "
                    + "503 without an API key, 502 if CurseForge fails.")
    public List<CurseForgeClient.Modpack> search(@RequestParam(defaultValue = "") String q) {
        return provisioning.searchModpacks(q);
    }

    @GetMapping("/modpacks/{id}/files")
    @Operation(summary = "A modpack's versions", description = "The versions of a pack that come with server files, newest first.")
    public List<CurseForgeClient.PackFile> files(@PathVariable int id) {
        return provisioning.modpackFiles(id);
    }

    @GetMapping("/modpacks/{id}/files/{fileId}/setup")
    @Operation(summary = "What a modpack version runs on",
            description = "The PufferPanel template for this pack version and that template's settings filled in from the pack's "
                    + "manifest: loader version, Minecraft version, Java and recommended memory. `template` is null when the "
                    + "manifest can't be read or names a loader with no matching template; the versions it found still come back.")
    public ServerProvisioningService.PackSetup setup(@PathVariable int id, @PathVariable int fileId) {
        return provisioning.packSetup(id, fileId);
    }
}
