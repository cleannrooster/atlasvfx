package dev.eann.atlasvfx.aura;

import dev.eann.atlasvfx.FxLife;
import dev.eann.atlasvfx.FxPainter;
import dev.eann.atlasvfx.FxShape;
import dev.eann.atlasvfx.FxStyle;
import dev.eann.atlasvfx.FxWorld;
import dev.eann.atlasvfx.SurfaceGrid;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Light that stays with a body for as long as it is wanted: a circle at its feet, a shell round
 * it, sparks circling it, embers rising off it - any stack of {@link AuraLayer layers}, in one
 * {@link FxStyle style}.
 *
 * <p>An aura is a description; {@link #on} makes the shape that draws it on one entity. Play that
 * through {@link dev.eann.atlasvfx.FxAttachments} to keep it on the entity across it leaving view
 * and coming back, or straight through {@link FxWorld} for as long as the entity stays.
 * {@link dev.eann.atlasvfx.FxHandle#release() Releasing} it fades it out.</p>
 *
 * <p>It follows the game's rules for the body it is on: it is not drawn on a body the viewer
 * cannot see - invisible, or a spectator to someone who is not - it fades as the body dies, and
 * the layers that would fill the screen are left out for someone looking out of that body in first
 * person.</p>
 *
 * <p>Sizes in its layers are in blocks, as they look on a player. {@link Sizing#FIXED Fixed}, the
 * default, draws them so on every body; {@link #scaledToBody()} draws them in proportion to the
 * body it is on, so the same aura sits right on a baby zombie and a ravager.</p>
 *
 * @param fadeIn  ticks to come up to full strength
 * @param fadeOut ticks to fade out once released
 * @param sizing  what its sizes are multiplied by on a given body
 */
public record Aura(FxStyle style, List<AuraLayer> layers, int fadeIn, int fadeOut, Sizing sizing) {

    public Aura {
        layers = List.copyOf(layers);
        fadeIn = Math.max(1, fadeIn);
        fadeOut = Math.max(1, fadeOut);
        sizing = sizing == null ? Sizing.FIXED : sizing;
    }

    public Aura(FxStyle style, List<AuraLayer> layers, int fadeIn, int fadeOut) {
        this(style, layers, fadeIn, fadeOut, Sizing.FIXED);
    }

    public Aura(FxStyle style, AuraLayer... layers) {
        this(style, List.of(layers), 10, 12, Sizing.FIXED);
    }

    /** The same aura in another style. */
    public Aura in(FxStyle other) {
        return new Aura(other, layers, fadeIn, fadeOut, sizing);
    }

    /** The same aura, sized another way. */
    public Aura sized(Sizing other) {
        return new Aura(style, layers, fadeIn, fadeOut, other);
    }

    /** The same aura in proportion to the body it is on: {@code sized(Sizing.BODY)}. */
    public Aura scaledToBody() {
        return sized(Sizing.BODY);
    }

    /** The same aura, everything given in blocks this many times as big. */
    public Aura scaled(float factor) {
        return sized(sizing.times(factor));
    }

    /** The shape that draws this aura on {@code entity}, until it is released or the entity goes. */
    public FxShape on(Entity entity, long seed) {
        return new OnEntity(this, entity, seed);
    }

    /**
     * How strongly an aura shows, 0 to 1: coming up, fading out after a release, and fading with
     * a dying body.
     *
     * @param age          ticks since it appeared, with the frame's fraction
     * @param sinceRelease ticks since it was released, with the frame's fraction; 0 if it was not
     * @param deathTime    ticks the body has been dying; 0 if it is not
     */
    public static float strength(float age, boolean released, float sinceRelease, int fadeIn, int fadeOut,
                                 float deathTime) {
        float in = Math.min(1.0f, age / fadeIn);
        float out = released ? Math.max(0.0f, 1.0f - sinceRelease / fadeOut) : 1.0f;
        float dying = Math.max(0.0f, 1.0f - deathTime / 10.0f);
        return Math.max(0.0f, in * out * dying);
    }

    private static final class OnEntity implements FxShape {

        private final Aura aura;
        private final Entity entity;
        private final long seed;
        private final SurfaceGrid grid = new SurfaceGrid();

        OnEntity(Aura aura, Entity entity, long seed) {
            this.aura = aura;
            this.entity = entity;
            this.seed = seed;
        }

        @Override
        public boolean done(FxLife life) {
            return entity.isRemoved() || (life.released() && life.sinceRelease(0.0f) >= aura.fadeOut());
        }

        @Override
        public void tick(FxWorld world, FxLife life, float density) {
            float strength = strength(life, 0.0f);
            if (strength <= 0.0f || hidden()) {
                return;
            }
            for (AuraLayer layer : aura.layers()) {
                layer.tick(world, aura.style(), entity.getPos(), entity.getHeight(), entity.getWidth(),
                        aura.sizing().scale(entity), density * strength);
            }
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta) {
            float strength = strength(life, tickDelta);
            if (strength <= 0.004f || entity.isRemoved() || hidden()) {
                return;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            boolean firstPerson = client.getCameraEntity() == entity && client.options.getPerspective().isFirstPerson();
            AuraFrame frame = new AuraFrame(painter, world, aura.style(), entity.getLerpedPos(tickDelta),
                    entity.getHeight(), entity.getWidth(), aura.sizing().scale(entity), life.age() + tickDelta, strength,
                    firstPerson, seed, grid);
            for (AuraLayer layer : aura.layers()) {
                if (!(firstPerson && layer.fillsView())) {
                    layer.draw(frame);
                }
            }
        }

        private float strength(FxLife life, float tickDelta) {
            float deathTime = entity instanceof LivingEntity living && living.isDead()
                    ? living.deathTime + tickDelta : 0.0f;
            return Aura.strength(life.age() + tickDelta, life.released(), life.sinceRelease(tickDelta), aura.fadeIn(),
                    aura.fadeOut(), deathTime);
        }

        /** Whether the viewer could not see the body this is on. */
        private boolean hidden() {
            PlayerEntity viewer = MinecraftClient.getInstance().player;
            if (viewer == null) {
                return false;
            }
            return entity.isInvisibleTo(viewer) || (entity.isSpectator() && !viewer.isSpectator());
        }
    }
}
