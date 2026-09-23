package de.janzimmermann.lastdays.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ATVDeployItem extends Item {
    public ATVDeployItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;

        var pos = player.blockPosition().relative(player.getDirection(), 3);
        Horse horse = EntityType.HORSE.spawn(server, pos, EntitySpawnReason.EVENT);
        if (horse == null) return InteractionResult.FAIL;

        horse.setCustomName(Component.literal("MILITARY ATV"));
        horse.setCustomNameVisible(true);
        horse.addTag("lastdays_vehicle");
        horse.setTamed(true);
        horse.setPersistenceRequired();
        var speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.36D);
        var health = horse.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(45.0D);
        horse.setHealth(45.0F);

        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
