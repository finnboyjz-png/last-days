package de.janzimmermann.lastdays.system;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Monster;

public final class DefenseSystem {
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) return;

        for (var player : level.players()) {
            for (SnowGolem turret : level.getEntitiesOfClass(SnowGolem.class,
                    player.getBoundingBox().inflate(80.0D),
                    g -> g.getTags().contains("lastdays_turret"))) {
                var targets = level.getEntitiesOfClass(Monster.class, turret.getBoundingBox().inflate(18.0D),
                        m -> m.isAlive() && !m.getTags().contains("lastdays_bandit"));
                Monster nearest = null;
                double best = Double.MAX_VALUE;
                for (Monster m : targets) {
                    double d = turret.distanceToSqr(m);
                    if (d < best) { best = d; nearest = m; }
                }
                if (nearest != null) turret.setTarget(nearest);
            }

            if (level.getGameTime() % 20 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(72.0D),
                        x -> x.getTags().contains("lastdays_brute") || x.getTags().contains("lastdays_boss"))) {
                    BlockPos c = e.blockPosition();
                    for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 2, 1))) {
                        var state = level.getBlockState(p);
                        if (state.is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                                || state.is(net.minecraft.world.level.block.Blocks.GLASS)
                                || state.is(net.minecraft.world.level.block.Blocks.OAK_DOOR)) {
                            level.destroyBlock(p, false);
                        }
                    }
                }
            }
        }
    }

    private DefenseSystem() {}
}