package com.fakeplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 管理真正的 ServerPlayer 假人。
 *
 * <p>假人使用 NeoForge 的 FakePlayer 基类，但会正式加入 PlayerList，
 * 因此可以被 /tp、/list、Tab 列表和 @a 正常识别。</p>
 */
public final class FakePlayerManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, FakeRecord> FAKES = new LinkedHashMap<>();
    private static Path dataFile;

    private FakePlayerManager() {
    }

    public record SpawnResult(boolean success, String message) {
    }

    private record SavedRecord(String name, String dimension, double x, double y, double z) {
    }

    private static final class FakeRecord {
        private final String name;
        private final String dimension;
        private final ServerPlayer player;

        private FakeRecord(String name, String dimension, ServerPlayer player) {
            this.name = name;
            this.dimension = dimension;
            this.player = player;
        }
    }

    public static void init(MinecraftServer server) {
        dataFile = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("fakeplayers.json");
        FAKES.clear();
        load(server);
    }

    public static void saveAndClear(MinecraftServer server) {
        save(server);
        FAKES.clear();
    }

    public static SpawnResult spawn(
            MinecraftServer server,
            ServerLevel level,
            String name,
            Vec3 position
    ) {
        return spawnInternal(server, level, name, position, false);
    }

    public static boolean remove(MinecraftServer server, String name) {
        String key = normalize(name);
        FakeRecord record = FAKES.get(key);
        if (record == null) {
            return false;
        }

        FAKES.remove(key);
        PlayerList playerList = server.getPlayerList();
        playerList.remove(record.player);
        playerList.deop(record.player.getGameProfile());
        save(server);
        LOGGER.info("Removed fake player {}", record.name);
        return true;
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        for (FakeRecord record : FAKES.values()) {
            names.add(record.name);
        }
        return names;
    }

    private static SpawnResult spawnInternal(
            MinecraftServer server,
            ServerLevel level,
            String name,
            Vec3 position,
            boolean restoring
    ) {
        String key = normalize(name);
        if (!isValidName(name)) {
            return new SpawnResult(false, "假人名字必须是 1-16 位字母、数字或下划线");
        }
        if (FAKES.containsKey(key)) {
            return new SpawnResult(false, "假人 " + name + " 已存在");
        }
        if (server.getPlayerList().getPlayerByName(name) != null) {
            return new SpawnResult(false, "已有同名玩家在线：" + name);
        }

        UUID uuid = fakeUuid(name);
        if (server.getPlayerList().getPlayer(uuid) != null) {
            return new SpawnResult(false, "假人 UUID 与现有玩家冲突：" + name);
        }

        // 强制加载目标区块，避免出现旧插件“未加载区块生成失败”的问题。
        int chunkX = position.x < 0 ? (int) Math.floor(position.x) >> 4 : (int) position.x >> 4;
        int chunkZ = position.z < 0 ? (int) Math.floor(position.z) >> 4 : (int) position.z >> 4;
        level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);

        GameProfile profile = new GameProfile(uuid, name);
        FakePlayer fake = new FakePlayer(level, profile);
        fake.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);

        FakeConnection connection = new FakeConnection(name);
        CommonListenerCookie cookie = new CommonListenerCookie(
                profile,
                0,
                ClientInformation.createDefault(),
                false,
                ConnectionType.OTHER
        );

        try {
            // 先写入 OP，保证 WorldEdit 和 /execute as 都有权限上下文。
            server.getPlayerList().op(profile);
            server.getPlayerList().placeNewPlayer(connection, fake, cookie);
            // placeNewPlayer 会加载旧玩家数据，这里再恢复本次指定位置。
            fake.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        } catch (Exception exception) {
            LOGGER.error("Failed to create fake player {}", name, exception);
            try {
                server.getPlayerList().deop(profile);
            } catch (Exception ignored) {
                // 权限清理失败不影响主流程。
            }
            return new SpawnResult(false, "创建假人失败，详见服务端日志");
        }

        FAKES.put(key, new FakeRecord(name, level.dimension().location().toString(), fake));
        save(server);
        LOGGER.info("{} fake player {} at {},{},{}",
                restoring ? "Restored" : "Created",
                name,
                position.x,
                position.y,
                position.z);
        return new SpawnResult(true, "已生成假人 " + name);
    }

    private static void load(MinecraftServer server) {
        if (dataFile == null || !Files.exists(dataFile)) {
            return;
        }

        try {
            String json = Files.readString(dataFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("fakes")) {
                return;
            }

            for (JsonElement element : root.getAsJsonArray("fakes")) {
                JsonObject object = element.getAsJsonObject();
                String name = object.get("name").getAsString();
                String dimension = object.has("dimension")
                        ? object.get("dimension").getAsString()
                        : server.overworld().dimension().location().toString();
                Vec3 position = new Vec3(
                        object.get("x").getAsDouble(),
                        object.get("y").getAsDouble(),
                        object.get("z").getAsDouble()
                );
                ServerLevel level = findLevel(server, dimension);
                spawnInternal(server, level, name, position, true);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to load fake players", exception);
        }
    }

    private static void save(MinecraftServer server) {
        if (dataFile == null) {
            return;
        }

        try {
            Files.createDirectories(dataFile.getParent());
            JsonArray array = new JsonArray();
            for (FakeRecord record : FAKES.values()) {
                ServerPlayer player = record.player;
                JsonObject object = new JsonObject();
                object.addProperty("name", record.name);
                object.addProperty("dimension", record.dimension);
                object.addProperty("x", player.getX());
                object.addProperty("y", player.getY());
                object.addProperty("z", player.getZ());
                array.add(object);
            }

            JsonObject root = new JsonObject();
            root.add("fakes", array);
            Files.writeString(dataFile, root.toString());
        } catch (IOException exception) {
            LOGGER.error("Failed to save fake players", exception);
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return server.overworld();
    }

    private static UUID fakeUuid(String name) {
        return UUID.nameUUIDFromBytes(
                ("fakeplayer:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }
}
