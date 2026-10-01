package io.github.autyi6969.keybindprofilesplus.server;

import io.github.autyi6969.keybindprofilesplus.notification.ProfileNotification;
import io.github.autyi6969.keybindprofilesplus.profile.ProfileService;
import net.minecraft.client.MinecraftClient;

import java.util.function.BooleanSupplier;

/** Applies the profile whose rule matches the world or server the player has just joined. */
public final class ServerAutoSwitchController {
    private final ProfileService profileService;
    private final ProfileNotification notification;
    private final BooleanSupplier enabled;

    private String lastLocationKey;

    public ServerAutoSwitchController(ProfileService profileService, ProfileNotification notification, BooleanSupplier enabled) {
        this.profileService = profileService;
        this.notification = notification;
        this.enabled = enabled;
    }

    /** Forgets where the player was, so the rules are evaluated again on the next tick. */
    public void reset() {
        lastLocationKey = null;
    }

    public void tick(MinecraftClient client) {
        if (client == null || !enabled.getAsBoolean()) {
            return;
        }

        ServerProfileMatcher.Location location = ServerProfileMatcher.currentLocation(client);
        if (location == null) {
            return;
        }

        String locationKey = location.key();
        if (locationKey.equals(lastLocationKey)) {
            return;
        }
        lastLocationKey = locationKey;

        ServerProfileMatcher.Match match = match(location);
        if (match == null || match.profile().equals(profileService.getCurrentProfile())) {
            return;
        }

        profileService.applyProfile(match.profile());
        notification.show(match.profile());
    }

    /** The rule that decides which profile belongs to a location, or null when none matches. */
    public ServerProfileMatcher.Match match(ServerProfileMatcher.Location location) {
        return ServerProfileMatcher.findBestMatch(location, profileService.profileAutoSwitchServers(), profileService.profiles().keySet());
    }
}
