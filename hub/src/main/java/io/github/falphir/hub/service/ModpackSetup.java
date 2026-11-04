package io.github.falphir.hub.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Puts a CurseForge modpack's server files on a server that was just created: download them, keep only
 * the pack's own folders ({@link ServerPack}), upload that as one zip, have the panel unpack it, and
 * then start the template's install. One zip and one unpack, rather than a request per file, because
 * a pack is thousands of files.
 * <p>
 * A pack is hundreds of MB, so this runs on its own virtual thread after the create request has
 * answered (nginx gives a request 60s). Its progress is kept per PufferPanel server and shown on the
 * server page; it's keyed by the panel id rather than the hub id so a rename doesn't lose it.
 * ponytail: in memory, so a hub restart forgets it (and stops a setup that was under way); a column
 * on servers if that ever matters.
 */
@Service
public class ModpackSetup {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModpackSetup.class);
    /**
     * Where the trimmed pack is uploaded before it's unpacked, and deleted again afterwards. In a folder
     * of its own because PufferPanel 2.7.1 can only delete folders: deleting a lone file fails with
     * "readdirent: not a directory".
     */
    static final String ARCHIVE_FOLDER = ".lodestone-modpack";
    static final String ARCHIVE = ARCHIVE_FOLDER + "/pack.zip";

    public enum Stage { DOWNLOADING, UPLOADING, UNPACKING, DONE, FAILED }

    /**
     * {@code error} is set only when {@code stage} is FAILED, worded for an admin. {@code done}/{@code
     * total} are the bytes of the download or upload under way: null while there's nothing to count
     * (unpacking, which the panel does in one call), and {@code total} null when its size isn't known.
     */
    public record Progress(String modpack, String version, Stage stage, String error, Long done, Long total, Instant updatedAt) {}

    /** How often byte counts reach the dashboards. Each one makes every open dashboard refetch the server list. */
    private static final Duration ANNOUNCE_EVERY = Duration.ofSeconds(1);

    private final CurseForgeClient curseForge;
    private final PufferPanelClient panel;
    private final ApplicationEventPublisher events;
    private final Executor runner;
    private final Map<String, Progress> progress = new ConcurrentHashMap<>();

    @Autowired
    public ModpackSetup(CurseForgeClient curseForge, PufferPanelClient panel, ApplicationEventPublisher events) {
        this(curseForge, panel, events, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** Lets tests run a setup on their own thread. */
    ModpackSetup(CurseForgeClient curseForge, PufferPanelClient panel, ApplicationEventPublisher events, Executor runner) {
        this.curseForge = curseForge;
        this.panel = panel;
        this.events = events;
        this.runner = runner;
    }

    /** How the modpack setup of this PufferPanel server is going; null if it never had one (since the hub started). */
    public Progress progressOf(String panelId) {
        return panelId == null ? null : progress.get(panelId);
    }

    public void start(String hubId, String panelId, CurseForgeClient.Modpack pack, CurseForgeClient.PackFile file) {
        set(hubId, panelId, pack, file, Stage.DOWNLOADING, null);
        runner.execute(() -> run(hubId, panelId, pack, file));
    }

    private void run(String hubId, String panelId, CurseForgeClient.Modpack pack, CurseForgeClient.PackFile file) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("lodestone-modpack");
            Path download = dir.resolve("download.zip");
            Path trimmed = dir.resolve("trimmed.zip");
            curseForge.download(pack.id(), file.serverPackFileId(), download, bytes(hubId, panelId, pack, file, Stage.DOWNLOADING));

            set(hubId, panelId, pack, file, Stage.UPLOADING, null);
            int files = ServerPack.filter(download, trimmed);
            if (files == 0) throw new IllegalStateException("the server files had nothing in them to put on a server");
            panel.putFile(panelId, ARCHIVE, trimmed, bytes(hubId, panelId, pack, file, Stage.UPLOADING));

            set(hubId, panelId, pack, file, Stage.UNPACKING, null);
            panel.extract(panelId, ARCHIVE);
            try {
                panel.deleteFolder(panelId, ARCHIVE_FOLDER);
            } catch (RuntimeException e) {
                LOGGER.warn("Couldn't delete {} from {} after unpacking it: {}", ARCHIVE_FOLDER, hubId, e.getMessage());
            }
            panel.install(panelId);
            set(hubId, panelId, pack, file, Stage.DONE, null);
            LOGGER.info("Put {} {} on {} ({} files) and started its install", pack.name(), file.displayName(), hubId, files);
        } catch (IOException | RuntimeException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LOGGER.warn("Modpack setup of {} failed: {}", hubId, why);
            set(hubId, panelId, pack, file, Stage.FAILED, why);
            // the loader's install still belongs on the server, pack or not
            try {
                panel.install(panelId);
            } catch (RuntimeException ignored) {
                // the admin is already told the setup failed, and installs from PufferPanel anyway
            }
        } finally {
            deleteQuietly(dir);
        }
    }

    private void set(String hubId, String panelId, CurseForgeClient.Modpack pack, CurseForgeClient.PackFile file,
            Stage stage, String error) {
        progress.put(panelId, new Progress(pack.name(), file.displayName(), stage, error, null, null, Instant.now()));
        events.publishEvent(HubEvent.servers(hubId));
    }

    /**
     * Byte counts for a download or upload: kept on every chunk, but announced to dashboards at most
     * once a second (and once more at the end), since each announcement is a refetch on every dashboard.
     */
    private TransferProgress bytes(String hubId, String panelId, CurseForgeClient.Modpack pack, CurseForgeClient.PackFile file,
            Stage stage) {
        long[] announced = {System.nanoTime()}; // the stage's start was just announced by set()
        return (done, total) -> {
            progress.put(panelId, new Progress(pack.name(), file.displayName(), stage, null, done, total < 0 ? null : total, Instant.now()));
            long now = System.nanoTime();
            if (now - announced[0] >= ANNOUNCE_EVERY.toNanos() || done == total) {
                announced[0] = now;
                events.publishEvent(HubEvent.servers(hubId));
            }
        };
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            LOGGER.warn("Couldn't clean up {}: {}", dir, e.getMessage());
        }
    }
}
