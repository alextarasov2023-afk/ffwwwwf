package fun.wonderful.api.storages.implement;

import com.google.gson.*;

import fun.wonderful.Wonderful;
import fun.wonderful.api.utils.cmd.macro.Macro;
import fun.wonderful.api.utils.namespaced.FileUtils;
import fun.wonderful.client.modules.settings.implement.BindSetting;
import fun.wonderful.client.ui.clickgui.ThemePanel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigStorage {

    public String currentConfig = "default";
    private final String extension = ".wonder";

    public ConfigStorage() {
        loadAll();
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveAll));
    }


    private void loadAll() {
        try {
            loadGlobals();
            loadConfig(currentConfig);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }


    private void saveAll() {
        try {
            saveGlobals();
            saveConfig(currentConfig);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }


    public void saveConfig(String config) throws Exception {
        File file = new File(Wonderful.INSTANCE.configsDir, config + extension);

        JsonObject object = new JsonObject();
        object.add("config", new JsonPrimitive(config));
        object.add("theme", new JsonPrimitive(Wonderful.INSTANCE.themeStorage.getThemes().name()));
        object.add("language", new JsonPrimitive(Wonderful.INSTANCE.localizationStorage.getLanguage().name()));

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(object));
        }

        this.currentConfig = config;
    }


    public void loadConfig(String config) throws Exception {
        if (!FileUtils.exists(Wonderful.INSTANCE.configsDir + "/" + config + extension)) return;
        JsonObject object;
        try (InputStream stream = Files.newInputStream(Paths.get(Wonderful.INSTANCE.configsDir + "/" + config + extension));
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            object = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (object.has("theme")) {
            String themeName = object.get("theme").getAsString();
            for (ThemeStorage.Themes theme : ThemeStorage.Themes.values()) {
                if (theme.name().equals(themeName)) {
                    Wonderful.INSTANCE.themeStorage.setThemes(theme);
                    break;
                }
            }
        }

        if (object.has("language")) {
            try {
                Wonderful.INSTANCE.localizationStorage.setLanguage(LocalizationStorage.Language.valueOf(object.get("language").getAsString()));
            } catch (Exception ignored) {
            }
        }

        this.currentConfig = config;
    }


    public void saveGlobals() throws Exception {
        File file = new File(Wonderful.INSTANCE.globalsDir, "globals" + extension);
        JsonObject object = new JsonObject();
        object.add("config", new JsonPrimitive(currentConfig));

        object.add("theme", new JsonPrimitive(Wonderful.INSTANCE.themeStorage.getThemes().name()));
        object.add("language", new JsonPrimitive(Wonderful.INSTANCE.localizationStorage.getLanguage().name()));

        JsonObject tp = new JsonObject();
        tp.addProperty("r1", ThemePanel.r1);
        tp.addProperty("g1", ThemePanel.g1);
        tp.addProperty("b1", ThemePanel.b1);
        tp.addProperty("r2", ThemePanel.r2);
        tp.addProperty("g2", ThemePanel.g2);
        tp.addProperty("b2", ThemePanel.b2);
        tp.addProperty("gradient", ThemePanel.gradient);
        object.add("themePanel", tp);

        JsonArray friendsArray = new JsonArray();
        Wonderful.INSTANCE.friendStorage.getFriends().forEach(friendsArray::add);
        object.add("friends", friendsArray);

        JsonArray staffsArray = new JsonArray();
        Wonderful.INSTANCE.staffStorage.getStaffs().forEach(staffsArray::add);
        object.add("staffs", staffsArray);

        JsonArray macrosArray = new JsonArray();
        Wonderful.INSTANCE.macroStorage.getMacros().forEach(macro -> {
            JsonObject macroObject = new JsonObject();
            macroObject.addProperty("name", macro.getName());
            macroObject.addProperty("command", macro.getCommand());
            macroObject.addProperty("key", macro.getBind().getKey());
            macrosArray.add(macroObject);
        });
        object.add("macros", macrosArray);

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(object));
        }
    }


    public void loadGlobals() throws Exception {
        if (!FileUtils.exists(Wonderful.INSTANCE.globalsDir + "/" + "globals" + extension)) return;
        JsonObject object;
        try (InputStream stream = Files.newInputStream(Paths.get(Wonderful.INSTANCE.globalsDir + "/" + "globals" + extension));
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            object = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (object.has("config")) currentConfig = object.get("config").getAsString();

        if (object.has("theme")) {
            String themeName = object.get("theme").getAsString();
            for (ThemeStorage.Themes theme : ThemeStorage.Themes.values()) {
                if (theme.name().equals(themeName)) {
                    Wonderful.INSTANCE.themeStorage.setThemes(theme);
                    break;
                }
            }
        }

        if (object.has("language")) {
            try {
                Wonderful.INSTANCE.localizationStorage.setLanguage(LocalizationStorage.Language.valueOf(object.get("language").getAsString()));
            } catch (Exception ignored) {
            }
        }

        if (object.has("themePanel")) {
            try {
                JsonObject tp = object.getAsJsonObject("themePanel");
                ThemePanel.setFromConfig(
                        tp.has("r1") ? tp.get("r1").getAsInt() : ThemePanel.r1,
                        tp.has("g1") ? tp.get("g1").getAsInt() : ThemePanel.g1,
                        tp.has("b1") ? tp.get("b1").getAsInt() : ThemePanel.b1,
                        tp.has("r2") ? tp.get("r2").getAsInt() : ThemePanel.r2,
                        tp.has("g2") ? tp.get("g2").getAsInt() : ThemePanel.g2,
                        tp.has("b2") ? tp.get("b2").getAsInt() : ThemePanel.b2,
                        !tp.has("gradient") || tp.get("gradient").getAsBoolean());
            } catch (Exception ignored) {
            }
        }

        if (object.has("friends")) {
            for (JsonElement element : object.get("friends").getAsJsonArray()) {
                if (Wonderful.INSTANCE.friendStorage.isFriend(element.getAsString())) continue;
                Wonderful.INSTANCE.friendStorage.add(element.getAsString());
            }
        }

        if (object.has("staffs")) {
            for (JsonElement element : object.get("staffs").getAsJsonArray()) {
                if (Wonderful.INSTANCE.staffStorage.isStaff(element.getAsString())) continue;
                Wonderful.INSTANCE.staffStorage.add(element.getAsString());
            }
        }

        if (object.has("macros")) {
            for (JsonElement element : object.get("macros").getAsJsonArray()) {
                try {
                    String name;
                    String command;
                    int key;

                    if (element.isJsonObject()) {
                        JsonObject macroObject = element.getAsJsonObject();
                        name = macroObject.has("name") ? macroObject.get("name").getAsString() : "";
                        command = macroObject.has("command") ? macroObject.get("command").getAsString() : "";
                        key = macroObject.has("key") ? macroObject.get("key").getAsInt() : -1;
                    } else {
                        String[] split = element.getAsString().split(":", 3);
                        if (split.length < 3) continue;
                        name = split[0];
                        command = split[1];
                        key = Integer.parseInt(split[2]);
                    }

                    if (name.isBlank() || Wonderful.INSTANCE.macroStorage.getMacro(name) != null) {
                        continue;
                    }

                    Wonderful.INSTANCE.macroStorage.add(new Macro(name, command, new BindSetting("bind", key)));
                } catch (Exception ignored) {
                }
            }
        }
    }
}
