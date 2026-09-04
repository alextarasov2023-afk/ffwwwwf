package fun.wonderful.api.utils.cmd.macro;

import lombok.AllArgsConstructor;
import lombok.Getter;
import fun.wonderful.client.modules.settings.implement.BindSetting;

@AllArgsConstructor @Getter
public class Macro {
    private String name, command;
    private BindSetting bind;
}