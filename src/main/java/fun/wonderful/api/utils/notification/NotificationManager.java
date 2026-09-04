package fun.wonderful.api.utils.notification;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {
    public static final long DURATION_MS = 2500;
    private static final List<Entry> entries = new ArrayList<>();

    public static void push(String moduleName, String categoryIcon, boolean enabled) {
        if (moduleName == null || moduleName.isEmpty()) return;
        entries.add(new Entry(moduleName, categoryIcon, enabled, null, System.currentTimeMillis()));
    }

    public static void pushCustom(String text, String categoryIcon) {
        if (text == null || text.isEmpty()) return;
        entries.add(new Entry(text, categoryIcon, false, text, System.currentTimeMillis()));
    }

    public static List<Entry> getActive() {
        long now = System.currentTimeMillis();
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (now - e.startTime > DURATION_MS) {
                it.remove();
            }
        }
        return entries;
    }

    public static class Entry {
        public final String moduleName;
        public final String categoryIcon;
        public final boolean enabled;
        public final String customText;
        public final long startTime;

        public Entry(String moduleName, String categoryIcon, boolean enabled, String customText, long startTime) {
            this.moduleName = moduleName;
            this.categoryIcon = categoryIcon;
            this.enabled = enabled;
            this.customText = customText;
            this.startTime = startTime;
        }

        public boolean isCustom() {
            return customText != null && !customText.isEmpty();
        }
    }
}
