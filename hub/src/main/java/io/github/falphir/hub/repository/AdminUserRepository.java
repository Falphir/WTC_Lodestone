package io.github.falphir.hub.repository;

import java.util.Optional;

import io.github.falphir.hub.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByEnabledTrueAndIdNot(Long id);
}
