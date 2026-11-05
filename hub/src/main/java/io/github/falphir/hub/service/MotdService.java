package io.github.falphir.hub.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.entity.MotdTemplate;
import io.github.falphir.hub.repository.GameServerRepository;
import io.github.falphir.hub.repository.MotdTemplateRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The network's MOTDs. Each server picks one (or none, keeping its own); the hub fills in {modpack},
 * {version} and {name} from that server's listing and sends the result with hello and every heartbeat,
 * so the mod shows it, and it's also written into a new server's template at creation.
 * <p>
 * A MOTD that needs a value the server's listing doesn't have renders to nothing, so the server keeps
 * its own rather than showing half a line.
 */
@Service
public class MotdService {

    private static final Pattern VARIABLE = Pattern.compile("\\{(modpack|version|name)}");
    /** A line that starts with {center} or {right}; without one it's left as written. */
    private static final Pattern ALIGN = Pattern.compile("^\\{(center|right)}");

    public record MotdView(long id, String name, String text, boolean isDefault, List<String> usedBy) {}

    private final MotdTemplateRepository motds;
    private final GameServerRepository servers;
    private final ApplicationEventPublisher events;

    public MotdService(MotdTemplateRepository motds, GameServerRepository servers, ApplicationEventPublisher events) {
        this.motds = motds;
        this.servers = servers;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<MotdView> list() {
        return motds.findAllByOrderByNameAsc().stream().map(this::view).toList();
    }

    @Transactional
    public MotdView create(String name, String text) {
        String cleanName = name.trim();
        if (motds.existsByName(cleanName)) throw duplicate(cleanName);
        MotdTemplate motd = motds.save(new MotdTemplate(cleanName, normalize(text)));
        // the first one is the default, so creating a server has one to offer from the start
        if (motds.count() == 1) motd.setDefault(true);
        changed();
        return view(motd);
    }

    @Transactional
    public MotdView update(long id, String name, String text) {
        MotdTemplate motd = find(id);
        String cleanName = name.trim();
        if (motds.existsByNameAndIdNot(cleanName, id)) throw duplicate(cleanName);
        motd.setName(cleanName);
        motd.setText(normalize(text));
        changed();
        return view(motd);
    }

    @Transactional
    public MotdView makeDefault(long id) {
        MotdTemplate motd = find(id);
        motds.findByIsDefaultTrue().ifPresent(old -> old.setDefault(false));
        motd.setDefault(true);
        changed();
        return view(motd);
    }

    /** Refused while servers use it: they'd lose their MOTD without anyone choosing that. */
    @Transactional
    public void delete(long id) {
        MotdTemplate motd = find(id);
        List<String> usedBy = usedBy(id);
        if (!usedBy.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + motd.getName() + "' is used by " + String.join(", ", usedBy) + ". Pick another MOTD for them first");
        }
        motds.delete(motd);
        changed();
    }

    /** Checks a pick exists before it's stored on a server. */
    @Transactional(readOnly = true)
    public void requireExists(long id) {
        find(id);
    }

    /** What this server should show, with its listing filled in; null when it has none, or can't fill one in. */
    @Transactional(readOnly = true)
    public String renderFor(GameServer server) {
        if (server.getMotdTemplateId() == null) return null;
        return motds.findById(server.getMotdTemplateId())
                .map(motd -> render(motd.getText(), server.getModpack(), server.getModpackVersion(), server.getName()))
                .orElse(null);
    }

    /** As {@link #renderFor(GameServer)}, by the server's id: what the bridge answers a server's own calls with. */
    @Transactional(readOnly = true)
    public String renderFor(String serverId) {
        return servers.findById(serverId).map(this::renderFor).orElse(null);
    }

    /** As {@link #renderFor(GameServer)}, for a server that doesn't exist yet. */
    @Transactional(readOnly = true)
    public String render(long id, String modpack, String version, String name) {
        return render(find(id).getText(), modpack, version, name);
    }

    /**
     * Fills in the variables, then lines up each line that starts with {center} or {right}: the spaces
     * that do it depend on this server's pack and version, which is why it happens here and not when the
     * MOTD is written. Null when a variable the text uses is blank.
     */
    static String render(String text, String modpack, String version, String name) {
        Map<String, String> values = Map.of(
                "modpack", modpack == null ? "" : modpack,
                "version", version == null ? "" : version,
                "name", name == null ? "" : name);
        StringBuilder out = new StringBuilder();
        // whether underline/strikethrough is on carries over a line break, as in the game
        MinecraftFont.Lines lines = new MinecraftFont.Lines();
        for (String line : text.split("\n", -1)) {
            MinecraftFont.Alignment alignment = MinecraftFont.Alignment.LEFT;
            Matcher marker = ALIGN.matcher(line);
            if (marker.find()) {
                alignment = MinecraftFont.Alignment.valueOf(marker.group(1).toUpperCase());
                line = line.substring(marker.end());
            }
            Matcher matcher = VARIABLE.matcher(line);
            StringBuilder filled = new StringBuilder();
            while (matcher.find()) {
                String value = values.get(matcher.group(1));
                if (value.isBlank()) return null;
                matcher.appendReplacement(filled, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(filled);
            // spaces typed to line it up by hand would only push it off-centre now; a struck-through or
            // underlined one is a stripe you can see, though, and stays
            String trimmed = lines.trimBlankSpaces(filled.toString());
            String done = alignment == MinecraftFont.Alignment.LEFT ? filled.toString() : MinecraftFont.pad(trimmed, alignment);
            if (!out.isEmpty()) out.append('\n');
            out.append(done);
        }
        return out.toString();
    }

    /** Real newlines, whether typed as such or as "\n" the way server.properties writes them. */
    static String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\\n", "\n");
    }

    private List<String> usedBy(long id) {
        return servers.findByMotdTemplateId(id).stream().map(GameServer::getId).sorted().toList();
    }

    private MotdView view(MotdTemplate motd) {
        return new MotdView(motd.getId(), motd.getName(), motd.getText(), motd.isDefault(), usedBy(motd.getId()));
    }

    private MotdTemplate find(long id) {
        return motds.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No MOTD " + id));
    }

    private static ResponseStatusException duplicate(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "There's already a MOTD called '" + name + "'");
    }

    /** Every server's rendered MOTD may have changed, so the server views refresh. */
    private void changed() {
        events.publishEvent(HubEvent.servers(null));
    }
}
