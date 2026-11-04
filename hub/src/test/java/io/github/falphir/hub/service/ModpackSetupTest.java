package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** The background setup, run on the test's own thread with CurseForge and the panel mocked. */
class ModpackSetupTest {

    private static final String PANEL_ID = "pack1234";
    private static final CurseForgeClient.Modpack PACK = new CurseForgeClient.Modpack(100, "All the Mods 10", null, null, null, true);
    private static final CurseForgeClient.PackFile FILE = new CurseForgeClient.PackFile(200, 201, "ATM10-5.1", List.of("1.21.1"), null);

    private final CurseForgeClient curseForge = mock(CurseForgeClient.class);
    private final PufferPanelClient panel = mock(PufferPanelClient.class);
    private final ModpackSetup setup = new ModpackSetup(curseForge, panel, mock(ApplicationEventPublisher.class), Runnable::run);

    @Test
    void uploadsTheTrimmedPackUnpacksItCleansUpAndThenInstalls() throws Exception {
        doAnswer(call -> {
            try (OutputStream file = Files.newOutputStream(call.getArgument(2, Path.class)); ZipOutputStream zip = new ZipOutputStream(file)) {
                for (String name : List.of("Server/mods/a.jar", "Server/start.sh")) {
                    zip.putNextEntry(new ZipEntry(name));
                    zip.closeEntry();
                }
            }
            // what the dashboard's progress bar is drawn from
            call.getArgument(3, TransferProgress.class).update(3, 10);
            var downloading = setup.progressOf(PANEL_ID);
            assertThat(downloading.stage()).isEqualTo(ModpackSetup.Stage.DOWNLOADING);
            assertThat(downloading.done()).isEqualTo(3);
            assertThat(downloading.total()).isEqualTo(10);
            return null;
        }).when(curseForge).download(eq(100), eq(201), any(), any());
        doAnswer(call -> {
            try (ZipFile zip = new ZipFile(call.getArgument(2, Path.class).toFile())) {
                assertThat(zip.stream().map(ZipEntry::getName)).containsExactly("mods/a.jar");
            }
            // an unknown size is no total, not -1
            call.getArgument(3, TransferProgress.class).update(5, -1);
            assertThat(setup.progressOf(PANEL_ID).total()).isNull();
            return null;
        }).when(panel).putFile(eq(PANEL_ID), eq(ModpackSetup.ARCHIVE), any(Path.class), any());

        setup.start("packed", PANEL_ID, PACK, FILE);

        var order = inOrder(panel);
        order.verify(panel).putFile(eq(PANEL_ID), eq(ModpackSetup.ARCHIVE), any(Path.class), any());
        order.verify(panel).extract(PANEL_ID, ModpackSetup.ARCHIVE);
        order.verify(panel).deleteFolder(PANEL_ID, ModpackSetup.ARCHIVE_FOLDER);
        order.verify(panel).install(PANEL_ID);
        assertThat(setup.progressOf(PANEL_ID).stage()).isEqualTo(ModpackSetup.Stage.DONE);
    }

    @Test
    void aFailureIsShownWithItsReasonAndTheInstallStillRuns() {
        doThrow(new CurseForgeClient.CurseForgeException("Couldn't reach CurseForge", null))
                .when(curseForge).download(anyInt(), anyInt(), any(), any());

        setup.start("packed", PANEL_ID, PACK, FILE);

        var progress = setup.progressOf(PANEL_ID);
        assertThat(progress.stage()).isEqualTo(ModpackSetup.Stage.FAILED);
        assertThat(progress.error()).isEqualTo("Couldn't reach CurseForge");
        assertThat(progress.modpack()).isEqualTo("All the Mods 10");
        verify(panel).install(PANEL_ID);
    }

    @Test
    void aServerThatNeverHadOneHasNoProgress() {
        assertThat(setup.progressOf("other123")).isNull();
        assertThat(setup.progressOf(null)).isNull();
    }
}
