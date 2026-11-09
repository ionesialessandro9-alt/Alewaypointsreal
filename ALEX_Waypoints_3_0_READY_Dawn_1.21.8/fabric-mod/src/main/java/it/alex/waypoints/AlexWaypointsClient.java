package it.alex.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AlexWaypointsClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir();
    private static final Path SAVE_FILE = CONFIG.resolve("alex-waypoints.json");
    private static final Path LIVE_FILE = CONFIG.resolve("alex-waypoints-live.json");
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();

    private static final class Waypoint {
        String name;
        double x;
        double y;
        double z;

        Waypoint(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class LiveData {
        String name;
        double x, y, z, distance;
        double playerX, playerY, playerZ;
        float yaw, pitch;
        long updatedAt;
        List<Waypoint> waypoints;

        LiveData(Waypoint waypoint, double distance, MinecraftClient client) {
            this.name = waypoint.name;
            this.x = waypoint.x;
            this.y = waypoint.y;
            this.z = waypoint.z;
            this.distance = distance;
            this.playerX = client.player.getX();
            this.playerY = client.player.getY();
            this.playerZ = client.player.getZ();
            this.yaw = client.player.getYaw();
            this.pitch = client.player.getPitch();
            this.updatedAt = System.currentTimeMillis();
            this.waypoints = new ArrayList<>(WAYPOINTS);
        }
    }

    @Override
    public void onInitializeClient() {
        loadWaypoints();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("alexwp")
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .executes(context -> {
                                MinecraftClient client = MinecraftClient.getInstance();
                                if (client.player == null) return 0;
                                String name = StringArgumentType.getString(context, "name");
                                addWaypoint(name, client.player.getX(), client.player.getY(), client.player.getZ());
                                client.player.sendMessage(Text.literal("[ALEX] Waypoint salvato: " + name), false);
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("addpos")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .then(ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
                                .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
                                    .then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            String name = StringArgumentType.getString(context, "name");
                                            double x = DoubleArgumentType.getDouble(context, "x");
                                            double y = DoubleArgumentType.getDouble(context, "y");
                                            double z = DoubleArgumentType.getDouble(context, "z");
                                            addWaypoint(name, x, y, z);
                                            MinecraftClient client = MinecraftClient.getInstance();
                                            if (client.player != null) {
                                                client.player.sendMessage(Text.literal("[ALEX] Waypoint salvato: " + name), false);
                                            }
                                            return 1;
                                        })))))
                    .then(ClientCommandManager.literal("list")
                        .executes(context -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            if (client.player != null) {
                                if (WAYPOINTS.isEmpty()) {
                                    client.player.sendMessage(Text.literal("[ALEX] Nessun waypoint salvato."), false);
                                } else {
                                    for (Waypoint waypoint : WAYPOINTS) {
                                        client.player.sendMessage(Text.literal(
                                            "[ALEX] " + waypoint.name + " - "
                                            + (int) waypoint.x + " " + (int) waypoint.y + " " + (int) waypoint.z
                                        ), false);
                                    }
                                }
                            }
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .executes(context -> {
                                String name = StringArgumentType.getString(context, "name");
                                boolean removed = WAYPOINTS.removeIf(w -> w.name.equalsIgnoreCase(name));
                                saveWaypoints();
                                MinecraftClient client = MinecraftClient.getInstance();
                                if (client.player != null) {
                                    client.player.sendMessage(Text.literal(
                                        removed ? "[ALEX] Rimosso: " + name : "[ALEX] Non trovato: " + name
                                    ), false);
                                }
                                return removed ? 1 : 0;
                            })))
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> writeLiveFile(client));
    }

    private static void addWaypoint(String name, double x, double y, double z) {
        WAYPOINTS.removeIf(w -> w.name.equalsIgnoreCase(name));
        WAYPOINTS.add(new Waypoint(name, x, y, z));
        saveWaypoints();
    }

    private static void loadWaypoints() {
        try {
            Files.createDirectories(CONFIG);
            if (Files.exists(SAVE_FILE)) {
                Waypoint[] saved = GSON.fromJson(Files.readString(SAVE_FILE), Waypoint[].class);
                if (saved != null) WAYPOINTS.addAll(Arrays.asList(saved));
            }
        } catch (Exception exception) {
            System.err.println("[ALEX] Errore caricamento waypoint: " + exception.getMessage());
        }
    }

    private static void saveWaypoints() {
        try {
            Files.createDirectories(CONFIG);
            Files.writeString(SAVE_FILE, GSON.toJson(WAYPOINTS));
        } catch (Exception exception) {
            System.err.println("[ALEX] Errore salvataggio waypoint: " + exception.getMessage());
        }
    }

    private static void writeLiveFile(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        try {
            Waypoint nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Waypoint waypoint : WAYPOINTS) {
                double dx = waypoint.x - client.player.getX();
                double dy = waypoint.y - client.player.getY();
                double dz = waypoint.z - client.player.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = waypoint;
                }
            }

            if (nearest == null) {
                nearest = new Waypoint("Nessun waypoint", client.player.getX(), client.player.getY(), client.player.getZ());
                nearestDistance = 0;
            }

            Files.createDirectories(CONFIG);
            Files.writeString(LIVE_FILE, GSON.toJson(new LiveData(nearest, nearestDistance, client)));
        } catch (Exception exception) {
            System.err.println("[ALEX] Errore aggiornamento dati live: " + exception.getMessage());
        }
    }
}
