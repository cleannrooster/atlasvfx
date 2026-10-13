package dev.eann.atlasvfx.example;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import dev.eann.atlasvfx.appearance.Appearance;
import dev.eann.atlasvfx.appearance.Appearances;
import dev.eann.atlasvfx.character.CharacterEffects;
import dev.eann.atlasvfx.model.ModelReplacement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * RPG Minibosses' Forsaken adventurers - Rogue, Templar, Juggernaut, Fire Mage with its White Mage
 * and Dark Mage recolours, and Mercenary - as appearances: each is a file in
 * {@code assets/atlasvfx_example/atlasvfx_appearances/}, naming its geometry, texture and own clips. They show on the appearance screen (the {@code O} key, with
 * {@code appearances.enabled} on in {@code config/atlasvfx.json}), and here for trying on anything:
 *
 * <p>{@code /atlasvfx_example_client <adventurer>} puts one on you directly, following your pose as
 * a chosen appearance does; {@code animated} has it play its own clips instead, and {@code target}
 * puts it on what you look at. {@code off} takes it off. Unlike the screen, this is only on your own
 * screen.</p>
 */
final class Adventurers {

    static final List<String> NAMES = List.of("rogue", "templar", "juggernaut", "fire_mage", "white_mage", "dark_mage",
            "mercenary");

    private Adventurers() {
    }

    static void registerCommand() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<ClientCommandSourceStack> root =
                    ClientCommandRegistrationEvent.literal(AtlasVfxExample.MOD_ID + "_client");
            for (String name : NAMES) {
                Identifier id = Identifier.of(AtlasVfxExample.MOD_ID, name);
                root.then(ClientCommandRegistrationEvent.literal(name)
                        .executes(context -> wear(id, false, false))
                        .then(ClientCommandRegistrationEvent.literal("animated")
                                .executes(context -> wear(id, true, false))
                                .then(ClientCommandRegistrationEvent.literal("target")
                                        .executes(context -> wear(id, true, true))))
                        .then(ClientCommandRegistrationEvent.literal("target")
                                .executes(context -> wear(id, false, true))));
            }
            root.then(ClientCommandRegistrationEvent.literal("off")
                    .executes(context -> wear(null, false, false))
                    .then(ClientCommandRegistrationEvent.literal("target")
                            .executes(context -> wear(null, false, true))));
            dispatcher.register(root);
        });
    }

    /** Puts appearance {@code id} on the player, or what it looks at; null takes it off. */
    private static int wear(@Nullable Identifier id, boolean animated, boolean target) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity entity = target ? client.targetedEntity : client.player;
        Appearance appearance = Appearances.get(id);
        if (entity == null || (id != null && appearance == null)) {
            return 0;
        }
        ModelReplacement replacement = null;
        if (appearance != null) {
            ModelReplacement clips = animated ? appearance.animated() : null;
            replacement = clips != null ? clips : appearance.replacement();
        }
        CharacterEffects.set(entity.getId(), CharacterEffects.get(entity.getId()).withReplacement(replacement));
        return 1;
    }
}
