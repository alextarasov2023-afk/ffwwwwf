package fun.wonderful.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface ICameraAccessor {

    @Invoker("setRotation")
    void invokeSetRotation(float yaw, float pitch);
}
