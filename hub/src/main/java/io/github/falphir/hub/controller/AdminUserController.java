package io.github.falphir.hub.controller;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.AdminUser;
import io.github.falphir.hub.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
public class AdminUserController {

    private final AdminUserService adminUsers;

    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    @GetMapping
    @Operation(summary = "List admins")
    public List<AdminView> list() {
        return adminUsers.list().stream().map(AdminView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an admin")
    public AdminView create(@Valid @RequestBody CreateAdminRequest request) {
        return AdminView.from(adminUsers.create(request.username(), request.password()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change an admin's password and/or enabled state",
            description = "Refuses to disable or delete the last enabled admin.")
    public AdminView update(@PathVariable Long id, @Valid @RequestBody UpdateAdminRequest request) {
        return AdminView.from(adminUsers.update(id, request.password(), request.enabled()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an admin")
    public void delete(@PathVariable Long id) {
        adminUsers.delete(id);
    }

    public record CreateAdminRequest(
            @Schema(example = "carol")
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{3,64}", message = "3-64 letters, numbers, _ and - only") String username,

            @Schema(example = "a-strong-password")
            @NotBlank @Size(min = 8, max = 100) String password) {}

    public record UpdateAdminRequest(
            @Size(min = 8, max = 100) String password,
            Boolean enabled) {}

    public record AdminView(Long id, String username, boolean enabled, Instant createdAt) {
        static AdminView from(AdminUser a) {
            return new AdminView(a.getId(), a.getUsername(), a.isEnabled(), a.getCreatedAt());
        }
    }
}
