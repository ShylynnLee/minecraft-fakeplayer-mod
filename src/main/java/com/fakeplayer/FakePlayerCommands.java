package com.fakeplayer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

public final class FakePlayerCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("fakeplayer")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> spawn(context, context.getSource().getPosition()))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(context -> spawn(
                                                        context,
                                                        Vec3Argument.getVec3(context, "pos")
                                                )))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> remove(context))))
                        .then(Commands.literal("list")
                                .executes(context -> list(context)))
        );
    }

    private static int spawn(CommandContext<CommandSourceStack> context, Vec3 position) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        ServerLevel level = source.getLevel();
        MinecraftServer server = source.getServer();

        FakePlayerManager.SpawnResult result = FakePlayerManager.spawn(server, level, name, position);
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()), true);
            return 1;
        }

        source.sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");

        if (FakePlayerManager.remove(source.getServer(), name)) {
            source.sendSuccess(() -> Component.literal("已删除假人 " + name), true);
            return 1;
        }

        source.sendFailure(Component.literal("假人不存在：" + name));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> names = FakePlayerManager.list();

        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前没有假人"), false);
            return 0;
        }

        String joined = String.join(", ", names);
        source.sendSuccess(() -> Component.literal("当前假人：" + joined), false);
        return names.size();
    }
}
