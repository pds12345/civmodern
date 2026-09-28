package sh.okx.civmodern.common.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import sh.okx.civmodern.common.gui.DoubleValue;

public class DoubleOptionUpdateableSliderWidget extends AbstractSliderButton {
    private final DoubleValue param;
    private final double min;
    private final double max;
    /** Granularity in value units; 0 means continuous. The knob snaps to it too. */
    private final double step;

    public DoubleOptionUpdateableSliderWidget(int x, int y, int width, int height, double min, double max, DoubleValue param) {
        this(x, y, width, height, min, max, 0, param);
    }

    public DoubleOptionUpdateableSliderWidget(int x, int y, int width, int height, double min, double max, double step, DoubleValue param) {
        super(x, y, width, height, Component.empty(), (param.get() - min) / (max - min));
        this.param = param;
        this.min = min;
        this.max = max;
        this.step = step;
        this.updateMessage();
    }

    public void update() {
        updateMessage();
        this.value = (param.get() - min) / (max - min);
    }

    @Override
    protected void updateMessage() {
        this.setMessage(this.param.getText(this.param.get()));
    }

    @Override
    protected void applyValue() {
        double result = Mth.lerp(this.value, min, max);
        if (step > 0) {
            result = min + Math.round((result - min) / step) * step;
            this.value = (result - min) / (max - min);
        }
        param.set(result);
    }
}
