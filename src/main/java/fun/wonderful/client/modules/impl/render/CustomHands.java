package fun.wonderful.client.modules.impl.render;

import net.minecraft.client.render.VertexConsumerProvider;

import fun.wonderful.api.utils.render.hands.HandsShaderProvider;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;

/**
 * Custom Hands — заливает руку (и предмет) анимированной космической
 * «шейдерной» заливкой в той же тематике, что и Shader Sky:
 * Туманность / Аврора / Галактика / Звёздная пыль.
 * Цвет считается по позиции вершин и времени, объём руки сохраняется.
 */
public class CustomHands extends Module {

    public static CustomHands INSTANCE = new CustomHands();

    public final ModeSetting style = new ModeSetting("Заливка", "Туманность",
            "Туманность", "Аврора", "Галактика", "Звёздная пыль");
    public final FloatSetting brightness = new FloatSetting("Яркость", 80f, 0f, 100f, 1f);
    public final BooleanSetting glow = new BooleanSetting("Свечение", true);

    private static final String[] STYLES = {"Туманность", "Аврора", "Галактика", "Звёздная пыль"};

    public CustomHands() {
        super("Custom Hands", "Космическая шейдерная заливка рук", ModuleCategory.RENDER);
        addSettings(style, brightness, glow);
    }

    private static boolean logged = false;

    public static VertexConsumerProvider wrap(VertexConsumerProvider parent) {
        if (INSTANCE == null || !INSTANCE.isEnable()) return parent;
        if (!logged) {
            logged = true;
            System.out.println("[CustomHands] hands shader wrap active: " + INSTANCE.style.getCurrent());
        }
        int idx = 0;
        String cur = INSTANCE.style.getCurrent();
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i].equals(cur)) {
                idx = i;
                break;
            }
        }
        float time = (System.currentTimeMillis() / 1000f) % 1800f;
        return new HandsShaderProvider(parent, idx, INSTANCE.brightness.get() / 100f,
                INSTANCE.glow.isState(), time);
    }
}
