package fun.wonderful.client.modules.impl.combat;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.api.events.implement.EventPacket;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;

public class Velocity extends Module {

    public static Velocity INSTANCE = new Velocity();

    private final ModeSetting mode = new ModeSetting("Мод", "Packet", "Packet", "Cancel", "Reverse");
    private final FloatSetting horizontal = new FloatSetting("Горизонталь", 0f, 0f, 100f, 5f);
    private final FloatSetting vertical = new FloatSetting("Вертикаль", 0f, 0f, 100f, 5f);

    private boolean gotHit = false;
    private double lastMotionX, lastMotionZ;

    public Velocity() {
        super("Velocity", "Снижение отбрасывания", ModuleCategory.COMBAT);
        addSettings(mode, horizontal, vertical);
    }

    @Override
    public void onDisable() {
        gotHit = false;
        super.onDisable();
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (!isEnable() || mc.player == null || mc.getNetworkHandler() == null) return;
        if (event.getType() != EventPacket.Type.RECEIVE) return;

        if (event.getPacket() instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket velocityPacket) {
            if (velocityPacket.getEntityId() == mc.player.getId()) {
                gotHit = true;
                lastMotionX = velocityPacket.getVelocityX() / 8000.0;
                lastMotionY = velocityPacket.getVelocityY() / 8000.0;
                lastMotionZ = velocityPacket.getVelocityZ() / 8000.0;

                if (mode.is("Cancel")) {
                    event.setCancelled(true);
                    gotHit = false;
                }
            }
        }
    }

    private double lastMotionY;

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isEnable() || mc.player == null) return;

        if (gotHit) {
            gotHit = false;

            switch (mode.getCurrent()) {
                case "Packet" -> {
                    double hMul = horizontal.get() / 100.0;
                    double vMul = vertical.get() / 100.0;
                    mc.player.setVelocity(
                            lastMotionX * hMul,
                            lastMotionY * vMul,
                            lastMotionZ * hMul
                    );
                }
                case "Reverse" -> {
                    double hMul = horizontal.get() / 100.0;
                    mc.player.setVelocity(
                            -lastMotionX * hMul,
                            lastMotionY,
                            -lastMotionZ * hMul
                    );
                }
            }
        }
    }
}
