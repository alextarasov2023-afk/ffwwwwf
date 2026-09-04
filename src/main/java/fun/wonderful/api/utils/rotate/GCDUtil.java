package fun.wonderful.api.utils.rotate;

import fun.wonderful.api.QClient;

public class GCDUtil implements QClient {

    public static float getGCDValue() {
        if (mc == null || mc.options == null) {
            return 0.15f;
        }
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        float gcd = (float) ((Math.pow(sensitivity * 0.6D + 0.2D, 3.0D) * 1.2D * 8.0D) * 0.15D);
        return Math.max(gcd, 0.01f);
    }

    public static float getFixedRotation(float angle) {
        float gcd = getGCDValue();
        return angle - angle % gcd;
    }
}
