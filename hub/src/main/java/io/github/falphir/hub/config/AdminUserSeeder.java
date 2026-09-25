package io.github.falphir.hub.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.falphir.hub.entity.AdminUser;
import io.github.falphir.hub.repository.AdminUserRepository;

/** Creates the admins listed in lodestone.admins that don't already exist in the database. */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private final AdminUserRepository repository;
    private final LodestoneProperties properties;
    private final PasswordEncoder encoder;

    public AdminUserSeeder(AdminUserRepository repository, LodestoneProperties properties, PasswordEncoder encoder) {
        this.repository = repository;
        this.properties = properties;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.admins() == null) return;
        for (LodestoneProperties.Admin admin : properties.admins()) {
            if (!repository.existsByUsername(admin.username())) {
                repository.save(new AdminUser(admin.username(), encoder.encode(admin.password())));
            }
        }
    }
}
