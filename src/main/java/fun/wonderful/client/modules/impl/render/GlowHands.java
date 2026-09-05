package fun.wonderful.client.modules.impl.render;

import fun.wonderful.client.modules.Module;

/**
 * GlowHands — руки и предмет в руке всегда освещены (fullbright):
 * в пещерах и ночью рука не темнеет, выглядит как мягкое свечение.
 */
public class GlowHands extends Module {

    public static GlowHands INSTANCE = new GlowHands();

    public GlowHands() {
        super("GlowHands", "Светящиеся руки и предмет в темноте", ModuleCategory.RENDER);
    }

    public static boolean isGlow() {
        return INSTANCE != null && INSTANCE.isEnable();
    }
}
