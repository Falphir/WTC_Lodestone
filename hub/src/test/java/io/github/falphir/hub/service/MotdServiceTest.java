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
    void anAlignedLineIsPaddedPerServerAndItsMarkerGone() {
        String text = "§6WTC NETWORK\n{center}§2{modpack} §8| §7v{version}";

        String shortPack = MotdService.render(text, "Greg-E", "0.6.4a", null);
        String longPack = MotdService.render(text, "All the Mods 10 - ATM10", "8.4", null);

        assertThat(shortPack).startsWith("§6WTC NETWORK\n ").doesNotContain("{center}");
        assertThat(shortPack.split("\n")[1].strip()).isEqualTo("§2Greg-E §8| §7v0.6.4a");
        // the same MOTD lines up differently for a short name and a long one
        assertThat(leadingSpaces(shortPack.split("\n")[1])).isGreaterThan(leadingSpaces(longPack.split("\n")[1]));
    }

    @Test
    void spacesTypedOnAnAlignedLineAreDroppedButALeftLineIsLeftAlone() {
        assertThat(MotdService.render("{center}      W      ", null, null, null))
                .isEqualTo(MinecraftFont.pad("W", MinecraftFont.Alignment.CENTER));
        assertThat(MotdService.render("   W", null, null, null)).isEqualTo("   W");
        assertThat(MotdService.render("{right}{modpack}", "", null, null)).isNull();
    }

    @Test
    void aCentredLinesStripesStayOnlyItsInvisibleSpacesGo() {
        // the General MOTD's first line, as saved: a struck-through stripe on each side of the title
        String line = "{center}§8§m        §r§6 ✦ §e§lW T C  N E T W O R K §r§6✦ §8§m        ";

        String rendered = MotdService.render(line, null, null, null);

        assertThat(rendered).endsWith("§6✦ §8§m        "); // the second stripe, every space of it
        assertThat(rendered.stripLeading()).startsWith("§8§m        §r"); // and the first

        // plain spaces typed around a centred line still go, before and after the codes
        assertThat(MotdService.render("{center}   §6Hi   ", null, null, null).strip()).isEqualTo("§6Hi");
    }

    @Test
    void aStripeThatRunsOnFromTheLineBeforeIsVisibleOnTheNext() {
        var lines = new MinecraftFont.Lines();
        lines.trimBlankSpaces("§mstruck"); // strikethrough is still on at the end of line 1
        assertThat(lines.trimBlankSpaces("  x  ")).isEqualTo("  x  ");
        // until something turns it off
        assertThat(lines.trimBlankSpaces("§r  y  ")).isEqualTo("§ry");
    }

    private static int leadingSpaces(String line) {
        return line.length() - line.stripLeading().length();
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
