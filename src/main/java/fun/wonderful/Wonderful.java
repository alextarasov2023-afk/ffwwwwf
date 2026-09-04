package fun.wonderful;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

import fun.wonderful.api.QClient;
import fun.wonderful.api.storages.InitializeStorage;
import fun.wonderful.api.storages.implement.*;
import fun.wonderful.api.utils.draggable.Draggable;
import fun.wonderful.api.utils.tps.TPSCalc;
import fun.wonderful.client.modules.Module;

public class Wonderful implements ModInitializer, QClient {

    public static Wonderful INSTANCE = new Wonderful();

    public ModuleStorage moduleStorage;
    public ThemeStorage themeStorage;
    public TPSCalc tpsCalc;
    public LocalizationStorage localizationStorage;
    public FreeLookStorage freeLookStorage;
    public RotationStorage rotationStorage;
    public FriendStorage friendStorage;
    public MacroStorage macroStorage;
    public StaffStorage staffStorage;
    public WaypointStorage waypointStorage;
    public CommandStorage commandStorage;
    public ConfigStorage configStorage;

    public File configsDir;
    public File globalsDir;

    private static volatile boolean ready = false;

    public Wonderful() {
        INSTANCE = this;
    }

    @Override
    public void onInitialize() {
        File root = FabricLoader.getInstance().getGameDir().resolve("wonderful").toFile();
        this.configsDir = new File(root, "configs");
        this.globalsDir = new File(root, "globals");
        if (!this.configsDir.exists()) this.configsDir.mkdirs();
        if (!this.globalsDir.exists()) this.globalsDir.mkdirs();

        new InitializeStorage().onInitialize();

        ready = true;
    }

    public static boolean isReady() {
        return ready;
    }

    public static Draggable draggable(Module module, String name, float initialXVal, float initialYVal) {
        Draggable draggable = new Draggable(module, name, initialXVal, initialYVal);
        DragStorage.draggables.put(name, draggable);
        return draggable;
    }
}
