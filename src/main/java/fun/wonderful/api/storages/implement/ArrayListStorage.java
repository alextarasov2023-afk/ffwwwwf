package fun.wonderful.api.storages.implement;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.impl.TestModules;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ArrayList — список включённых модулей слева под вотермаркой.
 * Сортировка по ширине названия, плавная перестановка (Y дотягивается до
 * целевого места), выезд слева при включении и затухание при выключении.
 * Цвет каждой строки — позиция в градиенте темы (как акцент клик-гуи).
 */
public class ArrayListStorage implements QClient {

    public static ArrayListStorage INSTANCE;

    private static final float X = 8f;
    private static final float Y0 = 52f;
    private static final float LINE_H = 15.5f;

    private Module hudModule;

    private static final class Line {
        float y = Float.NaN;
        final AnimationUtils alpha = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);
        final AnimationUtils slide = new AnimationUtils(0f, 11f, Easings.BACK_OUT);
    }

    private final Map<Module, Line> lines = new ConcurrentHashMap<>();
    private long lastFrameNanos;

    public ArrayListStorage() {
        INSTANCE = this;
        EventInvoker.register(this);
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.getClass() == TestModules.Watermark.class) {
                this.hudModule = m;
                break;
            }
        }
    }

    private boolean isEnabled() {
        if (hudModule == null || !hudModule.isEnable()) return false;
        return hudModule instanceof TestModules.Watermark wm && wm.showArrayList();
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016f
                : MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000f, 0.001f, 0.05f);
        lastFrameNanos = now;

        Font font = Fonts.getFont("suisse", 12);
        if (font == null) return;

        // Активные модули: включены, кроме самого Худа; по убыванию ширины
        List<Module> active = new ArrayList<>();
        if (isEnabled()) {
            for (Module m : ModuleClass.INSTANCE.getObject()) {
                if (m == hudModule || !m.isEnable()) continue;
                active.add(m);
            }
            active.sort(Comparator.comparingDouble((Module m) -> font.getWidth(m.getName())).reversed());
        }

        // Целевые анимации: кто активен — появляется, остальные — затухают
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m == hudModule) continue;
            Line l = lines.computeIfAbsent(m, k -> new Line());
            boolean on = active.contains(m);
            l.alpha.update(on ? 1f : 0f);
            l.slide.update(on ? 1f : 0f);
        }

        MatrixStack ms = event.getContext().getMatrices();

        // Раскладка: цель по порядку, текущий Y плывёт к цели (плавная перестановка)
        float targetY = Y0;
        float k = 1f - (float) Math.exp(-dt * 14.0f);
        for (Module m : active) {
            Line l = lines.get(m);
            if (l == null) continue;
            if (Float.isNaN(l.y)) l.y = targetY;
            l.y += (targetY - l.y) * k;
            targetY += LINE_H;
        }

        // Рендер: и активные, и затухающие строки (пока alpha > 0)
        Iterator<Map.Entry<Module, Line>> it = lines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Module, Line> e = it.next();
            Line l = e.getValue();
            float a = MathHelper.clamp(l.alpha.getValue(), 0f, 1f);
            float s = MathHelper.clamp(l.slide.getValue(), 0f, 1f);
            if (a <= 0.02f && !e.getKey().isEnable()) {
                it.remove();
                continue;
            }
            if (a <= 0.02f || Float.isNaN(l.y)) continue;

            Module m = e.getKey();
            float w = font.getWidth(m.getName());
            float px = X + (1f - s) * (-(w + 16f));
            float py = l.y + (1f - a) * 4f;

            // Цвет строки — единый акцент клиента (как в клик-гуи)
            int base = ThemePanel.accentSolid();

            // Тонкая акцентная риска + имя с тенью
            RenderUtils.drawRoundedRect(ms, X - 4f, py + 2.5f, 1.6f, LINE_H - 5f, 0.8f,
                    ColorUtils.applyAlpha(base, 0.65f * a));
            font.draw(ms, m.getName(), px + 0.8f, py + 0.8f,
                    ColorUtils.rgba(0, 0, 0, (int) (130 * a)));
            font.draw(ms, m.getName(), px, py, ColorUtils.applyAlpha(base, 0.95f * a));
        }
    }
}
