package fun.wonderful.client.modules.impl.movement;

import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventSlowWalking;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;

public class NoSlow extends Module {

    public static NoSlow INSTANCE = new NoSlow();

    private final ModeSetting mode = new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim");
    private final BooleanSetting sprint = new BooleanSetting("Спринт", true);

    public NoSlow() {
        super("NoSlow", "Убирает замедление от предметов", ModuleCategory.MOVEMENT);
        addSettings(mode, sprint);
    }

    @EventLink
    public void onSlowDown(EventSlowWalking event) {
        if (!isEnable() || mc.player == null || !mc.player.isUsingItem()) return;

        if (mode.is("Grim")) {
            Hand activeHand = mc.player.getActiveHand();
            Hand otherHand = activeHand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;

            if (sprint.isState()) {
                mc.player.setSprinting(
                        (mc.options.sprintKey.isPressed() || (Sprint.INSTANCE != null && Sprint.isSprinting()))
                                && mc.player.input.movementForward > 0
                                && !mc.player.isGliding()
                );
            }

            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(
                        new PlayerInteractItemC2SPacket(otherHand, 0, mc.player.getYaw(), mc.player.getPitch())
                );
            }
        }

        event.setCancelled(true);
    }
}
