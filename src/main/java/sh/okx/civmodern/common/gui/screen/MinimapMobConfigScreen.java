package sh.okx.civmodern.common.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.CivMapConfig;
import sh.okx.civmodern.common.gui.widget.TextRenderable;
import sh.okx.civmodern.common.gui.widget.ToggleButton;
import sh.okx.civmodern.common.map.mobs.MinimapMobTypes;
import sh.okx.civmodern.common.map.mobs.MobThreatCategory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Per-mob visibility toggles for the minimap's mob icons: one scrollable row per vanilla mob
 * with a spawn egg (see {@link MinimapMobTypes#all()}), grouped hostile/neutral/passive then
 * alphabetically. Clicking a row - or the eye icon on it - flips that mob's visibility; there is
 * no per-row edit modal, unlike {@link sh.okx.civmodern.common.map.screen.WaypointManagerScreen}
 * this table is modelled after, since a mob only has the one setting.
 */
final class MinimapMobConfigScreen extends AbstractConfigScreen {

    private static final int ROW_HEIGHT = 20;
    private static final int TOGGLE_COLUMN = 18;
    private static final int TOGGLE_SIZE = 16;
    private static final Identifier TOGGLE_TEXTURE = Identifier.fromNamespaceAndPath("civmodern", "gui/visibility.png");
    private static final int ICON_COLUMN = 22;
    private static final int CATEGORY_COLUMN = 60;

    private static final int NAME_COLOUR = 0xFFFFFFFF;
    private static final int HIDDEN_NAME_COLOUR = 0x88FFFFFF;
    private static final int STRIPE_COLOUR = 0x14FFFFFF;
    private static final int HOVER_COLOUR = 0x33FFFFFF;

    private record Row(EntityType<?> type, MobThreatCategory category, String name, ItemStack icon) {
    }

    private final List<Row> rows;
    private double scroll;
    private int rowsTop;
    private int rowsBottom;

    MinimapMobConfigScreen(
        final @NotNull CivMapConfig config,
        final @NotNull Screen parent
    ) {
        super(config, parent, Component.translatable("civmodern.screen.mobs.title"));
        this.rows = MinimapMobTypes.all().entrySet().stream()
            .map(entry -> new Row(entry.getKey(), entry.getValue(), entry.getKey().getDescription().getString(),
                new ItemStack(SpawnEggItem.byId(entry.getKey()))))
            .sorted(Comparator
                .<Row>comparingInt(row -> row.category().ordinal())
                .thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }

    @Override
    protected void init() {
        super.init();

        addRenderableOnly(new TextRenderable.CentreAligned(this.font, this.centreX, getHeaderY(), this.title));

        int offset = getBodyY();
        addRenderableWidget(new ToggleButton(
            this.centreX - (ToggleButton.DEFAULT_BUTTON_WIDTH / 2),
            offset,
            ToggleButton.DEFAULT_BUTTON_WIDTH,
            Component.translatable("civmodern.screen.mobs.enabled"),
            this.config::isMinimapMobsEnabled,
            this.config::setMinimapMobsEnabled,
            null,
            ToggleButton.DEFAULT_NARRATION
        ));
        offset += 24;

        this.rowsTop = offset;
        this.rowsBottom = this.height - 46;

        int left = tableLeft();
        int halfWidth = tableWidth() / 2 - 2;
        addRenderableWidget(Button.builder(Component.translatable("civmodern.screen.mobs.reset"), button -> {
            for (Row row : rows) {
                config.setMinimapMobVisible(row.type(), row.category().isDefaultVisible());
            }
        }).pos(left, this.height - 42).size(halfWidth, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            config.save();
            minecraft.setScreen(parent);
        }).pos(left + halfWidth + 4, this.height - 42).size(halfWidth, 20).build());
    }

    private int tableWidth() {
        return Math.min(300, this.width - 40);
    }

    private int tableLeft() {
        return (this.width - tableWidth()) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);

        int left = tableLeft();
        int right = left + tableWidth();
        int top = rowsTop;
        int bottom = rowsBottom;
        scroll = Mth.clamp(scroll, 0, maxScroll());

        int hovered = rowAt(mouseX, mouseY);

        guiGraphics.enableScissor(left, top, right, bottom);
        for (int i = 0; i < rows.size(); i++) {
            int rowY = top + i * ROW_HEIGHT - (int) scroll;
            if (rowY + ROW_HEIGHT <= top || rowY >= bottom) {
                continue;
            }
            Row row = rows.get(i);
            boolean visible = config.isMinimapMobVisible(row.type());

            if (i == hovered) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, HOVER_COLOUR);
            } else if (i % 2 == 1) {
                guiGraphics.fill(left, rowY, right, rowY + ROW_HEIGHT, STRIPE_COLOUR);
            }

            int toggleLeft = left + 3;
            int toggleTop = rowY + (ROW_HEIGHT - TOGGLE_SIZE) / 2;
            int toggleV = visible ? 0 : 20;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TOGGLE_TEXTURE, toggleLeft, toggleTop, 0, toggleV,
                TOGGLE_SIZE, TOGGLE_SIZE, 20, 20, 20, 40, -1);

            int iconLeft = left + TOGGLE_COLUMN + 3;
            int iconTop = rowY + (ROW_HEIGHT - 16) / 2;
            guiGraphics.renderItem(row.icon(), iconLeft, iconTop);

            int categoryRight = right - 6;
            int nameLeft = iconLeft + ICON_COLUMN;
            int nameRight = categoryRight - CATEGORY_COLUMN;
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;

            int nameColour = visible ? NAME_COLOUR : HIDDEN_NAME_COLOUR;
            String name = font.plainSubstrByWidth(row.name(), nameRight - nameLeft - 4);
            guiGraphics.drawString(font, name, nameLeft, textY, nameColour);

            String category = categoryLabel(row.category());
            guiGraphics.drawString(font, category, categoryRight - font.width(category), textY,
                categoryColour(row.category(), visible));
        }
        guiGraphics.disableScissor();

        int content = rows.size() * ROW_HEIGHT;
        int viewport = bottom - top;
        if (content > viewport) {
            int barHeight = Math.max(10, viewport * viewport / content);
            int barY = top + (int) ((viewport - barHeight) * (scroll / maxScroll()));
            guiGraphics.fill(right - 2, top, right, bottom, STRIPE_COLOUR);
            guiGraphics.fill(right - 2, barY, right, barY + barHeight, 0x88FFFFFF);
        }
    }

    private static String categoryLabel(MobThreatCategory category) {
        return switch (category) {
            case HOSTILE -> "Hostile";
            case NEUTRAL -> "Neutral";
            case PASSIVE -> "Passive";
        };
    }

    private static int categoryColour(MobThreatCategory category, boolean visible) {
        int colour = category.colour();
        return visible ? colour : (colour & 0x00FFFFFF) | 0x66000000;
    }

    private double maxScroll() {
        return Math.max(0, rows.size() * ROW_HEIGHT - (rowsBottom - rowsTop));
    }

    private int rowAt(double mouseX, double mouseY) {
        int left = tableLeft();
        if (mouseX < left || mouseX >= left + tableWidth() || mouseY < rowsTop || mouseY >= rowsBottom) {
            return -1;
        }
        return (int) ((mouseY - rowsTop + scroll) / ROW_HEIGHT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        int row = rowAt(event.x(), event.y());
        if (row < 0 || row >= rows.size()) {
            return false;
        }
        EntityType<?> type = rows.get(row).type();
        config.setMinimapMobVisible(type, !config.isMinimapMobVisible(type));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        scroll = Mth.clamp(scroll - scrollY * ROW_HEIGHT, 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        config.save();
        super.onClose();
    }
}
