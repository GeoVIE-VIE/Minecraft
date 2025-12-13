package com.aicraft.minimap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.map.*;

import java.util.List;

/**
 * Renders a dynamic minimap showing terrain, player position, and waypoints
 */
public class MinimapRenderer extends MapRenderer {

    private final MinimapManager manager;
    private final boolean circular;
    private final int radius;

    // Color palette for terrain
    private static final byte COLOR_GRASS = MapPalette.matchColor(34, 139, 34);      // Forest green
    private static final byte COLOR_GRASS_LIGHT = MapPalette.matchColor(124, 252, 0); // Lawn green
    private static final byte COLOR_WATER = MapPalette.matchColor(64, 64, 255);       // Blue
    private static final byte COLOR_WATER_DEEP = MapPalette.matchColor(0, 0, 139);    // Dark blue
    private static final byte COLOR_SAND = MapPalette.matchColor(238, 214, 175);      // Sand
    private static final byte COLOR_SNOW = MapPalette.matchColor(255, 250, 250);      // Snow white
    private static final byte COLOR_STONE = MapPalette.matchColor(128, 128, 128);     // Gray
    private static final byte COLOR_DIRT = MapPalette.matchColor(139, 90, 43);        // Brown
    private static final byte COLOR_TREE = MapPalette.matchColor(0, 100, 0);          // Dark green
    private static final byte COLOR_WOOD = MapPalette.matchColor(139, 90, 43);        // Wood brown
    private static final byte COLOR_ICE = MapPalette.matchColor(176, 224, 230);       // Light blue
    private static final byte COLOR_LAVA = MapPalette.matchColor(255, 69, 0);         // Orange red
    private static final byte COLOR_NETHERRACK = MapPalette.matchColor(139, 0, 0);    // Dark red
    private static final byte COLOR_END = MapPalette.matchColor(224, 224, 160);       // Pale yellow
    private static final byte COLOR_PATH = MapPalette.matchColor(210, 180, 140);      // Tan
    private static final byte COLOR_BUILDING = MapPalette.matchColor(160, 82, 45);    // Sienna

    // UI colors
    private static final byte COLOR_PLAYER = MapPalette.matchColor(255, 0, 0);        // Red
    private static final byte COLOR_WAYPOINT = MapPalette.matchColor(255, 255, 0);    // Yellow
    private static final byte COLOR_NPC = MapPalette.matchColor(0, 255, 0);           // Green
    private static final byte COLOR_QUEST = MapPalette.matchColor(255, 165, 0);       // Orange
    private static final byte COLOR_BORDER = MapPalette.matchColor(40, 40, 40);       // Dark gray
    private static final byte COLOR_COMPASS = MapPalette.matchColor(255, 255, 255);   // White
    private static final byte COLOR_OTHER_PLAYER = MapPalette.matchColor(0, 191, 255); // Deep sky blue

    public MinimapRenderer(MinimapManager manager, boolean circular, int radius) {
        super(true); // Contextual - renders per-player
        this.manager = manager;
        this.circular = circular;
        this.radius = radius;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (player == null || !player.isOnline()) return;

        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        int centerX = 64; // Map is 128x128
        int centerZ = 64;

        // Clear canvas
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                canvas.setPixel(x, z, (byte) 0);
            }
        }

        // Render terrain
        renderTerrain(canvas, player, world, centerX, centerZ);

        // Draw border (circular or square)
        if (circular) {
            drawCircularBorder(canvas, centerX, centerZ);
        } else {
            drawSquareBorder(canvas);
        }

        // Draw compass directions
        drawCompass(canvas, centerX, centerZ, player.getLocation().getYaw());

        // Draw waypoints
        drawWaypoints(canvas, player, centerX, centerZ);

        // Draw NPCs
        drawNPCs(canvas, player, centerX, centerZ);

        // Draw other players
        drawOtherPlayers(canvas, player, centerX, centerZ);

        // Draw player marker (always in center)
        drawPlayerMarker(canvas, centerX, centerZ, player.getLocation().getYaw());
    }

    private void renderTerrain(MapCanvas canvas, Player player, World world, int centerX, int centerZ) {
        Location playerLoc = player.getLocation();
        int playerBlockX = playerLoc.getBlockX();
        int playerBlockZ = playerLoc.getBlockZ();

        float yaw = playerLoc.getYaw();
        double yawRad = Math.toRadians(yaw);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        for (int px = 0; px < 128; px++) {
            for (int pz = 0; pz < 128; pz++) {
                // Check if pixel is within bounds (circular mask)
                if (circular) {
                    int dx = px - centerX;
                    int dz = pz - centerZ;
                    if (dx * dx + dz * dz > 60 * 60) {
                        continue; // Outside circle
                    }
                }

                // Calculate world position (with rotation for north-up)
                int offsetX = px - centerX;
                int offsetZ = pz - centerZ;

                // Rotate based on player yaw (comment out for north-up fixed)
                // int worldX = playerBlockX + (int)(offsetX * cos - offsetZ * sin);
                // int worldZ = playerBlockZ + (int)(offsetX * sin + offsetZ * cos);

                // North-up (fixed orientation)
                int worldX = playerBlockX + offsetX;
                int worldZ = playerBlockZ + offsetZ;

                // Get terrain color
                byte color = getTerrainColor(world, worldX, worldZ);
                canvas.setPixel(px, pz, color);
            }
        }
    }

    private byte getTerrainColor(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();

        // First check the block itself
        // Water
        if (type == Material.WATER) {
            Block below = world.getBlockAt(x, y - 3, z);
            if (below.getType() == Material.WATER) {
                return COLOR_WATER_DEEP;
            }
            return COLOR_WATER;
        }

        // Lava
        if (type == Material.LAVA) {
            return COLOR_LAVA;
        }

        // Ice/Snow
        if (type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE) {
            return COLOR_ICE;
        }
        if (type == Material.SNOW || type == Material.SNOW_BLOCK || type == Material.POWDER_SNOW) {
            return COLOR_SNOW;
        }

        // Trees/Leaves
        if (type.name().contains("LEAVES") || type.name().contains("LOG")) {
            return COLOR_TREE;
        }

        // Sand/Desert
        if (type == Material.SAND || type == Material.RED_SAND || type == Material.SANDSTONE) {
            return COLOR_SAND;
        }

        // Stone/Mountains
        if (type == Material.STONE || type == Material.COBBLESTONE || type == Material.ANDESITE ||
            type == Material.DIORITE || type == Material.GRANITE || type == Material.GRAVEL ||
            type.name().contains("DEEPSLATE")) {
            return COLOR_STONE;
        }

        // Dirt
        if (type == Material.DIRT || type == Material.COARSE_DIRT || type == Material.ROOTED_DIRT ||
            type == Material.PODZOL || type == Material.FARMLAND) {
            return COLOR_DIRT;
        }

        // Paths
        if (type == Material.DIRT_PATH) {
            return COLOR_PATH;
        }

        // Building materials (player structures)
        if (type.name().contains("PLANKS") || type.name().contains("BRICK") ||
            type.name().contains("STAIRS") || type.name().contains("SLAB") ||
            type == Material.COBBLESTONE || type == Material.STONE_BRICKS) {
            return COLOR_BUILDING;
        }

        // Nether
        if (type == Material.NETHERRACK || type == Material.NETHER_BRICKS) {
            return COLOR_NETHERRACK;
        }

        // End
        if (type == Material.END_STONE || type == Material.END_STONE_BRICKS) {
            return COLOR_END;
        }

        // Default: grass (check biome for variation)
        Biome biome = world.getBiome(x, y, z);
        if (biome.name().contains("FOREST") || biome.name().contains("JUNGLE") ||
            biome.name().contains("SWAMP")) {
            return COLOR_TREE;
        }
        if (biome.name().contains("PLAINS") || biome.name().contains("MEADOW")) {
            return COLOR_GRASS_LIGHT;
        }

        return COLOR_GRASS;
    }

    private void drawCircularBorder(MapCanvas canvas, int centerX, int centerZ) {
        int borderRadius = 61;
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int x = centerX + (int)(borderRadius * Math.cos(rad));
            int z = centerZ + (int)(borderRadius * Math.sin(rad));
            if (x >= 0 && x < 128 && z >= 0 && z < 128) {
                canvas.setPixel(x, z, COLOR_BORDER);
            }
            // Thicker border
            x = centerX + (int)((borderRadius + 1) * Math.cos(rad));
            z = centerZ + (int)((borderRadius + 1) * Math.sin(rad));
            if (x >= 0 && x < 128 && z >= 0 && z < 128) {
                canvas.setPixel(x, z, COLOR_BORDER);
            }
        }
    }

    private void drawSquareBorder(MapCanvas canvas) {
        // Draw border
        for (int i = 2; i < 126; i++) {
            canvas.setPixel(i, 2, COLOR_BORDER);
            canvas.setPixel(i, 3, COLOR_BORDER);
            canvas.setPixel(i, 124, COLOR_BORDER);
            canvas.setPixel(i, 125, COLOR_BORDER);
            canvas.setPixel(2, i, COLOR_BORDER);
            canvas.setPixel(3, i, COLOR_BORDER);
            canvas.setPixel(124, i, COLOR_BORDER);
            canvas.setPixel(125, i, COLOR_BORDER);
        }
    }

    private void drawCompass(MapCanvas canvas, int centerX, int centerZ, float playerYaw) {
        // Draw N, S, E, W labels
        // N at top
        drawLetter(canvas, centerX - 3, 6, 'N', COLOR_COMPASS);
        // S at bottom
        drawLetter(canvas, centerX - 2, 118, 'S', COLOR_COMPASS);
        // E at right
        drawLetter(canvas, 118, centerZ - 3, 'E', COLOR_COMPASS);
        // W at left
        drawLetter(canvas, 6, centerZ - 3, 'W', COLOR_COMPASS);
    }

    private void drawLetter(MapCanvas canvas, int x, int y, char letter, byte color) {
        // Simple 5x7 font for compass letters
        boolean[][] pattern = getLetterPattern(letter);
        if (pattern == null) return;

        for (int py = 0; py < pattern.length; py++) {
            for (int px = 0; px < pattern[py].length; px++) {
                if (pattern[py][px]) {
                    int drawX = x + px;
                    int drawY = y + py;
                    if (drawX >= 0 && drawX < 128 && drawY >= 0 && drawY < 128) {
                        canvas.setPixel(drawX, drawY, color);
                    }
                }
            }
        }
    }

    private boolean[][] getLetterPattern(char c) {
        switch (c) {
            case 'N':
                return new boolean[][] {
                    {true, false, false, false, true},
                    {true, true, false, false, true},
                    {true, false, true, false, true},
                    {true, false, false, true, true},
                    {true, false, false, false, true}
                };
            case 'S':
                return new boolean[][] {
                    {false, true, true, true, false},
                    {true, false, false, false, false},
                    {false, true, true, true, false},
                    {false, false, false, false, true},
                    {false, true, true, true, false}
                };
            case 'E':
                return new boolean[][] {
                    {true, true, true, true, true},
                    {true, false, false, false, false},
                    {true, true, true, true, false},
                    {true, false, false, false, false},
                    {true, true, true, true, true}
                };
            case 'W':
                return new boolean[][] {
                    {true, false, false, false, true},
                    {true, false, false, false, true},
                    {true, false, true, false, true},
                    {true, true, false, true, true},
                    {true, false, false, false, true}
                };
            default:
                return null;
        }
    }

    private void drawPlayerMarker(MapCanvas canvas, int centerX, int centerZ, float yaw) {
        // Draw a red triangle pointing in player's facing direction
        double yawRad = Math.toRadians(yaw + 180); // Adjust for Minecraft yaw

        // Triangle points
        int size = 5;

        // Tip of triangle (forward)
        int tipX = centerX + (int)(size * Math.sin(yawRad));
        int tipZ = centerZ - (int)(size * Math.cos(yawRad));

        // Back left
        int leftX = centerX + (int)(size * Math.sin(yawRad + 2.5));
        int leftZ = centerZ - (int)(size * Math.cos(yawRad + 2.5));

        // Back right
        int rightX = centerX + (int)(size * Math.sin(yawRad - 2.5));
        int rightZ = centerZ - (int)(size * Math.cos(yawRad - 2.5));

        // Draw filled triangle
        drawTriangle(canvas, tipX, tipZ, leftX, leftZ, rightX, rightZ, COLOR_PLAYER);

        // Draw center dot
        canvas.setPixel(centerX, centerZ, COLOR_PLAYER);
        canvas.setPixel(centerX + 1, centerZ, COLOR_PLAYER);
        canvas.setPixel(centerX - 1, centerZ, COLOR_PLAYER);
        canvas.setPixel(centerX, centerZ + 1, COLOR_PLAYER);
        canvas.setPixel(centerX, centerZ - 1, COLOR_PLAYER);
    }

    private void drawTriangle(MapCanvas canvas, int x1, int y1, int x2, int y2, int x3, int y3, byte color) {
        // Simple triangle rasterization
        drawLine(canvas, x1, y1, x2, y2, color);
        drawLine(canvas, x2, y2, x3, y3, color);
        drawLine(canvas, x3, y3, x1, y1, color);
    }

    private void drawLine(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x1 >= 0 && x1 < 128 && y1 >= 0 && y1 < 128) {
                canvas.setPixel(x1, y1, color);
            }
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private void drawWaypoints(MapCanvas canvas, Player player, int centerX, int centerZ) {
        List<Waypoint> waypoints = manager.getWaypoints(player);
        if (waypoints == null) return;

        Location playerLoc = player.getLocation();
        int playerX = playerLoc.getBlockX();
        int playerZ = playerLoc.getBlockZ();

        for (Waypoint wp : waypoints) {
            if (!wp.getWorld().equals(playerLoc.getWorld().getName())) continue;

            int dx = wp.getX() - playerX;
            int dz = wp.getZ() - playerZ;

            // Check if within minimap range
            if (Math.abs(dx) > 63 || Math.abs(dz) > 63) {
                // Draw on edge pointing to waypoint
                double angle = Math.atan2(dz, dx);
                int edgeX = centerX + (int)(58 * Math.cos(angle));
                int edgeZ = centerZ + (int)(58 * Math.sin(angle));
                drawWaypointMarker(canvas, edgeX, edgeZ, wp.getColor(), true);
            } else {
                // Draw at actual position
                int mapX = centerX + dx;
                int mapZ = centerZ + dz;
                drawWaypointMarker(canvas, mapX, mapZ, wp.getColor(), false);
            }
        }
    }

    private void drawWaypointMarker(MapCanvas canvas, int x, int z, byte color, boolean isEdge) {
        // Draw a diamond/marker shape
        if (x < 4 || x > 123 || z < 4 || z > 123) return;

        if (isEdge) {
            // Arrow pointing outward
            canvas.setPixel(x, z, color);
            canvas.setPixel(x + 1, z, color);
            canvas.setPixel(x - 1, z, color);
            canvas.setPixel(x, z + 1, color);
            canvas.setPixel(x, z - 1, color);
        } else {
            // Diamond marker
            canvas.setPixel(x, z - 2, color);
            canvas.setPixel(x - 1, z - 1, color);
            canvas.setPixel(x + 1, z - 1, color);
            canvas.setPixel(x - 2, z, color);
            canvas.setPixel(x + 2, z, color);
            canvas.setPixel(x - 1, z + 1, color);
            canvas.setPixel(x + 1, z + 1, color);
            canvas.setPixel(x, z + 2, color);

            // Fill center
            canvas.setPixel(x, z, color);
            canvas.setPixel(x, z - 1, color);
            canvas.setPixel(x, z + 1, color);
            canvas.setPixel(x - 1, z, color);
            canvas.setPixel(x + 1, z, color);
        }
    }

    private void drawNPCs(MapCanvas canvas, Player player, int centerX, int centerZ) {
        // Get NPCs from manager and draw green dots
        List<Location> npcLocations = manager.getNearbyNPCLocations(player);
        if (npcLocations == null || npcLocations.isEmpty()) return;

        Location playerLoc = player.getLocation();
        int playerX = playerLoc.getBlockX();
        int playerZ = playerLoc.getBlockZ();

        for (Location npcLoc : npcLocations) {
            if (npcLoc == null || npcLoc.getWorld() == null) continue;
            if (!npcLoc.getWorld().equals(playerLoc.getWorld())) continue;

            int dx = npcLoc.getBlockX() - playerX;
            int dz = npcLoc.getBlockZ() - playerZ;

            if (Math.abs(dx) > 60 || Math.abs(dz) > 60) continue;

            int mapX = centerX + dx;
            int mapZ = centerZ + dz;

            if (mapX >= 4 && mapX < 124 && mapZ >= 4 && mapZ < 124) {
                // Draw small green dot for NPC
                canvas.setPixel(mapX, mapZ, COLOR_NPC);
                canvas.setPixel(mapX + 1, mapZ, COLOR_NPC);
                canvas.setPixel(mapX - 1, mapZ, COLOR_NPC);
                canvas.setPixel(mapX, mapZ + 1, COLOR_NPC);
                canvas.setPixel(mapX, mapZ - 1, COLOR_NPC);
            }
        }
    }

    private void drawOtherPlayers(MapCanvas canvas, Player player, int centerX, int centerZ) {
        Location playerLoc = player.getLocation();
        int playerX = playerLoc.getBlockX();
        int playerZ = playerLoc.getBlockZ();
        World playerWorld = playerLoc.getWorld();

        for (Player other : Bukkit.getOnlinePlayers()) {
            // Skip self
            if (other.equals(player)) continue;

            Location otherLoc = other.getLocation();

            // Skip if in different world
            if (!otherLoc.getWorld().equals(playerWorld)) continue;

            int dx = otherLoc.getBlockX() - playerX;
            int dz = otherLoc.getBlockZ() - playerZ;

            // Check if within minimap range
            if (Math.abs(dx) > 60 || Math.abs(dz) > 60) continue;

            int mapX = centerX + dx;
            int mapZ = centerZ + dz;

            if (mapX >= 4 && mapX < 124 && mapZ >= 4 && mapZ < 124) {
                // Draw blue dot for other players (slightly larger than NPCs)
                canvas.setPixel(mapX, mapZ, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX + 1, mapZ, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX - 1, mapZ, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX, mapZ + 1, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX, mapZ - 1, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX + 1, mapZ + 1, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX - 1, mapZ - 1, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX + 1, mapZ - 1, COLOR_OTHER_PLAYER);
                canvas.setPixel(mapX - 1, mapZ + 1, COLOR_OTHER_PLAYER);
            }
        }
    }
}
