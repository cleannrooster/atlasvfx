package dev.eann.atlasvfx.parity;

import net.minecraft.util.Arm;

/**
 * Who asked for a layer while an entity was drawn: the renderer's class, which says what body part
 * the layer is for. A report keeps only the label.
 */
public sealed interface LayerSource {

    String label();

    /** The dispatcher itself, around the renderer: shadow, fire and hitboxes. */
    record Dispatcher() implements LayerSource {
        @Override
        public String label() {
            return "dispatcher";
        }
    }

    /** The entity's renderer outside its feature layers: the model, and its name tag. */
    record Model(Class<?> renderer) implements LayerSource {
        @Override
        public String label() {
            return "model " + simpleName(renderer);
        }
    }

    /** One feature renderer: armour, held items, capes, and every mod's own layers. */
    record Feature(Class<?> renderer) implements LayerSource {
        @Override
        public String label() {
            return "feature " + simpleName(renderer);
        }
    }

    /** A replacement model, drawn in place of the body: never swapped or hidden itself. */
    record Replacement() implements LayerSource {
        @Override
        public String label() {
            return "replacement";
        }
    }

    /** A first-person arm. */
    record FirstPersonArm(Arm side) implements LayerSource {
        @Override
        public String label() {
            return side == Arm.LEFT ? "left arm" : "right arm";
        }
    }

    private static String simpleName(Class<?> type) {
        String className = type.getName();
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
