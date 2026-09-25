package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.falphir.hub.entity.AdminUser;

@SpringBootTest
@Transactional
class AdminUserServiceTest {

    @Autowired
    private AdminUserService service;

    @Test
    void refusesToRemoveTheLastEnabledAdmin() {
        AdminUser dave = service.create("dave", "dave-password");
        AdminUser seeded = service.list().stream()
                .filter(a -> !a.getId().equals(dave.getId()))
                .findFirst()
                .orElseThrow();

        service.delete(seeded.getId());

        assertThatThrownBy(() -> service.delete(dave.getId()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.list()).extracting(AdminUser::getId).containsExactly(dave.getId());
    }
}
