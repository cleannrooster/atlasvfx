package dev.eann.atlasvfx;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Motes alive together: ticked together and drawn together.
 *
 * <p>Holds at most its own ceiling, and never more than {@link FxBudget} has room for across
 * every pool; past either, a new mote is simply not made.</p>
 */
public final class MotePool {

    private final int cap;
    private final List<Mote> motes = new ArrayList<>();

    /** @param cap the most this pool holds at once */
    public MotePool(int cap) {
        this.cap = cap;
    }

    /** Whether one more mote would be kept. */
    public boolean hasRoom() {
        return motes.size() < cap && FxBudget.hasRoom();
    }

    /** Keeps a mote if there is room for it; false if it was not kept. */
    public boolean add(Mote mote) {
        if (motes.size() >= cap || !FxBudget.tryReserve()) {
            return false;
        }
        motes.add(mote);
        return true;
    }

    /** Advances every mote a tick; the spent ones go, and anchored ones whose anchor let go fade. */
    public void tick() {
        int gone = 0;
        Iterator<Mote> iterator = motes.iterator();
        while (iterator.hasNext()) {
            Mote mote = iterator.next();
            FxAnchor anchor = mote.anchor();
            if (anchor != null && !anchor.held()) {
                mote.letGo();
            }
            if (!mote.tick()) {
                iterator.remove();
                gone++;
            }
        }
        FxBudget.release(gone);
    }

    public void clear() {
        FxBudget.release(motes.size());
        motes.clear();
    }

    public boolean isEmpty() {
        return motes.isEmpty();
    }

    public int size() {
        return motes.size();
    }

    /** Whether any is drawn into the first-person view. */
    public boolean anyInView() {
        for (Mote mote : motes) {
            if (mote.inView()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws the motes that belong to one place: those out in the world, or those drawn into the
     * first-person view. Each anchor is found once, however many motes it holds.
     *
     * @param world     the world, for the light lit motes are drawn in; null in the view
     * @param inView    which place to draw
     * @param viewLight the light lit motes are drawn in when there is no world to ask
     */
    public void draw(FxPainter painter, @Nullable ClientWorld world, boolean inView, float tickDelta,
                     int viewLight) {
        Map<FxAnchor, Vec3d> anchors = new HashMap<>();
        for (Mote mote : motes) {
            if (mote.inView() != inView) {
                continue;
            }
            FxAnchor anchor = mote.anchor();
            Vec3d at;
            if (anchor != null) {
                Vec3d base = anchors.computeIfAbsent(anchor, key -> key.position(tickDelta));
                if (base == null) {
                    continue;
                }
                at = base.add(mote.offsetOrPosition(tickDelta));
            } else {
                at = mote.offsetOrPosition(tickDelta);
            }
            draw(painter, world, mote, at, tickDelta, viewLight);
        }
    }

    /** One mote, at a point already worked out. */
    public static void draw(FxPainter painter, @Nullable ClientWorld world, Mote mote, Vec3d at, float tickDelta,
                            int viewLight) {
        MoteRecipe recipe = mote.recipe();
        if (recipe.sprite() == null && recipe.blend() != FxBlend.GLOW) {
            return;
        }
        float alpha = mote.alpha(tickDelta);
        float size = mote.drawnSize(tickDelta);
        int light;
        if (recipe.blend() != FxBlend.LIT) {
            light = FxLight.FULL_BRIGHT;
        } else {
            light = world != null ? FxLight.at(world, at) : viewLight;
        }
        if (recipe.blend() == FxBlend.GLOW || recipe.sprite() == null) {
            painter.glow(at, size * 0.5f, mote.tint(), alpha);
            return;
        }
        painter.sprite(recipe.blend(), recipe.sprite(), mote.lifeFraction(tickDelta), mote.pick(), recipe.flips(),
                at, size, mote.roll(tickDelta), mote.tint(), alpha, light, recipe.crumb());
    }
}
