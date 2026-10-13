package dev.eann.atlasvfx.character;

import java.util.EnumSet;
import java.util.Set;

/** What part of a body a layer is drawn for, as far as a look is concerned. */
public enum BodyPart {
    /** The body itself: the model, its skin and anything grown on it. */
    SKIN,
    /** Worn armour, a horse's or wolf's armour, a saddle, a llama's carpet. */
    ARMOR,
    CAPE,
    ELYTRA,
    /** What is worn on the head: a skull, a carved pumpkin, a block. */
    HEAD,
    /** What is held in the hands, or the mouth. */
    HELD,
    /** What is stuck in or sitting on the body rather than part of it: arrows, stingers, a parrot. */
    ATTACHMENT,
    /** Any other feature layer: most often another mod's. */
    OTHER,
    /** The first-person arm and sleeve. */
    ARM;

    /** Everything a body is seen as, but not what it holds or what is stuck in it. */
    public static final Set<BodyPart> WORN = Set.copyOf(EnumSet.of(SKIN, ARMOR, CAPE, ELYTRA, HEAD, OTHER, ARM));
    /** The skin alone, in third and first person. */
    public static final Set<BodyPart> SKIN_ONLY = Set.copyOf(EnumSet.of(SKIN, ARM));
}
