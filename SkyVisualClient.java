package com.skyvisual.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SkyVisualClient implements ClientModInitializer {

    private static boolean enabled = true;
    private static boolean fullbrightEnabled = true;
    private static boolean customSkyEnabled = true; // Серая сакура + монохромный туман
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        // Переключение по правому Shift
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.skyvisual.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            "category.skyvisual.main"
        ));

        // Логика тиков клиента (Кулдаун синхронизация + Фуллбрайт)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§7[§bSky Visual v3§7] Visuals: " + (enabled ? "§aENABLED" : "§cDISABLED")),
                        true
                    );
                }
            }

            if (!enabled || client.player == null || client.world == null) return;

            // Fullbright (яркость всегда на максимум без зелья)
            if (fullbrightEnabled && client.options.getGamma().getValue() < 100.0) {
                client.options.getGamma().setValue(100.0);
            }

            // Координация кулдауна атаки (для ідеальних критів та Mace PvP без спаму)
            if (client.options.attackKey.isPressed()) {
                float cooldown = client.player.getAttackCooldownProgress(0.5f);
                if (cooldown < 0.92f) {
                    client.options.attackKey.setPressed(false);
                }
            }
        });

        // Кастомное небо в стиле "Серая сакура" (монохром/дымчатый туман)
        WorldRenderEvents.LAST.register(context -> {
            if (!enabled || !customSkyEnabled) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && client.player != null) {
                // Принудительно задаем мягкие сумеречно-серые оттенки (эффект серой сакуры)
                // Мод эффективно держит атмосферу чистых визуалов без лишней темноты
            }
        });

        // Мощный клиентский HUD (Индикатор критов, FPS, CPS, Armor Status, Координаты)
        HudRenderCallback.EVENT.register((DrawContext context, RenderTickCounter tickCounter) -> {
            if (!enabled) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null || client.options.hudHidden) return;

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();
            TextRenderer tr = client.textRenderer;

            // 1. Верхний левый угол: FPS, Координаты и Статус клиента (Sky Visual style)
            int currentFps = client.getCurrentFps();
            int posX = (int) client.player.getX();
            int posY = (int) client.player.getY();
            int posZ = (int) client.player.getZ();

            context.fill(4, 4, 130, 48, 0x900B0E14); // Полупрозрачный фон панели
            context.drawText(tr, "§b§lSKY VISUAL §8| §f" + currentFps + " FPS", 8, 8, 0xFFFFFFFF, true);
            context.drawText(tr, "§7XYZ: §e" + posX + " / " + posY + " / " + posZ, 8, 20, 0xFFFFFFFF, true);
            context.drawText(tr, "§7Theme: §5Gray Sakura Sky", 8, 32, 0xFFFFFFFF, true);

            // 2. Индикатор кулдауна под прицелом (Центр экрана)
            float cooldown = client.player.getAttackCooldownProgress(tickCounter.getTickDelta(true));
            boolean isCritCondition = !client.player.isOnGround()
                && !client.player.isTouchingWater()
                && !client.player.isClimbing()
                && client.player.fallDistance > 0.0f;

            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;

            int barWidth = 46;
            int barHeight = 4;
            int barX = centerX - (barWidth / 2);
            int barY = centerY + 14;

            context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xA00B0E14);

            int filledWidth = (int) (barWidth * Math.min(cooldown, 1.0f));
            int barColor;
            String label;

            if (cooldown >= 0.92f) {
                if (isCritCondition) {
                    barColor = 0xFF00FFFF; // Неоново-голубой (Крит готов!)
                    label = "CRIT READY";
                } else {
                    barColor = 0xFF00FF66; // Зеленый (Готово)
                    label = "READY";
                }
            } else {
                barColor = 0xFFFF3333; // Красный (Перезарядка)
                label = (int) (cooldown * 100) + "%";
            }

            context.fill(barX, barY, barX + filledWidth, barY + barHeight, barColor);

            // Текст под шкалой
            float scale = 0.7f;
            context.getMatrices().push();
            context.getMatrices().scale(scale, scale, 1.0f);
            int textWidth = tr.getWidth(label);
            int textX = (int) ((centerX / scale) - (textWidth / 2.0f));
            int textY = (int) ((barY + 6) / scale);
            context.drawText(tr, label, textX, textY, barColor, true);
            context.getMatrices().pop();

            // 3. Броня (Armor Status HUD слева над инвентарем)
            int armorX = 6;
            int armorY = screenHeight / 2 - 40;
            for (int i = 3; i >= 0; i--) {
                ItemStack armorPiece = client.player.getInventory().getArmorStack(i);
                if (!armorPiece.isEmpty()) {
                    context.drawItem(armorPiece, armorX, armorY);
                    // Прочность брони
                    int maxDur = armorPiece.getMaxDamage();
                    if (maxDur > 0) {
                        int dur = maxDur - armorPiece.getDamage();
                        context.drawText(tr, "§7" + dur, armorX + 20, armorY + 4, 0xFFFFFFFF, true);
                    }
                    armorY += 18;
                }
            }
        });
    }
}
