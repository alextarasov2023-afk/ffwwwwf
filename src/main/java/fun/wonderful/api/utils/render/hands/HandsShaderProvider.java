package fun.wonderful.api.utils.render.hands;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/** Оборачивает провайдер руки: каждый буфер — с космической заливкой. */
public class HandsShaderProvider implements VertexConsumerProvider {

    private final VertexConsumerProvider parent;
    private final int mode;
    private final float mix;
    private final boolean glow;
    private final float time;

    public HandsShaderProvider(VertexConsumerProvider parent, int mode, float mix, boolean glow, float time) {
        this.parent = parent;
        this.mode = mode;
        this.mix = mix;
        this.glow = glow;
        this.time = time;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new HandsShaderConsumer(parent.getBuffer(layer), mode, mix, glow, time);
    }
}
