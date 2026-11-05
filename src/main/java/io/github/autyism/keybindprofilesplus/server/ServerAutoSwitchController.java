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

    // situation rules (SituationRules): a profile for a while, then back
    private static final int ENTER_TICKS = 3;
    private static final int LEAVE_TICKS = 10;
    /** The profile to go back to; null = no situation profile is on. */
    private String baseProfile;
    /** The situation profile that was applied (to notice the player choosing another one meanwhile). */
    private String situationProfile;
    private String candidate;
    private int candidateTicks;
    private int noneTicks;
    /** The player picked a profile himself while a situation applied: leave that situation alone until it ends. */
    private String suppressed;

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
        if (baseProfile != null) {
            // leaving while a situation profile is on: back to the real one first
            String back = baseProfile;
            forgetSituation();
            if (!back.isEmpty() && profileService.profiles().containsKey(back) && !back.equals(profileService.getCurrentProfile())) {
                profileService.applyProfile(back);
            }
        }
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
        candidate = null;
        candidateTicks = 0;
    }

    /** Leaving a world: no situation profile is on any more. */
    private void forgetSituation() {
        baseProfile = null;
        situationProfile = null;
        candidate = null;
        candidateTicks = 0;
        noneTicks = 0;
        suppressed = null;
    }

    /** True while a situation rule has put on its profile (it goes back to {@link #situationBase()} afterwards). */
    public boolean inSituation() {
        return baseProfile != null;
    }

    public String situationBase() {
        return baseProfile;
    }

    public void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        if (!settings.autoSwitch()) {
            // switched off while a situation profile was on: back to the real one
            if (baseProfile != null) {
                String back = baseProfile;
                forgetSituation();
                if (!back.isEmpty() && profileService.profiles().containsKey(back) && !back.equals(profileService.getCurrentProfile())) {
                    profileService.applyProfileForAWhile(back);
                    notification.show(back);
                }
            }
            return;
        }

        ServerProfileMatcher.Location location = ServerProfileMatcher.currentLocation(client);
        if (location == null) {
            return;
        }

        String locationKey = location.key();
        if (!locationKey.equals(lastLocationKey)) {
            lastLocationKey = locationKey;
            ServerProfileMatcher.Match match = match(location);
            if (match != null && !match.profile().equals(profileService.getCurrentProfile())) {
                profileService.applyProfile(match.profile());
                notification.show(match.profile());
                if (baseProfile != null) {
                    // the place changed while a situation applied: that is the profile to go back to now
                    baseProfile = match.profile();
                    situationProfile = null;
                }
            }
        }
        tickSituations(client);
    }

    private void tickSituations(Minecraft client) {
        String current = profileService.getCurrentProfile();
        if (baseProfile != null && situationProfile != null && !situationProfile.equals(current)) {
            // the player chose a profile himself: keep it, and do not put the situation one on again until it ends
            suppressed = situationProfile;
            baseProfile = null;
            situationProfile = null;
        }
        String best = SituationRules.bestProfile(client, profileService.profileAutoSwitchServers(), profileService.profiles().keySet());
        if (best != null && best.equals(suppressed)) {
            best = null;
        } else if (best == null) {
            suppressed = null;
        }
        if (best == null) {
            candidate = null;
            candidateTicks = 0;
            if (baseProfile != null && ++noneTicks >= LEAVE_TICKS) {
                String back = baseProfile;
                baseProfile = null;
                situationProfile = null;
                noneTicks = 0;
                if (!back.isEmpty() && profileService.profiles().containsKey(back) && !back.equals(current)) {
                    profileService.applyProfileForAWhile(back);
                    notification.show(back);
                }
            }
            return;
        }
        noneTicks = 0;
        if (!best.equals(candidate)) {
            candidate = best;
            candidateTicks = 0;
        }
        if (++candidateTicks < ENTER_TICKS || best.equals(current)) {
            if (best.equals(current) && baseProfile != null) situationProfile = best;
            return;
        }
        if (baseProfile == null) baseProfile = current == null ? "" : current;
        situationProfile = best;
        profileService.applyProfileForAWhile(best);
        notification.show(best);
    }

    /** The rule that decides which profile belongs to a location, or null when none matches. */
    public ServerProfileMatcher.Match match(ServerProfileMatcher.Location location) {
        return ServerProfileMatcher.findBestMatch(location, profileService.profileAutoSwitchServers(), profileService.profiles().keySet());
    }
}
