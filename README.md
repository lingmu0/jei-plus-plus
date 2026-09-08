# JEI++

JEI++ 是一个仅客户端运行的 JEI 附属模组，为 JEI 增加配方目录、配方树、制作助手、流体支持、存储网络库存读取、创造物品分类栏和可折叠物品分组。它不添加方块、物品、配方或其他游戏内容。

## 中文说明

### 配方目录与导航

- 当 JEI 配方槽包含多个等价候选物品时，点击槽位可打开分页目录查看全部候选。
- 目录中的候选可以继续查看配方、用途和标签信息；标签信息不会被当作配方页。
- 顶部左右箭头与页码区域的滚轮执行上一页/下一页整页操作。
- 配方名称行的滚轮切换前后配方页，配方分类图标行的滚轮切换前后配方分类。

### 配方书签与默认配方

- 真实 JEI 配方提供配方树按钮和默认配方按钮，按钮只绘制图标，不渲染额外的物品贴图。
- 查看书签配方时仍显示该物品的全部匹配配方和用途；收藏的配方所在分类和配方会被优先显示，而不会隐藏其他配方。
- 启用 JEI 自身的书签优先排序，并且 `preferRecipeBookmarkOnOutput=true` 时，收藏输出槽会收藏对应配方；输入槽始终收藏物品。
- 关闭 `preferRecipeBookmarkOnOutput` 后，收藏输出槽只收藏产物物品。`hideRecipeBookmarkButton` 可以独立控制是否隐藏 JEI 原生的添加到书签按钮。
- 多输出配方按照鼠标指向的具体输出物品确定收藏目标，不会固定使用第一个输出。
- 默认配方按输出物品保存，支持多输出配方的部分选择，保存在 `config/jei_plus_plus/recipe_defaults.json`。
- 内置默认选择会避开原木/去皮原木到木板以及铁粒到铁锭等不适合作为通用默认的配方；这些配方仍可手动设为默认。

### 配方树与制作助手

- 提供类似 EMI 的配方树视图，显示节点、连线、配方分类、输入、输出和产物数量。
- 节点可以展开、折叠、替换配方、选择具体输出，并在配方树内打开候选材料窗口固定某个输入；右键可解除固定。
- 多个等价输入槽会合并显示。没有固定候选时，所有候选都会参与库存数量、背包高亮和 JEI 配方转移。
- 候选选择优先使用背包或存储网络中已有的物品，并递归查找可制作的材料链，例如从云杉原木继续查找云杉木板；不会按库存评分擅自替换已经选择的配方。
- 默认配方、配方书签和物品书签都会参与配方树构建，收藏的中间产物也可以成为候选。
- 制作模式支持拖动、缩放、自动适配完整视图、重新居中、目标份数、批量数量、循环保护、深度/节点限制、总耗材、副产物和剩余材料统计。
- 最终产物、总耗材与副产物沿同一条竖直中心线布局；最终产物、中间产物、基础材料以及缺少/已有状态使用不同颜色区分。
- 制作模式会把最终产物、中间产物和基础材料分行加入 JEI 书签，即使库存中已经有这些物品也会继续显示。数量实时读取背包和存储网络库存。
- 普通左键只转移当前步骤的直接材料，不进行递归，也不会因为缺少直接材料而强行打开配方。启用 `automaticCraftingEnabled` 后，Ctrl+左键只递归处理缺少的步骤。
- 即时工作方块会在可行时自动取出中间产物；有加工时间的机器只放入材料。
- 配方节点工作方块右下角的 `+` 可执行 JEI 风格的当前配方转移，Shift+点击转移完整数量。转移后会关闭配方树和其他 JEI 配方覆盖层并返回工作方块界面。
- JEI 作弊模式下，配方树书签对最终产物和中间产物不拦截 JEI 原生拿取一个/一组物品的操作。
- 总配方树按钮只在 JEI 相关界面和配方树界面显示：左键打开或返回，右键清除；按 E 或 Esc 也可以返回。
- 当前制作中的配方树保存到 `config/jei_plus_plus/recipe_tree_session.json`，可跨存档重新进入、游戏重启和 JEI 重载恢复；主动切换或清除配方树时才会删除保存状态。

### 流体配方

- 配方树和书签直接显示流体，不把流体统一替换成桶。
- 配方明确要求某种桶时保持桶输入；配方要求流体时，流体和可用容器都可作为候选，并可通过 `+` 固定具体候选。
- 支持流体输入、流体输出、多输出流体选择、流体能力槽位、流体桶等容器转移，以及流体存储网络中的数量检测。
- 1000 mB 等于 1 B。不足 1000 mB 时显示 mB，达到 1000 mB 后按一位小数显示 B；已有数量显示实际库存量，不会被需求上限截断。

### 存储网络集成

JEI++ 通过可选的客户端反射集成读取以下网络中的物品和流体，不要求这些模组成为硬依赖：

- AE2（包括不同版本的客户端终端路径）；
- Refined Storage RS1/RS2；
- 超越维度；
- 集成动力/集成终端。

网络库存可参与配方树计算、候选选择、数量显示、背包/终端高亮和 JEI 配方转移。匹配的网络物品和流体会优先显示在终端列表前方，这个排序是 JEI++ 的高亮排序，不改变终端自身的分类排序。

自动转移优先使用终端提供的客户端接口、官方数据包或通用容器点击协议；兼容的终端可以把材料从网络取入工作方块，并在背包空间不足时把产物放回网络。缺少网络模组、API 变化或终端类型不匹配时，集成会安全跳过，不影响 JEI++ 的其他功能。

### 创造物品分类栏

- 在 JEI 物品列表上方显示全部物品和创造模式分类。
- 分类栏提供与物品槽等大的左右翻页按钮、整页滚轮翻页和居中的页码覆盖层。
- 页码不占用普通物品槽位；分类栏、物品贴图、数量和页码覆盖层的层级经过调整，数字会显示在物品贴图上方。

### 物品分组与折叠

- 内置羊毛、地毯、混凝土、陶瓦、玻璃、蜡烛、床、旗帜、潜影盒、木板、原木、台阶、楼梯、墙、门、活板门、按钮、压力板、矿石、锭、粒、工具、船、苗木、种子、花、树叶、铁轨、告示牌等分组。
- 同一注册名但 NBT/组件不同的物品可以折叠，适用于药水、附魔书等变体。
- 分组可以展开并重新折叠；数量和页码覆盖层始终绘制在物品贴图上方。
- 支持物品列表、Tag 和正则表达式 JSON 分组。分组可设置优先级、翻译键或直接名称、排除项和启用状态。
- 每个默认分组都有独立开关，`mixNamespaceGroups` 控制默认分组是否混合不同 Mod 命名空间。
- 检测到 JEI Tag Groups、JEI Groups 或 Collapsible Groups 等外部分组模组时，JEI++ 会自动停用自己的物品变换，避免重复处理。

### 性能与兼容性

- 配方候选、布局、背包/网络库存快照和流体访问器使用缓存，并按游戏刻和修订号进行刷新，避免每帧重复扫描。
- 递归候选搜索具备深度、访问数量和循环保护；递归只用于配方树和默认配方相关流程。
- 兼容多个 JEI 配方布局、书签、物品列表和渲染路径，并分别提供 Minecraft 1.20.1 Forge 与 1.21.1 NeoForge 实现。
- JEI 重载、存档切换、终端切换、可选模组缺失或 API 变化时会安全清理和恢复运行时缓存。

## 配置

客户端配置文件：`config/jei_plus_plus-client.toml`

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `recipeTreeEnabled` | `true` | 配方树与制作助手总开关 |
| `automaticCraftingEnabled` | `true` | 允许配方树书签 Ctrl+左键递归转移缺少的步骤 |
| `preferRecipeBookmarkOnOutput` | `true` | 在 JEI 书签优先排序开启时，收藏输出槽的对应配方 |
| `hideRecipeBookmarkButton` | `true` | 隐藏 JEI 原生添加到书签按钮 |
| `creativeTabBarEnabled` | `true` | 显示创造物品分类栏 |
| `stackGroupingEnabled` | `true` | 物品分组总开关 |
| `nbtGroupingEnabled` | `true` | 折叠同一注册名的不同 NBT/组件 |
| `tagGroupingEnabled` | `true` | 启用 Tag 分组 |
| `jsonGroupingEnabled` | `true` | 启用 JSON 自定义分组 |
| `mixNamespaceGroups` | `true` | 默认分组是否混合不同 Mod 命名空间 |
| `defaultGroups.<分组名>` | `true` | 单独启用或禁用一个内置分组 |

默认配方文件：`config/jei_plus_plus/recipe_defaults.json`

配方树会话文件：`config/jei_plus_plus/recipe_tree_session.json`

JSON 分组目录：`config/jei_plus_plus/stack_groups/*.json`

JSON 分组示例：

```json
{
  "groups": [
    {
      "id": "mypack:shiny_things",
      "type": "jei_plus_plus:group",
      "name": "mypack.group.shiny_things",
      "enabled": true,
      "priority": 10,
      "contents": [
        "minecraft:diamond",
        "minecraft:emerald",
        "#c:glass_blocks"
      ],
      "exclusions": ["minecraft:purple_stained_glass"]
    },
    {
      "id": "mypack:ores",
      "type": "jei_plus_plus:regex",
      "regex": "minecraft:.*_ore",
      "priority": 5
    }
  ]
}
```

每个文件也仍支持旧的单对象格式；多个分组可以放在顶层数组中，或放在对象的 `groups` 数组中。支持的 JSON 类型：`jei_plus_plus:group`（物品 ID 或 Tag）、`jei_plus_plus:tag`（Tag 成员）和 `jei_plus_plus:regex`（按完整 `namespace:path` 注册名匹配）。`name` 可以是翻译键或直接显示文本，`priority` 越大越优先。

## 版本与依赖

| 游戏版本 | 加载器 | Java | 当前开发依赖 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.22 | 17 | JEI 15.58.0.209 |
| 1.21.1 | NeoForge 21.1.238 | 21 | JEI 19.51.0.418 |

1.20.1 的声明兼容范围从 JEI 15.19.5.99 起，1.21.1 的声明兼容范围从 JEI 19.27.0 起；表中版本是当前开发依赖。

JEI 是可选的客户端依赖；服务端不需要安装 JEI++。模组本身不提供服务端游戏逻辑，若被加载到服务端也不会启用这些客户端功能。

## English

JEI++ is a client-only JEI addon. It adds no blocks, items, recipes, or other gameplay content.

### Recipe directories and navigation

- Recipe slots with multiple equivalent ingredients open a paged directory containing every candidate.
- Directory entries can be used to inspect recipes, usages, and tag information; tag pages are not treated as recipes.
- The top arrow/page-number band scrolls by complete pages. The recipe-title row changes recipe pages, and the category-icon row changes recipe categories.

### Recipe bookmarks and defaults

- Real JEI recipes expose recipe-tree and default-recipe buttons that render only their icons.
- Opening a bookmarked recipe keeps every matching recipe and usage visible while putting the bookmarked recipe and its category first.
- When JEI's own bookmark-priority mode and `preferRecipeBookmarkOnOutput=true` are both enabled, bookmarking an output slot bookmarks the recipe. Input slots always bookmark the item.
- `hideRecipeBookmarkButton` independently controls whether JEI's native add-to-bookmark button is hidden.
- Multi-output recipes use the output under the cursor instead of always selecting the first output.
- Default recipes are stored per output, including partial choices for multi-output recipes, in `config/jei_plus_plus/recipe_defaults.json`.
- Generic defaults skip log/stripped-log to planks and nugget to ingot recipes; either recipe can still be selected manually.

### Recipe Tree and Crafting Assistant

- An EMI-style tree view shows recipe nodes, connectors, categories, inputs, outputs, and quantities.
- Nodes can be expanded, collapsed, assigned another recipe, assigned a concrete output, or locked to a concrete input from an in-tree picker. Right-click unlocks the input.
- Equivalent inputs are merged. When no candidate is locked, every candidate contributes to inventory counts, highlighting, and JEI transfer.
- Candidate selection prefers inventory or storage-network entries and recursively searches craftable dependency chains without replacing the selected recipe by an inventory score.
- Default recipes, recipe bookmarks, and item bookmarks participate in tree construction, including bookmarked intermediate products.
- Crafting mode supports panning, zooming, fit-to-view, recentering, target quantities, batches, cycle protection, depth/node limits, total costs, by-products, and leftovers.
- Final products, total costs, and by-products share a vertical center line; final, intermediate, base, missing, and owned states use different colors.
- Tree bookmarks are separated into final products, intermediates, and base materials. They remain visible even when already owned, and their quantities are refreshed from live inventory and network snapshots.
- Plain left-click transfers only the current step's direct inputs. With `automaticCraftingEnabled`, Ctrl+left-click recursively transfers missing steps only.
- Create's sequenced-assembly recipes include the additional item and fluid inputs for every loop when calculating tree materials.
- Compatible instant workstations take intermediate outputs automatically; timed machines receive their inputs without pretending that processing is complete.
- The lower-right `+` on a recipe node performs a JEI-style transfer for that recipe; Shift+click requests the full amount. The tree and other JEI recipe overlays close and the work-block screen is shown afterward.
- In JEI cheat mode, final and intermediate tree bookmarks do not consume JEI's native one-item/stack cheat clicks.
- The global tree button is shown only on JEI-related screens and the tree screen. Left-click opens or returns, right-click clears, and E/Escape returns.
- The active tree is saved to `config/jei_plus_plus/recipe_tree_session.json` and can be restored after changing worlds, restarting the game, or reloading JEI. Explicitly switching or clearing the tree removes the saved state.

### Fluids

- Fluids remain fluids in the tree and bookmarks instead of being rendered as buckets.
- A recipe that explicitly requires a bucket remains bucket-based. A recipe that requires a fluid can use the fluid or a compatible container as candidates, with the `+` button used to lock one.
- Fluid inputs, outputs, multi-output selection, fluid-capability slots, filled containers, and network fluid quantities are supported.
- 1000 mB equals 1 B. Amounts below 1000 mB use mB; amounts at or above 1000 mB use B with one decimal place. Displayed availability is the actual amount, not a value clamped to the requirement.

### Storage-network integrations

Optional reflective client integrations can read item and fluid storage from AE2, Refined Storage RS1/RS2, Beyond Dimensions, and Integrated Dynamics/Integrated Terminals without hard dependencies.

Network contents participate in tree planning, candidate selection, counts, highlighting, and JEI transfer. Matching network entries are placed before ordinary entries in supported terminal views. Shared snapshots, revisions, and on-demand scans avoid probing AE2 again while an RS, Beyond Dimensions, or Integrated Terminals screen is active.

Transfers prefer the terminal's client API, official packet path, or the generic container-click path. Compatible terminals can supply recipe inputs from the network and deposit outputs when the player's inventory is full. Missing mods, changed APIs, or unsupported menus are skipped safely.

### Creative item tabs

- All-items and creative-mode categories are shown above JEI's ingredient list.
- Equal-sized left/right buttons, complete-page wheel scrolling, and a centered page overlay control the category pages.
- Page numbers do not consume ingredient slots, and count/page overlays render above item textures.

### Expandable item groups

- Built-in groups cover wool, carpets, concrete, terracotta, glass, candles, beds, banners, shulker boxes, planks, logs, slabs, stairs, walls, doors, tools, ores, ingots, nuggets, boats, plants, rails, signs, and related families.
- NBT/component variants of one registered item can be collapsed, including potion and enchanted-book variants.
- Groups can be expanded and collapsed repeatedly. Item-list, tag, and regular-expression JSON groups support priorities, names, exclusions, and enable switches.
- Every default group has its own switch, and `mixNamespaceGroups` controls namespace mixing for default groups.
- When JEI Tag Groups, JEI Groups, Collapsible Groups, or a compatible grouping addon is present, JEI++ disables its own ingredient transformation to avoid double grouping.

### Performance and compatibility

Recipe candidates, layouts, inventory/network snapshots, and fluid accessors are cached and refreshed by game ticks and revisions. Recursive searches have depth, visit, and cycle limits and are used only for tree/default-recipe workflows.

The addon supports multiple JEI layout, bookmark, ingredient-list, and renderer paths on both Minecraft 1.20.1 Forge and 1.21.1 NeoForge. JEI reloads, world changes, terminal changes, missing optional mods, and API changes are handled without retaining stale runtime objects.

### Versions and dependencies

| Minecraft | Loader | Java | Current development dependency |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.22 | 17 | JEI 15.58.0.209 |
| 1.21.1 | NeoForge 21.1.238 | 21 | JEI 19.51.0.418 |

The declared compatibility floor is JEI 15.19.5.99 for 1.20.1 and JEI 19.27.0 for 1.21.1; the table lists the current development dependencies.

JEI++ is client-only and JEI is an optional client dependency. The server does not need to install JEI++.
