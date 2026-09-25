package io.github.falphir.hub.service;

import java.util.List;

import io.github.falphir.hub.entity.AdminUser;
import io.github.falphir.hub.repository.AdminUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {

    private final AdminUserRepository repository;
    private final PasswordEncoder encoder;

    public AdminUserService(AdminUserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Transactional
    public AdminUser create(String username, String password) {
        if (repository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin '" + username + "' already exists");
        }
        return repository.save(new AdminUser(username, encoder.encode(password)));
    }

    @Transactional(readOnly = true)
    public List<AdminUser> list() {
        return repository.findAll();
    }

    /** Updates an admin's password and/or enabled state. Either argument may be null to leave it unchanged. */
    @Transactional
    public AdminUser update(Long id, String newPassword, Boolean enabled) {
        AdminUser admin = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown admin"));
        if (Boolean.FALSE.equals(enabled)) {
            requireAnotherEnabledAdmin(id);
        }
        if (newPassword != null) {
            admin.changePassword(encoder.encode(newPassword));
        }
        if (enabled != null) {
            admin.setEnabled(enabled);
        }
        return admin;
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown admin");
        }
        requireAnotherEnabledAdmin(id);
        repository.deleteById(id);
    }

    private void requireAnotherEnabledAdmin(Long excludingId) {
        if (repository.countByEnabledTrueAndIdNot(excludingId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last admin");
        }
    }
}
