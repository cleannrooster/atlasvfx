package dev.eann.atlasvfx.parity;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code /atlasvfx parity [self|target|arm] [diff <name|last>]}: captures one frame of an entity's
 * layers, lists them in chat and saves them under {@code atlasvfx/parity/} in the game folder.
 *
 * <ul>
 *   <li>{@code target} - the entity under the crosshair; the default when there is one.</li>
 *   <li>{@code self} - the player, in third person (in first person the body is not drawn).</li>
 *   <li>{@code arm} - the player's first-person arm, drawn when the hand is empty.</li>
 *   <li>{@code diff <name>} - also compares the capture with a saved one; {@code last} is the most
 *       recent saved.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class ParityCommand {

    private static final int TIMEOUT_FRAMES = 40;
    private static final int MAX_LINES = 40;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ParityCommand() {
    }

    public static int run(ClientCommandSourceStack source, String options) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = source.arch$getPlayer();
        if (player == null || source.arch$getLevel() == null) {
            return 0;
        }
        String subject = null;
        String diff = null;
        String[] words = options.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < words.length; i++) {
            switch (words[i]) {
                case "" -> {
                }
                case "self", "target", "arm" -> subject = words[i];
                case "diff" -> {
                    if (i + 1 >= words.length) {
                        source.arch$sendFailure(Text.literal("diff needs a saved capture's name, or last"));
                        return 0;
                    }
                    diff = words[++i];
                }
                default -> {
                    source.arch$sendFailure(Text.literal("Unknown option: " + words[i]
                            + " (self, target, arm, diff <name|last>)"));
                    return 0;
                }
            }
        }
        Entity target = client.targetedEntity;
        if (subject == null) {
            subject = target != null ? "target" : "self";
        }
        Entity entity = switch (subject) {
            case "target" -> target;
            default -> player;
        };
        if (entity == null) {
            source.arch$sendFailure(Text.literal("Nothing under the crosshair"));
            return 0;
        }
        if (subject.equals("self") && client.options.getPerspective().isFirstPerson()) {
            source.arch$sendFailure(Text.literal("Your body is not drawn in first person: press F5, or use 'arm'"));
            return 0;
        }
        Path dir = directory(client);
        ParityCapture.Mode mode = subject.equals("arm") ? ParityCapture.Mode.ARM : ParityCapture.Mode.ENTITY;
        ParityReport baseline = null;
        if (diff != null) {
            try {
                Path file = resolve(dir, diff, prefix(Registries.ENTITY_TYPE.getId(entity.getType()).toString(), mode));
                if (file == null) {
                    source.arch$sendFailure(Text.literal(diff.equals("last") ? "No earlier capture of this in " + dir
                            : "No saved capture called " + diff + " in " + dir));
                    return 0;
                }
                baseline = ParityReport.read(file);
            } catch (IOException | RuntimeException e) {
                source.arch$sendFailure(Text.literal("Could not read " + diff + ": " + e.getMessage()));
                return 0;
            }
        }
        ParityReport before = baseline;
        ParityCapture.arm(entity.getId(), mode, TIMEOUT_FRAMES, outcome -> deliver(client, dir, outcome, before));
        String what = mode == ParityCapture.Mode.ARM ? "your arm" : entity.getName().getString();
        source.arch$sendSuccess(() -> Text.literal("Capturing " + what + " on the next frame")
                .formatted(Formatting.GRAY), false);
        return 1;
    }

    private static void deliver(MinecraftClient client, Path dir, ParityCapture.Outcome outcome,
                                @Nullable ParityReport baseline) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        if (outcome instanceof ParityCapture.Outcome.TimedOut timedOut) {
            player.sendMessage(Text.literal("Not drawn in " + timedOut.frames() + " frames"
                    + (timedOut.mode() == ParityCapture.Mode.ARM ? ": empty your main hand and stay in first person"
                    : ": is it on screen?")).formatted(Formatting.RED), false);
            return;
        }
        ParityReport report = ((ParityCapture.Outcome.Captured) outcome).report();
        for (Text line : summary(report)) {
            player.sendMessage(line, false);
        }
        try {
            Path file = dir.resolve(prefix(report.entity(), report.mode()) + LocalDateTime.now().format(STAMP) + ".json");
            report.write(file);
            player.sendMessage(Text.literal("Saved ").formatted(Formatting.GRAY).append(Text.literal(
                    file.getFileName().toString()).formatted(Formatting.UNDERLINE).styled(style -> style.withClickEvent(
                    new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toAbsolutePath().toString())))), false);
        } catch (IOException | UncheckedIOException e) {
            player.sendMessage(Text.literal("Could not save the capture: " + e.getMessage()).formatted(Formatting.RED),
                    false);
        }
        if (baseline != null) {
            List<String> changes = report.diff(baseline);
            player.sendMessage(Text.literal(changes.isEmpty() ? "Same layers as the saved capture"
                    : changes.size() + " change(s) from the saved capture:").formatted(Formatting.GOLD), false);
            for (String change : changes.subList(0, Math.min(changes.size(), MAX_LINES))) {
                player.sendMessage(Text.literal(change).formatted(change.startsWith("+") ? Formatting.GREEN
                        : change.startsWith("-") ? Formatting.RED : Formatting.YELLOW), false);
            }
        }
    }

    static List<Text> summary(ParityReport report) {
        StringBuilder counts = new StringBuilder();
        for (Map.Entry<LayerClass, Integer> entry : report.counts().entrySet()) {
            counts.append(counts.isEmpty() ? "" : ", ").append(entry.getValue()).append(' ')
                    .append(entry.getKey().name().toLowerCase(Locale.ROOT));
        }
        String provider = report.provider().substring(report.provider().lastIndexOf('.') + 1);
        MutableText header = Text.literal("Parity: " + report.entity() + " (" + report.mode() + "), "
                + report.uses().size() + " layer requests").formatted(Formatting.GOLD);
        List<Text> lines = new java.util.ArrayList<>();
        lines.add(header);
        lines.add(Text.literal(counts + " | " + report.loader() + ", provider " + provider
                + (report.iris() ? ", Iris" + (report.shaderPack() ? " with a pack" : " without a pack") : "")
                + (report.sodium() ? ", Sodium" : "")).formatted(Formatting.GRAY));
        for (LayerUse use : report.uses().subList(0, Math.min(report.uses().size(), MAX_LINES))) {
            lines.add(Text.literal("#" + use.order() + " ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(use.layerClass().name()).formatted(colour(use.layerClass())))
                    .append(Text.literal(" " + use.source() + ": ").formatted(Formatting.GRAY))
                    .append(Text.literal(use.layerName()))
                    .append(Text.literal(use.drawnAs() == null ? "" : " -> " + use.drawnAs())
                            .formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.literal((use.texture() == null ? "" : " " + use.texture()) + " (" + use.vertices()
                            + ", " + use.reason() + ")").formatted(Formatting.DARK_GRAY)));
        }
        if (report.uses().size() > MAX_LINES) {
            lines.add(Text.literal("... " + (report.uses().size() - MAX_LINES) + " more in the file")
                    .formatted(Formatting.GRAY));
        }
        return lines;
    }

    private static Formatting colour(LayerClass layerClass) {
        return switch (layerClass) {
            case SURFACE -> Formatting.GREEN;
            case TRANSLUCENT_SURFACE -> Formatting.AQUA;
            case EFFECT_OVERLAY -> Formatting.LIGHT_PURPLE;
            case UNKNOWN -> Formatting.YELLOW;
        };
    }

    private static Path directory(MinecraftClient client) {
        return client.runDirectory.toPath().resolve("atlasvfx").resolve("parity");
    }

    /** What every saved capture of one entity type in one mode is named starting with. */
    private static String prefix(String entity, ParityCapture.Mode mode) {
        return prefix(entity, mode.name().toLowerCase(Locale.ROOT));
    }

    private static String prefix(String entity, String mode) {
        return entity.replace(':', '_') + "_" + mode + "_";
    }

    /**
     * A saved capture by name, or for {@code last} the newest of the same entity type and mode -
     * comparing a body with an arm, or a zombie with a player, says nothing. Null when there is none.
     */
    private static @Nullable Path resolve(Path dir, String name, String lastPrefix) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        if (name.equals("last")) {
            try (Stream<Path> files = Files.list(dir)) {
                return files.filter(file -> file.getFileName().toString().startsWith(lastPrefix)
                                && file.getFileName().toString().endsWith(".json"))
                        .max(Comparator.comparing(file -> file.toFile().lastModified())).orElse(null);
            }
        }
        // Names only: a capture lives in this folder, and nothing outside it is read.
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        Path file = dir.resolve(name.endsWith(".json") ? name : name + ".json");
        return Files.isRegularFile(file) ? file : null;
    }
}
