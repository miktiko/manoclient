// The package should match your project's structure
package me.alpha432.oyvey.features.modules.combat;

// Imports from your framework
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;

// Imports from Minecraft
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;

public class TriggerBot extends Module {
    public TriggerBot() {
        super("TriggerBot", "Automatically attacks entities on attack press", Category.COMBAT);
    }

    @Subscribe
    private void onPacketSend(PacketEvent.Send event) {
        // We only care about attack packets
        if (event.getPacket() instanceof ServerboundInteractPacket packet && packet.action.getType() == ServerboundInteractPacket.ActionType.ATTACK) {
            // Get the entity the player is trying to attack
            Entity entity = mc.level.getEntity(packet.entityId);

            // Basic checks to avoid attacking invalid or unwanted targets
            if (entity == null || !mc.player.canAttack(entity)) {
                return;
            }
            
            // Avoid attacking non-living things like armor stands or crystals
            if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity instanceof EndCrystal) {
                return;
            }

            // Avoid attacking players on the same team (if applicable)
            if (entity instanceof Player && mc.player.isTeammate((Player) entity)) {
                return;
            }
            
            // If all checks pass, the module will allow the original attack packet to be sent.
            // The original code already handles the attack, so we don't need to do anything else.
            // We can add a swing packet here for visual feedback if the original doesn't.
            mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    @Override
    public String getDisplayInfo() {
        return "Packet";
    }
}
