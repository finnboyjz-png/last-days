package de.janzimmermann.lastdays.system;

import de.janzimmermann.lastdays.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Urban generator inspired by the Apache-2.0 Cartopia building/road generator:
 * multi-floor facades, repeating window cadence, roads, sidewalks and material palettes.
 * The old plain cube lots are deliberately gone.
 */
public final class CityGenerator {
    private enum Kind {
        APARTMENT, OFFICE, SHOP, HOSPITAL, POLICE, WAREHOUSE, GAS, MILITARY, RUIN, PARKING
    }

    private record Lot(BlockPos center, Kind kind, int seed) {}
    private enum Axis { X, Z }
    private record Road(BlockPos center, Axis axis, int halfLength, int halfWidth) {}

    private static final Deque<Lot> LOTS = new ArrayDeque<>();
    private static final Deque<Road> ROADS = new ArrayDeque<>();
    private static final int STEP = 22;
    private static final int HALF_GRID = 5; // 11 x 11 = 121 lots
    private static int builtLots = 0;
    private static int totalLots = 0;

    public static void queueMegaCity(ServerLevel level, BlockPos requestedCenter) {
        if (hasWork()) return;

        int cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                requestedCenter.getX(), requestedCenter.getZ());
        BlockPos center = new BlockPos(requestedCenter.getX(), cy, requestedCenter.getZ());

        builtLots = 0;
        LOTS.clear();
        ROADS.clear();

        int extent = HALF_GRID * STEP + STEP / 2;

        // Road boundaries between lots. This follows terrain rather than making a floating slab.
        for (int i = -HALF_GRID - 1; i <= HALF_GRID; i++) {
            int off = i * STEP + STEP / 2;
            ROADS.add(new Road(center.offset(off, 0, 0), Axis.Z, extent, i == -1 ? 3 : 2));
            ROADS.add(new Road(center.offset(0, 0, off), Axis.X, extent, i == -1 ? 3 : 2));
        }

        for (int gx = -HALF_GRID; gx <= HALF_GRID; gx++) {
            for (int gz = -HALF_GRID; gz <= HALF_GRID; gz++) {
                BlockPos lotCenter = center.offset(gx * STEP, 0, gz * STEP);
                int seed = Math.abs(gx * 73471 + gz * 91241 + center.getX() * 17 + center.getZ() * 31);

                Kind kind;
                if (gx == 0 && gz == 0) kind = Kind.HOSPITAL;
                else if (gx == 2 && gz == -2) kind = Kind.POLICE;
                else if (gx == -3 && gz == 2) kind = Kind.GAS;
                else if (gx == 4 && gz == 3) kind = Kind.MILITARY;
                else if (gx == -4 && gz == -2) kind = Kind.WAREHOUSE;
                else {
                    int roll = seed % 100;
                    kind = roll < 28 ? Kind.APARTMENT :
                           roll < 50 ? Kind.OFFICE :
                           roll < 63 ? Kind.SHOP :
                           roll < 72 ? Kind.WAREHOUSE :
                           roll < 82 ? Kind.RUIN :
                           roll < 90 ? Kind.PARKING :
                           roll < 95 ? Kind.GAS :
                           Kind.APARTMENT;
                }
                LOTS.add(new Lot(lotCenter, kind, seed));
            }
        }

        totalLots = LOTS.size();
        for (var player : level.players()) {
            player.sendSystemMessage(Component.literal(
                    "§6§lLAST DAYS CITY §7— Straßen + " + totalLots + " echte Gebäudelots werden aufgebaut."));
        }
    }

    public static boolean hasWork() {
        return !ROADS.isEmpty() || !LOTS.isEmpty();
    }

    public static void tick(ServerLevel level) {
        if (!ROADS.isEmpty()) {
            buildRoad(level, ROADS.removeFirst());
            return;
        }
        if (LOTS.isEmpty()) return;

        Lot lot = LOTS.removeFirst();
        buildLot(level, lot);
        builtLots++;

        if (builtLots % 15 == 0 || LOTS.isEmpty()) {
            int pct = totalLots == 0 ? 100 : (int)Math.round(100.0D * builtLots / totalLots);
            for (var player : level.players()) {
                player.sendSystemMessage(Component.literal("§8[City] §7" + pct + "% §8(" + builtLots + "/" + totalLots + ")"));
            }
        }
    }

    private static void buildRoad(ServerLevel level, Road road) {
        for (int d = -road.halfLength(); d <= road.halfLength(); d++) {
            for (int w = -road.halfWidth() - 1; w <= road.halfWidth() + 1; w++) {
                int x = road.center().getX() + (road.axis() == Axis.X ? d : w);
                int z = road.center().getZ() + (road.axis() == Axis.Z ? d : w);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                clearAbove(level, x, y, z, 4);

                boolean sidewalk = Math.abs(w) == road.halfWidth() + 1;
                level.setBlockAndUpdate(new BlockPos(x, y - 1, z),
                        sidewalk ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());

                if (!sidewalk && w == 0 && Math.floorMod(d, 7) < 3) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.YELLOW_CONCRETE.defaultBlockState());
                }
            }
        }

        // Occasional street lights on the sidewalk.
        for (int d = -road.halfLength(); d <= road.halfLength(); d += 14) {
            for (int side : new int[]{-1, 1}) {
                int w = side * (road.halfWidth() + 1);
                int x = road.center().getX() + (road.axis() == Axis.X ? d : w);
                int z = road.center().getZ() + (road.axis() == Axis.Z ? d : w);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.IRON_BARS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, y + 1, z), Blocks.IRON_BARS.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, y + 2, z), Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    private static void buildLot(ServerLevel level, Lot lot) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                lot.center().getX(), lot.center().getZ());
        BlockPos c = new BlockPos(lot.center().getX(), y, lot.center().getZ());

        switch (lot.kind()) {
            case HOSPITAL -> buildBuilding(level, c, 7, 5, 4,
                    Blocks.WHITE_CONCRETE, Blocks.SMOOTH_STONE, Blocks.LIGHT_BLUE_STAINED_GLASS,
                    true, Loot.MEDICAL, lot.seed());
            case POLICE -> buildBuilding(level, c, 6, 5, 3,
                    Blocks.GRAY_CONCRETE, Blocks.DEEPSLATE_TILES, Blocks.BLUE_STAINED_GLASS,
                    false, Loot.POLICE, lot.seed());
            case MILITARY -> {
                buildBuilding(level, c, 7, 6, 2,
                        Blocks.GREEN_TERRACOTTA, Blocks.POLISHED_DEEPSLATE, Blocks.TINTED_GLASS,
                        false, Loot.MILITARY, lot.seed());
                fenceCompound(level, c, 9);
            }
            case WAREHOUSE -> buildWarehouse(level, c, lot.seed());
            case GAS -> buildGasStation(level, c, lot.seed());
            case PARKING -> buildParking(level, c, lot.seed());
            case SHOP -> buildBuilding(level, c, 6, 5, 1 + lot.seed() % 2,
                    Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_STONE, Blocks.GLASS,
                    false, Loot.SHOP, lot.seed());
            case OFFICE -> buildBuilding(level, c, 6, 6, 3 + lot.seed() % 5,
                    Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_STONE, Blocks.LIGHT_BLUE_STAINED_GLASS,
                    false, Loot.OFFICE, lot.seed());
            case RUIN -> {
                buildBuilding(level, c, 6, 6, 2 + lot.seed() % 4,
                        Blocks.STONE_BRICKS, Blocks.SMOOTH_STONE, Blocks.GLASS,
                        false, Loot.GENERAL, lot.seed());
                ruin(level, c, 6, 6, 8 + (lot.seed() % 10));
            }
            case APARTMENT -> buildBuilding(level, c, 6, 6, 2 + lot.seed() % 4,
                    Blocks.BRICKS, Blocks.SPRUCE_PLANKS, Blocks.GLASS_PANE,
                    false, Loot.GENERAL, lot.seed());
        }
    }

    private static void buildBuilding(ServerLevel level, BlockPos c, int rx, int rz, int floors,
                                      Block facade, Block floorBlock, Block glass,
                                      boolean redCross, Loot loot, int seed) {
        int floorHeight = 4;
        int height = floors * floorHeight + 1;
        clearBox(level, c.offset(-rx, 0, -rz), c.offset(rx, height + 3, rz));

        // Foundation.
        fill(level, c.offset(-rx, -1, -rz), c.offset(rx, -1, rz), Blocks.STONE_BRICKS.defaultBlockState());

        for (int dy = 0; dy <= height; dy++) {
            int floorIndex = dy / floorHeight;
            boolean slabLevel = dy % floorHeight == 0;
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz;

                    if (dy == height) {
                        level.setBlockAndUpdate(c.offset(dx, dy, dz), floorBlock.defaultBlockState());
                        continue;
                    }

                    if (slabLevel && dy > 0 && !edge) {
                        // Floors with a central climb opening.
                        if (!(Math.abs(dx) <= 1 && Math.abs(dz) <= 1)) {
                            level.setBlockAndUpdate(c.offset(dx, dy, dz), floorBlock.defaultBlockState());
                        }
                        continue;
                    }

                    if (!edge) continue;

                    // Main entrance faces south.
                    if (dz == -rz && Math.abs(dx) <= 1 && dy <= 2) {
                        level.setBlockAndUpdate(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                        continue;
                    }

                    boolean windowBand = dy % floorHeight == 1 || dy % floorHeight == 2;
                    boolean cadence = ((Math.abs(dx) + Math.abs(dz) + floorIndex) % 3) != 0;
                    if (windowBand && cadence && dy > 0) {
                        level.setBlockAndUpdate(c.offset(dx, dy, dz), glass.defaultBlockState());
                    } else {
                        level.setBlockAndUpdate(c.offset(dx, dy, dz), facade.defaultBlockState());
                    }
                }
            }
        }

        // Internal floor partitions and climbable central scaffold.
        for (int dy = 1; dy < height; dy++) {
            level.setBlockAndUpdate(c.offset(0, dy, 0), Blocks.SCAFFOLDING.defaultBlockState());
        }

        // Basic room separators on every floor.
        for (int f = 0; f < floors; f++) {
            int fy = f * floorHeight + 1;
            for (int dz = -rz + 2; dz <= rz - 2; dz++) {
                if (dz == 0 || dz == 1) continue;
                level.setBlockAndUpdate(c.offset(2, fy, dz), Blocks.OAK_PLANKS.defaultBlockState());
                level.setBlockAndUpdate(c.offset(2, fy + 1, dz), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }

        // Roof clutter.
        level.setBlockAndUpdate(c.offset(rx - 2, height + 1, rz - 2), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(c.offset(rx - 2, height + 2, rz - 2), Blocks.IRON_BARS.defaultBlockState());
        level.setBlockAndUpdate(c.offset(-rx + 2, height + 1, rz - 2), Blocks.CAMPFIRE.defaultBlockState());

        if (redCross) {
            for (int x = -1; x <= 1; x++) level.setBlockAndUpdate(c.offset(x, height - 2, -rz), Blocks.RED_CONCRETE.defaultBlockState());
            for (int y = height - 3; y <= height - 1; y++) level.setBlockAndUpdate(c.offset(0, y, -rz), Blocks.RED_CONCRETE.defaultBlockState());
        }

        placeLoot(level, c.offset(rx - 2, 1, rz - 2), loot);
        if (floors >= 3) placeLoot(level, c.offset(-rx + 2, floorHeight + 1, rz - 2), loot);
    }

    private static void buildWarehouse(ServerLevel level, BlockPos c, int seed) {
        int rx = 7, rz = 7, height = 7;
        clearBox(level, c.offset(-rx, 0, -rz), c.offset(rx, height + 2, rz));
        fill(level, c.offset(-rx, -1, -rz), c.offset(rx, -1, rz), Blocks.STONE.defaultBlockState());

        for (int dy = 0; dy <= height; dy++) for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) {
            boolean edge = Math.abs(dx) == rx || Math.abs(dz) == rz || dy == height;
            if (!edge) continue;
            if (dz == -rz && Math.abs(dx) <= 3 && dy <= 4) continue;
            BlockState state = dy == height ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.BRICKS.defaultBlockState();
            level.setBlockAndUpdate(c.offset(dx, dy, dz), state);
        }
        for (int x=-4;x<=4;x+=4) level.setBlockAndUpdate(c.offset(x,1,2), Blocks.BARREL.defaultBlockState());
        placeLoot(level, c.offset(5,1,5), Loot.INDUSTRIAL);
        placeLoot(level, c.offset(-5,1,5), Loot.GENERAL);
    }

    private static void buildGasStation(ServerLevel level, BlockPos c, int seed) {
        clearBox(level, c.offset(-8, 0, -8), c.offset(8, 9, 8));
        fill(level, c.offset(-8,-1,-8), c.offset(8,-1,8), Blocks.GRAY_CONCRETE.defaultBlockState());

        // Shop.
        for (int dx=-6; dx<=0; dx++) for(int dz=-5; dz<=5; dz++) for(int dy=0; dy<=4; dy++) {
            boolean edge = dx==-6 || dx==0 || dz==-5 || dz==5 || dy==4;
            if (!edge) continue;
            if (dz==-5 && dx==-3 && dy<=2) continue;
            level.setBlockAndUpdate(c.offset(dx,dy,dz), dy==4 ? Blocks.RED_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState());
        }
        fill(level, c.offset(2,4,-6), c.offset(8,4,6), Blocks.RED_CONCRETE.defaultBlockState());
        for(int x=3;x<=7;x+=4) for(int z=-3;z<=3;z+=6){
            for(int y=0;y<=3;y++) level.setBlockAndUpdate(c.offset(x,y,z), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(x,0,z), Blocks.RED_CONCRETE.defaultBlockState());
        }
        placeLoot(level, c.offset(-5,1,3), Loot.SHOP);
    }

    private static void buildParking(ServerLevel level, BlockPos c, int seed) {
        clearBox(level, c.offset(-8,0,-8), c.offset(8,4,8));
        fill(level, c.offset(-8,-1,-8), c.offset(8,-1,8), Blocks.GRAY_CONCRETE.defaultBlockState());
        for(int x=-6;x<=6;x+=4){
            for(int z=-6;z<=6;z+=6){
                level.setBlockAndUpdate(c.offset(x,0,z), Blocks.IRON_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(c.offset(x+1,0,z), Blocks.IRON_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(c.offset(x,1,z), Blocks.BLACK_STAINED_GLASS.defaultBlockState());
                level.setBlockAndUpdate(c.offset(x-1,-1,z), Blocks.BLACK_CONCRETE.defaultBlockState());
                level.setBlockAndUpdate(c.offset(x+2,-1,z), Blocks.BLACK_CONCRETE.defaultBlockState());
            }
        }
    }

    private static void fenceCompound(ServerLevel level, BlockPos c, int r) {
        for(int i=-r;i<=r;i++){
            level.setBlockAndUpdate(c.offset(i,0,-r), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(i,1,-r), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(i,0,r), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(i,1,r), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(-r,0,i), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(-r,1,i), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(r,0,i), Blocks.IRON_BARS.defaultBlockState());
            level.setBlockAndUpdate(c.offset(r,1,i), Blocks.IRON_BARS.defaultBlockState());
        }
        for(int x=-1;x<=1;x++) {
            level.setBlockAndUpdate(c.offset(x,0,-r), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(c.offset(x,1,-r), Blocks.AIR.defaultBlockState());
        }
    }

    private static void ruin(ServerLevel level, BlockPos c, int rx, int rz, int holes) {
        for (int i=0; i<holes; i++) {
            int side = Math.floorMod(i * 17 + holes, 4);
            int y = 1 + Math.floorMod(i * 7, 8);
            int offset = -rx + 1 + Math.floorMod(i * 11, rx * 2 - 1);
            BlockPos p = switch(side) {
                case 0 -> c.offset(offset, y, -rz);
                case 1 -> c.offset(offset, y, rz);
                case 2 -> c.offset(-rx, y, offset);
                default -> c.offset(rx, y, offset);
            };
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(p.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(p.below(), Blocks.COBBLESTONE.defaultBlockState());
        }
    }

    private enum Loot { GENERAL, MEDICAL, POLICE, MILITARY, INDUSTRIAL, SHOP, OFFICE }

    private static void placeLoot(ServerLevel level, BlockPos pos, Loot type) {
        level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof Container c)) return;

        c.setItem(0, new ItemStack(ModItems.SCRAP.get(), 2 + level.getRandom().nextInt(7)));
        switch(type) {
            case MEDICAL -> {
                c.setItem(1, new ItemStack(ModItems.MEDKIT.get(), 1 + level.getRandom().nextInt(2)));
                c.setItem(2, new ItemStack(ModItems.PISTOL_AMMO.get(), 4 + level.getRandom().nextInt(8)));
            }
            case POLICE -> {
                c.setItem(1, new ItemStack(ModItems.PISTOL_AMMO.get(), 10 + level.getRandom().nextInt(15)));
                if (level.getRandom().nextInt(5) == 0) c.setItem(2, new ItemStack(ModItems.PISTOL.get()));
            }
            case MILITARY -> {
                c.setItem(1, new ItemStack(ModItems.RIFLE_AMMO.get(), 14 + level.getRandom().nextInt(24)));
                c.setItem(2, new ItemStack(ModItems.SHELLS.get(), 4 + level.getRandom().nextInt(8)));
                if (level.getRandom().nextInt(7) == 0) c.setItem(3, new ItemStack(ModItems.M4A1.get()));
            }
            case INDUSTRIAL -> c.setItem(1, new ItemStack(ModItems.SCRAP.get(), 8 + level.getRandom().nextInt(14)));
            case SHOP -> {
                c.setItem(1, new ItemStack(ModItems.PISTOL_AMMO.get(), 3 + level.getRandom().nextInt(7)));
                if (level.getRandom().nextInt(4) == 0) c.setItem(2, new ItemStack(ModItems.MEDKIT.get()));
            }
            case OFFICE, GENERAL -> {
                if (level.getRandom().nextInt(3) == 0) c.setItem(1, new ItemStack(ModItems.BLUEPRINT.get()));
            }
        }
    }

    private static void clearAbove(ServerLevel level, int x, int y, int z, int h) {
        for (int dy=0; dy<h; dy++) level.setBlockAndUpdate(new BlockPos(x,y+dy,z), Blocks.AIR.defaultBlockState());
    }

    private static void clearBox(ServerLevel level, BlockPos a, BlockPos b) {
        for (int x=Math.min(a.getX(),b.getX()); x<=Math.max(a.getX(),b.getX()); x++)
            for (int y=Math.min(a.getY(),b.getY()); y<=Math.max(a.getY(),b.getY()); y++)
                for (int z=Math.min(a.getZ(),b.getZ()); z<=Math.max(a.getZ(),b.getZ()); z++)
                    level.setBlockAndUpdate(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState());
    }

    private static void fill(ServerLevel level, BlockPos a, BlockPos b, BlockState state) {
        for (int x=Math.min(a.getX(),b.getX()); x<=Math.max(a.getX(),b.getX()); x++)
            for (int y=Math.min(a.getY(),b.getY()); y<=Math.max(a.getY(),b.getY()); y++)
                for (int z=Math.min(a.getZ(),b.getZ()); z<=Math.max(a.getZ(),b.getZ()); z++)
                    level.setBlockAndUpdate(new BlockPos(x,y,z), state);
    }

    private CityGenerator() {}
}
