# 更新日志 / Changelog

## 1.0.8 — 2026-10-25

### 中文

- 额外适配 JEI 15.74.0.229（1.20.1）、19.70.0.458（1.21.1）与 29.47.0.111（26.1.2）的新版输入交互、分类导航和书签接口，保留旧版接口分支。
- 修复多级配方将同一输出槽的互斥候选当作同时产出，导致其他分支复用不存在的副产物而少算材料的问题；真正的多输出槽仍保留副产物。
- 快照使用无焦点的标准布局，并区分不同配方对象，避免界面打开顺序影响缓存内容。
- 修复旧节点需求数量残留和库存统计读取已扣除工作副本的问题；暂停界面内的库存刷新不再依赖游戏时间推进。
- 新增无图形界面的材料计算、JEI 输入桥和注入目标/直接调用兼容性检查。不将构建检查等同于游戏内整合包验证。

### English

- Added compatibility with JEI 15.74.0.229 (1.20.1), 19.70.0.458 (1.21.1), and 29.47.0.111 (26.1.2), including the new input interaction, category navigation, and bookmark paths, while retaining legacy API branches.
- Fixed nested recipes treating mutually exclusive alternatives in one output slot as simultaneous products, which could undercount materials in sibling branches. Genuine separate output slots still contribute byproducts.
- Cache canonical, unfocused recipe layouts and distinguish recipe objects so opening order does not contaminate snapshots.
- Clear stale node demand, retain the original inventory for stock totals, and refresh inventory in paused interfaces without relying on world ticks.
- Added headless material-planning, JEI input bridge, injection-target, and direct-call compatibility checks. Build checks are not an in-game modpack test.

## 1.0.7-1.21.1-fabric — 2026-10-11

### 中文

- 新增 Minecraft 1.21.1 的 Fabric 移植版本，保留对应分支的配方树、目录、分组、书签、分类弹窗、高亮与制作助手。
- 使用 Fabric 网络和可选服务端 AE2 样板通道；服务器未安装 JEI++ 时，其他客户端功能仍可用。
- 改用可热重载的原生 JSON 配置；分组、默认配方与配方树的存储位置保持不变。
- 适配 Fabric 流体容器、JEI 流体类型及 AE2 库存的流体计量单位，保留 JEI 新旧签名选择逻辑。
- 添加无游戏启动的配置回归、发布 JAR、客户端/服务端隔离与 JEI Mixin 目标检查。

### English

- Added the Minecraft 1.21.1 Fabric port, retaining recipe trees, ingredient directories, groups, bookmarks, the category picker, highlighting and crafting assistance.
- Switched to Fabric networking with an optional server-side AE2 pattern channel; other client features remain usable without server-side JEI++.
- Added native, live-reloading JSON settings; existing group, default-recipe and tree-session paths are unchanged.
- Adapted Fabric fluid containers, JEI fluid ingredients and AE2 fluid inventory units while retaining version-selected JEI signatures.
- Added non-launch config regression tests and release-JAR, sidedness and required JEI Mixin target checks. Optional mod integrations require a compatible Fabric release.

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
