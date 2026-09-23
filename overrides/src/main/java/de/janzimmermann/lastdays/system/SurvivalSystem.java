package de.janzimmermann.lastdays.system;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class SurvivalSystem {
    public static void tickSecond(ServerPlayer player, int day) {
        PlayerState.init(player);

        if (player.tickCount % 1200 == 0) {
            PlayerState.setThirst(player, PlayerState.thirst(player) - 1);
        }
        if (PlayerState.noise(player) > 0 && player.tickCount % 40 == 0) {
            NoiseSystem.decay(player);
        }

        int thirst = PlayerState.thirst(player);
        if (thirst <= 20) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
        if (thirst <= 10) player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, false, false));
        if (thirst == 0 && player.level() instanceof ServerLevel sl && player.tickCount % 80 == 0) {
            player.hurtServer(sl, sl.damageSources().starve(), 1.0F);
        }

        int infection = PlayerState.infection(player);
        if (infection >= 50) player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
        if (infection >= 80) player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, false, false));

        if (PlayerState.bleeding(player) > 0 && player.level() instanceof ServerLevel sl && player.tickCount % 100 == 0) {
            player.hurtServer(sl, sl.damageSources().generic(), 1.0F);
        }

        if (PlayerState.lastSkillDay(player) < day) {
            PlayerState.addSkillPoint(player, Math.max(1, day - PlayerState.lastSkillDay(player)));
            PlayerState.setLastSkillDay(player, day);
        }

        Component hud = Component.literal(
                "§4☣ §cLAST DAYS §8• §7Tag §f" + day +
                " §8| §bH₂O §f" + thirst +
                " §8| §eLärm §f" + PlayerState.noise(player) +
                " §8| §6Schrott §f" + PlayerState.scrap(player) +
                " §8| §dSP §f" + PlayerState.skillPoints(player));
        player.connection.send(new ClientboundSystemChatPacket(hud, true));
    }

    private SurvivalSystem() {}
}
