package dev.eann.atlasvfx.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.eann.atlasvfx.parity.LayerSource;
import dev.eann.atlasvfx.parity.ProviderSeam;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The provider seam: every entity the world draws passes through here with the provider it is
 * drawn into, which {@link ProviderSeam} may swap. Wrapped rather than redirected, so other mods'
 * hooks on the same method still run, in their order.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {

    @WrapMethod(method = "render")
    private void atlasvfx$seam(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                               MatrixStack matrices, VertexConsumerProvider provider, int light,
                               Operation<Void> original) {
        VertexConsumerProvider wrapped = ProviderSeam.wrap(entity, provider);
        if (wrapped == provider) {
            original.call(entity, x, y, z, yaw, tickDelta, matrices, provider, light);
            return;
        }
        try {
            original.call(entity, x, y, z, yaw, tickDelta, matrices, wrapped, light);
        } finally {
            ProviderSeam.finish(wrapped);
        }
    }

    /** Layers asked for inside the entity's renderer are the model's, unless a feature claims them. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void atlasvfx$attributeModel(EntityRenderer<?> renderer, Entity entity, float yaw, float tickDelta,
                                         MatrixStack matrices, VertexConsumerProvider provider, int light,
                                         Operation<Void> original) {
        if (!ProviderSeam.scoped()) {
            original.call(renderer, entity, yaw, tickDelta, matrices, provider, light);
            return;
        }
        ProviderSeam.enter(new LayerSource.Model(renderer.getClass()));
        try {
            original.call(renderer, entity, yaw, tickDelta, matrices, provider, light);
        } finally {
            ProviderSeam.exit();
        }
    }
}
