package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MinecraftFontTest {

    @Test
    void measuresTheDefaultFontsGlyphs() {
        assertThat(MinecraftFont.width("i")).isEqualTo(2);
        assertThat(MinecraftFont.width("W")).isEqualTo(6);
        assertThat(MinecraftFont.width(" ")).isEqualTo(4);
        assertThat(MinecraftFont.width("✦")).isEqualTo(6); // outside the ASCII table: guessed
    }

    @Test
    void codesTakeNoRoomAndBoldAddsAPixelUntilReset() {
        assertThat(MinecraftFont.width("§6WW")).isEqualTo(12);
        assertThat(MinecraftFont.width("&6WW")).isEqualTo(12); // the mod reads & codes too
        assertThat(MinecraftFont.width("§lWW")).isEqualTo(14);
        assertThat(MinecraftFont.width("§lW§rW")).isEqualTo(13);
        assertThat(MinecraftFont.width("§lW§6W")).isEqualTo(13); // a colour ends bold
    }

    @Test
    void centresAndRightAlignsWithLeadingSpaces() {
        // 10 W's: 60px, so 210px to spare: centred, 105px a side ≈ 26 spaces; right, 52 spaces
        String line = "W".repeat(10);
        assertThat(MinecraftFont.pad(line, MinecraftFont.Alignment.CENTER)).isEqualTo(" ".repeat(26) + line);
        assertThat(MinecraftFont.pad(line, MinecraftFont.Alignment.RIGHT)).isEqualTo(" ".repeat(52) + line);
        assertThat(MinecraftFont.pad(line, MinecraftFont.Alignment.LEFT)).isEqualTo(line);
    }

    @Test
    void aLineWiderThanTheAreaGetsNoSpaces() {
        String wide = "W".repeat(60);
        assertThat(MinecraftFont.pad(wide, MinecraftFont.Alignment.CENTER)).isEqualTo(wide);
    }
}
