package dev.eann.atlasvfx;

/**
 * An effect kept on an entity by {@link FxAttachments}, as whoever attached it holds on to it.
 *
 * <p>Every call is safe on one already detached; it does nothing.</p>
 */
public interface FxAttachment {

    /** The entity it is kept on, by id. */
    int entityId();

    /** Takes it off: what is showing is released and fades out, and it is not started again. */
    void detach();

    /** Takes it off now, with no fade. */
    void cancel();

    /** Whether it is still kept on its entity, showing or waiting for it to come back into view. */
    boolean attached();

    /** Whether it is showing now: its entity is in view and its shape is playing. */
    boolean showing();
}
