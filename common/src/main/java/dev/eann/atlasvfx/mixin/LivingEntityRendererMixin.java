package dev.eann.atlasvfx.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.eann.atlasvfx.character.CharacterEffects;
import dev.eann.atlasvfx.parity.LayerSource;
import dev.eann.atlasvfx.model.Replacements;
import dev.eann.atlasvfx.parity.ProviderSeam;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Names the feature renderer behind each layer asked for while the provider seam has the entity
 * wrapped - for a capture to attribute it, and for a look to tell which body part it is for - and
 * draws a replacement model where the vanilla one was drawn.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {

    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private void atlasvfx$attributeFeature(FeatureRenderer<?, ?> feature, MatrixStack matrices,
                                           VertexConsumerProvider provider, int light, Entity entity,
                                           float limbAngle, float limbDistance, float tickDelta,
                                           float animationProgress, float headYaw, float headPitch,
                                           Operation<Void> original) {
        if (!ProviderSeam.scoped()) {
            original.call(feature, matrices, provider, light, entity, limbAngle, limbDistance, tickDelta,
                    animationProgress, headYaw, headPitch);
            return;
        }
        ProviderSeam.enter(new LayerSource.Feature(feature.getClass()));
        try {
            original.call(feature, matrices, provider, light, entity, limbAngle, limbDistance, tickDelta,
                    animationProgress, headYaw, headPitch);
        } finally {
            ProviderSeam.exit();
        }
    }

    /**
     * Right after the vanilla model is drawn - into hidden layers, if the body is replaced - draws
     * the replacement in the same pose matrices, light, hurt tint and colour.
     */
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"))
    private void atlasvfx$drawReplacement(EntityModel<?> model, MatrixStack matrices, VertexConsumer buffer, int light,
                                          int overlay, int color, Operation<Void> original,
                                          @Local(argsOnly = true) LivingEntity entity,
                                          @Local(argsOnly = true) VertexConsumerProvider consumers,
                                          @Local(argsOnly = true, ordinal = 1) float tickDelta) {
        original.call(model, matrices, buffer, light, overlay, color);
        if (CharacterEffects.any()) {
            Replacements.drawBody(entity, model, matrices, consumers, light, overlay, color, tickDelta);
        }
    }
}
