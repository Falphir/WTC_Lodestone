package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import io.github.falphir.hub.entity.AdminUser;
import io.github.falphir.hub.repository.AdminUserRepository;

@SpringBootTest
@Transactional
class AdminUserDetailsServiceTest {

    @Autowired
    private AdminUserDetailsService service;

    @Autowired
    private AdminUserRepository repository;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    void loadsAnExistingAdminWithItsStoredHash() {
        repository.save(new AdminUser("carol", encoder.encode("carol-pass")));

        UserDetails carol = service.loadUserByUsername("carol");

        assertThat(encoder.matches("carol-pass", carol.getPassword())).isTrue();
        assertThat(encoder.matches("wrong-pass", carol.getPassword())).isFalse();
    }

    @Test
    void rejectsAnUnknownUsername() {
        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
