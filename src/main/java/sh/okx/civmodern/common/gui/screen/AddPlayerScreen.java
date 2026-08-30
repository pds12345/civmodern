package sh.okx.civmodern.common.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.radar.PlayerRelation;
import sh.okx.civmodern.common.radar.PlayerRelations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small popup, shown over {@link PlayerRelationsScreen}, that adds one username to a list.
 * Suggests currently-online players whose name starts with what has been typed so far, sorted
 * alphabetically; Tab completes to the top suggestion.
 */
public class AddPlayerScreen extends Screen {

    private static final int MAX_SUGGESTIONS = 5;
    private static final int SUGGESTION_ROW_HEIGHT = 11;
    private static final int SUGGESTION_COLOUR = 0xFFAAAAAA;
    private static final int SUGGESTION_HOVER_COLOUR = 0xFFFFFFFF;
    private static final int SUGGESTION_BG_COLOUR = 0x88000000;

    private final Screen parent;
    private final PlayerRelations relations;
    private final PlayerRelation category;

    /** Online players' names, alphabetised once when the screen opens. */
    private List<String> onlineNames = List.of();
    private List<String> suggestions = List.of();

    private EditBox nameBox;
    private Button addButton;

    private int boxX;
    private int boxY;
    private int boxWidth;

    public AddPlayerScreen(Screen parent, PlayerRelations relations, PlayerRelation category) {
        super(Component.translatable("civmodern.screen.playerrelations.add.title",
            Component.translatable("civmodern.screen.playerrelations." + category.toDatabaseKey())));
        this.parent = parent;
        this.relations = relations;
        this.category = category;
    }

    @Override
    protected void init() {
        boxWidth = 200;
        int centreX = this.width / 2;
        boxX = centreX - boxWidth / 2;
        boxY = this.height / 2 - 30;

        onlineNames = new ArrayList<>();
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
            for (PlayerInfo info : Minecraft.getInstance().player.connection.getOnlinePlayers()) {
                onlineNames.add(info.getProfile().name());
            }
        }
        onlineNames.sort(String.CASE_INSENSITIVE_ORDER);

        nameBox = new EditBox(this.font, boxX, boxY, boxWidth, 20, Component.empty());
        nameBox.setMaxLength(16);
        nameBox.setResponder(value -> {
            updateAddActive();
            updateSuggestions();
        });
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int buttonY = boxY + 24 + MAX_SUGGESTIONS * SUGGESTION_ROW_HEIGHT + 6;

        addButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.add"), button -> add())
            .pos(boxX, buttonY)
            .size(96, 20)
            .build();
        addButton.active = false;
        addRenderableWidget(addButton);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
            .pos(boxX + boxWidth - 96, buttonY)
            .size(96, 20)
            .build());
    }

    private void updateAddActive() {
        addButton.active = !nameBox.getValue().isBlank();
    }

    private void updateSuggestions() {
        String typed = nameBox.getValue();
        if (typed.isBlank()) {
            suggestions = List.of();
            return;
        }
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String name : onlineNames) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(name);
                if (matches.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }
        }
        suggestions = matches;
    }

    private void add() {
        String username = nameBox.getValue().trim();
        if (username.isEmpty()) {
            return;
        }
        relations.setRelation(username, category);
        Minecraft.getInstance().setScreen(parent);
    }

    private void completeToTopSuggestion() {
        if (suggestions.isEmpty()) {
            return;
        }
        String top = suggestions.get(0);
        nameBox.setValue(top);
        nameBox.setCursorPosition(top.length());
        updateAddActive();
        updateSuggestions();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            completeToTopSuggestion();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (addButton.active) {
                add();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (super.mouseClicked(event, bl)) {
            return true;
        }
        int index = suggestionAt(event.x(), event.y());
        if (index < 0) {
            return false;
        }
        nameBox.setValue(suggestions.get(index));
        nameBox.setCursorPosition(suggestions.get(index).length());
        updateAddActive();
        updateSuggestions();
        return true;
    }

    /** @return the suggestion row index under the mouse, or -1 if none. */
    private int suggestionAt(double mouseX, double mouseY) {
        if (suggestions.isEmpty() || mouseX < boxX || mouseX >= boxX + boxWidth) {
            return -1;
        }
        int top = boxY + 22;
        if (mouseY < top || mouseY >= top + suggestions.size() * SUGGESTION_ROW_HEIGHT) {
            return -1;
        }
        return (int) ((mouseY - top) / SUGGESTION_ROW_HEIGHT);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, boxY - 20, -1);

        if (suggestions.isEmpty()) {
            return;
        }
        int top = boxY + 22;
        int hovered = suggestionAt(mouseX, mouseY);
        guiGraphics.fill(boxX, top, boxX + boxWidth, top + suggestions.size() * SUGGESTION_ROW_HEIGHT, SUGGESTION_BG_COLOUR);
        for (int i = 0; i < suggestions.size(); i++) {
            int rowY = top + i * SUGGESTION_ROW_HEIGHT + 1;
            guiGraphics.drawString(this.font, suggestions.get(i), boxX + 4, rowY,
                i == hovered ? SUGGESTION_HOVER_COLOUR : SUGGESTION_COLOUR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
