package de.janzimmermann.lastdays.system;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class InfectedFactory {
    public enum Variant { WALKER, RUNNER, BRUTE, SPITTER, BOSS }

    public static LivingEntity spawn(ServerLevel level, BlockPos pos, Variant variant, int day) {
        LivingEntity mob = switch (variant) {
            case SPITTER -> EntityType.WITCH.spawn(level, pos, EntitySpawnReason.EVENT);
            case BOSS -> EntityType.RAVAGER.spawn(level, pos, EntitySpawnReason.EVENT);
            default -> EntityType.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
        };
        if (mob == null) return null;

        mob.addTag("lastdays_infected");
        mob.addTag("lastdays_" + variant.name().toLowerCase());
        if (mob instanceof Mob m) m.setPersistenceRequired();

        double scale = 1.0 + Math.min(day, 500) / 220.0;
        var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        var moveSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);

        if (maxHealth != null) maxHealth.setBaseValue(maxHealth.getBaseValue() * scale);
        if (attack != null) attack.setBaseValue(Math.max(attack.getBaseValue(), 3.0D) * (1.0 + Math.min(day, 300) / 350.0));

        switch (variant) {
            case RUNNER -> {
                mob.setCustomName(Component.literal("RUNNER"));
                if (moveSpeed != null) moveSpeed.setBaseValue(0.39D + Math.min(day, 250) * 0.00025D);
                if (maxHealth != null) maxHealth.setBaseValue(18.0D * scale);
            }
            case BRUTE -> {
                mob.setCustomName(Component.literal("BRUTE"));
                if (moveSpeed != null) moveSpeed.setBaseValue(0.24D);
                if (maxHealth != null) maxHealth.setBaseValue(55.0D * scale);
                if (attack != null) attack.setBaseValue(10.0D * scale);
            }
            case SPITTER -> {
                mob.setCustomName(Component.literal("SPITTER"));
                if (maxHealth != null) maxHealth.setBaseValue(30.0D * scale);
            }
            case BOSS -> {
                mob.setCustomName(Component.literal("SIEGE BEAST"));
                mob.setCustomNameVisible(true);
                if (maxHealth != null) maxHealth.setBaseValue(220.0D * scale);
                if (attack != null) attack.setBaseValue(20.0D * scale);
            }
            case WALKER -> mob.setCustomName(Component.literal("INFECTED"));
        }
        mob.setHealth((float) mob.getAttributeValue(Attributes.MAX_HEALTH));
        return mob;
    }

    private InfectedFactory() {}
}