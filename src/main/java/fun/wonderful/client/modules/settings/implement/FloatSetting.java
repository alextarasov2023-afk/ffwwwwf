package fun.wonderful.client.modules.settings.implement;

import lombok.Getter;
import fun.wonderful.client.modules.settings.Setting;

@Getter
public class FloatSetting extends Setting<FloatSetting> {

    private float value;
    @Getter
    private final float min;
    @Getter
    private final float max;
    @Getter
    private final float step;

    public FloatSetting(String name, float value, float min, float max, float step) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step <= 0f ? 0.01f : step;
        this.value = clamp(value);
    }

    public Number getValue() {
        return this.value;
    }

    public void setValue(Number value) {
        this.value = clamp(value.floatValue());
    }

    public float get() {
        return this.value;
    }

    private float clamp(float v) {
        return Math.max(this.min, Math.min(this.max, v));
    }

    @Override
    public FloatSetting visible(java.util.function.Supplier<Boolean> visible) {
        return super.visible(visible);
    }
}
