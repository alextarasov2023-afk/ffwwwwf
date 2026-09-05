package fun.wonderful.client.modules.impl.render;

import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;

/**
 * Худ — единый HUD-модуль: ватермарка, Target HUD, кейбинды,
 * клавиши (keystrokes) и список стаффа онлайн.
 * Отрисовкой занимается HudStorage (слушает EventRender.Default).
 */
public class Hud extends Module {

    public static Hud INSTANCE = new Hud();

    public final BooleanSetting watermark = new BooleanSetting("Ватермарка", true);
    public final BooleanSetting targetHud = new BooleanSetting("Target HUD", true);
    public final BooleanSetting keybinds = new BooleanSetting("Кейбинды", true);
    public final BooleanSetting keystrokes = new BooleanSetting("Клавиши", true);
    public final BooleanSetting staffList = new BooleanSetting("Staff List", true);
    public final BooleanSetting showFps = new BooleanSetting("FPS", true);
    public final BooleanSetting showPing = new BooleanSetting("Пинг", true);

    public Hud() {
        super("Худ", "HUD: ватермарка, таргет-панель, кейбинды, клавиши, стафф", ModuleCategory.RENDER);
        addSettings(watermark, targetHud, keybinds, keystrokes, staffList, showFps, showPing);
    }
}
