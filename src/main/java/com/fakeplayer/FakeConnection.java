package com.fakeplayer;

import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.ProtocolInfo;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 假人的空连接：不向客户端发送任何网络包。
 *
 * <p>命令输出中的聊天包会写入服务端日志，避免出现“命令执行成功但输出全空”。</p>
 */
public final class FakeConnection extends Connection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String playerName;
    private final Channel channel = new EmbeddedChannel();

    public FakeConnection(String playerName) {
        super(PacketFlow.SERVERBOUND);
        this.playerName = playerName;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void send(Packet<?> packet) {
        send(packet, null);
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener) {
        send(packet, listener, true);
    }

    @Override
    public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
        logChat(packet);
        if (listener != null) {
            listener.onSuccess();
        }
    }

    private void logChat(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket chat && !chat.overlay()) {
            LOGGER.info("[FakePlayer {}] {}", playerName, chat.content().getString());
        } else if (packet instanceof ClientboundDisguisedChatPacket chat) {
            LOGGER.info("[FakePlayer {}] {}", playerName, chat.message().getString());
        }
    }

    @Override
    public void flushChannel() {
        // 假人没有真实 channel。
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T listener) {
        // 假人没有真实客户端，不需要切换协议。
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocolInfo) {
        // 假人没有真实客户端，不需要切换协议。
    }

    @Override
    public void disconnect(Component message) {
        // 不触发真实连接断开。
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
        // 不触发真实连接断开。
    }

    @Override
    public void setReadOnly() {
        // 假人没有真实 channel。
    }

    @Override
    public void handleDisconnection() {
        // 假人没有真实 channel。
    }

    @Override
    public void tick() {
        // 假人的 listener 不需要网络 tick。
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return new InetSocketAddress("127.0.0.1", 0);
    }

    @Override
    public String getLoggableAddress(boolean logIp) {
        return "fake-player";
    }
}
