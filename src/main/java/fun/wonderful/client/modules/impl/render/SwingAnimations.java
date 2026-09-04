package fun.wonderful.client.modules.impl.render;
 
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
 
public class SwingAnimations extends Module {
 
    public static SwingAnimations INSTANCE = new SwingAnimations();
 
    public final BooleanSetting swingEnabled = new BooleanSetting("Анимация свинга", true);
 
    public final ModeSetting swingType = new ModeSetting(
            "Тип свинга",
            "Smooth",
            "Smooth", "ToBack", "SelfBack", "Spin", "Heavy", "Swipe", "Slash"
    ).visible(() -> swingEnabled.isState());
 
    public final FloatSetting swingStrength = new FloatSetting("Сила анимации", 1f, 0.1f, 3f, 0.01f)
            .visible(() -> swingEnabled.isState());
 
    public final BooleanSetting auraTargetOnly = new BooleanSetting("Только при Aura", false)
            .visible(() -> swingEnabled.isState());
 
    public final BooleanSetting swapHands = new BooleanSetting("Свап рук", false)
            .visible(() -> swingEnabled.isState());
 
    public final BooleanSetting eatAnim = new BooleanSetting("Анимация еды", false)
            .visible(() -> swingEnabled.isState());
 
    public final BooleanSetting smoothSlowdown = new BooleanSetting("Плавное замедление", true)
            .visible(() -> swingEnabled.isState());
 
    public final FloatSetting slowdown = new FloatSetting("Замедление", 2.0f, 1.0f, 6.0f, 0.1f)
            .visible(() -> swingEnabled.isState() && smoothSlowdown.isState());
 
    public SwingAnimations() {
        super("SwingAnimations", "Кастомная анимация атаки", ModuleCategory.RENDER);
        addSettings(swingEnabled, swingType, swingStrength, auraTargetOnly, swapHands, eatAnim, smoothSlowdown, slowdown);
    }
}
