package de.janzimmermann.lastdays.item;

import de.janzimmermann.lastdays.system.NoiseSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TurretDeployItem extends Item {
    public TurretDeployItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        var pos = player.blockPosition().relative(player.getDirection(), 2);
        SnowGolem turret = EntityType.SNOW_GOLEM.spawn(server, pos, EntitySpawnReason.EVENT);
        if (turret == null) return InteractionResult.FAIL;
        turret.setCustomName(Component.literal("AUTO-TURRET"));
        turret.setCustomNameVisible(true);
        turret.addTag("lastdays_turret");
        turret.setPersistenceRequired();
        NoiseSystem.addNoise(sp, 3);

        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}