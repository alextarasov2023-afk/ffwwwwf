package fun.wonderful.client.modules.impl.misc;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.mixin.ILivingEntityAccessor;
import fun.wonderful.mixin.IMinecraftClientAccessor;

/**
 * ND (No Delay) — убирает ванильные задержки:
 * ПКМ (itemUseCooldown после использования предмета) и прыжок (jumpingCooldown).
 */
public class NoDelay extends Module {

    public static NoDelay INSTANCE = new NoDelay();

    private final BooleanSetting rmbDelay = new BooleanSetting("ПКМ", true);
    private final BooleanSetting jumpDelay = new BooleanSetting("Прыжок", true);

    public NoDelay() {
        super("ND", "No Delay: убирает задержку на ПКМ и прыжок", ModuleCategory.MISC);
        addSettings(rmbDelay, jumpDelay);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isEnable() || mc.player == null || mc.world == null) return;

        // ПКМ: ваниль ставит itemUseCooldown = 4 тика после использования предмета
        if (rmbDelay.isState()) {
            ((IMinecraftClientAccessor) mc).setItemUseCooldown(0);
        }

        // Прыжок: ваниль ставит jumpingCooldown = 10 тиков после прыжка
        if (jumpDelay.isState()) {
            ((ILivingEntityAccessor) mc.player).setJumpingCooldown(0);
        }
    }

    @Override
    public void onDisable() {
        // Возвращаем ванильное значение кулдауна ПКМ
        if (mc != null) {
            ((IMinecraftClientAccessor) mc).setItemUseCooldown(4);
        }
        super.onDisable();
    }
}
