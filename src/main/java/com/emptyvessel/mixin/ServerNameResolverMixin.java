package com.emptyvessel.mixin;

import com.emptyvessel.SrvResolver;
import java.util.Optional;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerNameResolver.class)
public abstract class ServerNameResolverMixin {
    @Shadow
    @Final
    private ServerAddressResolver resolver;
    @Shadow
    @Final
    private AddressCheck addressCheck;

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void serverresolvefix$resolveAllCandidates(ServerAddress address,
                                             CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        cir.setReturnValue(SrvResolver.resolve(address, this.resolver, this.addressCheck));
    }
}
