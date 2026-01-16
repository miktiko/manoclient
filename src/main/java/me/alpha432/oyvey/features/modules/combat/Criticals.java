package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

public class TriggerBot extends Module {

    public Setting<Boolean> onlySword = bool("OnlySword", true);
    public Setting<Boolean> onlyWhenMouse = bool("OnlyMouseHold", false);
    public Setting<Boolean> ignoreInvisible = bool("IgnoreInvisible", true);
    public Setting<Boolean> ignorePassive = bool("IgnorePassive", true);
    public Setting<Integer> delay = num("DelayMS", 50, 0, 300);
    public Setting<Float> cooldown = num("Cooldown", 0.9f, 0.1f, 1.0f);

    private long lastAttack = 0;

    public TriggerBot() {
        super("TriggerBot", "Auto attacks when crosshair is on entity", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;

        if (onlyWhenMouse.getValue() && !mc.options.keyAttack.isDown()) return;

        if (!(mc.hitResult instanceof EntityHitResult hit)) return;
        Entity target = hit.getEntity();

        if (!isValidTarget(target)) return;

        if (onlySword.getValue()) {
            if (!mc.player.getMainHandItem().getItem().toString().toLowerCase().contains("sword")) return;
        }

        if (mc.player.getAttackStrengthScale(0) < cooldown.getValue()) return;

        if (System.currentTimeMillis() - lastAttack < delay.getValue()) return;

        attack(target);
        lastAttack = System.currentTimeMillis();
    }

    private void attack(Entity entity) {
        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean isValidTarget(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (e == mc.player) return false;
        if (!e.isAlive()) return false;

        if (ignoreInvisible.getValue() && e.isInvisible()) return false;

        if (ignorePassive.getValue()) {
            if (!(e instanceof Player)) return false;
        }

        return true;
    }

    @Override
    public String getDisplayInfo() {
        return delay.getValue() + "ms";
    }
}
