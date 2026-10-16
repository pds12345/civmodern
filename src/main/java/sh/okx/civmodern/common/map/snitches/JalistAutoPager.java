package sh.okx.civmodern.common.map.snitches;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import org.jetbrains.annotations.Nullable;

/**
 * "Refresh all snitches": runs {@code /jalist} and clicks the next arrow until the last page,
 * then closes the GUI, which ends a complete pass in {@link SnitchListCapture} and so replaces
 * the stored set. All the reading is the capture's; this only drives the GUI.
 *
 * <p>After each click it waits for the server to change the page, for the page to go quiet,
 * and for {@link #CLICK_INTERVAL_MS} since the last click, so the rate stays modest however
 * fast the server answers.
 */
public final class JalistAutoPager {

    /** Minimum time between page clicks. Raise it if the server objects to the rate. */
    private static final long CLICK_INTERVAL_MS = 250;
    /** How long to wait for the GUI to open, or for a page to arrive after a click. */
    private static final int TIMEOUT_TICKS = 100;

    /** FINISHING: the last page is closed, waiting for the capture to finish the pass. */
    private enum Phase { IDLE, OPENING, PAGING, FINISHING }

    private Phase phase = Phase.IDLE;
    private int waitedTicks;
    private boolean awaitingPage;
    private int pageChangesAtClick;
    private long lastClickMs;
    /** Run once a refresh has gone through every page and the pass is finished; may be null. */
    private @Nullable Runnable onComplete;

    public boolean isRunning() {
        return phase != Phase.IDLE;
    }

    /** @param onComplete run after a refresh that reached the last page, e.g. to reopen a screen */
    public void start(@Nullable Runnable onComplete) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || isRunning()) {
            return;
        }
        this.onComplete = onComplete;
        phase = Phase.OPENING;
        waitedTicks = 0;
        mc.player.displayClientMessage(Component.translatable("civmodern.snitches.refresh.started").withStyle(ChatFormatting.GRAY), false);
        mc.player.connection.sendCommand("jalist");
    }

    public void stop() {
        phase = Phase.IDLE;
        onComplete = null;
    }

    public void tick(SnitchListCapture capture) {
        Minecraft mc = Minecraft.getInstance();
        if (phase == Phase.IDLE || mc.player == null || mc.gameMode == null) {
            return;
        }
        waitedTicks++;
        AbstractContainerScreen<?> screen = SnitchListCapture.jalistScreen(-1);

        if (phase == Phase.FINISHING) {
            // The capture ends the pass a few ticks after the GUI closes, and only then has
            // the stored list been replaced; by then the player may have opened something else.
            if (!capture.isOpen() || waitedTicks > TIMEOUT_TICKS) {
                Runnable done = onComplete;
                stop();
                if (done != null && mc.screen == null) {
                    done.run();
                }
            }
            return;
        }

        if (phase == Phase.OPENING) {
            if (screen != null && capture.hasPage()) {
                phase = Phase.PAGING;
                awaitingPage = false;
                lastClickMs = 0;
                waitedTicks = 0;
            } else if (waitedTicks > TIMEOUT_TICKS) {
                fail(mc);
            }
            return;
        }

        if (screen == null) {
            // Closed by the player (or the server). The capture says whether anything was lost.
            stop();
            return;
        }
        if (awaitingPage) {
            if (capture.pageChanges() == pageChangesAtClick) {
                if (waitedTicks > TIMEOUT_TICKS) {
                    fail(mc);
                }
                return;
            }
            awaitingPage = false;
        }
        if (!capture.isSettled() || System.currentTimeMillis() - lastClickMs < CLICK_INTERVAL_MS) {
            return;
        }
        if (capture.hasNextPage()) {
            awaitingPage = true;
            pageChangesAtClick = capture.pageChanges();
            lastClickMs = System.currentTimeMillis();
            waitedTicks = 0;
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, SnitchListCapture.NEXT_SLOT, 0, ClickType.PICKUP, mc.player);
        } else {
            // The last page: closing ends the pass, and the capture reports the result.
            phase = Phase.FINISHING;
            waitedTicks = 0;
            mc.player.closeContainer();
        }
    }

    private void fail(Minecraft mc) {
        stop();
        if (SnitchListCapture.jalistScreen(-1) != null) {
            mc.player.closeContainer();
        }
        mc.player.displayClientMessage(Component.translatable("civmodern.snitches.refresh.timeout").withStyle(ChatFormatting.YELLOW), false);
    }
}
