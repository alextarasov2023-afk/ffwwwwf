package fun.wonderful.api.storages.implement.helpertstorages.enumvar;


import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.client.modules.Module;

public class ModuleClass extends GlobalObject<Module> implements ModuleRewords {

    public static ModuleClass INSTANCE = new ModuleClass();

    public void initialize() {
        for (final Module module : this.getObject()) {
            if (module.isEnable()) {
                EventInvoker.register(module);
            }
        }
    }
}
