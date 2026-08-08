# JEI++

JEI++ 是一个只在客户端运行的 JEI 附属模组，不添加方块、物品或其他游戏内容。

## 中文说明

### 功能

- **多物品配方目录**：配方槽包含多个可选物品时，点击槽位会打开 JEI++ 物品目录；目录按 JEI 配方页分页显示。
- **配方导航滚轮**：
  - 鼠标放在顶部左右箭头和页码数字组成的区域时，向上/向下滚动等同于点击上一页/下一页，翻过完整的 JEI 配方页。
  - 鼠标放在配方页名称/导航行时，可滚动切换前后配方页。
  - 鼠标放在配方分类图标行时，可滚动切换前后配方分类。
- **书签配方改进**：
  - 查看书签中的配方时保留全部匹配配方，并优先显示该书签配方。
  - 查看书签物品的用途时仍然显示完整用途列表。
  - 开启 JEI 的优先书签配方设置后，收藏配方输出槽会收藏对应配方；收藏输入槽仍只收藏物品。
  - 多输出配方会根据鼠标实际指向的输出物品收藏对应配方，不会固定选择第一个输出。
  - 是否在输出槽收藏对应配方由 `preferBookmarkedRecipeOnIngredientBookmark` 控制，默认开启。
- **创造物品分类栏**：在 JEI 物品列表上方显示全部物品和各个创造模式分类；可点击或滚轮切换分类。分类栏会为 JEI 列表预留空间，页码文字和分组数量覆盖层位于物品贴图上方。
- **物品分组折叠**：
  - 内置后缀分组（羊毛、木板、原木、工具、矿石等）可点击展开/重新折叠。
  - 相同物品注册名但 NBT/组件不同的物品会折叠到一起，例如不同药水、附魔书；默认开启。
  - 支持通过 JSON 定义物品列表分组、Tag 分组和正则表达式分组。
  - 默认分组可以逐组关闭；默认分组是否跨 Mod 命名空间混组也可设置，默认跨命名空间混组。

### 配置

配置文件为 `config/jei_plus_plus-client.toml`。主要开关如下：

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `stackGroupingEnabled` | `true` | 总开关，关闭所有物品分组 |
| `nbtGroupingEnabled` | `true` | 折叠相同物品的不同 NBT/组件 |
| `tagGroupingEnabled` | `true` | 启用 JSON Tag 分组 |
| `jsonGroupingEnabled` | `true` | 启用 JSON 自定义分组 |
| `mixNamespaceGroups` | `true` | 允许默认分组混合不同 Mod 命名空间 |
| `defaultGroups.<分组名>` | `true` | 单独启用/禁用一个内置默认分组 |

JSON 分组文件放在 `config/jei_plus_plus/stack_groups/*.json`。文件修改、新增或删除后，JEI++ 会在物品列表刷新时自动重新读取。

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

可用的 JSON 类型：

- `jei_plus_plus:group`：`contents` 中填写物品注册名或 `#namespace:tag`。
- `jei_plus_plus:tag`：填写 `tag` 或 `contents`，将同一个物品 Tag 中的物品折叠。
- `jei_plus_plus:regex`：填写 `regex` 或 `regexes`，按完整物品注册名 `namespace:path` 匹配。

`name` 可以是翻译键（例如 `mypack.group.shiny_things`），也可以直接填写显示文本；`priority` 越大越优先匹配。可以使用同一个默认分组 ID 覆盖或关闭默认分组：

```json
{
  "id": "jei_plus_plus:wool",
  "enabled": false
}
```

内置默认分组包括：`wool`、`carpet`、`concrete`、`concrete_powder`、`terracotta`、`glazed_terracotta`、`stained_glass`、`stained_glass_pane`、`candle`、`bed`、`banner`、`shulker_box`、`planks`、`stripped_logs`、`logs`、`stripped_wood`、`wood`、`slab`、`stairs`、`wall`、`fence_gate`、`fence`、`door`、`trapdoor`、`button`、`pressure_plate`、`glass`、`pane`、`ore`、`raw_material`、`ingot`、`nugget`、`sword`、`pickaxe`、`axe`、`shovel`、`hoe`、`boat`、`sapling`、`seed`、`flower`、`leaves`、`rail`、`hanging_sign`、`sign`。每个名称同时对应 TOML 中的 `defaultGroups.<名称>` 和 `jei_plus_plus.group.default.<名称>` 翻译键。

### 版本

- 分支 `1.20.1`：Minecraft 1.20.1 + Forge，Java 17。
- 分支 `1.21.1`：Minecraft 1.21.1 + NeoForge，Java 21。
- JEI 是可选的客户端依赖；没有 JEI 时不会加载 JEI 客户端功能。

## English

JEI++ is a client-only JEI addon. It does not add blocks, items, recipes, or other gameplay content.

### Features

- **Multi-ingredient recipe directories**: clicking a recipe slot with many alternatives opens a paged JEI++ ingredient directory.
- **Recipe navigation scrolling**:
  - Scrolling over the top band containing the previous/next arrows and page number performs a complete previous/next JEI page action.
  - Scrolling over the recipe title/navigation row changes the previous/next recipe page.
  - Scrolling over the recipe-category icon row changes the previous/next recipe category.
- **Bookmark improvements**:
  - Opening a bookmarked recipe keeps every matching recipe available while putting the bookmarked recipe first.
  - Uses for a bookmarked ingredient still show the full usage list.
  - When JEI's bookmarked-recipe priority is enabled, bookmarking an output slot bookmarks that recipe; input slots still bookmark only the item.
  - For recipes with multiple outputs, the output under the cursor determines which recipe is bookmarked instead of always choosing the first output.
  - `preferBookmarkedRecipeOnIngredientBookmark` controls output-slot recipe bookmarking and defaults to `true`.
- **Creative item tab bar**: shows the all-items entry and vanilla/modded creative tabs above JEI's item list. Tabs can be clicked or scrolled. JEI receives reserved space, and page/count overlays render above item textures.
- **Expandable item groups**:
  - Built-in suffix groups (wool, planks, logs, tools, ores, and more) can be expanded and collapsed.
  - Different NBT/component variants of the same registered item are grouped together, including potion and enchanted-book variants; enabled by default.
  - Item-list, tag, and regular-expression groups can be defined with JSON files.
  - Every built-in group has its own switch, and default groups can either mix or separate Mod namespaces; namespace mixing defaults to enabled.

### Configuration

The configuration file is `config/jei_plus_plus-client.toml`:

| Option | Default | Description |
| --- | --- | --- |
| `stackGroupingEnabled` | `true` | Master switch for item grouping |
| `nbtGroupingEnabled` | `true` | Group different NBT/component variants of one item |
| `tagGroupingEnabled` | `true` | Enable JSON tag groups |
| `jsonGroupingEnabled` | `true` | Enable custom JSON groups |
| `mixNamespaceGroups` | `true` | Mix default groups across Mod namespaces |
| `defaultGroups.<group>` | `true` | Enable or disable one built-in group |

JSON group files are loaded from `config/jei_plus_plus/stack_groups/*.json`. JEI++ rescans the directory when the item list refreshes, so files can be added, changed, or removed without rebuilding the mod.

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

Supported JSON types:

- `jei_plus_plus:group`: `contents` accepts item IDs or `#namespace:tag` entries.
- `jei_plus_plus:tag`: use `tag` or `contents` to group members of an item tag.
- `jei_plus_plus:regex`: use `regex` or `regexes` to match the complete `namespace:path` item ID.

`name` can be a translation key (for example `mypack.group.shiny_things`) or literal display text. Higher `priority` values win when groups overlap. A built-in group can be overridden or disabled with its ID:

```json
{
  "id": "jei_plus_plus:wool",
  "enabled": false
}
```

Built-in group IDs are: `wool`, `carpet`, `concrete`, `concrete_powder`, `terracotta`, `glazed_terracotta`, `stained_glass`, `stained_glass_pane`, `candle`, `bed`, `banner`, `shulker_box`, `planks`, `stripped_logs`, `logs`, `stripped_wood`, `wood`, `slab`, `stairs`, `wall`, `fence_gate`, `fence`, `door`, `trapdoor`, `button`, `pressure_plate`, `glass`, `pane`, `ore`, `raw_material`, `ingot`, `nugget`, `sword`, `pickaxe`, `axe`, `shovel`, `hoe`, `boat`, `sapling`, `seed`, `flower`, `leaves`, `rail`, `hanging_sign`, and `sign`. Each ID maps to `defaultGroups.<id>` in TOML and the `jei_plus_plus.group.default.<id>` translation key.

### Versions

- Branch `1.20.1`: Minecraft 1.20.1 + Forge, Java 17.
- Branch `1.21.1`: Minecraft 1.21.1 + NeoForge, Java 21.
- JEI is an optional client dependency; the JEI client features are not loaded without it.
