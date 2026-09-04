package fun.wonderful.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import fun.wonderful.Wonderful;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.implement.EventGameUpdate;
import fun.wonderful.api.events.implement.EventUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Unique
    private long wonderful$lastHookTime = Util.getMeasuringTimeNano();
    @Unique
    private int wonderful$accumulatedCalls = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void wonderful$onTick(CallbackInfo ci) {
        if (Wonderful.INSTANCE != null && Wonderful.isReady()) {
            new EventUpdate().call();
        }
    }

    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void wonderful$onRender(boolean tick, CallbackInfo ci) {
        if (Wonderful.INSTANCE == null || !Wonderful.isReady()
                || !EventInvoker.hasListeners(EventGameUpdate.class)) {
            this.wonderful$lastHookTime = Util.getMeasuringTimeNano();
            this.wonderful$accumulatedCalls = 0;
            return;
        }

        long now = Util.getMeasuringTimeNano();
        long delta = now - this.wonderful$lastHookTime;
        this.wonderful$accumulatedCalls += (int) (delta / 4_166_666L);
        this.wonderful$lastHookTime += (long) this.wonderful$accumulatedCalls * 4_166_666L;

        for (this.wonderful$accumulatedCalls = Math.min(this.wonderful$accumulatedCalls, 240);
             this.wonderful$accumulatedCalls > 0; --this.wonderful$accumulatedCalls) {
            try {
                EventInvoker.invoke(new EventGameUpdate());
            } catch (Exception ignored) {
            }
        }
    }
}
