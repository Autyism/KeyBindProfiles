# Changelog

## 0.2.0+26.3 — 2026-10-07

KeyBind Profiles+ for Minecraft 26.3, with the same features as 0.2.0 for 1.21.11.

- Needs Java 25 and Fabric Loader 0.19.3 or newer.
- 26.3 numbers keys in a new way. Profiles, share codes and key combinations still store the key names of the older versions, so a profile made on another version binds the same physical keys here, and the other way round.
- The profile manager opens with `I` by default, as on 26.2.
- Meteor Client has no 26.3 build yet, so its module keys could not be tested on 26.3.

### 中文

适用于 Minecraft 26.3 的 KeyBind Profiles+，功能与 1.21.11 的 0.2.0 相同。

- 需要 Java 25 和 Fabric Loader 0.19.3 或更高。
- 26.3 换了一套按键编号。档案、分享码和组合键仍然按旧版本的键名保存，所以在其他版本做的档案在这里绑的是同一批物理按键，反过来也一样。
- 和 26.2 一样，档案管理界面默认用 `I` 打开。
- Meteor Client 还没有 26.3 版，所以它的模块按键没法在 26.3 上测试。

## 0.2.0+26.2 — 2026-10-07

KeyBind Profiles+ for Minecraft 26.2, with the same features as 0.2.0 for 1.21.11.

- Needs Java 25 and Fabric Loader 0.19.3 or newer.
- The profile manager opens with `I` by default, because vanilla 26.2 uses `O`. If you already bound it to `O`, that binding is kept and shows as a conflict.

### 中文

适用于 Minecraft 26.2 的 KeyBind Profiles+，功能与 1.21.11 的 0.2.0 相同。

- 需要 Java 25 和 Fabric Loader 0.19.3 或更高。
- 档案管理界面默认改用 `I` 打开，因为原版 26.2 占用了 `O`。如果你已经把它绑在 `O` 上，会保留原来的绑定，并标成冲突。

## 0.2.0+26.1.2 — 2026-10-07

KeyBind Profiles+ for Minecraft 26.1, 26.1.1 and 26.1.2, with the same features as 0.2.0 for 1.21.11.

- Needs Java 25 and Fabric Loader 0.19.3 or newer.
- 1.21.11 players keep using `keybindprofilesplus-0.2.0.jar`.

### 中文

适用于 Minecraft 26.1、26.1.1 和 26.1.2 的 KeyBind Profiles+，功能与 1.21.11 的 0.2.0 相同。

- 需要 Java 25 和 Fabric Loader 0.19.3 或更高。
- 1.21.11 玩家继续用 `keybindprofilesplus-0.2.0.jar`。

## 0.2.0 — 2026-10-05

- New: **Export / Import Mod Configs** (Settings). Saves the settings files of all mods into one file and imports such files; profiles and share codes are unchanged.
- Finds the files of every installed mod by reading which file and folder names its code really uses, including mods with their own folder outside `config/`; recognises the leftovers of switched-off and removed mods.
- Never exports login data, maps, caches, logs, backups, dated records or machine state; per-world and per-server data is offered but not ticked.
- This mod's own profiles and settings can go along too; its working files (exports, a waiting import, the scan cache) never do.
- Add-ons whose settings another mod saves are named with that mod ("Meteor Client (incl. ...)").
- Per-world data shows the world's real name, also when a mod turned it into a file name ("local_New_World__1_"), and is recognised when one file holds the data of every world.
- Choose what to export in a tick tree, by mod and by file, with search.
- Import preview per file (new, replaces, already the same, mod not installed here); the files are written on the next start before any mod reads its settings, with a backup that undoes the import.

### 中文

- 新功能：**导出 / 导入模组配置**（在设置里）。把所有模组的设置文件存成一个文件，或导入这样的文件；档案和分享码不受影响。
- 通过读取每个已安装模组的代码、看它真正使用的文件和文件夹名，找出它的配置，包括在 `config/` 以外自建文件夹的模组；也认得已停用、已删除的模组留下的文件。
- 登录信息、地图、缓存、日志、备份、带日期的记录和本机状态永远不会导出；按世界或服务器保存的数据会列出，但默认不勾选。
- 本模组自己的档案和设置也可以一起导出；它的工作文件（导出文件、待导入、扫描缓存）永远不会。
- 设置由另一个模组保存的附属模组会写在那个模组旁边（"Meteor Client（含 ……）"）。
- 按世界保存的数据会显示世界的真实名字，模组把名字改成文件名（"local_New_World__1_"）时也一样；一个文件里装着所有世界数据的情况也能认出来。
- 用勾选树按模组、按文件选择要导出的内容，可以搜索。
- 导入前逐个文件预览（新增、替换、已经一样、这里没装这个模组）；文件在下次启动、任何模组读取设置之前写入，并留有可撤销的备份。

## 0.1.0 — 2026-10-04

First public release.

- Key binding profiles: save, apply, rename, edit and delete them; each profile is a plain JSON file in `config/keybindprofilesplus/`.
- Choose exactly what a profile saves: single key bindings, hotkeys of other mods, and game settings such as FOV, sensitivity or volumes.
- Preview of every change before a profile is applied, with "Don't ask again".
- Profile hotkeys of one or two keys, with a short notice above the hotbar; they only react during play.
- The last applied profile is applied again when the game starts.
- Side-by-side comparison of two profiles, or of a profile and the current settings, with "Only differences".
- Share codes (`KBP1-...`) to copy a profile as one line of text and import it with a preview; damaged codes are explained.
- A new Key Binds screen in place of the vanilla one (can be turned off): the source mod of every key, search, source filter, grouping, details on hover, reset one or all keys.
- Hotkeys of Meteor Client (modules, key settings inside modules, macros, addon modules), MaLiLib mods (Litematica, Tweakeroo, MiniHUD, ...) and Inventory Profiles Next: listed, editable, and saved in profiles.
- Layered conflict detection: red, yellow or no mark depending on when the keys are in use, updated live, across mods; when a mod's key counts as in use can be set per key.
- Ctrl / Shift / Alt + key combinations, in this mod's Key Binds screen and in the vanilla one.
- Numpad keys are named "Num 5", "Num +", "Num Enter".
- Automatic switching per world or server with exact address, host, wildcard, `singleplayer`, `lan`, `realms` and `*` rules; all rules on one screen; optional return to a default profile when leaving.
- Configure button in Mod Menu (optional).
- Profiles and the "open" key of the original KeyBindProfiles are carried over on the first start.
- English and Simplified Chinese.

### 中文

首个公开版本。

- 键位档案：保存、应用、重命名、编辑、删除；每个档案是 `config/keybindprofilesplus/` 下的 JSON 文件。
- 自选档案保存的内容：单个键位、其他模组的热键，以及视场角、灵敏度、音量等游戏设置。
- 应用档案前预览每一项改动，可勾选“下次不再询问”。
- 档案热键（一个或两个键），切换后快捷栏上方有简短提示；只在游戏中生效。
- 启动游戏时重新应用上次应用的档案。
- 并排对比两个档案，或档案与当前设置，带“只看差异”。
- 分享码（`KBP1-...`）：把档案复制成一行文字，导入时先预览；损坏的分享码会说明原因。
- 新的按键绑定界面替代原版界面（可关闭）：标注每个键的来源模组，支持搜索、按来源筛选、分组、悬停详情，可重置单个或全部按键。
- Meteor Client（模块、模块设置里的按键、宏、插件模块）、MaLiLib 系模组（Litematica、Tweakeroo、MiniHUD……）和 Inventory Profiles Next 的热键：列出、可修改、可存进档案。
- 分层冲突检测：根据按键的生效场合标红、标黄或不标，实时更新，跨模组检测；可以逐个设定模组按键的生效场合。
- Ctrl / Shift / Alt + 键的组合键，本模组的按键绑定界面和原版界面都能录入。
- 小键盘按键显示为“小键盘 5”“小键盘 +”等。
- 按世界 / 服务器自动切换，支持完整地址、主机名、通配符、`singleplayer`、`lan`、`realms` 和 `*` 规则；所有规则集中在一个界面；可选在离开时切回默认档案。
- Mod Menu 配置按钮（可选）。
- 第一次启动时沿用原版 KeyBindProfiles 的档案和打开界面的按键。
- 英文和简体中文。
