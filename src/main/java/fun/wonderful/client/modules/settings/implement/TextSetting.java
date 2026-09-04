package fun.wonderful.client.modules.settings.implement;

import fun.wonderful.client.modules.settings.Setting;

public class TextSetting extends Setting<TextSetting> {

    private String text;

    public TextSetting(String name, String text) {
        super(name);
        this.text = text;
    }

    public String get() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public TextSetting visible(java.util.function.Supplier<Boolean> visible) {
        return super.visible(visible);
    }
}
