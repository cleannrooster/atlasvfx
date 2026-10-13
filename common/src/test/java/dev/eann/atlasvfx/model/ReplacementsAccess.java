package dev.eann.atlasvfx.model;

import net.minecraft.client.render.entity.model.BipedEntityModel;

/** Reaches {@link Replacements}' package-private steps from tests in other packages. */
public final class ReplacementsAccess {

    private ReplacementsAccess() {
    }

    public static boolean partlyHidden(BipedEntityModel<?> biped) {
        return Replacements.partlyHidden(biped);
    }

    public static void showAsVanilla(BakedGeo model, BipedEntityModel<?> biped) {
        Replacements.showAsVanilla(model, biped);
    }
}
