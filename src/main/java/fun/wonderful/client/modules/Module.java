package fun.wonderful.client.modules;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleRewords;
import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.notification.NotificationManager;
import fun.wonderful.client.modules.settings.Setting;
import fun.wonderful.client.modules.settings.implement.BindSetting;

@Getter
public abstract class Module implements QClient, ModuleRewords {

    private final String name;
    private final String description;
    private final ModuleCategory category;
    @Setter
    private int key = -1;
    private boolean enable;

    private final List<Setting<?>> settings = new ArrayList<>();

    private AnimationUtils arrayAnimka;
    private AnimationUtils animka;

    protected Module(String name, String description, ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    @SafeVarargs
    protected final void addSettings(Setting<?>... setting) {
        for (Setting<?> s : setting) {
            if (s == null) continue;
            if (s instanceof BindSetting bind) {
                bind.setOwner(this);
                this.key = bind.getKey();
            }
            this.settings.add(s);
        }
    }

    /**
     * Смена бинда модуля из любого места (команда .bind, СКМ в списке модулей, GUI-настройка):
     * поле key и Bind-настройка модуля всегда синхронизированы.
     */
    public void setKey(int key) {
        this.key = key;
        for (Setting<?> s : this.settings) {
            if (s instanceof BindSetting bind) {
                bind.setKey(key);
            }
        }
    }

    public boolean isEnable() {
        return this.enable;
    }

    public void toggle() {
        this.setEnabled(!this.enable);
    }

    public void setEnabled(boolean value) {
        if (this.enable == value) return;
        this.enable = value;
        if (value) {
            EventInvoker.register(this);
            this.onEnable();
        } else {
            this.onDisable();
            EventInvoker.unregister(this);
        }
        if (fun.wonderful.Wonderful.isReady()) {
            NotificationManager.push(this.name, "", value);
        }
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public String getDisplayName() {
        return this.name;
    }

    public AnimationUtils getArrayAnimka() {
        if (this.arrayAnimka == null) {
            this.arrayAnimka = new AnimationUtils(0.0f, 12.0f, Easings.CUBIC_OUT);
        }
        return this.arrayAnimka;
    }

    public AnimationUtils getAnimka() {
        if (this.animka == null) {
            this.animka = new AnimationUtils(0.0f, 12.0f, Easings.CUBIC_OUT);
        }
        return this.animka;
    }

    public enum ModuleCategory {
        COMBAT("Combat"),
        MOVEMENT("Movement"),
        PLAYER("Player"),
        MISC("Misc"),
        RENDER("Render");

        private final String name;

        ModuleCategory(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }
}
