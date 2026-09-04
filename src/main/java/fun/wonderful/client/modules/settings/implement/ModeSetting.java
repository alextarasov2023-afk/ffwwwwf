package fun.wonderful.client.modules.settings.implement;

import lombok.Getter;
import fun.wonderful.client.modules.settings.Setting;

public class ModeSetting extends Setting<ModeSetting> {

    @Getter
    private final String[] modes;
    private String current;

    public ModeSetting(String name, String current, String... modes) {
        super(name);
        this.modes = modes;
        this.current = current;
    }

    public String getCurrent() {
        return this.current;
    }

    public void set(String mode) {
        for (String m : this.modes) {
            if (m.equals(mode)) {
                this.current = m;
                return;
            }
        }
    }

    public boolean is(String mode) {
        return this.current.equals(mode);
    }

    public int getIndex() {
        for (int i = 0; i < this.modes.length; i++) {
            if (this.modes[i].equals(this.current)) {
                return i;
            }
        }
        return 0;
    }

    public void cycle() {
        for (int i = 0; i < this.modes.length; i++) {
            if (this.modes[i].equals(this.current)) {
                this.current = this.modes[(i + 1) % this.modes.length];
                return;
            }
        }
    }

    @Override
    public ModeSetting visible(java.util.function.Supplier<Boolean> visible) {
        return super.visible(visible);
    }
}
