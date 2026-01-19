package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.math.MathUtils;
import me.alpha432.oyvey.util.misc.Timer;
import me.alpha432.oyvey.util.combat.CombatUtil;
import me.alpha432.oyvey.util.player.FriendManager;
import me.alpha432.oyvey.util.player.TeamManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class TriggerBot extends Module {
    private final Setting<Double> swordThreshold = register(new Setting<>("Sword Threshold", 0.9, 0.1, 1.0));
    private final Setting<Double> axeThreshold = register(new Setting<>("Axe Threshold", 0.9, 0.1, 1.0));
    private final Setting<Double> axePostDelay = register(new Setting<>("Axe Post Delay", 120.0, 1.0, 500.0));
    private final Setting<Double> reactionTime = register(new Setting<>("Reaction Time", 20.0, 1.0, 350.0));
    private final Setting<Double> missChance = register(new Setting<>("Miss Chance", 0.0, 0.0, 100.0));
    private final Setting<String> cooldownMode = register(new Setting<>("Cooldown Mode", "Smart", "Smart", "Strict", "None"));
    private final Setting<String> critMode = register(new Setting<>("Criticals", "Strict", "None", "Strict"));
    private final Setting<Boolean> ignorePassiveMobs = register(new Setting<>("No Passive", true));
    private final Setting<Boolean> ignoreInvisible = register(new Setting<>("No Invisible", true));
    private final Setting<Boolean> ignoreCrystals = register(new Setting<>("No Crystals", true));
    private final Setting<Boolean> respectShields = register(new Setting<>("Ignore Shields", false));
    private final Setting<Boolean> useOnlySwordOrAxe = register(new Setting<>("Only Sword or Axe", true));
    private final Setting<Boolean> onlyWhenMouseDown = register(new Setting<>("Only Mouse Hold", false));
    private final Setting<Boolean> disableOnWorldChange = register(new Setting<>("Disable on Load", false));
    private final Setting<Boolean> samePlayer = register(new Setting<>("Same Player", false));
    
    private final Timer timer = new Timer();
    private final Timer samePlayerTimer = new Timer();
    private final Timer timerReactionTime = new Timer();
    private boolean waitingForDelay = false;
    private boolean waitingForReaction = false;
    private long currentReactionDelay = 0;
    private float randomizedPostDelay = 0;
    private float randomizedThreshold = 0;
    private Entity target;
    private String lastTargetUUID = null;
    
    public TriggerBot() {
        super("TriggerBot", "Makes you automatically attack once aimed at a target", Category.COMBAT);
    }
    
    @Override
    public void onEnable() {
        timer.reset();
        timerReactionTime.reset();
        waitingForReaction = false;
        waitingForDelay = false;
    }
    
    @Override
    public void onDisable() {
        timer.reset();
        timerReactionTime.reset();
        waitingForReaction = false;
        waitingForDelay = false;
    }
    
    @Override
    public void onUpdate() {
        if (nullCheck()) return;
        
        if (mc.player.isUsingItem()) return;
        if (mc.screen != null) return;
        
        target = mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY ? 
                ((EntityHitResult) mc.crosshairTarget).getEntity() : null;
        
        if (target == null) return;
        
        if (!isHoldingSwordOrAxe()) return;
        
        if (onlyWhenMouseDown.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            return;
        }
        
        if (!hasTarget(target)) return;
        
        if (respectShields.getValue()) {
            Item item = mc.player.getMainHandStack().getItem();
            if (target instanceof PlayerEntity playerTarget && CombatUtil.isShieldFacingAway(playerTarget) && item instanceof SwordItem) {
                return;
            }
        }
        
        if (target != null && (!target.getUuidAsString().equals(lastTargetUUID))) {
            lastTargetUUID = target.getUuidAsString();
        }
        
        if (!waitingForReaction) {
            waitingForReaction = true;
            timerReactionTime.reset();
            long delay;
            switch (cooldownMode.getValue()) {
                case "Smart" -> {
                    double distance = mc.player.distanceTo(target);
                    double maxDistance = 3.0;
                    double multiplier = distance < maxDistance / 2 ? 0.66 : 1.0;
                    delay = (long) MathUtils.randomDoubleBetween(reactionTime.getValue(), reactionTime.getMax());
                    delay *= (long) multiplier;
                }
                case "None" -> delay = 0;
                default -> delay = (long) MathUtils.randomDoubleBetween(reactionTime.getValue(), reactionTime.getMax());
            }
            currentReactionDelay = delay;
        }
        
        if (waitingForReaction && timerReactionTime.hasPassed(currentReactionDelay)) {
            if (critMode.getValue().equals("Strict")) {
                if (!mc.player.isOnGround() && !mc.player.isClimbing()) {
                    if (canCrit() && mc.player.getAttackCooldownProgress(0.0f) >= swordThreshold.getValue().floatValue()) {
                        if (hasTarget(target) && samePlayerCheck(target)) {
                            attack();
                            waitingForReaction = false;
                        }
                    }
                } else {
                    if (hasElapsedDelay() && hasTarget(target) && samePlayerCheck(target)) {
                        attack();
                        waitingForReaction = false;
                    }
                }
            } else {
                if (hasElapsedDelay() && hasTarget(target) && samePlayerCheck(target)) {
                    attack();
                    waitingForReaction = false;
                }
            }
        }
    }
    
    private boolean samePlayerCheck(Entity entity) {
        if (!samePlayer.getValue()) return true;
        if (entity == null) return false;
        if (lastTargetUUID == null || samePlayerTimer.hasPassed(3000)) {
            lastTargetUUID = entity.getUuidAsString();
            samePlayerTimer.reset();
            return true;
        }
        return entity.getUuidAsString().equals(lastTargetUUID);
    }
    
    private boolean canCrit() {
        if (mc.player == null) return false;
        return !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isInLava() && 
               !mc.player.hasEffect(StatusEffects.BLINDNESS) && mc.player.fallDistance > 0.065f && 
               mc.player.getVehicle() == null;
    }
    
    private boolean setPreferCrits() {
        if (mc.player == null || mc.level == null) return false;
        String mode = critMode.getValue();
        if (mode.equals("None")) return false;
        
        if (mc.player.hasEffect(StatusEffects.LEVITATION) || 
            mc.player.hasEffect(StatusEffects.SLOW_FALLING) || 
            mc.player.hasEffect(StatusEffects.BLINDNESS)) {
            return false;
        }
        
        if (!(mc.crosshairTarget instanceof EntityHitResult hitResult)) return false;
        Entity targetEntity = hitResult.getEntity();
        if (targetEntity != target || !hasTarget(targetEntity)) return false;
        
        if (mc.player.isInWater() || mc.player.isInLava() || mc.player.isSwimming() || mc.player.isClimbing()) {
            return false;
        }
        
        BlockState state = mc.level.getBlockState(mc.player.blockPosition());
        if (state.is(Blocks.COBWEB) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.VINE) || 
            state.is(Blocks.SCAFFOLDING) || state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK) || 
            state.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        
        boolean cooldownReady = mc.player.getAttackCooldownProgress(0.0f) >= swordThreshold.getValue().floatValue();
        return mode.equals("Strict") && cooldownReady && canCrit();
    }
    
    private boolean hasElapsedDelay() {
        if (setPreferCrits()) return false;
        
        Item heldItem = mc.player.getMainHandStack().getItem();
        float cooldown = mc.player.getAttackCooldownProgress(0.0f);
        
        if (heldItem instanceof AxeItem) {
            if (!waitingForDelay) {
                randomizedThreshold = (float) MathUtils.randomDoubleBetween(axeThreshold.getMinValue(),
                        axeThreshold.getMaxValue());
                randomizedPostDelay = (float) MathUtils.randomDoubleBetween(axePostDelay.getMinValue(),
                        axePostDelay.getMaxValue());
                waitingForDelay = true;
            }
            if (cooldown >= randomizedThreshold) {
                if (timer.hasElapsedTime((long) randomizedPostDelay, true)) {
                    waitingForDelay = false;
                    return true;
                }
            } else {
                timer.reset();
            }
            return false;
        } else {
            float swordDelay = (float) MathUtils.randomDoubleBetween(swordThreshold.getMinValue(),
                    swordThreshold.getMaxValue());
            return cooldown >= swordDelay;
        }
    }

    private boolean isHoldingSwordOrAxe() {
        if (!useOnlySwordOrAxe.getValue())
            return true;
        assert mc.player != null;
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof AxeItem || item instanceof SwordItem;
    }

    public void attack() {
        if (missChance.getMinValue() > 0 && Math.random() * 100 < missChance.getMinValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.resetLastAttackedTicks();
        } else {
            ((MinecraftClientAccessor) mc).invokeDoAttack();
        }
        if (samePlayer.getValue() && target != null) {
            lastTargetUUID = target.getUuidAsString();
            samePlayerTimer.reset();
        }
        waitingForDelay = false;
    }

    public boolean hasTarget(Entity en) {
        if (en == mc.player || en == mc.cameraEntity || !en.isAlive())
            return false;
        if (en instanceof PlayerEntity player && FriendManager.isFriend(player.getUuid()))
            return false;
        if (Teams.isTeammate(en))
            return false;
        if (en instanceof WindChargeEntity)
            return false;

        return switch (en) {
            case EndCrystalEntity ignored when ignoreCrystals.getValue() -> false;
            case Tameable ignored -> false;
            case PassiveEntity ignored when ignorePassiveMobs.getValue() -> false;
            default -> !ignoreInvisible.getValue() || !en.isInvisible();
        };
    }

    @Override
    public void onEnable() {
        timer.reset();
        timerReactionTime.reset();
        waitingForReaction = false;
        waitingForDelay = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        timer.reset();
        timerReactionTime.reset();
        waitingForReaction = false;
        waitingForDelay = false;
        super.onDisable();
    }
}
