package fun.wonderful.api.storages.implement.helpertstorages;

import lombok.Getter;
import lombok.Setter;
import fun.wonderful.api.QClient;
import fun.wonderful.api.utils.color.ColorUtils;

@Getter
@Setter
public class Theme implements QClient {
    private String name;
    public int[] color;

    public Theme(String name, int... color) {
        this.name = name;
        this.color = color;
    }

    public int getColor(int index) {
        if (this.name.equals("Rainbow")) {
            return ColorUtils.rainbow(10, index, 0.6f, 1, 1);
        }
        return ColorUtils.gradient(5, index, color);
    }
}
