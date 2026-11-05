package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MotdServiceTest {

    private static final String WTC = "§6✦ §e§lW T C  N E T W O R K §6✦\n§2§l{modpack} §8| §7v{version}";

    @Autowired
    private MotdService motds;

    @Test
    void fillsInTheListing() {
        assertThat(MotdService.render(WTC, "Greg-E", "0.6.4a", "Genesis"))
                .isEqualTo("§6✦ §e§lW T C  N E T W O R K §6✦\n§2§lGreg-E §8| §7v0.6.4a");
        assertThat(MotdService.render("Welcome to {name}", null, null, "Genesis")).isEqualTo("Welcome to Genesis");
    }

    @Test
    void aBlankValueTheTextUsesMeansNoMotdRatherThanHalfALine() {
        assertThat(MotdService.render(WTC, "Greg-E", "", "Genesis")).isNull();
        assertThat(MotdService.render(WTC, null, "0.6.4a", "Genesis")).isNull();
        // {name} isn't used, so its being blank doesn't matter
        assertThat(MotdService.render(WTC, "Greg-E", "0.6.4a", null)).isNotNull();
    }

    @Test
    void aBackslashNTypedAsInServerPropertiesIsALineBreak() {
        var saved = motds.create("motd-test-newline", "first\\nsecond");
        assertThat(saved.text()).isEqualTo("first\nsecond");
    }

    @Test
    void thereIsOnlyEverOneDefault() {
        var a = motds.create("motd-test-a", "a");
        var b = motds.create("motd-test-b", "b");

        motds.makeDefault(a.id());
        motds.makeDefault(b.id());

        assertThat(motds.list()).filteredOn(MotdService.MotdView::isDefault)
                .extracting(MotdService.MotdView::name).containsExactly("motd-test-b");
    }
}
