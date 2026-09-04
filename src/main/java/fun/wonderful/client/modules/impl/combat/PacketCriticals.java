package fun.wonderful.client.modules.impl.combat;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventAttackEntity;
import fun.wonderful.api.utils.combat.IdealHitUtils;
import fun.wonderful.client.modules.Module;

public class PacketCriticals extends Module {

    public static PacketCriticals INSTANCE = new PacketCriticals();

    public PacketCriticals() {
        super("PacketCriticals", "Бьет критами под эффектом плавного падения / в паутине", ModuleCategory.COMBAT);
    }

    @EventLink
    public void onAttack(final EventAttackEntity event) {
        if (!isEnable() || mc.player == null || mc.world == null) return;

        boolean inWeb = IdealHitUtils.isInCobweb();
        boolean slowFalling = mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                && mc.player.getVelocity().y < 0
                && mc.player.fallDistance > 0;

        // В паутине и при плавном падении крит «провоцируется» микро-сдвигом вверх:
        // сервер видит движение вверх перед ударом и засчитывает критический урон
        if (inWeb || slowFalling) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.00300, z, false, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
        }
    }
}
