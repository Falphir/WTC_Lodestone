package io.github.falphir.hub.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.github.falphir.hub.entity.AdminUser;
import io.github.falphir.hub.repository.AdminUserRepository;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repository;

    public AdminUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AdminUser admin = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown admin '" + username + "'"));
        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .disabled(!admin.isEnabled())
                .roles("ADMIN")
                .build();
    }
}
