package de.janzimmermann.lastdays.item;

import de.janzimmermann.lastdays.system.NoiseSystem;
import de.janzimmermann.lastdays.system.ProjectileSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

public final class GunItem extends Item {
    private final float damage;
    private final double range;
    private final int cooldownTicks;
    private final int pellets;
    private final RegistryObject<Item> ammo;

    public GunItem(Properties properties, float damage, double range, int cooldownTicks,
                   int pellets, RegistryObject<Item> ammo) {
        super(properties);
        this.damage = damage;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.pellets = pellets;
        this.ammo = ammo;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }

        if (!player.getAbilities().instabuild && !consumeAmmo(player, ammo.get())) {
            player.sendSystemMessage(Component.literal("§cKeine passende Munition."));
            return InteractionResult.FAIL;
        }

        boolean shotgun = pellets > 1;
        boolean sniper = range >= 90.0D;
        boolean smg = cooldownTicks <= 3;

        double projectileSpeed = sniper ? 7.0D : shotgun ? 4.2D : smg ? 5.0D : 5.8D;
        double spread = shotgun ? 0.155D : sniper ? 0.004D : smg ? 0.048D : 0.018D;

        ProjectileSystem.fire(server, sp, damage, projectileSpeed, range, pellets, spread);
        player.getCooldowns().addCooldown(gun, cooldownTicks);
        NoiseSystem.addNoise(sp, shotgun ? 11 : sniper ? 9 : smg ? 7 : 6);

        var shotSound = shotgun ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.CROSSBOW_SHOOT.value();
        server.playSound(null, player.blockPosition(), shotSound,
                SoundSource.PLAYERS, shotgun ? 0.55F : 0.85F, shotgun ? 1.35F : 0.78F);

        Vec3 look = player.getLookAngle();
        if (shotgun) {
            player.push(-look.x * 0.10D, 0.025D, -look.z * 0.10D);
        } else if (sniper) {
            player.push(-look.x * 0.055D, 0.012D, -look.z * 0.055D);
        } else {
            player.push(-look.x * 0.018D, 0.004D, -look.z * 0.018D);
        }

        Vec3 muzzle = player.getEyePosition().add(look.scale(0.9D));
        server.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z,
                shotgun ? 7 : 3, 0.035D, 0.035D, 0.035D, 0.012D);
        return InteractionResult.SUCCESS;
    }

    private static boolean consumeAmmo(Player player, Item ammo) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ammo)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
