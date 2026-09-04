package fun.wonderful.api.utils.render.fonts.msdf;

import java.util.HashMap;

/**
 * Кэш MSDF-шрифтов. Грузятся только реально используемые атласы:
 * suisse (весь GUI/HUD) и sf_regular. Размеры 8–99 прекешированы.
 */
public class Fonts {

    private static final HashMap<String, MsdfFont> loadedFonts = new HashMap<>();
    private static final HashMap<String, Font[]> fontCache = new HashMap<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        loadFont("sf_regular");
        loadFont("suisse");
    }

    private static void loadFont(String name) {
        try {
            MsdfFont msdfFont = MsdfFont.builder().atlas(name).data(name).build();
            loadedFonts.put(name, msdfFont);

            Font[] fonts = new Font[100];
            for (int i = 8; i < 100; i++) {
                fonts[i] = new Font(msdfFont, i);
            }
            fontCache.put(name, fonts);
        } catch (Exception e) {
            System.err.println("[Fonts] Failed to load " + name + ": " + e.getMessage());
        }
    }

    public static Font getFont(String name, int size) {
        if (!initialized) init();

        String cleanName = name.replace(".ttf", "");

        if (size < 8) size = 8;
        if (size >= 100) size = 99;

        Font[] fonts = fontCache.get(cleanName);
        if (fonts != null && fonts[size] != null) {
            return fonts[size];
        }

        if (!loadedFonts.containsKey(cleanName)) {
            loadFont(cleanName);
        }

        fonts = fontCache.get(cleanName);
        if (fonts != null && fonts[size] != null) {
            return fonts[size];
        }

        return null;
    }
}
