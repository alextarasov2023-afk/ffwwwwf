package fun.wonderful.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import fun.wonderful.api.events.Event;

@Getter
@Setter
@AllArgsConstructor
public class Event3DRender extends Event {
    private final MatrixStack matrices;
    private final Matrix4f positionMatrix;
    private final Matrix4f projectionMatrix;
    private final Camera camera;
    private final float tickDelta;
}
