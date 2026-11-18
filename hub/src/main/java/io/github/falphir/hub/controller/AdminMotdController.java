package io.github.falphir.hub.controller;

import java.util.List;

import io.github.falphir.hub.service.MotdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/motds")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminMotdController {

    private final MotdService motds;

    public AdminMotdController(MotdService motds) {
        this.motds = motds;
    }

    @GetMapping
    @Operation(summary = "Network MOTDs", description = "Every MOTD servers can pick, by name, each with the ids of the servers using it.")
    public List<MotdService.MotdView> list() {
        return motds.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a MOTD", description = "The first one added becomes the default. 409 if the name is taken.")
    public MotdService.MotdView create(@Valid @RequestBody MotdRequest request) {
        return motds.create(request.name(), request.text());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a MOTD",
            description = "Servers using it show the change within a heartbeat (with a mod that applies MOTDs).")
    public MotdService.MotdView update(@PathVariable long id, @Valid @RequestBody MotdRequest request) {
        return motds.update(id, request.name(), request.text());
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "Make a MOTD the default", description = "The one preselected when creating a server.")
    public MotdService.MotdView makeDefault(@PathVariable long id) {
        return motds.makeDefault(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a MOTD", description = "409 while servers use it; the message names them.")
    public void delete(@PathVariable long id) {
        motds.delete(id);
    }

    public record MotdRequest(
            @Schema(example = "WTC") @NotBlank @Size(max = 60) String name,
            @Schema(description = "Two lines at most. {modpack}, {version} and {name} are filled in from each server's listing; "
                    + "§ or & codes add colour. \\n typed literally counts as a line break.")
            @NotBlank @Size(max = 300) String text) {}
}
