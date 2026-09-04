package fun.wonderful.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import fun.wonderful.api.utils.input.KeyBoardUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void wonderful$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (this.client.currentScreen != null || this.client.player == null) {
            return;
        }
        if (action == 1 || action == 0) {
            KeyBoardUtils.callMouse(button, action);
        }
    }
}
