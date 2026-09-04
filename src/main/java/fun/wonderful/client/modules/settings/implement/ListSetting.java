package fun.wonderful.client.modules.settings.implement;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import fun.wonderful.client.modules.settings.Setting;

public class ListSetting extends Setting<ListSetting> {

    @Getter
    private final List<BooleanSetting> settings = new ArrayList<>();

    public ListSetting(String name, BooleanSetting... settings) {
        super(name);
        for (BooleanSetting setting : settings) {
            this.settings.add(setting);
        }
    }

    public boolean is(String name) {
        for (BooleanSetting setting : this.settings) {
            if (setting.name().equals(name)) {
                return setting.isState();
            }
        }
        return false;
    }

    @Override
    public ListSetting visible(java.util.function.Supplier<Boolean> visible) {
        return super.visible(visible);
    }
}
