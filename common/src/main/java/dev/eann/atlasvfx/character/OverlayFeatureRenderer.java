package dev.eann.atlasvfx.character;

import dev.eann.atlasvfx.model.Replacements;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

import java.util.List;

/**
 * The feature layer every living entity's renderer is given, through each loader's own hook for
 * adding one: draws the body's overlays over its model, posed exactly as the renderer just drew
 * it - by whatever animation mod posed it.
 *
 * <p>Added once per renderer, last of the features added at that point, so armour drawn before it
 * hides the body under it the way it would hide the skin.</p>
 */
public final class OverlayFeatureRenderer<T extends LivingEntity, M extends EntityModel<T>>
        extends FeatureRenderer<T, M> {

    public OverlayFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, T entity, float limbAngle,
                       float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!CharacterEffects.any() || !CharacterEffects.drawable(entity)) {
            return;
        }
        List<OverlayPass> passes = CharacterEffects.overlaysOf(entity);
        if (passes.isEmpty()) {
            return;
        }
        Replacements.Active replaced = Replacements.active(entity);
        if (replaced != null) {
            // Over the replacement, as the replacement left its bones this frame.
            CharacterEffects.drawPart(passes, replaced.model().root(), replaced.replacement().texture(), matrices,
                    consumers, light, animationProgress);
            return;
        }
        CharacterEffects.drawModel(passes, getContextModel(), getTexture(entity), matrices, consumers, light,
                animationProgress);
    }
}
