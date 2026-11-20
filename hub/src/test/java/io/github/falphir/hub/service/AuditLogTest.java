package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jayway.jsonpath.JsonPath;

import io.github.falphir.hub.entity.AuditEntry;
import io.github.falphir.hub.repository.AuditEntryRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogTest {

    @Autowired
    private AuditLog audit;

    @Autowired
    private AuditEntryRepository entries;

    @Autowired
    private AuditRetentionTask retention;

    @Autowired
    private AdminUserService admins;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void ownTheTable() {
        entries.deleteAll(); // rolled back with the test, so a dev database keeps its rows
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void outsideARequestTheActorIsTheSystem() {
        audit.record("whitelist.remove", "Alice", null);

        AuditEntry entry = audit.page(null, 1).get(0);
        assertThat(entry.getActor()).isEqualTo("system");
        assertThat(entry.getVia()).isNull();
        assertThat(entry.getAction()).isEqualTo("whitelist.remove");
        assertThat(entry.getTarget()).isEqualTo("Alice");
    }

    @Test
    void theActorIsTheSignedInAccountAndViaComesFromTheBotsHeader() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("discord-bot", null, "ROLE_ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuditLog.ON_BEHALF_OF, "  staffer (123)\n\u0007 ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        audit.record("application.approved", "456", null);

        AuditEntry entry = audit.page(null, 1).get(0);
        assertThat(entry.getActor()).isEqualTo("discord-bot");
        assertThat(entry.getVia()).isEqualTo("staffer (123)"); // control characters and padding gone
    }

    @Test
    void pagesGoNewestFirstFromBelowTheGivenId() {
        audit.record("motd.create", "one", null);
        audit.record("motd.create", "two", null);
        audit.record("motd.create", "three", null);

        List<AuditEntry> first = audit.page(null, 2);
        assertThat(first).extracting(AuditEntry::getTarget).containsExactly("three", "two");
        assertThat(audit.page(first.get(1).getId(), 2)).extracting(AuditEntry::getTarget).containsExactly("one");
    }

    @Test
    void theNightlyPruneOnlyDeletesEntriesPastTheRetention() {
        entries.save(new AuditEntry(Instant.now().minus(Duration.ofDays(181)), "admin", null, "ban.add", "old", null));
        entries.save(new AuditEntry(Instant.now().minus(Duration.ofDays(179)), "admin", null, "ban.add", "recent", null));

        retention.pruneOldEntries();

        assertThat(entries.findAll()).extracting(AuditEntry::getTarget).containsExactly("recent");
    }

    @Test
    void theEndpointListsEntriesAndRefusesABadLimit() throws Exception {
        admins.create("audit-tester", "audit-tester-password"); // records itself, so it goes first
        audit.record("server.edit", "alpha", "name");
        String token = JsonPath.read(mvc.perform(post("/api/auth/token").with(httpBasic("audit-tester", "audit-tester-password")))
                .andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("server.edit"))
                .andExpect(jsonPath("$[0].target").value("alpha"))
                .andExpect(jsonPath("$[0].detail").value("name"));
        mvc.perform(get("/api/admin/audit?limit=0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
