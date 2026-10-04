# Changelog

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
