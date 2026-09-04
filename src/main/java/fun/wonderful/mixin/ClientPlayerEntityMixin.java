package fun.wonderful.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.implement.EventUpdatePost;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void wonderful$onTickPost(CallbackInfo ci) {
        if (EventInvoker.hasListeners(EventUpdatePost.class)) {
            new EventUpdatePost().call();
        }
    }
}
