package dev.eann.atlasvfx.compat;

import dev.architectury.platform.Platform;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Iris, when it is there, through its public API by reflection: nothing here names an Iris class,
 * so without Iris nothing fails to load. Anything that goes wrong reads as "no shader pack".
 *
 * <p>Shared by everything that draws on bodies: none of it should draw in the shadow pass, where a
 * shader pack draws the world again from the sun.</p>
 */
@ApiStatus.Internal
public final class IrisCompat {

    private static final @Nullable Object API;
    private static final @Nullable MethodHandle SHADER_PACK_IN_USE;
    private static final @Nullable MethodHandle RENDERING_SHADOW_PASS;

    static {
        Object api = null;
        MethodHandle inUse = null;
        MethodHandle shadow = null;
        if (irisLoaded()) {
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                api = lookup.findStatic(type, "getInstance", MethodType.methodType(type)).invoke();
                inUse = lookup.findVirtual(type, "isShaderPackInUse", MethodType.methodType(boolean.class));
                shadow = lookup.findVirtual(type, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            } catch (Throwable ignored) {
                api = null;
            }
        }
        API = api;
        SHADER_PACK_IN_USE = api == null ? null : inUse;
        RENDERING_SHADOW_PASS = api == null ? null : shadow;
    }

    private IrisCompat() {
    }

    /** Whether Iris is there; false where the loader cannot be asked, as in tests. */
    private static boolean irisLoaded() {
        try {
            return Platform.isModLoaded("iris");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean loaded() {
        return API != null;
    }

    public static boolean shaderPackInUse() {
        return ask(SHADER_PACK_IN_USE);
    }

    public static boolean renderingShadowPass() {
        return ask(RENDERING_SHADOW_PASS);
    }

    private static boolean ask(@Nullable MethodHandle question) {
        if (question == null) {
            return false;
        }
        try {
            return (boolean) question.invoke(API);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
