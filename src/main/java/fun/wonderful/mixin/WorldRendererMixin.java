package fun.wonderful.mixin;

import fun.wonderful.Wonderful;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.implement.Event3DRender;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void wonderful$onWorldRender(ObjectAllocator allocator,
                                         RenderTickCounter tickCounter,
                                         boolean renderBlockOutline,
                                         Camera camera,
                                         GameRenderer gameRenderer,
                                         Matrix4f positionMatrix,
                                         Matrix4f projectionMatrix,
                                         CallbackInfo ci) {
        if (Wonderful.INSTANCE == null || !Wonderful.isReady()) return;

        boolean has3DListeners = EventInvoker.hasListeners(Event3DRender.class);
        if (!has3DListeners) return;

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);

        new Event3DRender(matrices, positionMatrix, projectionMatrix, camera, tickCounter.getTickDelta(false)).call();
    }
}
