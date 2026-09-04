package fun.wonderful.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import fun.wonderful.api.events.Event;

public class EventRender extends Event {

    /** 2D-HUD рендер (InGameHud.render TAIL), матрицы экрана внутри DrawContext. */
    @Getter
    @Setter
    @AllArgsConstructor
    public static class Default extends Event {
        private final DrawContext context;
        private final float partialTicks;
    }
}
