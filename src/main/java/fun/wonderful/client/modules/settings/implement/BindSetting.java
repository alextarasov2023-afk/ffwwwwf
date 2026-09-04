package fun.wonderful.client.modules.settings.implement;

import lombok.Getter;
import lombok.Setter;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.Setting;

public class BindSetting extends Setting<BindSetting> {

    @Getter
    private int key;

    /** Модуль-владелец бинда: Module.addSettings проставляет его автоматически,
     *  через него GUI-настройка и module.getKey()/setKey() синхронизированы. */
    @Getter @Setter
    private Module owner;

    public BindSetting(String name, int key) {
        super(name);
        this.key = key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    @Override
    public BindSetting visible(java.util.function.Supplier<Boolean> visible) {
        return super.visible(visible);
    }
}
