package de.janzimmermann.lastdays.system;

import de.janzimmermann.lastdays.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class CommonEvents {
    private static long lastSecond = -1L;
    private static int lastDropDay = -1;

    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(CommonEvents::onServerTick);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(CommonEvents::onLogin);
        PlayerEvent.Clone.BUS.addListener(CommonEvents::onClone);
        LivingDeathEvent.BUS.addListener(CommonEvents::onDeath);
        RegisterCommandsEvent.BUS.addListener(LastDaysCommands::register);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerState.init(player);

        var root = player.getPersistentData().getCompoundOrEmpty("LastDays");
        if (!root.getBooleanOr("StarterKit", false)) {
            root.putBoolean("StarterKit", true);
            player.getInventory().add(new ItemStack(ModItems.PISTOL.get()));
            player.getInventory().add(new ItemStack(ModItems.M4A1.get()));
            player.getInventory().add(new ItemStack(ModItems.RIFLE_AMMO.get(), 48));
            player.getInventory().add(new ItemStack(ModItems.PISTOL_AMMO.get(), 32));
            player.getInventory().add(new ItemStack(ModItems.MEDKIT.get(), 2));
            player.getInventory().add(new ItemStack(ModItems.TURRET_DEPLOYER.get(), 1));
            player.sendSystemMessage(Component.literal("§4§lLAST DAYS §7— Evakuierung fehlgeschlagen. Überlebe."));
            player.sendSystemMessage(Component.literal("§eHOTFIX 1.0.1: Mega-City wird nicht mehr während des Weltstarts erzeugt."));
            player.sendSystemMessage(Component.literal("§7Zum Testen der Stadt später: §f/lastdays city"));
        }

        // IMPORTANT HOTFIX:
        // Do NOT queue the 121-lot city here. In the old build this could synchronously
        // touch/generate far-away chunks while the integrated server was still finishing
        // the first player join, leaving the client stuck on "Preparing world...".
        //
        // Safezone spawning is also deferred. World creation must complete before any
        // optional large-scale content generation begins.
    }

    private static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer fresh) || !(event.getOriginal() instanceof ServerPlayer old)) return;
        if (old.getPersistentData().contains("LastDays")) {
            fresh.getPersistentData().put("LastDays", old.getPersistentData().getCompoundOrEmpty("LastDays").copy());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;

        int reward = monster.entityTags().contains("lastdays_boss") ? 12 :
                     monster.entityTags().contains("lastdays_brute") ? 4 :
                     monster.entityTags().contains("lastdays_runner") ? 2 : 1;
        reward += PlayerState.skill(killer, "Scavenging") / 2;
        PlayerState.addScrap(killer, reward);

        if (monster.entityTags().contains("lastdays_infected") && killer.level().getRandom().nextInt(100) < 9) {
            killer.getInventory().add(new ItemStack(ModItems.RIFLE_AMMO.get(), 2 + killer.level().getRandom().nextInt(5)));
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        var server = event.server();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        HordeDirector.tick(server);
        DefenseSystem.tick(overworld);

        // City work only runs when it was explicitly queued after entering the world.
        if (CityGenerator.hasWork() && overworld.getGameTime() % 20 == 0) {
            CityGenerator.tick(overworld);
        }

        long second = overworld.getGameTime() / 20L;
        if (second == lastSecond) return;
        lastSecond = second;

        int day = (int)(overworld.getOverworldClockTime() / 24000L) + 1;
        long tod = overworld.getOverworldClockTime() % 24000L;

        for (ServerPlayer player : overworld.players()) {
            SurvivalSystem.tickSecond(player, day);
            EndgameSystem.tickSecond(overworld, player, day);

            if (day % 3 == 0 && tod >= 5000 && tod < 5020 && lastDropDay != day) {
                lastDropDay = day;
                AirdropSystem.drop(overworld, player);
            }

            if (player.tickCount % 80 == 0) {
                boolean close = !overworld.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(2.2D),
                        m -> m.entityTags().contains("lastdays_infected")).isEmpty();
                if (close && overworld.getRandom().nextInt(12) == 0) {
                    PlayerState.setInfection(player, PlayerState.infection(player) + 4);
                    if (overworld.getRandom().nextBoolean()) {
                        PlayerState.setBleeding(player, PlayerState.bleeding(player) + 1);
                    }
                }
            }
        }
    }

    private CommonEvents() {}
}
