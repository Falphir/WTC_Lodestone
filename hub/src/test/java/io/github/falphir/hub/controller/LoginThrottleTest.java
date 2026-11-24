package io.github.falphir.hub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import io.github.falphir.hub.entity.AuditEntry;
import io.github.falphir.hub.repository.AuditEntryRepository;
import io.github.falphir.hub.service.AdminUserService;

/** The throttle is in memory and shared by the whole test context, so each test uses its own IPs and names. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginThrottleTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminUserService admins;

    @Autowired
    private AuditEntryRepository audit;

    @Test
    void repeatedFailuresThrottleEvenTheRightPassword() throws Exception {
        admins.create("throttle-a", "right-password");
        for (int i = 0; i < 5; i++) signIn("10.39.1.1", "throttle-a", "wrong", status().isUnauthorized());

        signIn("10.39.1.1", "throttle-a", "right-password", status().isTooManyRequests());
        mvc.perform(post("/api/auth/token").with(httpBasic("throttle-a", "right-password")).with(from("10.39.1.1")))
                .andExpect(header().string("Retry-After", "60"));
        // Basic works on every admin endpoint, so it's throttled there too.
        mvc.perform(get("/api/admin/servers").with(httpBasic("throttle-a", "wrong")).with(from("10.39.1.1")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void successResetsTheIp() throws Exception {
        admins.create("throttle-b", "right-password");
        for (int i = 0; i < 4; i++) signIn("10.39.2.1", "throttle-b", "wrong", status().isUnauthorized());
        signIn("10.39.2.1", "throttle-b", "right-password", status().isOk());
        for (int i = 0; i < 4; i++) signIn("10.39.2.1", "throttle-b", "wrong", status().isUnauthorized());
    }

    @Test
    void anotherIpIsNotAffected() throws Exception {
        admins.create("throttle-c", "right-password");
        for (int i = 0; i < 5; i++) signIn("10.39.3.1", "throttle-c", "wrong", status().isUnauthorized());
        signIn("10.39.3.1", "throttle-c", "right-password", status().isTooManyRequests());
        signIn("10.39.3.2", "throttle-c", "right-password", status().isOk());
    }

    @Test
    void usernameLockSparesCleanIps() throws Exception {
        admins.create("throttle-d", "right-password");
        // 5 IPs x 4 failures: no IP is locked, the username is (20).
        for (int ip = 1; ip <= 5; ip++) {
            for (int i = 0; i < 4; i++) signIn("10.39.4." + ip, "throttle-d", "wrong", status().isUnauthorized());
        }
        signIn("10.39.4.1", "throttle-d", "right-password", status().isTooManyRequests());
        signIn("10.39.4.99", "throttle-d", "right-password", status().isOk()); // the bot on its own loopback
    }

    @Test
    void unknownUsernameIsThrottledTheSameWay() throws Exception {
        for (int i = 0; i < 5; i++) signIn("10.39.5.1", "throttle-nobody", "wrong", status().isUnauthorized());
        signIn("10.39.5.1", "throttle-nobody", "wrong", status().isTooManyRequests());
    }

    @Test
    void lockoutIsAudited() throws Exception {
        for (int i = 0; i < 5; i++) signIn("10.39.6.1", "throttle-f", "wrong", status().isUnauthorized());
        assertThat(audit.findAll()).extracting(AuditEntry::getAction, AuditEntry::getTarget, AuditEntry::getActor)
                .contains(org.assertj.core.groups.Tuple.tuple("login.lockout", "IP 10.39.6.1", "system"));
    }

    private void signIn(String ip, String username, String password, ResultMatcher expected) throws Exception {
        mvc.perform(post("/api/auth/token").with(httpBasic(username, password)).with(from(ip))).andExpect(expected);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
