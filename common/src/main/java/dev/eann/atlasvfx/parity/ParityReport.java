package dev.eann.atlasvfx.parity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one capture saw: every layer asked for while one entity, or one first-person arm, was drawn
 * for one frame, and what the game was running with. Plain values only, so it is written to a file
 * as it is and read back to compare two captures.
 *
 * @param entity   the entity type's id, e.g. {@code minecraft:player}
 * @param mode     {@code entity} or {@code arm}
 * @param provider the class of the provider the entity was drawn into - the game's own, Iris's, the
 *                 outline provider for a glowing entity
 */
public record ParityReport(String entity, String mode, String loader, boolean iris, boolean shaderPack,
                           boolean sodium, String provider, List<LayerUse> uses) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public ParityReport {
        uses = List.copyOf(uses);
    }

    /** How many uses each class has, in enum order. */
    public Map<LayerClass, Integer> counts() {
        Map<LayerClass, Integer> counts = new java.util.EnumMap<>(LayerClass.class);
        for (LayerUse use : uses) {
            counts.merge(use.layerClass(), 1, Integer::sum);
        }
        return counts;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ParityReport fromJson(String json) {
        ParityReport report = GSON.fromJson(json, ParityReport.class);
        if (report == null || report.uses() == null) {
            throw new JsonParseException("not a parity report");
        }
        return report;
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    }

    public static ParityReport read(Path file) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    /**
     * What changed from {@code before} to this: uses that appeared or went, and uses whose class,
     * substitution or vertex count moved. Uses match by {@link LayerUse#key()}, in order among
     * equal keys; where a layer is asked for twice, the first asks are matched first. Empty when
     * nothing changed.
     */
    public List<String> diff(ParityReport before) {
        Map<String, List<LayerUse>> old = new HashMap<>();
        for (LayerUse use : before.uses()) {
            old.computeIfAbsent(use.key(), key -> new ArrayList<>()).add(use);
        }
        List<String> lines = new ArrayList<>();
        for (LayerUse use : uses) {
            List<LayerUse> matches = old.get(use.key());
            if (matches == null || matches.isEmpty()) {
                lines.add("+ " + use.key() + " (" + use.layerClass() + ")");
                continue;
            }
            LayerUse was = matches.removeFirst();
            if (was.layerClass() != use.layerClass()) {
                lines.add("~ " + use.key() + ": " + was.layerClass() + " -> " + use.layerClass());
            }
            if (!java.util.Objects.equals(was.drawnAs(), use.drawnAs())) {
                lines.add("~ " + use.key() + ": drawn as " + (was.drawnAs() == null ? "asked" : was.drawnAs())
                        + " -> " + (use.drawnAs() == null ? "asked" : use.drawnAs()));
            }
            if (was.vertices() != use.vertices()) {
                lines.add("~ " + use.key() + ": " + was.vertices() + " -> " + use.vertices() + " vertices");
            }
        }
        for (LayerUse use : before.uses()) {
            List<LayerUse> left = old.get(use.key());
            if (left != null && left.remove(use)) {
                lines.add("- " + use.key() + " (" + use.layerClass() + ")");
            }
        }
        return lines;
    }
}
