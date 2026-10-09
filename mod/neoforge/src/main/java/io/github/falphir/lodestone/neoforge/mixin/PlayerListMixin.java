package io.github.falphir.lodestone.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.falphir.lodestone.LodestoneConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.players.PlayerList;

/**
 * Replaces the kick message a non-whitelisted player gets, so a network that whitelists through the
 * hub can say where to apply instead of vanilla's "You are not white-listed on this server!".
 * <p>
 * This needs a mixin: vanilla returns {@code Component.translatable("multiplayer.disconnect.not_whitelisted")},
 * and a translatable key is resolved on the <em>client</em> from its own language file -- a server-side
 * lang override would never reach it. Swapping in a literal component is the only way a server-only mod
 * can change the text. There is no loader event for this, which is why it lives here and not in common/.
 * <p>
 * Injected at RETURN rather than HEAD so vanilla keeps deciding who may log in: we only rewrite the
 * answer when the reason it gave was the whitelist, leaving bans, ip-bans and server-full alone.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    private static final String NOT_WHITELISTED = "multiplayer.disconnect.not_whitelisted";

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void wtc_lodestone$customWhitelistMessage(CallbackInfoReturnable<Component> callback) {
        String message = LodestoneConfig.whitelistMessage();
        if (message.isBlank()) return;

        Component reason = callback.getReturnValue();
        if (reason != null && reason.getContents() instanceof TranslatableContents contents
                && NOT_WHITELISTED.equals(contents.getKey())) {
            callback.setReturnValue(Component.literal(message));
        }
    }
}
