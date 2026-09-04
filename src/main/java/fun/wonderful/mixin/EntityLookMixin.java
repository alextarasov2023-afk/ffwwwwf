package fun.wonderful.mixin;

import fun.wonderful.api.events.implement.EventLook;
import fun.wonderful.api.storages.implement.FreeLookStorage;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void wonderful$onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!FreeLookStorage.isActive()) {
            return;
        }

        EventLook event = new EventLook(cursorDeltaX, cursorDeltaY);
        event.call();

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
