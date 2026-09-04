package fun.wonderful.client.modules.settings;

import java.util.function.Supplier;

public abstract class Setting<T extends Setting<T>> {

    private final String name;
    private Supplier<Boolean> visible = () -> true;

    protected Setting(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    public boolean isVisible() {
        return this.visible.get();
    }

    @SuppressWarnings("unchecked")
    public T visible(Supplier<Boolean> visible) {
        this.visible = visible;
        return (T) this;
    }
}
