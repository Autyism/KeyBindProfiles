package io.github.autyism.keybindprofilesplus.server;

import io.github.autyism.keybindprofilesplus.notification.ProfileNotification;
import io.github.autyism.keybindprofilesplus.profile.ProfileService;
import io.github.autyism.keybindprofilesplus.storage.ModSettings;
import net.minecraft.client.Minecraft;

/** Applies the profile whose rule matches the world or server the player has just joined. */
public final class ServerAutoSwitchController {
    private final ProfileService profileService;
    private final ProfileNotification notification;
    private final ModSettings settings;

    private String lastLocationKey;

    public ServerAutoSwitchController(ProfileService profileService, ProfileNotification notification, ModSettings settings) {
        this.profileService = profileService;
        this.notification = notification;
        this.settings = settings;
    }

    /**
     * Leaving a world or server: goes back to the default profile if the player asked for that.
     * Returns the profile that was applied, or null when nothing changed.
     */
    public String onLeave() {
        String defaultProfile = settings.defaultProfile();
        if (!settings.returnToDefault() || defaultProfile == null || !profileService.profiles().containsKey(defaultProfile)
                || defaultProfile.equals(profileService.getCurrentProfile())) {
            return null;
        }
        profileService.applyProfile(defaultProfile);
        notification.show(defaultProfile);
        return defaultProfile;
    }

    /** Forgets where the player was, so the rules are evaluated again on the next tick. */
    public void reset() {
        lastLocationKey = null;
    }

    public void tick(Minecraft client) {
        if (client == null || !settings.autoSwitch()) {
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
