package fun.wonderful.client.modules.settings.implement;

import lombok.Getter;
import fun.wonderful.client.modules.settings.Setting;

@Getter
public class BooleanSetting extends Setting<BooleanSetting> {

    private boolean state;

    public BooleanSetting(String name, boolean state) {
        super(name);
        this.state = state;
    }

    public boolean isState() {
        return this.state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    @Override
    public BooleanSetting visible(java.util.function.Supplier<Boolean> visible) {
        return super.visible(visible);
    }

    public void toggle() {
        this.state = !this.state;
    }
}
