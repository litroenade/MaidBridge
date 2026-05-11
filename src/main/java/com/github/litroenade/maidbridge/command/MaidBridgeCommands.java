package com.github.litroenade.maidbridge.command;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.transport.BridgeTransport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

// Minecraft 的 Level 由服务端持有，命令只借用查询结果，不能在这里关闭。
public final class MaidBridgeCommands {
    private static final int DEFAULT_EVENT_LIMIT = 10;
    private static final int MAX_EVENT_LIMIT = 50;

    private MaidBridgeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, BridgeTransport transport) {
        dispatcher.register(Commands.literal("maidbridge")
                .requires(source -> source.hasPermission(2))
                .executes(context -> runSummary(context, transport))
                .then(Commands.literal("summary")
                        .executes(context -> runSummary(context, transport)))
                .then(Commands.literal("ws")
                        .executes(context -> runWebSocket(context, transport))
                        .then(Commands.literal("restart")
                                .requires(source -> source.hasPermission(3))
                                .executes(context -> restartTransport(context, transport))))
                .then(Commands.literal("chat")
                        .executes(context -> runChat(context, DEFAULT_EVENT_LIMIT))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_EVENT_LIMIT))
                                .executes(context -> runChat(context, IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("turns")
                        .executes(context -> runTurns(context, DEFAULT_EVENT_LIMIT))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_EVENT_LIMIT))
                                .executes(context -> runTurns(context, IntegerArgumentType.getInteger(context, "limit"))))));
    }

    private static int runSummary(CommandContext<CommandSourceStack> context, BridgeTransport transport) {
        return sendLines(context, MaidBridgeDebugReports.summaryLines(transport));
    }

    private static int runWebSocket(CommandContext<CommandSourceStack> context, BridgeTransport transport) {
        return sendLines(context, MaidBridgeDebugReports.webSocketLines(transport));
    }

    private static int runChat(CommandContext<CommandSourceStack> context, int limit) {
        return sendLines(context, MaidBridgeDebugReports.chatLines(limit));
    }

    private static int runTurns(CommandContext<CommandSourceStack> context, int limit) {
        return sendLines(context, MaidBridgeDebugReports.turnsLines(limit));
    }

    private static int restartTransport(CommandContext<CommandSourceStack> context, BridgeTransport transport) {
        MinecraftServer server = server(context);
        Config.refreshFromSpec();
        AiChainEventSink.configure(Config.maxBufferedEvents);
        transport.restart(server);
        AiChainEventSink.setConsumer(transport::publish);
        return sendLines(context, MaidBridgeDebugReports.restartWebSocketLines(transport));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer();
    }

    private static int sendLines(CommandContext<CommandSourceStack> context, List<String> lines) {
        for (String line : lines) {
            send(context, line);
        }
        return lines.size();
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }
}
