# JEI++ Update Summary Since 1.0.2

This document summarizes the changes made to JEI++ after 1.0.2 through the current codebase, including the 1.0.4 release line and its subsequent feature work.

## Core additions

- Recipe Tree now supports fluids. Fluids can be tree nodes, bookmark candidates, and transfer targets, with quantity formatting and storage-network inventory support.
- The automatic-crafting shortcut changed from Shift-left-click to Ctrl-left-click, reducing conflicts with JEI, AE2, RS, and other terminal screens that handle Shift-left-click themselves.
- Recipe Tree now has direct recipe-transfer controls. The `+` on a recipe node transfers that recipe, while Shift-click requests the full amount.
- Recipe-tree bookmarks place final products, intermediate products, and base materials on separate rows, with independent quantity and status updates.
- Final products now participate in inventory highlighting and front-of-list ordering, so crafting progress can be tracked from the target item itself.
- Automatic-crafting compatibility was expanded: only missing steps are processed, instant and timed workstations are handled differently, fluid inputs are supported, and AE2, Refined Storage, Beyond Dimensions, and Integrated Terminals can use their available transfer paths. JEI's native cheat-mode item clicks remain available.

## Version and input changes

- The project continues to maintain Minecraft 1.20.1 Forge and 1.21.1 NeoForge variants.
- Recipe-tree bookmark auto-crafting uses Ctrl-left-click to avoid the Shift-left-click interception used by newer JEI and storage-terminal interfaces.
- `preferRecipeBookmarkOnOutput` and `hideRecipeBookmarkButton` are independent settings, so recipe-bookmark preference and visibility of JEI's native bookmark button can be controlled separately.

## Recipe browsing and bookmarks

- Multi-candidate recipe slots open a paged ingredient directory. Every candidate can still be used to inspect recipes, usages, and tag information.
- Tag-information pages are not treated as recipes.
- The top arrow/page-number band scrolls by complete pages; the recipe-title row switches recipe pages; the category-icon row switches recipe categories.
- Bookmarked recipes no longer restrict the view to one recipe. All matching recipes and usages remain visible, while the bookmarked recipe and its category are placed first.
- Multi-output recipes use the output under the cursor instead of always selecting the first output.
- An output slot is bookmarked as a recipe only when JEI's own bookmark-priority mode and JEI++'s recipe-bookmark preference are both enabled. Input slots continue to bookmark items.
- Default recipes are stored per output, including partial selections for multi-output recipes, in `config/jei_plus_plus/recipe_defaults.json`.
- Log/stripped-log to planks and nugget to ingot are excluded from automatic generic defaults but remain manually selectable.

## Recipe Tree and Crafting Assistant

- The tree view provides EMI-style nodes, connectors, recipe categories, inputs, outputs, and quantities.
- Nodes can be expanded, collapsed, assigned another recipe, assigned a concrete output, or locked to a concrete input in the tree. Right-click unlocks the candidate.
- Equivalent input slots are merged. When no input is locked, all candidates participate in inventory totals, highlighting, and JEI transfer.
- Candidate selection prefers items already in the inventory or storage network and recursively searches craftable dependency chains without replacing the selected recipe based on inventory score.
- Default recipes, recipe bookmarks, and item bookmarks participate in tree construction, including bookmarked intermediate products.
- Crafting mode supports target quantities, batches, cycle protection, depth/node limits, total costs, by-products, leftovers, panning, zooming, fit-to-view, and recentering.
- Final products, total costs, and by-products share a vertical center line. Final, intermediate, base, missing, and owned states use different colors.
- Recipe-tree bookmarks are separated into final-product, intermediate-product, and base-material rows. Owned entries remain visible and quantities are refreshed from the live inventory.
- Plain left-click transfers direct inputs only. Ctrl-left-click recursively transfers missing steps; instant workstations can take intermediate outputs, while timed machines receive their inputs only.
- The lower-right `+` on a recipe node performs a JEI-style direct transfer, and Shift-click requests the complete amount. The tree and other JEI recipe overlays close and the work-block screen is shown afterward.
- When JEI cheat mode is enabled, final and intermediate tree bookmarks do not intercept JEI's native one-item/stack cheat clicks.
- The global recipe-tree button supports opening, returning, and right-click clearing, and is shown only on JEI-related screens. E/Escape also returns.
- The active tree is saved to `config/jei_plus_plus/recipe_tree_session.json` and can be restored after changing worlds, restarting the game, or reloading JEI. Explicitly switching or clearing the tree removes the saved state.

## Fluid recipes

- Fluids remain fluids in recipe trees and bookmarks instead of being converted to buckets.
- A recipe that explicitly requires a bucket remains bucket-based. A recipe that requires a fluid can use the fluid or a compatible container as a candidate, and the `+` button can lock the selected candidate.
- Fluid inputs, fluid outputs, multi-output fluid selection, fluid-capability slots, container transfers, and network fluid quantities are supported.
- 1000 mB equals 1 B. Amounts below 1000 mB use mB; amounts at or above 1000 mB use B with one decimal place. Displayed amounts use the actual inventory quantity rather than clamping to the required amount.

## AE2, RS, Beyond Dimensions, and Integrated Terminals

- Optional reflective client integrations read item and fluid storage without making these mods hard dependencies.
- AE2, Refined Storage RS1/RS2, Beyond Dimensions, and Integrated Dynamics/Integrated Terminals can participate in tree planning, counts, candidate selection, highlighting, and transfer.
- Matching network entries are placed before ordinary entries in supported terminal views. Shared snapshots, revisions, and on-demand scans reduce repeated probing; RS, Beyond Dimensions, and Integrated Terminals do not probe AE2 again.
- Transfers prefer a terminal's client API, official packet path, or the generic container-click path. When supported, outputs can be deposited back into the network if the player's inventory is full.
- Missing optional mods, changed APIs, or unsupported terminal types are skipped safely.

## Creative item tabs

- All-items and creative categories are shown above JEI's ingredient list.
- The category bar provides slot-sized left/right buttons, complete-page wheel scrolling, and a centered page overlay.
- Page numbers do not consume ingredient slots, and count/page overlays render above item textures.

## Expandable item groups

- Reliable-EMI-style expandable groups cover wool, planks, logs, ores, tools, glass, doors, slabs, stairs, and related families.
- NBT/component variants of one registered item can be grouped, including potions and enchanted books.
- Item-list, tag, and regular-expression JSON groups support priorities, translation keys or literal names, exclusions, and enable switches.
- Every built-in group has an independent configuration switch, and `mixNamespaceGroups` controls namespace mixing.
- When JEI Tag Groups, JEI Groups, Collapsible Groups, or a compatible grouping addon is present, JEI++ disables its own ingredient transformation to avoid double grouping.

## Performance, compatibility, and stability

- Recipe candidates, layouts, inventory snapshots, network snapshots, and fluid accessors are cached and refreshed by game ticks and revisions.
- Recursive candidate searches have depth, visit, and cycle limits and are used only for recipe-tree and default-recipe workflows.
- Multiple JEI recipe-layout, bookmark, ingredient-list, and renderer paths are supported on both platform variants.
- JEI reloads, world changes, terminal changes, missing optional mods, and API changes safely clear or restore runtime state instead of retaining stale objects.

## Current files and configuration

- Client configuration: `config/jei_plus_plus-client.toml`
- Default recipes: `config/jei_plus_plus/recipe_defaults.json`
- Active recipe tree: `config/jei_plus_plus/recipe_tree_session.json`
- JSON groups: `config/jei_plus_plus/stack_groups/*.json`
