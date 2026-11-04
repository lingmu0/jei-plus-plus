# 更新日志 / Changelog

## 1.0.7 — 2026-11-04

### 中文

- 适配 JEI 19.64 起的新输入系统（`IInputTarget`）：`RecipesGui.mouseScrolled` 已被移除，`BookmarkInputHandler` 也被 `BookmarkCommands` 取代，旧写法在 JEI 19.70 上会直接注入失败导致崩溃。
- 滚轮导航照旧：分类箭头所在区域滚轮切换配方分类，分类页签行的页码条滚轮翻页；JEI 19.64+ 由新的 `scroll` 钩子实现，旧版本仍走 `RecipesGui.mouseScrolled`。
- 书签树按钮、创造物品分类栏、输出槽位「优先配方书签」等与输入相关的功能改由新的输入桥接层实现，语义与旧路径一致。
- JEI 19.57 及更早的版本继续使用原有的 `IUserInputHandler` 路径，行为不变。
- 编译与本地运行使用的 JEI 更新至 19.70.0.469，并补充 Maven Central 仓库（JEI 拆分子模块的传递依赖）。

### English

- Support JEI 19.64+ input targets (`IInputTarget`). `RecipesGui.mouseScrolled` no longer exists and `BookmarkInputHandler` became `BookmarkCommands`, so the previous injections crashed on JEI 19.70.
- Wheel navigation is unchanged: scrolling over the recipe-category arrows cycles categories and scrolling over the category tab page indicator flips its page. JEI 19.64+ uses a new `scroll` hook while older builds keep `RecipesGui.mouseScrolled`.
- The recipe-tree button, creative-tab bar, and the preferred output-slot recipe bookmark now use a new input bridge with the same behavior as before.
- JEI 19.57 and older keep the original `IUserInputHandler` path and are not affected.
- Updated the JEI compile and runtime version to 19.70.0.469 and added Maven Central for the transitive dependencies of JEI's split modules.

## 1.0.6 — 2026-09-27

### 中文

- 创造物品分类栏支持循环翻页，并新增分类选择弹窗：每行四个分类，左键切换，右键置顶/取消置顶；置顶顺序会影响分类显示顺序。
- 新增页码显示开关，以及配方多候选物品目录功能开关。
- 修复分类弹窗被底层物品贴图、高亮轮廓和 tooltip 遮挡的问题；弹窗打开时阻止选择底层物品。
- 打开容器中的物品现在会参与配方树库存计算，并按配方树需求高亮。
- 缓存配方快照和配方状态，减少多候选配方页面渲染时的重复扫描。
- Forge 1.20.1 与 NeoForge 1.21.1 版本号均更新至 1.0.6。

### English

- Creative item tabs now wrap when paging and include a category picker with four entries per row. Left-click selects a category; right-click pins or unpins it, and pin order determines display order.
- Added separate toggles for the page indicator and the multi-candidate recipe ingredient directory.
- Fixed the category picker being obscured by underlying item sprites, highlight outlines, and tooltips. Underlying items can no longer be selected while the picker is open.
- Items in an open container now count toward recipe-tree inventory and are highlighted according to recipe-tree requirements.
- Cached recipe snapshots and recipe status to reduce repeated scans while rendering recipe pages with many candidates.
- Updated both Forge 1.20.1 and NeoForge 1.21.1 builds to version 1.0.6.
