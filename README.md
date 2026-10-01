# KeyBind Profiles+

A client-side Fabric mod for Minecraft Java **1.21.11** that manages your key bindings in one place: save them as named profiles, switch between profiles instantly, compare profiles side by side, see where every key binding comes from, and get conflict warnings that know when a key is actually in use.

Based on [KeyBindProfiles](https://github.com/imsawiq/KeyBindProfiles) by sawiq_ (MIT).

## Features

- **Profiles**: save the current key bindings as a named profile; apply, rename or delete profiles. The last applied profile is restored when the game starts. Profiles are readable JSON files in `config/keybindprofilesplus/`.
- **Choose what a profile saves**: a tree with a check box for every key binding and every game setting (FOV, sensitivity, volumes, ...). Anything not ticked is left alone when the profile is applied.
- **Apply confirmation**: before applying, a list shows what will change from what to what. "Don't ask again" turns it off; the settings screen turns it back on.
- **Compare**: two profiles (or a profile and your current settings) side by side, differences highlighted, with an "only differences" switch. Reachable from the profile list and from the vanilla Key Binds screen.
- **All key bindings in one list**, each labelled with the mod it comes from; filter or group by source.
- **Hotkeys of Meteor Client and malilib mods (Litematica, MiniHUD, Tweakeroo, ...)** are shown in the same list, read-only. Their files are only ever read.
- **Smarter conflict detection**, live while you rebind: red for a real conflict, yellow for one that only matters in some situations, nothing for keys that can never clash (`G` and `F3 + G`, an inventory-only key and an in-game key).
- **Modifier combinations**: bind `Ctrl + X`, `Shift + X` or `Alt + X` on the vanilla Key Binds screen. Plain `X` keeps working for whatever else is bound to it.
- **Readable key names**: numpad keys read "Num 5", "Num +", "Num Enter".
- **Switch profiles with a hotkey**, with a short on-screen notice in your game language.
- **Switch automatically per world or server**: rules with exact addresses, host names, wildcards, `singleplayer`, `lan`, `realms` or `*`; optionally go back to a default profile when you leave.
- **Share codes**: turn a profile into one line of text and import it somewhere else.
- A "Manage Profiles" button in Options → Controls → Key Binds, plus a key to open the manager anywhere (default `O`).
- Profiles from the original KeyBindProfiles mod are picked up automatically on first start.
- English and Simplified Chinese.

## Requirements

- Minecraft 1.21.11, Fabric Loader 0.17.3 or newer, Fabric API.
- Client-side only. It sends no packets and does not change gameplay.

## Building

```
.\gradlew build
```

Needs JDK 21. The jar ends up in `build/libs/`.

## License

GPL-3.0 (see `LICENSE`). The upstream code this mod started from is MIT licensed by sawiq_; that notice is kept in `LICENSE-upstream-MIT`.

---

# KeyBind Profiles+（中文）

Minecraft Java **1.21.11** 的纯客户端 Fabric 模组，把按键绑定集中管理：把当前键位存成有名字的档案，随时一键切换；并排对比档案，标注每个键位来自哪个模组，冲突提示会判断这个键到底在什么场合生效。

基于 sawiq_ 的 [KeyBindProfiles](https://github.com/imsawiq/KeyBindProfiles)（MIT 协议）。

## 功能

- **档案**：把当前键位保存为档案；应用、重命名、删除档案。启动游戏时自动恢复上次应用的档案。档案是 `config/keybindprofilesplus/` 下可以直接看懂的 JSON 文件。
- **自选保存内容**：一棵可以打勾的树，每个键位、每项游戏设置（视场角、灵敏度、音量……）都能单独勾选。没勾的项目在应用档案时不会被改动。
- **应用前确认**：应用之前先列出每一项会从什么改成什么。可以勾"下次不再询问"，也能在设置界面里重新打开。
- **对比**：两个档案（或档案和当前设置）并排显示，不同的行高亮，有"只看差异"开关。档案列表和原版按键绑定界面都有入口。
- **键位全景**：所有键位列在一起，每行标注来自哪个模组，可以按来源筛选或分组。
- **Meteor Client 和 malilib 系模组（Litematica、MiniHUD、Tweakeroo……）的热键**也显示在同一个列表里，只读。模组只读取它们的文件，绝不写入。
- **更聪明的冲突检测**，改键时实时显示：红色是真冲突，黄色是只在某些场合才冲突，永远不会撞上的不标记（`G` 和 `F3 + G`、只在物品栏界面生效的键和游戏中的键）。
- **组合键**：在原版按键绑定界面可以绑 `Ctrl + X`、`Shift + X`、`Alt + X`。单按 `X` 时，绑在 `X` 上的其他功能照常工作。
- **看得懂的键名**：小键盘显示成"小键盘 5""小键盘 +""小键盘 Enter"。
- **热键切换档案**，屏幕上有简短提示，跟随游戏语言。
- **按世界 / 服务器自动切换**：规则支持完整地址、主机名、通配符、`singleplayer`、`lan`、`realms`、`*`；可选在退出时切回默认档案。
- **分享码**：把档案变成一行文字，在别处导入。
- 在"选项 → 控制 → 按键绑定"里加了"管理档案"按钮，也可以用按键随时打开（默认 `O`）。
- 第一次启动时会自动读取原版 KeyBindProfiles 模组留下的档案。
- 英文和简体中文。

## 运行要求

- Minecraft 1.21.11，Fabric Loader 0.17.3 及以上，Fabric API。
- 纯客户端，不发送任何数据包，不改变游戏玩法。

## 构建

```
.\gradlew build
```

需要 JDK 21，产物在 `build/libs/`。

## 协议

GPL-3.0（见 `LICENSE`）。本模组起步时使用的上游代码由 sawiq_ 以 MIT 协议发布，其声明保留在 `LICENSE-upstream-MIT`。
