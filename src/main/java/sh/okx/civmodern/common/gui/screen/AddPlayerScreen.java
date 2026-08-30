package sh.okx.civmodern.common.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import sh.okx.civmodern.common.radar.PlayerRelation;
import sh.okx.civmodern.common.radar.PlayerRelations;

/** Small popup, shown over {@link PlayerRelationsScreen}, that adds one username to a list. */
public class AddPlayerScreen extends Screen {

    private final Screen parent;
    private final PlayerRelations relations;
    private final PlayerRelation category;

    private EditBox nameBox;
    private Button addButton;

    public AddPlayerScreen(Screen parent, PlayerRelations relations, PlayerRelation category) {
        super(Component.translatable("civmodern.screen.playerrelations.add.title",
            Component.translatable("civmodern.screen.playerrelations." + category.toDatabaseKey())));
        this.parent = parent;
        this.relations = relations;
        this.category = category;
    }

    @Override
    protected void init() {
        int boxWidth = 200;
        int centreX = this.width / 2;
        int boxY = this.height / 2 - 10;

        nameBox = new EditBox(this.font, centreX - boxWidth / 2, boxY, boxWidth, 20, Component.empty());
        nameBox.setMaxLength(16);
        nameBox.setResponder(value -> updateAddActive());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        addButton = Button.builder(Component.translatable("civmodern.screen.playerrelations.add"), button -> add())
            .pos(centreX - boxWidth / 2, boxY + 24)
            .size(96, 20)
            .build();
        addButton.active = false;
        addRenderableWidget(addButton);

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
            .pos(centreX + boxWidth / 2 - 96, boxY + 24)
            .size(96, 20)
            .build());
    }

    private void updateAddActive() {
        addButton.active = !nameBox.getValue().isBlank();
    }

    private void add() {
        String username = nameBox.getValue().trim();
        if (username.isEmpty()) {
            return;
        }
        relations.setRelation(username, category);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (addButton.active) {
                add();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 30, -1);
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
