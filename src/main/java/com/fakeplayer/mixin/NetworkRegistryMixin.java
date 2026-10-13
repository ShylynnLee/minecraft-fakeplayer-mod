package com.fakeplayer.mixin;

import com.fakeplayer.FakeConnection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 假人没有真实客户端，也没有协商出的 NeoForge payload 通道。
 *
 * <p>某些模组（例如暮色森林）在玩家登录时会主动发送自定义 payload；
 * 原版 NeoForge 会因为通道不存在而抛出异常。这里对假连接跳过发送前校验，
 * 实际网络发送仍由 FakeConnection 直接丢弃。</p>
 */
@Mixin(value = NetworkRegistry.class, remap = false)
public abstract class NetworkRegistryMixin {

    @Inject(
        method = "checkPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void fakeplayer$allowFakePlayerPayloads(
            Packet<?> packet,
            ServerCommonPacketListener listener,
            CallbackInfo callback
    ) {
        Connection connection = listener.getConnection();
        if (connection instanceof FakeConnection) {
            callback.cancel();
        }
    }
}
