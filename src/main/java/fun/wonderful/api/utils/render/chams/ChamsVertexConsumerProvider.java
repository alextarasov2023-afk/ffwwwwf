package fun.wonderful.api.utils.render.chams;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * Прокси-потребитель вершинных буферов для Chams.
 * <p>
 * Оборачивает исходный {@link VertexConsumerProvider} и на каждый запрос
 * буфера возвращает прокси {@link ChamsVertexConsumer}, перекрашивающий
 * отрисованную модель в цвет модуля.
 */
public class ChamsVertexConsumerProvider implements VertexConsumerProvider {

    private final VertexConsumerProvider delegate;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    public ChamsVertexConsumerProvider(VertexConsumerProvider delegate, float red, float green, float blue, float alpha) {
        this.delegate = delegate;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new ChamsVertexConsumer(delegate.getBuffer(layer), red, green, blue, alpha);
    }
}
