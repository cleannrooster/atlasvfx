package dev.eann.atlasvfx.model;

import net.minecraft.client.model.ModelPart;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A geometry file baked into the game's own model parts, ready to pose and draw.
 *
 * <p>One baked model is shared by every body it replaces, so posing it is done just before each
 * body is drawn, from rest: {@link #reset()} first, then whatever animation and following apply.
 * Render thread only.</p>
 *
 * @param root  holds the file's root bones; drawn as a whole body
 * @param bones every bone's part, by its name in the file
 */
public record BakedGeo(GeoModel source, ModelPart root, Map<String, ModelPart> bones, GeoRig rig) {

    public BakedGeo {
        bones = Map.copyOf(bones);
    }

    public @Nullable ModelPart bone(@Nullable String name) {
        return name == null ? null : bones.get(name);
    }

    /** Every bone back to the pose the file gives it. */
    public void reset() {
        root.traverse().forEach(part -> {
            part.resetTransform();
            part.visible = true;
            part.hidden = false;
            part.xScale = 1.0f;
            part.yScale = 1.0f;
            part.zScale = 1.0f;
        });
    }
}
