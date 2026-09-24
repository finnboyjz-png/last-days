package de.janzimmermann.lastdays.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fast server-side projectile simulation adapted from the collision/movement approach used by
 * the CC0 Vanilla Gun Mod / bren_port BulletEntity. We deliberately do not register hundreds
 * of physical bullet entities: each shot is simulated for a few ticks and rendered with tracer
 * particles, which is much friendlier to large Blood Moon hordes.
 */
public final class ProjectileSystem {
    private static final List<Shot> SHOTS = new ArrayList<>();

    private static final class Shot {
        final UUID owner;
        Vec3 pos;
        final Vec3 velocity;
        final float damage;
        int life;

        Shot(UUID owner, Vec3 pos, Vec3 velocity, float damage, int life) {
            this.owner = owner;
            this.pos = pos;
            this.velocity = velocity;
            this.damage = damage;
            this.life = life;
        }
    }

    public static void fire(ServerLevel level, ServerPlayer shooter, float damage,
                            double speed, double range, int pellets, double spread) {
        Vec3 look = shooter.getLookAngle().normalize();
        Vec3 eye = shooter.getEyePosition();

        for (int i = 0; i < pellets; i++) {
            double sx = (level.getRandom().nextDouble() - 0.5D) * spread;
            double sy = (level.getRandom().nextDouble() - 0.5D) * spread * 0.75D;
            double sz = (level.getRandom().nextDouble() - 0.5D) * spread;
            Vec3 dir = look.add(sx, sy, sz).normalize();
            int life = Math.max(2, (int)Math.ceil(range / speed));
            SHOTS.add(new Shot(shooter.getUUID(), eye.add(dir.scale(0.55D)), dir.scale(speed), damage, life));
        }

        Vec3 muzzle = eye.add(look.scale(0.85D));
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.025D, 0.025D, 0.025D, 0.005D);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.04D, 0.04D, 0.04D, 0.015D);
    }

    public static void tick(ServerLevel level) {
        if (SHOTS.isEmpty()) return;

        Iterator<Shot> it = SHOTS.iterator();
        while (it.hasNext()) {
            Shot shot = it.next();
            var foundOwner = level.getPlayerByUUID(shot.owner);
            if (!(foundOwner instanceof ServerPlayer owner) || shot.life-- <= 0) {
                it.remove();
                continue;
            }

            Vec3 start = shot.pos;
            Vec3 end = start.add(shot.velocity);

            BlockHitResult blockHit = level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            double maxDistance = start.distanceToSqr(end);
            Vec3 blockPoint = null;
            if (blockHit.getType() != HitResult.Type.MISS) {
                blockPoint = blockHit.getLocation();
                maxDistance = start.distanceToSqr(blockPoint);
            }

            LivingEntity target = null;
            Vec3 entityPoint = null;
            double best = maxDistance;

            AABB search = new AABB(
                    Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                    Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
            ).inflate(0.8D);

            for (Entity e : level.getEntities(owner, search,
                    e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
                LivingEntity living = (LivingEntity)e;
                Optional<Vec3> hit = living.getBoundingBox().inflate(0.28D).clip(start, end);
                if (hit.isEmpty()) continue;
                double d = start.distanceToSqr(hit.get());
                if (d < best) {
                    best = d;
                    target = living;
                    entityPoint = hit.get();
                }
            }

            Vec3 visibleEnd = target != null ? entityPoint : (blockPoint != null ? blockPoint : end);
            tracer(level, start, visibleEnd);

            if (target != null) {
                float finalDamage = shot.damage;
                if (entityPoint != null && entityPoint.y >= target.getEyeY() - 0.35D) {
                    finalDamage *= 1.55F;
                }
                target.hurtServer(level, level.damageSources().playerAttack(owner), finalDamage);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        entityPoint.x, entityPoint.y, entityPoint.z, 4, 0.12D, 0.12D, 0.12D, 0.02D);
                it.remove();
                continue;
            }

            if (blockPoint != null) {
                BlockPos pos = blockHit.getBlockPos();
                var state = level.getBlockState(pos);
                level.sendParticles(ParticleTypes.SMOKE,
                        blockPoint.x, blockPoint.y, blockPoint.z, 3, 0.06D, 0.06D, 0.06D, 0.01D);
                level.playSound(null, pos, state.getSoundType().getBreakSound(),
                        SoundSource.BLOCKS, 0.22F, 1.7F + level.getRandom().nextFloat() * 0.4F);

                if (state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS)) {
                    level.destroyBlock(pos, false, owner);
                }
                it.remove();
                continue;
            }

            shot.pos = end;
        }
    }

    private static void tracer(ServerLevel level, Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        int steps = Math.max(1, (int)Math.ceil(distance / 0.55D));
        for (int i = 1; i <= steps; i++) {
            double t = (double)i / (double)steps;
            Vec3 p = start.lerp(end, t);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private ProjectileSystem() {}
}
