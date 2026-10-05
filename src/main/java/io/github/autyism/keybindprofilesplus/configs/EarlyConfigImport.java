package io.github.autyism.keybindprofilesplus.configs;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * Writes a staged config import at the earliest moment a mod can run code: Fabric creates language
 * adapters while it freezes the mod list, before it reads any mod's mixin configuration and long
 * before any mod reads its settings. So an import is complete with the next start, including the
 * settings some mods read while the game is still being put together.
 *
 * <p>This is registered as a language adapter only for that moment; no entrypoint uses it. It must not
 * touch Minecraft, any library or any other class of this mod that does, because the game is not
 * loaded yet.</p>
 */
public final class EarlyConfigImport implements LanguageAdapter {
    public EarlyConfigImport() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            ConfigImportApplier.Outcome outcome = ConfigImportApplier.applyPending(loader.getGameDir(), loader.getConfigDir());
            if (outcome != null) {
                System.out.println("[KeyBind Profiles+] Imported mod configs: " + outcome.written() + " written, "
                        + outcome.removed() + " removed, " + outcome.skipped().size() + " skipped, "
                        + outcome.errors().size() + " errors" + (outcome.backup() != null ? ", backup " + outcome.backup() : ""));
            }
        } catch (Throwable t) {
            // Never stop the game from starting because of an import.
            System.err.println("[KeyBind Profiles+] Importing mod configs failed: " + t);
        }
    }

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        throw new LanguageAdapterException("keybindprofilesplus_early is not an entrypoint language");
    }
}
