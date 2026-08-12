# JEI++

JEI++ 是一个只在客户端运行的 JEI 附属模组，不添加方块、物品或其他游戏内容。

## 中文说明

### 功能

- **多物品配方目录**：配方槽包含多个候选物品时，点击槽位打开分页目录；目录中的物品可以继续查看配方、用途和标签信息。
- **配方树与制作助手**：
  - 每个真实 JEI 配方提供配方树按钮和默认配方按钮；标签信息页不会被当作配方，按钮只显示对应图标。
  - 默认配方按输出物品保存，支持多输出配方的完整或部分默认状态；个人选择保存到 `config/jei_plus_plus/recipe_defaults.json`。
  - 配方树和书签中的候选输入提示采用 JEI 风格，显示标签名称、候选图标和模组信息；内置默认清单不会选择“原木/去皮原木→木头”或“铁粒→铁锭”，这些配方仍可手动设为默认。
  - 配方树节点可以展开、折叠、选择配方和固定具体输入。多个等价输入槽会合并为一个节点；节点上的 `+` 会在配方树内打开候选材料窗口，选择后所有对应槽位统一使用该物品，右键可恢复候选状态。
  - 未固定输入时，所有候选都会参与库存统计、背包高亮和 JEI 配方转移。候选选择会优先使用背包中的物品，并递归查找可获得的材料链（例如原木→木板），不会按库存评分自动替换已选配方。
  - 制作模式支持拖动、缩放、份数调整、循环保护和副产物统计；最终产物、总耗材与副产物以同一竖直中心线布局。缺少的基础材料和中间产物会分别高亮。
  - 制作模式会把目标、中间产物和基础材料追加到 JEI 书签栏，即使库存已经满足也会继续显示。提示中的已有数量实时读取背包；左键转移当前步骤所需材料，Ctrl+左键递归转移并只处理缺少的步骤，兼容的即时工作方块会自动取出中间产物，有加工时间的机器只放入材料。
  - 全局配方树按钮位于 JEI 书签/历史按钮一行，只在 JEI 界面和配方树界面显示。左键再次点击或按 E/Esc 返回，右键清除当前配方树；没有活动配方树时显示引导页。
- **配方导航滚轮**：
  - 在顶部左右箭头与页码区域滚轮，执行上一页/下一页整页操作。
  - 在配方名称行或配方分类图标行滚轮，切换前后配方页或配方分类。
- **书签配方改进**：查看书签配方时保留全部匹配配方并优先显示书签配方；查看用途时保留完整用途列表。启用 JEI 的优先书签配方设置后，收藏输出槽会收藏对应配方，输入槽仍只收藏物品；多输出配方按鼠标指向的输出物品确定收藏配方。
- **创造物品分类栏**：在 JEI 物品列表上方显示全部物品和创造模式分类，可点击或滚轮切换；列表会预留分类栏空间，页码和数量覆盖层始终绘制在物品贴图上方。
- **物品分组折叠**：内置羊毛、木板、原木、工具、矿石等分组，可展开和重新折叠；相同注册名但 NBT/组件不同的物品（例如药水、附魔书）可以折叠。支持物品列表、Tag 和正则表达式 JSON 分组；每个默认分组可单独开关，也可设置是否混合不同 Mod 命名空间。

### 配置

客户端配置文件为 `config/jei_plus_plus-client.toml`，常用选项如下：

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `stackGroupingEnabled` | `true` | 物品分组总开关 |
| `recipeTreeEnabled` | `true` | 配方树与制作助手总开关 |
| `automaticCraftingEnabled` | `true` | 允许配方树书签 Ctrl+左键自动合成缺少的中间步骤 |
| `nbtGroupingEnabled` | `true` | 折叠相同物品的不同 NBT/组件 |
| `tagGroupingEnabled` | `true` | 启用 Tag 分组 |
| `jsonGroupingEnabled` | `true` | 启用 JSON 自定义分组 |
| `mixNamespaceGroups` | `true` | 默认分组是否混合不同 Mod 命名空间 |
| `defaultGroups.<分组名>` | `true` | 单独启用或禁用一个默认分组 |
| `preferBookmarkedRecipeOnIngredientBookmark` | `true` | 收藏输出槽时优先收藏对应配方 |

默认配方选择保存在 `config/jei_plus_plus/recipe_defaults.json`。JSON 分组文件放在 `config/jei_plus_plus/stack_groups/*.json`，刷新 JEI 物品列表时会重新读取。

示例：

```json
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
}
```

支持的 JSON 类型：`jei_plus_plus:group`（物品 ID 或 Tag）、`jei_plus_plus:tag`（Tag 成员）和 `jei_plus_plus:regex`（按完整 `namespace:path` 注册名匹配）。`name` 可以是翻译键或直接显示的文本，`priority` 越大越优先。

默认分组可通过同 ID 的 JSON 文件覆盖或关闭，例如：

```json
{
  "id": "jei_plus_plus:wool",
  "enabled": false
}
```

内置默认分组包括 `wool`、`carpet`、`concrete`、`concrete_powder`、`terracotta`、`glazed_terracotta`、`stained_glass`、`stained_glass_pane`、`candle`、`bed`、`banner`、`shulker_box`、`planks`、`stripped_logs`、`logs`、`stripped_wood`、`wood`、`slab`、`stairs`、`wall`、`fence_gate`、`fence`、`door`、`trapdoor`、`button`、`pressure_plate`、`glass`、`pane`、`ore`、`raw_material`、`ingot`、`nugget`、`sword`、`pickaxe`、`axe`、`shovel`、`hoe`、`boat`、`sapling`、`seed`、`flower`、`leaves`、`rail`、`hanging_sign` 和 `sign`；每个 ID 都有独立的 TOML 开关。

### 版本

- 分支 `1.20.1`：Minecraft 1.20.1 + Forge，Java 17。
- 分支 `1.21.1`：Minecraft 1.21.1 + NeoForge，Java 21。
- JEI 是可选的客户端依赖；未安装 JEI 时不会加载 JEI 客户端功能。

## English

JEI++ is a client-only JEI addon. It does not add blocks, items, recipes, or other gameplay content.

### Features

- **Multi-ingredient recipe directories**: recipe slots with multiple candidates open a paged directory; entries can be used to inspect recipes, usages, and tag information.
- **Recipe Tree and Crafting Assistant**:
  - Every real JEI recipe has recipe-tree and default-recipe buttons. Tag-information pages are excluded and the buttons render only their icons.
  - Default recipes are stored per output, including partial selections for multi-output recipes, in `config/jei_plus_plus/recipe_defaults.json`.
  - Candidate and bookmark tooltips use JEI-style tag names, candidate icons, and Mod information. The built-in defaults do not select logs/stripped logs → wood or nuggets → ingots; either can still be selected manually.
  - Nodes can be expanded, collapsed, assigned a recipe, or locked to a concrete input. Equivalent input slots are merged; the `+` button opens an in-tree candidate picker and right-click restores the unlocked state.
  - With no locked input, every candidate participates in inventory totals, highlighting, and JEI transfer. Candidate display prefers items already in the inventory and recursively searches craftable material chains (for example logs → planks), without replacing a selected recipe by inventory score.
  - Crafting mode supports panning, zooming, batch counts, cycle protection, and leftover reporting. The final product, total costs, and leftovers share one vertical center line, while missing base materials and intermediate products are highlighted separately.
  - Targets, intermediates, and base costs are shown in JEI bookmarks even when already owned. Counts are refreshed from the live inventory. Left-click transfers the current step; Ctrl+left-click recursively transfers only missing steps, taking intermediate outputs from compatible instant stations and placing only inputs into timed machines.
  - The global tree button sits beside JEI bookmarks/history and appears only in JEI screens and the tree screen. Left-clicking it again or pressing E/Escape returns; right-clicking clears the active tree. A welcome page is shown when no tree is active.
- **Recipe navigation scrolling**: the top arrow/page-number band performs previous/next full-page actions; the recipe-title row and category-icon row switch recipe pages and categories respectively.
- **Bookmark recipe behavior**: bookmarked recipes keep all matching recipes while putting the bookmarked one first, and usages remain complete. With JEI bookmark-recipe priority enabled, output slots bookmark their recipe while input slots bookmark only the item; multi-output recipes use the output under the cursor.
- **Creative item tab bar**: all-items and creative categories appear above JEI's item list, with click and wheel navigation. JEI reserves the tab space, and page/count overlays render above item textures.
- **Expandable item groups**: built-in wool, planks, logs, tools, ores, and similar groups can be expanded or collapsed. NBT/component variants of one registered item can be grouped, including potion and enchanted-book variants. Item-list, tag, and regular-expression groups are configurable through JSON; every default group has its own switch and namespace mixing is configurable.
  - JEI++ automatically disables its own item grouping when JEI Tag Groups, JEI Groups, or Collapsible Groups is loaded, preventing two addons from transforming the JEI ingredient list at the same time.

### Configuration

The client configuration file is `config/jei_plus_plus-client.toml`:

| Option | Default | Description |
| --- | --- | --- |
| `stackGroupingEnabled` | `true` | Master item-grouping switch |
| `recipeTreeEnabled` | `true` | Recipe Tree and Crafting Assistant switch |
| `automaticCraftingEnabled` | `true` | Allow Ctrl-left-click in recipe-tree bookmarks to auto-craft missing steps |
| `nbtGroupingEnabled` | `true` | Group NBT/component variants |
| `tagGroupingEnabled` | `true` | Enable tag groups |
| `jsonGroupingEnabled` | `true` | Enable custom JSON groups |
| `mixNamespaceGroups` | `true` | Mix default groups across Mod namespaces |
| `defaultGroups.<group>` | `true` | Enable or disable one default group |
| `preferBookmarkedRecipeOnIngredientBookmark` | `true` | Prefer the recipe when bookmarking an output |

Default-recipe choices are saved in `config/jei_plus_plus/recipe_defaults.json`. JSON group files are loaded from `config/jei_plus_plus/stack_groups/*.json` whenever JEI refreshes its item list.

Supported JSON types are `jei_plus_plus:group` (item IDs or tags), `jei_plus_plus:tag` (tag members), and `jei_plus_plus:regex` (full `namespace:path` item-ID matching). `name` accepts a translation key or literal text, and higher `priority` values win when groups overlap.

Built-in group IDs include `wool`, `carpet`, `concrete`, `concrete_powder`, `terracotta`, `glazed_terracotta`, `stained_glass`, `stained_glass_pane`, `candle`, `bed`, `banner`, `shulker_box`, `planks`, `stripped_logs`, `logs`, `stripped_wood`, `wood`, `slab`, `stairs`, `wall`, `fence_gate`, `fence`, `door`, `trapdoor`, `button`, `pressure_plate`, `glass`, `pane`, `ore`, `raw_material`, `ingot`, `nugget`, `sword`, `pickaxe`, `axe`, `shovel`, `hoe`, `boat`, `sapling`, `seed`, `flower`, `leaves`, `rail`, `hanging_sign`, and `sign`; each ID has an independent TOML switch.

### Versions

- Branch `1.20.1`: Minecraft 1.20.1 + Forge, Java 17.
- Branch `1.21.1`: Minecraft 1.21.1 + NeoForge, Java 21.
- Development is verified with JEI `15.20.0.105` through `15.21.0.148` on 1.20.1 and JEI `19.27.0.336` through `19.44.0.401` on 1.21.1.
- JEI is an optional client dependency; JEI client features are skipped when JEI is absent.
