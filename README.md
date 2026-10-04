<p align="center"><img src="docs/icon.png" width="128" alt="icon"></p>
<h1 align="center">KeyBind Profiles+</h1>
<p align="center">Save, switch, compare and share whole sets of key binds, and manage the keys of the game, your mods, Meteor Client, MaLiLib mods and Inventory Profiles Next from one screen.</p>

**English** | [简体中文](README.zh-CN.md)

![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-62B47A) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue)

KeyBind Profiles+ is a client-side Fabric mod for Minecraft 1.21.11 that keeps your key bindings in named profiles. Keep one profile for PvP, one for building and one for redstone, and switch with a click, with a hotkey of your own, or automatically when you join a server. A profile can also carry the game settings you pick, such as FOV or mouse sensitivity.

Its Key Binds screen puts every key in one list: the game's keys, the keys your other mods register, and the hotkeys that Meteor Client, MaLiLib mods (Litematica, Tweakeroo, MiniHUD, ...) and Inventory Profiles Next normally keep in their own menus. Every key shows the mod it comes from, conflicts are marked while you rebind, and keys can be put on Ctrl / Shift / Alt combinations.

KeyBind Profiles+ is a fork of [KeyBindProfiles](https://github.com/imsawiq/KeyBindProfiles) by sawiq_.

## Features

### Profiles

- **Named profiles.** Save your current keys as a profile, then apply, rename, edit or delete it at any time. Profiles are plain JSON files (`.kbp`) in `config/keybindprofilesplus/`, easy to back up.
- **Choose what a profile saves.** A tick tree lists every key binding, every hotkey of the supported mods and the game's own settings (FOV, sensitivity, volumes, chat, video and more), down to single entries. Anything left unticked is not touched when the profile is applied, so a profile can hold just your FOV or just your Litematica hotkeys.
- **Preview before applying.** Before a profile is applied you see every change, from the old value to the new one. Tick "Don't ask again" to skip the preview; the setting "Ask before applying" turns it back on.
- **Profile hotkeys.** Give a profile a hotkey of one or two keys (mouse buttons work too) and press it in game to switch; a short notice appears above the hotbar. Profile hotkeys only react during play, so typing in chat never switches profiles.
- **Compare profiles.** See two profiles, or a profile and your current settings, side by side with the differences highlighted. "Only differences" hides everything else; key bindings, other mods' hotkeys and saved game settings are all compared.
- **Share codes.** "Copy Code" turns a profile into one line of text starting with `KBP1-`, and "Import" turns such a line back into a profile after showing a preview. A damaged or incomplete code is explained instead of failing.
- **Picks up where you left off.** The profile you applied last is applied again when the game starts.
- **Coming from KeyBindProfiles.** On the first start, the profiles of the original mod are copied over (its own folder is left untouched) and your old key for opening the profiles is kept.

### Key Binds screen

- **Every key in one list.** Options → Controls → Key Binds opens this mod's screen in place of the vanilla one (you can switch back in the settings). Rebind keys there, and reset one key or all of them.
- **The source of every key.** Each row names the mod a key belongs to, so you can see at a glance which mod added which key. Search by name, key or mod, filter by source, group by category or by source, or show conflicts only.
- **Details on hover.** Point at a key's name for its source, category and default key; point at its key button to see what it conflicts with and why.
- **Readable key names.** Numpad keys show as "Num 5", "Num +" or "Num Enter" everywhere in the game. In this mod's screens, mod keys without a translation show readable words instead of raw names like `key.somemod.toggle_zoom`.

### Hotkeys of other mods

- **Meteor Client.** The key of every module, the key settings inside modules and your macros are listed, unbound ones included. Modules of Meteor addons get their own group ("Meteor: addon name").
- **MaLiLib mods.** Every hotkey registered with MaLiLib, for example those of Litematica, Tweakeroo, MiniHUD or Item Scroller, is listed by mod under that mod's own names.
- **Inventory Profiles Next.** Its hotkeys are kept in its own library rather than in the game's key list; here they are listed and editable next to everything else.
- **Edit them like any other key.** Click the key button and press the new key (Ctrl / Shift / Alt combinations included), press Esc for no key, or click Reset for that mod's default. The mod then saves the change in its own config file, just as if you had used its own menu.
- **Part of your profiles.** These hotkeys are saved in profiles (ticked by default in new ones), compared, listed in the preview, switched by hotkeys and auto-switch, and carried in share codes.
- **The other mod's rules still apply.** For example, Meteor does not let a module sit on the left or right mouse button, so such a change is refused with a message that says why.

### Conflicts

- **Three levels.** Red means two keys act in the same situation. Yellow means they only meet in some situations, for example only in Creative mode, or one works during play and the other only in screens. No mark means they never get in each other's way.
- **No false alarms.** `G` and `F3 + G`, `X` and `Ctrl + X`, an F3 combination added by a mod and a plain key, or Litematica's tool and the attack button are not marked; the tooltip still lists what shares the key.
- **Across mods.** A Meteor module on `Z` and a vanilla key on `Z` are marked, and so are a Meteor module and a Litematica hotkey on the same key.
- **Live and adjustable.** The marks and the conflict counter update the moment you change a key. If the mod guessed wrong about when a mod's key is in use, click the key's name and set it yourself.

### Modifier combinations

- **Ctrl / Shift / Alt + key.** Put any key binding on a combination such as `Ctrl + X`, in this mod's Key Binds screen or in the vanilla one. Pressing `Ctrl + X` triggers only the combination, while a plain `X` keeps doing whatever else is bound to it.

### Worlds and servers

- **Auto-switch.** Give a profile rules and it is applied when you join a matching world or server. A rule is an exact address (`play.example.org:25566`), a host on any port including its subdomains (`example.org`), a wildcard (`*.example.org`), `singleplayer`, `lan`, `realms`, or `*` for anywhere; the most specific matching rule wins.
- **All rules on one screen.** The Rules screen lists the rules of all profiles and lets you add or remove them. While you are in a world, it also shows which profile the rules pick there.
- **Back to a default profile.** Optionally return to a default profile whenever you leave a world or server.

### More

- **Mod Menu button.** With Mod Menu installed, KeyBind Profiles+ gets a configure button in the mod list. It opens the settings, with shortcuts to the profiles and the Key Binds screen; Mod Menu is not required.
- **Languages.** English and Simplified Chinese, plus the partial Russian translation of the original mod.
- **Client-side only.** The mod sends nothing to the server and does not change gameplay.

## Screenshots

![Key Binds screen](docs/images/key-binds.png)

*The Key Binds screen: every key with the mod it comes from, conflicts in red (real) or yellow (possible), details on hover.*

![Profile manager](docs/images/profile-list.png)

*The profile manager: one profile selected, a second one marked for comparison.*

![What a profile saves](docs/images/saved-contents.png)

*Choose what a profile saves, down to single keys. A saved value that differs from your current one is shown in yellow.*

![Preview before applying](docs/images/apply-confirm.png)

*Before a profile is applied, every change is listed from the old to the new value.*

![Compare profiles](docs/images/compare.png)

*Two profiles side by side: differences are highlighted, "-" means only one profile saves that key.*

![Hotkeys of other mods](docs/images/other-mods-hotkeys.png)

*Hotkeys of other mods sit in the same list: here Litematica's main menu hotkey, rebound to Ctrl + F19.*

![Import a share code](docs/images/import-share-code.png)

*Importing a share code: a wrong or damaged code is explained and Import stays disabled.*

![Settings](docs/images/settings.png)

*The settings, here opened from Mod Menu, with shortcuts to the profile manager and the Key Binds screen.*

## How to use

### Keys

| Action | Default key | Where to change it |
|---|---|---|
| Open the profile manager ("Open KeyBind Profiles+") | `O` | Key Binds screen, category "Miscellaneous" |
| Switch to a particular profile | not set | Profile manager → select the profile → Edit → Hotkey |

Both work during play, when no screen is open. KeyBind Profiles+ has no commands.

Inside the mod's screens:

| Where | Input | What it does |
|---|---|---|
| Profile list | Click | Select a profile |
| Profile list | Double-click, Enter or Space | Apply the selected profile |
| Profile list | Ctrl + click or right-click | Mark a second profile, then click "Compare 2" |
| Recording a profile hotkey | Enter / Backspace / Esc | Save / remove the hotkey / cancel |
| Key Binds screen, after clicking a key button | Any key or mouse button | Bind it |
| Key Binds screen, after clicking a key button | Hold Ctrl, Shift or Alt, then press a key | Bind a combination such as `Ctrl + X` |
| Key Binds screen, after clicking a key button | Press and release Ctrl, Shift or Alt on its own | Bind that key itself |
| Key Binds screen, after clicking a key button | Esc | Leave it without a key |
| Key Binds screen | Click the name of a mod's key | Change when that key counts as in use |

### Where things are

- **Profile manager:** press `O` in game, or click "Manage Profiles" at the bottom of the Key Binds screen.
- **Key Binds screen:** Options → Controls → Key Binds, or "Key Binds" in the profile manager.
- **Settings:** "Settings" in the profile manager, or the configure button in Mod Menu.
- **Auto-switch rules:** "Rules" in the profile manager for all profiles, or Edit → "Applied automatically in" for one profile.

### Your first profile

1. Press `O` and click **New Profile...**.
2. Type a name. Every key binding and every hotkey of a supported mod is ticked; game settings are not.
3. Untick what this profile should leave alone. To include a game setting such as FOV, open **Other game settings** and tick it; the search box above the tree finds entries quickly.
4. Click **Create Profile**.

### Switching profiles

- Select a profile and click **Apply** (or double-click it). If something would change, a preview lists it first (unless you turned "Ask before applying" off); click **Apply** to confirm.
- To switch with a hotkey, select the profile, click **Edit**, click the button under **Hotkey**, press one or two keys and then Enter. In game, press the hotkey; the notice above the hotbar confirms the switch.

### Keeping a profile up to date

When you have changed keys and want a profile to keep them, select it and click **Edit** → **Saved Contents...** → **Use Current Values** → **Done**. On the same screen you can tick or untick what the profile saves; a saved value that differs from your current one is shown in yellow, with "(now ...)" next to it.

### Comparing

Select a profile and click **Compare** to compare it with your current settings. To compare two profiles, select one, Ctrl + click (or right-click) the other and click **Compare 2**. In the compare screen, A and B can be set to any profile or to "Current settings", and **Only differences** hides all rows that match.

### Sharing

Select a profile and click **Copy Code**: the code is now in your clipboard, ready to paste into a chat or a file. To use a code, click **Import** → **Paste**, check the preview and the suggested name, and click **Import**.

### Auto-switching on servers

1. Select a profile → **Edit** → **Applied automatically in**.
2. Type an address or rule and click **Add**, or use **+ Singleplayer** or the button showing the address of the server you are on (or joined last).
3. To go back to a default profile when you leave: set **Use as default profile** to ON for that profile (or choose it under Settings → **Default profile**), then turn on **Back to default when leaving** in the settings.

### Rebinding keys and fixing conflicts

1. Open Options → Controls → Key Binds.
2. Click a key button: it shows `> key <` while it waits. Press the new key, a combination or a mouse button, or Esc for no key. **Reset** on a row restores its default; **Reset Keys** at the bottom resets all game key bindings after asking.
3. Conflicts show as `[ key ]` in red or yellow, with a bar of the same colour. Point at the key button to see with what and why, or turn on **Conflicts only** to list nothing else.
4. If a mod's key is marked although it can never clash (or the other way round), click its name. Each click changes when it counts as in use: automatic, during play, only while a screen is open, only in a special situation (never a conflict), and back to automatic. Vanilla keys and F3 combinations follow fixed rules.

## Settings

Open them with "Settings" in the profile manager or with the configure button in Mod Menu.

| Setting | Default | What it does |
|---|---|---|
| Replace the vanilla Key Binds screen | ON | ON: Options → Controls → Key Binds opens this mod's screen. OFF: the vanilla screen is kept, with this mod's buttons, conflict marks and combination recording added (hotkeys of other mods are only listed in this mod's screen). |
| Ask before applying | ON | The Apply button first shows what will change. Hotkeys and auto-switch never ask. |
| Auto-switch when joining | ON | Applies the profile whose rule matches the world or server you join. |
| Default profile | None | The profile to go back to when you leave a world or server. |
| Back to default when leaving | OFF | Applies the default profile again whenever you leave a world or server. |
| Open Profiles Folder | (button) | Opens `config/keybindprofilesplus/` in your file manager. |

Each profile has its own options under **Edit**:

| Option | Default | What it does |
|---|---|---|
| Hotkey | Not set | One or two keys that switch to this profile during play. |
| Saved Contents... | New profiles: all key bindings and mod hotkeys, no game settings | What the profile saves and applies. |
| Applied automatically in | No rules | Worlds and servers in which this profile is applied when you join. |
| Use as default profile | OFF | Makes this the profile to go back to (the same as "Default profile" above). |

## Requirements

- Minecraft Java Edition 1.21.11
- Fabric Loader 0.17.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21 or newer
- Client-side only: install it in your game. Servers do not need it, and it works on any server.

Optional, each one only adds its own part:

| Mod | What it adds |
|---|---|
| [Mod Menu](https://modrinth.com/mod/modmenu) | A configure button in the mod list |
| [Meteor Client](https://meteorclient.com) (and its addons) | Module keys, key settings and macros in the Key Binds screen and in profiles |
| [MaLiLib](https://modrinth.com/mod/malilib) and the mods built on it ([Litematica](https://modrinth.com/mod/litematica), Tweakeroo, MiniHUD, Item Scroller, ...) | Their hotkeys in the Key Binds screen and in profiles |
| [Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) (with libIPN) | Its hotkeys in the Key Binds screen and in profiles |

Tested with Mod Menu 17.0.1, Meteor Client for 1.21.11 (build 86), MaLiLib 0.27.20 with Litematica 0.26.16, and Inventory Profiles Next 2.2.6 with libIPN 6.6.3.

## Compatibility

- **Meteor Client, MaLiLib mods, Inventory Profiles Next:** tested with the versions above. Meteor addons and other MaLiLib mods (Tweakeroo, MiniHUD, Item Scroller, ...) are read the same way as Meteor itself and Litematica, but have not each been tested. If a future version of one of these mods changes too much inside, its hotkeys become read-only (Meteor, MaLiLib) or are left out (Inventory Profiles Next) instead of causing errors, and a warning is written to the log.
- **Other key binds screens:** only the vanilla Key Binds screen is replaced. If another mod opens a key binds screen of its own, that screen is left as it is.
- **Mods that add F3 combinations:** keys that mods put into the game's Debug category are treated like the vanilla F3 combinations, so they do not clash with plain keys.
- **Inventory and recipe mods:** key bindings of mods known to work only inside screens (for example REI, JEI, EMI, Mouse Tweaks, Mouse Wheelie) count as "only while a screen is open" for the conflict check. Any guess can be corrected by clicking the key's name.
- **Rendering mods:** KeyBind Profiles+ does not change how the world is rendered, so it is not expected to interact with Sodium, Iris or similar mods. This combination has not been specifically tested.
- **The original KeyBindProfiles:** KeyBind Profiles+ takes over its job and its profiles, and the two cannot be installed together (the game tells you at startup).

## Installation

1. Install Fabric Loader 0.17.3 or newer for Minecraft 1.21.11.
2. Download Fabric API for 1.21.11 and KeyBind Profiles+, and put both jar files into the `mods` folder of your game.
3. Optional: add Mod Menu and any of the mods listed under Requirements.
4. Start the game and press `O` to open the profile manager.

Switching from the original KeyBindProfiles: remove its jar (the two cannot run together). Your profiles are copied over on the first start.

Building from source: run `./gradlew build` (`gradlew build` on Windows) with JDK 21. The jar ends up in `build/libs/`.

## FAQ

**Does it work on servers?**
Yes. KeyBind Profiles+ only changes key and game settings on your own computer; it sends nothing to the server and does not change gameplay. Server rules about other mods, such as Meteor Client, are a separate matter.

**Do I need Mod Menu, Meteor Client, Litematica or Inventory Profiles Next?**
No. They are all optional. Without them, their parts simply do not appear.

**Where are my profiles, and how do I back them up?**
In `config/keybindprofilesplus/` inside your game folder, one `.kbp` file (plain JSON) per profile, next to the mod's own settings. Settings → "Open Profiles Folder" opens it; copying the folder backs up everything.

**I changed some keys, restarted the game, and my changes were gone.**
When the game starts, KeyBind Profiles+ applies the profile you applied last. Save your changes into that profile first (Edit → Saved Contents... → Use Current Values → Done), or save them as a new profile and apply that one.

**Why is a key marked yellow, or not marked at all, although another key uses it?**
Point at its key button: the tooltip lists what shares the key and why it counts as a real, a possible or no conflict. If the mod guessed wrong about when a mod's key is in use, click the key's name to change it.

**A hotkey of another mod has no key button.**
It is read-only. It belongs to a Meteor profile that is not loaded (shown in grey), or that mod could not be reached. Point at its name for details and change it in that mod.

**How do I get the vanilla Key Binds screen back?**
Settings → "Replace the vanilla Key Binds screen" → OFF. The vanilla screen then gets this mod's buttons, conflict marks and combination recording.

**I used the original KeyBindProfiles. What happens to my profiles?**
On the first start they are copied to `config/keybindprofilesplus/` (the old folder is not changed), and your old key for opening the profiles carries over.

## Known limitations

- Combinations use Ctrl, Shift and Alt only (the left and right keys count the same) plus one key or mouse button. The Windows / Command key is not supported; Meteor binds that use it are shown but not checked for conflicts.
- While the modifier of a combination is held, only the combination reacts on that key. For example, with something on `Ctrl + W`, holding Ctrl to sprint and pressing W does not walk forward, so avoid combinations on keys you already press together with that modifier.
- Hotkeys of other mods can only be changed while that mod is running. Key binds stored in Meteor profiles that are not loaded are read-only.
- For Inventory Profiles Next, only the main key of each hotkey is shown and changed; its alternative keys stay as they are.
- "Reset Keys" resets the game's key bindings. Hotkeys of other mods are reset with the Reset button on their own rows.
- Share codes do not include the profile's hotkey and server rules.
- The notice above the hotbar uses the same line as the name of the held item and can overlap it.
- The Russian translation covers only a few lines; everything else is shown in English.

## Credits

- **sawiq_**, author of [KeyBindProfiles](https://github.com/imsawiq/KeyBindProfiles). KeyBind Profiles+ grew out of it: saving and switching key binding profiles, profile hotkeys and switching per server started there. Its MIT notice is kept in [LICENSE-upstream-MIT](LICENSE-upstream-MIT).
- KeyBind Profiles+ is developed by Autyism.

## License

**GPL-3.0-only.** KeyBind Profiles+ is free software under the GNU General Public License v3.0 (see [LICENSE](LICENSE)); the original KeyBindProfiles code by sawiq_ that it started from is MIT-licensed, and its notice is kept in [LICENSE-upstream-MIT](LICENSE-upstream-MIT).
