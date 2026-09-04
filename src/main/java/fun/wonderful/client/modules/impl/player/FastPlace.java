package fun.wonderful.client.modules.impl.player;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.mixin.IMinecraftClientAccessor;

public class FastPlace extends Module {

    public static FastPlace INSTANCE = new FastPlace();

    private final FloatSetting delay = new FloatSetting("Задержка", 0f, 0f, 4f, 1f);

    public FastPlace() {
        super("FastPlace", "Быстрая установка блоков", ModuleCategory.PLAYER);
        addSettings(delay);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isEnable() || mc.player == null) return;
        ((IMinecraftClientAccessor) mc).setItemUseCooldown((int) delay.get());
    }

    @Override
    public void onDisable() {
        ((IMinecraftClientAccessor) mc).setItemUseCooldown(4);
        super.onDisable();
    }
}
