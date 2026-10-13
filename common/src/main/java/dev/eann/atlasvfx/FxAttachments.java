package dev.eann.atlasvfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Effects kept on entities for as long as they are wanted, rather than for as long as one shape
 * lasts: an aura on a player, a burning crown on a boss.
 *
 * <p>An attachment is kept by entity id and a way of making its shape, not by the shape itself.
 * While the entity is in view, its shape plays in {@link FxWorld}; when the entity leaves view the
 * shape lets go, and when it comes back - or is replaced under the same id, the way a player is on
 * respawning - a fresh shape is made for it. A shape that ends by itself while its entity is in
 * view is started again, so a timed shape attached this way loops. Everything is forgotten when a
 * world is joined or left, since ids mean nothing in the next one.</p>
 *
 * <p>Attaching by id works before the entity has reached this client: a server can name an entity
 * in the same message that says to attach something to it.</p>
 *
 * <p>Client thread only, like everything that plays effects.</p>
 */
public final class FxAttachments {

    /** Makes the shape an attachment shows on one entity. */
    @FunctionalInterface
    public interface Factory {
        FxShape create(Entity entity);
    }

    private static final FxAttachments INSTANCE = new FxAttachments();

    private final class Attachment implements FxAttachment {
        final int entityId;
        final Factory factory;
        final FxDensity host;
        @Nullable FxHandle live;
        @Nullable Entity bound;
        boolean detached;

        Attachment(int entityId, Factory factory, FxDensity host) {
            this.entityId = entityId;
            this.factory = factory;
            this.host = host;
        }

        @Override
        public int entityId() {
            return entityId;
        }

        @Override
        public void detach() {
            end(false);
        }

        @Override
        public void cancel() {
            end(true);
        }

        private void end(boolean now) {
            if (detached) {
                return;
            }
            detached = true;
            if (live != null) {
                if (now) {
                    live.cancel();
                } else {
                    live.release();
                }
            }
            live = null;
            bound = null;
            List<Attachment> list = byEntity.get(entityId);
            if (list != null) {
                list.remove(this);
                if (list.isEmpty()) {
                    byEntity.remove(entityId);
                }
            }
        }

        @Override
        public boolean attached() {
            return !detached;
        }

        @Override
        public boolean showing() {
            return !detached && live != null && live.alive();
        }

        /** Starts, restarts or lets go of its shape to match whether its entity is in view. */
        void refresh(@Nullable ClientWorld world) {
            Entity entity = world == null ? null : world.getEntityById(entityId);
            if (entity == null || entity.isRemoved()) {
                if (live != null) {
                    live.release();
                }
                live = null;
                bound = null;
                return;
            }
            if (live != null && live.alive() && bound == entity) {
                return;
            }
            if (live != null) {
                // Still showing on what used to have this id.
                live.cancel();
            }
            bound = entity;
            live = FxWorld.get().play(factory.create(entity), host);
        }
    }

    private final Map<Integer, List<Attachment>> byEntity = new HashMap<>();

    private FxAttachments() {
    }

    public static FxAttachments get() {
        return INSTANCE;
    }

    /**
     * Keeps an effect on the entity with this id, starting it at once if the entity is in view.
     *
     * @param host whose effect it is, for how much of it to make
     */
    public FxAttachment attach(int entityId, Factory factory, FxDensity host) {
        Attachment attachment = new Attachment(entityId, factory, host);
        byEntity.computeIfAbsent(entityId, id -> new ArrayList<>()).add(attachment);
        attachment.refresh(MinecraftClient.getInstance().world);
        return attachment;
    }

    /** Keeps an effect on this entity. */
    public FxAttachment attach(Entity entity, Factory factory, FxDensity host) {
        return attach(entity.getId(), factory, host);
    }

    /** Everything kept on the entity with this id. */
    public List<FxAttachment> of(int entityId) {
        List<Attachment> list = byEntity.get(entityId);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Takes everything off the entity with this id, fading out. */
    public void detachAll(int entityId) {
        for (FxAttachment attachment : of(entityId)) {
            attachment.detach();
        }
    }

    /** Once a client tick: matches every attachment to whether its entity is in view. */
    public void tick(MinecraftClient client) {
        if (client.world == null) {
            clear();
            return;
        }
        for (List<Attachment> list : List.copyOf(byEntity.values())) {
            for (Attachment attachment : List.copyOf(list)) {
                attachment.refresh(client.world);
            }
        }
    }

    /** Forgets every attachment, without a fade: the world they were in is gone. */
    public void clear() {
        Iterator<List<Attachment>> lists = byEntity.values().iterator();
        while (lists.hasNext()) {
            for (Attachment attachment : lists.next()) {
                attachment.detached = true;
                if (attachment.live != null) {
                    attachment.live.cancel();
                }
                attachment.live = null;
                attachment.bound = null;
            }
            lists.remove();
        }
    }

    /** How many attachments are kept, showing or not. */
    public int size() {
        int size = 0;
        for (List<Attachment> list : byEntity.values()) {
            size += list.size();
        }
        return size;
    }
}
