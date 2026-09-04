package fun.wonderful.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fun.wonderful.Wonderful;
import fun.wonderful.api.events.implement.EventPacket;

/**
 * Перехват входящих пакетов: стреляет EventPacket(RECEIVE).
 * Без этого миксина событие не вызывалось вообще — не работали
 * Velocity, TpsSync (TPSCalc) и NameProtect.
 */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void wonderful$onPacketReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (packet == null || Wonderful.INSTANCE == null || !Wonderful.isReady()) return;
        try {
            EventPacket event = new EventPacket(packet, EventPacket.Type.RECEIVE);
            event.call();
            if (event.isCancelled()) {
                ci.cancel();
            }
        } catch (Exception ignored) {
        }
    }
}
