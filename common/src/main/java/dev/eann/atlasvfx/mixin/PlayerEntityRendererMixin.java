package dev.eann.atlasvfx.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.eann.atlasvfx.character.CharacterEffects;
import dev.eann.atlasvfx.model.Replacements;
import dev.eann.atlasvfx.parity.ProviderSeam;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;

/**
 * First-person arms, which the world's entity pass never draws: the provider seam, and the body's
 * overlays drawn over the arm.
 */
@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixin {

    @WrapMethod(method = "renderRightArm")
    private void atlasvfx$seamRightArm(MatrixStack matrices, VertexConsumerProvider provider, int light,
                                       AbstractClientPlayerEntity player, Operation<Void> original) {
        arm(Arm.RIGHT, matrices, provider, light, player, original);
    }

    @WrapMethod(method = "renderLeftArm")
    private void atlasvfx$seamLeftArm(MatrixStack matrices, VertexConsumerProvider provider, int light,
                                      AbstractClientPlayerEntity player, Operation<Void> original) {
        arm(Arm.LEFT, matrices, provider, light, player, original);
    }

    private void arm(Arm side, MatrixStack matrices, VertexConsumerProvider provider, int light,
                     AbstractClientPlayerEntity player, Operation<Void> original) {
        VertexConsumerProvider wrapped = ProviderSeam.wrapArm(player, side, provider);
        try {
            original.call(matrices, wrapped, light, player);
            overlays(side, matrices, wrapped, light, player);
        } finally {
            if (wrapped != provider) {
                ProviderSeam.finish(wrapped);
            }
        }
    }

    /**
     * A replacement's arm in place of the one just drawn, if the body is replaced; then the body's
     * overlays over whichever arm is showing, as they are over the rest of it in third person.
     */
    private void overlays(Arm side, MatrixStack matrices, VertexConsumerProvider consumers, int light,
                          AbstractClientPlayerEntity player) {
        if (!CharacterEffects.any()) {
            return;
        }
        PlayerEntityModel<AbstractClientPlayerEntity> model = ((PlayerEntityRenderer) (Object) this).getModel();
        Replacements.PosedArm replaced = Replacements.drawArm(player, side, model, matrices, consumers, light);
        if (replaced != null) {
            CharacterEffects.drawArm(player, replaced.bone(), null, replaced.texture(), matrices, consumers, light);
            return;
        }
        CharacterEffects.drawArm(player, side == Arm.RIGHT ? model.rightArm : model.leftArm,
                side == Arm.RIGHT ? model.rightSleeve : model.leftSleeve, player.getSkinTextures().texture(),
                matrices, consumers, light);
    }
}
