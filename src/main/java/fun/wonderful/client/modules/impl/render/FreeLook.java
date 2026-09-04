package fun.wonderful.client.modules.impl.render;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.api.storages.implement.FreeLookStorage;
import fun.wonderful.client.modules.Module;

/**
 * FreeLook — позволяет свободно осматриваться при включённой ротации.
 * Камера следует за мышкой, ротация применяется к удару, а не к камере.
 */
public class FreeLook extends Module {

    public static FreeLook INSTANCE = new FreeLook();

    public FreeLook() {
        super("FreeLook", "Свободный обзор камеры при ротации", ModuleCategory.RENDER);
    }

    @Override
    public void onEnable() {
        FreeLookStorage.setActive(true);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        FreeLookStorage.setActive(false);
        super.onDisable();
    }
}
