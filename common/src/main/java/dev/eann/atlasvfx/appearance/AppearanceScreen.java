package dev.eann.atlasvfx.appearance;

import dev.eann.atlasvfx.character.CharacterEffect;
import dev.eann.atlasvfx.character.CharacterEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Choosing what to be seen as: the appearances there are on the left, each with a small likeness of
 * you wearing it, and on the right a large one of you in whichever is selected, turning to follow
 * the pointer as the inventory's does. Done keeps the choice; Cancel leaves things as they were.
 *
 * <p>Built from the game's own list, buttons and background, so it looks like the game's own
 * screens and follows their theme, blur and narration.</p>
 */
public final class AppearanceScreen extends Screen {

    private static final int ROW_HEIGHT = 36;
    /** The game's own grey for secondary text. */
    private static final int GRAY = 0xA0A0A0;
    /** Darker, for what cannot be chosen yet. */
    private static final int LOCKED = 0x707070;
    private static final int HEADER = 33;
    private static final int FOOTER = 33;

    private final @Nullable Screen parent;
    /** Whether it opened by itself on a first join, to ask: it says how to come back to it. */
    private final boolean prompt;
    private @Nullable Identifier chosen;
    private AppearanceList list;
    private ButtonWidget done;

    public AppearanceScreen(@Nullable Screen parent) {
        this(parent, false);
    }

    public AppearanceScreen(@Nullable Screen parent, boolean prompt) {
        super(Text.translatable("atlasvfx.appearance.title"));
        this.parent = parent;
        this.prompt = prompt;
    }

    @Override
    protected void init() {
        ClientPlayerEntity player = Objects.requireNonNull(client).player;
        Identifier current = player == null ? null : AppearanceClient.choiceOf(player);
        Identifier selected = list != null && list.getSelectedOrNull() != null ? list.getSelectedOrNull().id() : current;
        chosen = current;
        list = new AppearanceList(client, width / 2, height - HEADER - FOOTER, HEADER);
        list.add(null, Text.translatable("atlasvfx.appearance.default"),
                Text.translatable("atlasvfx.appearance.default.description"));
        for (Appearance appearance : Appearances.all()) {
            list.add(appearance.id(), appearance.name(), appearance.description());
        }
        list.select(selected);
        addDrawableChild(list);
        int buttons = height - FOOTER + 6;
        done = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
            AppearanceList.Entry entry = list.getSelectedOrNull();
            Identifier pick = entry == null ? null : entry.id();
            if (!Objects.equals(pick, chosen) && !AppearanceClient.locked(pick)) {
                AppearanceClient.choose(pick);
            }
            close();
        }).dimensions(width / 2 - 155, buttons, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
                .dimensions(width / 2 + 5, buttons, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (prompt) {
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("atlasvfx.appearance.prompt",
                    AppearanceClient.OPEN.getBoundKeyLocalizedText()), width / 2, 18, GRAY);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 13, 0xFFFFFF);
        }
        ClientPlayerEntity player = client == null ? null : client.player;
        AppearanceList.Entry selected = list.getSelectedOrNull();
        // A locked one may be looked at, not chosen.
        done.active = selected == null || !AppearanceClient.locked(selected.id());
        if (player == null || selected == null) {
            return;
        }
        int left = width / 2 + 10;
        int right = width - 10;
        int top = HEADER + 6;
        int bottom = height - FOOTER - 34;
        int size = Math.max(20, Math.min((bottom - top) * 2 / 5, (right - left) / 2));
        preview(context, selected.id(), player, left, top, right, bottom, size, mouseX, mouseY);
        context.drawCenteredTextWithShadow(textRenderer, selected.name(), (left + right) / 2, bottom + 4, 0xFFFFFF);
        if (AppearanceClient.locked(selected.id())) {
            context.drawCenteredTextWithShadow(textRenderer, lockedBy(selected.id()), (left + right) / 2, bottom + 16,
                    LOCKED);
        } else if (selected.description() != null) {
            List<OrderedText> lines = textRenderer.wrapLines(selected.description(), right - left);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                context.drawCenteredTextWithShadow(textRenderer, lines.get(i), (left + right) / 2, bottom + 16 + i * 10,
                        GRAY);
            }
        }
    }

    /** "Locked: <the advancement's title>", or its id if this client does not know it. */
    static Text lockedBy(@Nullable Identifier appearance) {
        Identifier advancement = AppearanceClient.requirement(appearance);
        Text name = advancement == null ? Text.empty() : Text.literal(advancement.toString());
        MinecraftClient client = MinecraftClient.getInstance();
        if (advancement != null && client.getNetworkHandler() != null) {
            var entry = client.getNetworkHandler().getAdvancementHandler().get(advancement);
            if (entry != null && entry.value().display().isPresent()) {
                name = entry.value().display().get().getTitle();
            }
        }
        return Text.translatable("atlasvfx.appearance.locked", name);
    }

    /** Draws {@code player} wearing {@code appearance}, as nowhere else sees it: only for this draw. */
    static void preview(DrawContext context, @Nullable Identifier appearance, ClientPlayerEntity player, int x1, int y1,
                        int x2, int y2, int size, float mouseX, float mouseY) {
        Appearance wearing = Appearances.get(appearance);
        CharacterEffect effect = wearing == null ? CharacterEffect.NONE : wearing.effect();
        CharacterEffects.preview(player, effect,
                () -> InventoryScreen.drawEntity(context, x1, y1, x2, y2, size, 0.0625f, mouseX, mouseY, player));
    }

    @Override
    public void close() {
        if (prompt) {
            // However it is left - Done, Cancel or escape - it has been asked.
            AppearanceClient.prompted();
        }
        Objects.requireNonNull(client).setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** The appearances there are, each row with a likeness of the player wearing it. */
    static final class AppearanceList extends AlwaysSelectedEntryListWidget<AppearanceList.Entry> {

        AppearanceList(MinecraftClient client, int width, int height, int y) {
            super(client, width, height, y, ROW_HEIGHT);
        }

        void add(@Nullable Identifier id, Text name, @Nullable Text description) {
            addEntry(new Entry(id, name, description));
        }

        void select(@Nullable Identifier id) {
            for (Entry entry : children()) {
                if (Objects.equals(entry.id(), id)) {
                    setSelected(entry);
                    centerScrollOn(entry);
                    return;
                }
            }
            if (!children().isEmpty()) {
                setSelected(children().get(0));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(260, width - 20);
        }

        final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final @Nullable Identifier id;
            private final Text name;
            private final @Nullable Text description;

            Entry(@Nullable Identifier id, Text name, @Nullable Text description) {
                this.id = id;
                this.name = name;
                this.description = description;
            }

            @Nullable Identifier id() {
                return id;
            }

            Text name() {
                return name;
            }

            @Nullable Text description() {
                return description;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                               int mouseX, int mouseY, boolean hovered, float tickDelta) {
                MinecraftClient client = MinecraftClient.getInstance();
                ClientPlayerEntity player = client.player;
                if (player != null) {
                    // Facing out of the screen, a little to one side, as a portrait.
                    preview(context, id, player, x + 2, y + 1, x + 30, y + entryHeight - 1, 14, x + 26, y + 4);
                }
                boolean locked = AppearanceClient.locked(id);
                context.drawTextWithShadow(client.textRenderer, name, x + 36, y + 5, locked ? GRAY : 0xFFFFFF);
                Text below = locked ? lockedBy(id) : description != null ? description
                        : id == null ? Text.empty() : Text.literal(id.toString());
                List<OrderedText> lines = client.textRenderer.wrapLines(below, entryWidth - 40);
                if (!lines.isEmpty()) {
                    context.drawTextWithShadow(client.textRenderer, lines.get(0), x + 36, y + 17, locked ? LOCKED : GRAY);
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                setSelected(this);
                return true;
            }

            @Override
            public Text getNarration() {
                return Text.translatable("narrator.select", name);
            }
        }
    }
}
