package fun.wonderful.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.wonderful.client.modules.impl.render.SwingAnimations;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
            )
    )
    private void wonderful$onRenderFirstPersonItemCall(HeldItemRenderer instance, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        Hand renderHand = hand;
        SwingAnimations tweaks = SwingAnimations.INSTANCE;
        if (tweaks != null && tweaks.isEnable() && tweaks.swapHands.isState()) {
            renderHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }
        ((HeldItemRendererInvoker) instance).whylol$callRenderFirstPersonItem(player, tickDelta, pitch, renderHand, swingProgress, stack, equipProgress, matrices, vertexConsumers, light);
    }

    @ModifyArg(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IFFLnet/minecraft/util/Arm;)V"
            ),
            index = 5
    )
    private Arm wonderful$swapEmptyHandArm(Arm arm) {
        SwingAnimations tweaks = SwingAnimations.INSTANCE;
        if (tweaks != null && tweaks.isEnable() && tweaks.swapHands.isState()) {
            return arm == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT;
        }
        return arm;
    }

    @Redirect(
            method = "renderFirstPersonItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FFLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V",
                    ordinal = 2
            )
    )
    private void wonderful$onSwingArm(HeldItemRenderer instance, float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm) {
        SwingAnimations tweaks = SwingAnimations.INSTANCE;
        if (tweaks == null || !tweaks.isEnable() || !tweaks.swingEnabled.isState()) {
            this.callSwingArm(instance, swingProgress, equipProgress, matrices, armX, arm);
            return;
        }

        float strength = tweaks.swingStrength.get();
        
        float p = swingProgress;
        if (tweaks.smoothSlowdown.isState()) {
            float e = tweaks.slowdown.get();
            if (e > 1.0f) {
                if (p < 0.5f) {
                    p = (float) Math.pow(2f * p, e) / 2f;
                } else {
                    p = 1f - (float) Math.pow(2f * (1f - p), e) / 2f;
                }
            }
        }

        String mode = tweaks.swingType.getCurrent();
        switch (mode) {
            case "Smooth" -> {
                applySwingOffset(matrices, armX, p, strength);
            }
            case "ToBack" -> {
                float g = MathHelper.sin(MathHelper.sqrt(p) * (float) Math.PI);
                matrices.translate(0.65f * armX, -0.45f, -0.9f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50f));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((-30f * (1f - g * strength) - 30f) * armX));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110f * armX));
            }
            case "SelfBack" -> {
                float anim = (float) Math.sin(p * (Math.PI / 2) * 2);
                matrices.translate(0.65f * armX, -0.3f, -0.8f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * armX));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-70 * armX));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100 - (60 * strength) * anim));
            }
            case "Spin" -> {
                float angle = p * 360f * strength;
                matrices.translate(0.5f * armX, -0.2f, -0.7f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle * armX));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30f * (1f - p)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(15f * armX * (1f - p)));
            }
            default -> this.callSwingArm(instance, swingProgress, equipProgress, matrices, armX, arm);
        }
    }

    private void callSwingArm(HeldItemRenderer instance, float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm) {
        ((HeldItemRendererInvoker) instance).whylol$callSwingArm(swingProgress, equipProgress, matrices, armX, arm);
    }

    private void applySwingOffset(MatrixStack matrices, int i, float swingProgress, float strength) {
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.translate(0.56f * i, -0.52f, -0.72f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45f + f * -20f * strength)));
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * g * -20f * strength));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80f * strength));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45f));
    }

    @Inject(method = "applyEatOrDrinkTransformation", at = @At("HEAD"), cancellable = true)
    private void wonderful$onApplyEatOrDrinkTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, PlayerEntity player, CallbackInfo ci) {
        SwingAnimations tweaks = SwingAnimations.INSTANCE;
        if (tweaks == null || !tweaks.isEnable() || !tweaks.eatAnim.isState() || !player.isUsingItem()) {
            return;
        }
        wonderful$applyEatOrDrinkTransformationCustom(matrices, tickDelta, arm, stack);
        ci.cancel();
    }

    private void wonderful$applyEatOrDrinkTransformationCustom(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack) {
        if (MinecraftClient.getInstance().player == null) {
            return;
        }

        float f = (float) MinecraftClient.getInstance().player.getItemUseTimeLeft() - tickDelta + 1.0F;
        float g = f / (float) stack.getMaxUseTime(MinecraftClient.getInstance().player);
        float h;
        if (g < 0.8F) {
            h = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.005F);
            matrices.translate(0f, h, 0f);
        }

        h = 1.0F - (float) Math.pow(g, 27.0);
        int i = arm == Arm.RIGHT ? 1 : -1;

        matrices.translate(h * 0.6F * i, h * -0.5F, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * h * 90f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * h * 30f));
    }
}
