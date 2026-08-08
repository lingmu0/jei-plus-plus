# JEI++

JEI++ æ¯ä¸ä¸ªåªå¨å®¢æ·ç«¯è¿è¡ç JEI éå±æ¨¡ç»ï¼ä¸æ·»å æ¹åãç©åæå¶ä»æ¸¸æåå®¹ã

## ä¸­æè¯´æ

### åè½

- **å¤ç©åéæ¹ç®å½**ï¼éæ¹æ§½åå«å¤ä¸ªå¯éç©åæ¶ï¼ç¹å»æ§½ä½ä¼æå¼ JEI++ ç©åç®å½ï¼ç®å½æ JEI éæ¹é¡µåé¡µæ¾ç¤ºã
- **éæ¹å¯¼èªæ»è½®**ï¼
  - é¼ æ æ¾å¨é¡¶é¨å·¦å³ç®­å¤´åé¡µç æ°å­ç»æçåºåæ¶ï¼åä¸/åä¸æ»å¨ç­åäºç¹å»ä¸ä¸é¡µ/ä¸ä¸é¡µï¼ç¿»è¿å®æ´ç JEI éæ¹é¡µã
  - é¼ æ æ¾å¨éæ¹é¡µåç§°/å¯¼èªè¡æ¶ï¼å¯æ»å¨åæ¢ååéæ¹é¡µã
  - é¼ æ æ¾å¨éæ¹åç±»å¾æ è¡æ¶ï¼å¯æ»å¨åæ¢ååéæ¹åç±»ã
- **ä¹¦ç­¾éæ¹æ¹è¿**ï¼
  - æ¥çä¹¦ç­¾ä¸­çéæ¹æ¶ä¿çå¨é¨å¹ééæ¹ï¼å¹¶ä¼åæ¾ç¤ºè¯¥ä¹¦ç­¾éæ¹ã
  - æ¥çä¹¦ç­¾ç©åçç¨éæ¶ä»ç¶æ¾ç¤ºå®æ´ç¨éåè¡¨ã
  - å¼å¯ JEI çä¼åä¹¦ç­¾éæ¹è®¾ç½®åï¼æ¶èéæ¹è¾åºæ§½ä¼æ¶èå¯¹åºéæ¹ï¼æ¶èè¾å¥æ§½ä»åªæ¶èç©åã
  - å¤è¾åºéæ¹ä¼æ ¹æ®é¼ æ å®éæåçè¾åºç©åæ¶èå¯¹åºéæ¹ï¼ä¸ä¼åºå®éæ©ç¬¬ä¸ä¸ªè¾åºã
  - æ¯å¦å¨è¾åºæ§½æ¶èå¯¹åºéæ¹ç± `preferBookmarkedRecipeOnIngredientBookmark` æ§å¶ï¼é»è®¤å¼å¯ã
- **åé ç©ååç±»æ **ï¼å¨ JEI ç©ååè¡¨ä¸æ¹æ¾ç¤ºå¨é¨ç©åååä¸ªåé æ¨¡å¼åç±»ï¼å¯ç¹å»ææ»è½®åæ¢åç±»ãåç±»æ ä¼ä¸º JEI åè¡¨é¢çç©ºé´ï¼é¡µç æå­ååç»æ°éè¦çå±ä½äºç©åè´´å¾ä¸æ¹ã
- **ç©ååç»æå **ï¼
  - åç½®åç¼åç»ï¼ç¾æ¯ãæ¨æ¿ãåæ¨ãå·¥å·ãç¿ç³ç­ï¼å¯ç¹å»å±å¼/éæ°æå ã
  - ç¸åç©åæ³¨ååä½ NBT/ç»ä»¶ä¸åçç©åä¼æå å°ä¸èµ·ï¼ä¾å¦ä¸åè¯æ°´ãéé­ä¹¦ï¼é»è®¤å¼å¯ã
  - æ¯æéè¿ JSON å®ä¹ç©ååè¡¨åç»ãTag åç»åæ­£åè¡¨è¾¾å¼åç»ã
  - é»è®¤åç»å¯ä»¥éç»å³é­ï¼é»è®¤åç»æ¯å¦è·¨ Mod å½åç©ºé´æ··ç»ä¹å¯è®¾ç½®ï¼é»è®¤è·¨å½åç©ºé´æ··ç»ã

### éç½®

éç½®æä»¶ä¸º `config/jei_plus_plus-client.toml`ãä¸»è¦å¼å³å¦ä¸ï¼

| éç½®é¡¹ | é»è®¤å¼ | ä½ç¨ |
| --- | --- | --- |
| `stackGroupingEnabled` | `true` | æ»å¼å³ï¼å³é­ææç©ååç» |
| `nbtGroupingEnabled` | `true` | æå ç¸åç©åçä¸å NBT/ç»ä»¶ |
| `tagGroupingEnabled` | `true` | å¯ç¨ JSON Tag åç» |
| `jsonGroupingEnabled` | `true` | å¯ç¨ JSON èªå®ä¹åç» |
| `mixNamespaceGroups` | `true` | åè®¸é»è®¤åç»æ··åä¸å Mod å½åç©ºé´ |
| `defaultGroups.<åç»å>` | `true` | åç¬å¯ç¨/ç¦ç¨ä¸ä¸ªåç½®é»è®¤åç» |

JSON åç»æä»¶æ¾å¨ `config/jei_plus_plus/stack_groups/*.json`ãæä»¶ä¿®æ¹ãæ°å¢æå é¤åï¼JEI++ ä¼å¨ç©ååè¡¨å·æ°æ¶èªå¨éæ°è¯»åã

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

å¯ç¨ç JSON ç±»åï¼

- `jei_plus_plus:group`ï¼`contents` ä¸­å¡«åç©åæ³¨ååæ `#namespace:tag`ã
- `jei_plus_plus:tag`ï¼å¡«å `tag` æ `contents`ï¼å°åä¸ä¸ªç©å Tag ä¸­çç©åæå ã
- `jei_plus_plus:regex`ï¼å¡«å `regex` æ `regexes`ï¼æå®æ´ç©åæ³¨åå `namespace:path` å¹éã

`name` å¯ä»¥æ¯ç¿»è¯é®ï¼ä¾å¦ `mypack.group.shiny_things`ï¼ï¼ä¹å¯ä»¥ç´æ¥å¡«åæ¾ç¤ºææ¬ï¼`priority` è¶å¤§è¶ä¼åå¹éãå¯ä»¥ä½¿ç¨åä¸ä¸ªé»è®¤åç» ID è¦çæå³é­é»è®¤åç»ï¼

```json
{
  "id": "jei_plus_plus:wool",
  "enabled": false
}
```

åç½®é»è®¤åç»åæ¬ï¼`wool`ã`carpet`ã`concrete`ã`concrete_powder`ã`terracotta`ã`glazed_terracotta`ã`stained_glass`ã`stained_glass_pane`ã`candle`ã`bed`ã`banner`ã`shulker_box`ã`planks`ã`stripped_logs`ã`logs`ã`stripped_wood`ã`wood`ã`slab`ã`stairs`ã`wall`ã`fence_gate`ã`fence`ã`door`ã`trapdoor`ã`button`ã`pressure_plate`ã`glass`ã`pane`ã`ore`ã`raw_material`ã`ingot`ã`nugget`ã`sword`ã`pickaxe`ã`axe`ã`shovel`ã`hoe`ã`boat`ã`sapling`ã`seed`ã`flower`ã`leaves`ã`rail`ã`hanging_sign`ã`sign`ãæ¯ä¸ªåç§°åæ¶å¯¹åº TOML ä¸­ç `defaultGroups.<åç§°>` å `jei_plus_plus.group.default.<åç§°>` ç¿»è¯é®ã

### çæ¬

- åæ¯ `1.20.1`ï¼Minecraft 1.20.1 + Forgeï¼Java 17ã
- åæ¯ `1.21.1`ï¼Minecraft 1.21.1 + NeoForgeï¼Java 21ã
- JEI æ¯å¯éçå®¢æ·ç«¯ä¾èµï¼æ²¡æ JEI æ¶ä¸ä¼å è½½ JEI å®¢æ·ç«¯åè½ã

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
