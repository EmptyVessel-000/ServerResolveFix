package com.emptyvessel.mixin;

import com.emptyvessel.SrvResolver;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {
    @Inject(method = "pingServer", at = @At("HEAD"))
    private void serverresolvefix$refreshSelection(ServerData data, Runnable onPingSuccess, Runnable onPingFailure,
                                         CallbackInfo ci) {
        if (ServerAddress.isValidAddress(data.ip)) {
            SrvResolver.refresh(ServerAddress.parseString(data.ip));
        }
    }
}
