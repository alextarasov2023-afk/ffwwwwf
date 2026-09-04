package fun.wonderful.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import fun.wonderful.api.utils.input.KeyBoardUtils;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void wonderful$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (this.client.currentScreen != null || this.client.player == null) {
            return;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT && action == 1) {
            KeyBoardUtils.call(key, action);
            ci.cancel();
            return;
        }
        if (action == 1 || action == 0) {
            KeyBoardUtils.call(key, action);
        }
    }
}
