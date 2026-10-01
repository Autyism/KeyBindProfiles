# KeyBind Profiles+

A client-side Fabric mod for Minecraft Java **1.21.11** that manages your key bindings in one place: save them as named profiles, switch between profiles instantly, and (work in progress) compare profiles, see where every key binding comes from, and get smarter conflict warnings.

Based on [KeyBindProfiles](https://github.com/imsawiq/KeyBindProfiles) by sawiq_ (MIT).

## Features

- Save the current key bindings as a named profile; apply, rename or delete profiles.
- Switch profiles with a hotkey, with a short on-screen notice.
- Switch profiles automatically when joining a given server.
- The last applied profile is restored when the game starts.
- A "Manage Profiles" button in Options → Controls → Key Binds, plus a key to open the manager anywhere (default `O`).
- Profiles from the original KeyBindProfiles mod are picked up automatically on first start.

Planned for the first release: profile comparison, key binding overview with source mod labels, selective saving of other options, smarter conflict detection, read-only display of Meteor / malilib hotkeys, modifier combos and share codes.

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

Minecraft Java **1.21.11** 的纯客户端 Fabric 模组，把按键绑定集中管理：把当前键位存成有名字的档案，随时一键切换；对比档案、标注每个键位来自哪个模组、更聪明的冲突提示等功能正在开发中。

基于 sawiq_ 的 [KeyBindProfiles](https://github.com/imsawiq/KeyBindProfiles)（MIT 协议）。

## 功能

- 把当前键位保存为档案；应用、重命名、删除档案。
- 用热键切换档案，屏幕上会有简短提示。
- 进入指定服务器时自动切换档案。
- 启动游戏时自动恢复上次应用的档案。
- 在"选项 → 控制 → 按键绑定"里加了"管理档案"按钮，也可以用按键随时打开（默认 `O`）。
- 第一次启动时会自动读取原版 KeyBindProfiles 模组留下的档案。

首个正式版计划加入：档案对比、带来源模组标注的键位总览、可选保存其他游戏设置、更聪明的冲突检测、只读显示 Meteor / malilib 的热键、组合键、分享码。

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
