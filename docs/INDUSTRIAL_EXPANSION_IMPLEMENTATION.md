# Industrial Expansion implementation log

## E0 baseline (2026-10-17)
- Source: `main`, Unusual Copper 0.20.6-alpha.1, Forge 1.20.1 / Java 17.
- Preserve the ordinal of every existing `MachineKind` (client menu serializes the ordinal).
- Reuse `CopperMachineBlockEntity`, `CopperEnergyBuffer`, `OperationMath.payment`, inventory side caps, `BoundStorageLogic`, and optional SSN bridge. No changes to existing energy rates or SSN protocols.
- The old BE `DataVersion=2` schema remains unchanged for the first vertical slice; new block IDs are additive.
- Copper yield limit: raw copper → 1 grit; no reverse crafting to copper ingot. Iron/gold provide 2 grit each, not directly smeltable until the cleaning stage.
- Crusher 60 ticks/180 CuE; two output slots use the existing output plan. Redstone, pause, output blocked, NBT save and bound external storage inherit the shared implementation.
- No Minecraft client or GameTest server executed. In-game acceptance is pending.

## E1 vertical slice
- Data-driven `industrial_processing` recipes, with server-side input validation, no NBT on inputs and a bounded per-recipe cost.
- Resonance Crusher registered with raw copper, raw iron, raw gold recipes and outputs.
- Standard four-slot machine menu, item/energy side configuration, block model, crafting, loot and Russian/English translations.
- Distinguish **implemented mechanics** from **in-game verified**; JEI custom category is not yet implemented.

## Manual E1 checks
1. Feed one and 64 raw copper through top item input, connect energy side and bottom chest.
2. Verify 1 copper grit per raw copper, 180 CuE each and 60 paid ticks.
3. Fill output slots and attached chest: ensure pause without recharging or deleting items.
4. Pause by redstone, unload/reload chunk, use `/reload` and remove recipe datapack temporarily.
5. Verify disabled side, restricted owner and claimed chunks. Verify external SSN only via installed bridge; no automatic direct dependency.

## E2: Copper Press and industrial components (2026-10-17)
- Added `copper_press`: plate die produces 2 copper plates from 1 copper ingot; wire die converts 2 plates into 4 wire spools. Each operation takes 40 ticks/120 CuE.
- Dies remain physical, reusable items in slot 1. A missing/wrong die stops the operation, without consuming additional CuE.
- Added `copper_plate`, `copper_wire_spool`, `resonant_coil`, `reinforced_frame`, `circuit_blank` and two press dies.
- Bootstrap is explicit: 1 ingot → 1 plate at crafting table; plate + 2 ingots + redstone + quartz → 1 coil. This avoids the chicken-and-egg dependency: coils can be made before the first press exists.
- Full-efficiency coil: 4 wire spools + redstone → 1 coil. No plate/wire recipe returns ingots; no copper mass multiplication.
- The generic machine menu activates slot 1 for the press; the same two protected output slots work with chests and SSN via preexisting logic.

### Manual E2 checks
1. Craft plate and coil by bootstrap route, craft two dies, assemble press.
2. 1 ingot + plate die → exactly 2 plates; 2 plates + wire die → 4 spools.
3. Remove die mid-operation, restore it; verify payment is not repeated, and 120 CuE total.
4. Pipe input and output using external chest; test full output, redstone pause, reload, old worlds.
5. Craft 16 coils from 16 ingots + 16 redstone: expect 16 plate cycles, 16 wire cycles and 3 840 CuE spent in presses, excluding other systems.

## E3A: Water purifier, dust and tailings (2026-10-17)
- Added `vortex_purifier` with a 4 000 mB water-only Forge fluid tank and persisted water state. A physical water bucket in the second input slot fills 1 000 mB and becomes an empty bucket; fluid pipes may fill via Forge capability.
- Each 1 grit + 250 mB + 300 CuE over 80 ticks produces 1 washed dust and 1 inert tailings. Both outputs are planned together before spending energy or consuming water. Output capacity pauses processing with no loss.
- Water cost and secondary result are saved in the shared block entity's optional NBT. Existing old-world data without these fields stays valid.
- Washed copper/iron/gold dust smelts through vanilla SMELTING recipes already consumed by the existing Resonance Smelter. Copper remains capped at 1 ingot per raw copper; iron and gold permit a paid 2× route.
- This completes the basic purification vertical slice, **not** the later crystallizer or etcher.

### Manual E3A checks
1. Insert a water bucket in slot 1 or fill through Forge fluid pipe. Tank should gain exactly 1 000 mB and leave an empty bucket.
2. Process 16 grit against a full 4 000 mB tank; 17th operation pauses. Verify 16 dust + 16 tailings, 4 800 CuE spent, 1 280 machine ticks per slot sequence.
3. Fill output slots, suspend CuE, restart, `/reload` and vary redstone. Assert no duplicate tailings/dust and no lost water.
4. Smelt washed copper dust in existing smelter to a single ingot. Verify iron/gold 2 dust from 1 raw via crusher.
5. Test fluid capability, item output pipe/chest and SSN bridge with the standard permission and protection rules.

## E3B: Resonance Crystallizer and Circuit Etcher (2026-10-17)
- Registered `resonance_crystallizer` and `circuit_etcher`, reusing the existing CuE network, sides, block entity and GUI.
- Crystallizer: 1 amethyst shard + 1 resonant coil + 1 saturated copper essence → 1 crystal in 120 ticks for 8,000 CuE. All three input items are consumed.
- Etcher: 1 circuit blank + 1 etching reagent + 250 mB water → 1 etched circuit in 100 ticks for 1,200 CuE. Its extra GUI slot accepts a water bucket and returns an empty bucket; Forge water pipes can supply fluid.
- New dedicated third input and operation snapshots save to optional NBT. The legacy 4 machine inventory slots keep their old indices; extra input is automation slot 4 only on the two new machines.
- Fluid capability is now input-only, blocking an external pipe from draining water already promised to an operation. Water is consumed internally only on successful operation completion.
- Added processing JSON, survival crafting, item/block models, RU/EN text, and exact-energy JUnit cases. New textures are placeholders.
- The etching reagent currently has a chemistry crafting bootstrap. Integration as an output of the existing Aetheric Brewer is not implemented yet. Existing potion recipes are left unchanged.
- No game or GameTest launched. CI and manual in-game acceptance are separate checks.

### Manual E3B checks
1. Supply the crystallizer with amethyst, coil and essence: 120 paid ticks, exactly 8000 CuE, all three consumed, one crystal.
2. Remove the coil or essence while operating: no output; replacing them must not allow unpaid completion. Test save/reload and /reload.
3. Etch a blank board with chemical reagent and water: 100 paid ticks, exactly 1200 CuE, 250 mB and one reagent consumed.
4. Fill output slots or stop the energy line during processing: no duplication, no extra billing, no lost water, correct resume.
5. Test fluid pipes (fill works, drain must return zero), third input via item pipe, old-world loading, redstone, SSN and all side modes.

## E4A: Precision Assembler, safe single-recipe execution (2026-10-17)
- Added `precision_assembler` with 100,000 CuE buffer, physically stored 3×3 item grid and reusable `pattern_card` in standard machine input 0.
- New `precision_assembly` JSON serializer accepts **exactly nine nonempty ingredients** and bounded 40–200 ticks / 400–4,000 CuE; no free-form client output or custom NBT recipes. The matching recipe is selected by the server RecipeManager from an explicit industrial type.
- Server snapshots all nine grid item identities at job start, persists the snapshot and recipe/progress/paid CuE to NBT, and rechecks input identities, recipe ID, cost, result and output capacity before performing an all-or-nothing local commit.
- The card is not consumed; exactly one item from each of the nine real grid slots is consumed per completed operation. Both output slots are protected and use the existing item pipe/SSN-compatible output path.
- Machine item automation exposes grid slots 4–12 only for this kind. Old 0–3 slots, other machine menus, CuE sides, existing NBT and the optional SSN bridge retain their previous semantics.
- Shipped 2 production recipes: `precision_processor` (120 ticks, 2,500 CuE) and `module_blank_i` (160 ticks, 3,200 CuE). Both use E2/E3 materials.
- **Current limitation**: the blank pattern card authorizes the current 3×3 industrial grid rather than storing a programmable per-card recipe. Only the dedicated `precision_assembly` recipe type is supported, not arbitrary vanilla/capability/NBT/container recipes. Pattern engraving and safe container returns remain E4B.
- No Minecraft/GameTest launch. CI compilation/JUnit and manual game tests are separate.

### Manual E4A checks
1. Craft pattern card and assembler without using assembler outputs. Place card in primary slot, fill 3×3 grid with one of the specified recipes.
2. Verify exactly one unit from each grid slot is consumed, the pattern card remains, expected item is output, and CuE matches 2,500/3,200.
3. Fill outputs, remove one reagent mid-operation, unload/reload or /reload. No duplicated output, missing materials or repeated energy billing.
4. Check 9-slot GUI layout, shift transfer, separate item-pipe insertion into slots 4–12, proper side modes and item drops after block break.
5. Test empty/unrelated grid, card removal, low energy, redstone control, old worlds, SSN bridge and the two-step dependency processor → module blank.

## E3 balance revision: washed dust x3 (2026-10-17)
- User explicitly superseded the earlier 1:1 copper mass rule: every grit now produces **3 washed dust** for the existing 250 mB water and 300 CuE process. Copper: 1 raw copper → 1 grit → 3 dust → 3 ingots. Iron/gold: 1 raw → 2 grit → 6 dust → 6 ingots.
- No loop converting ingots, plates, dust or tailings back to raw copper is added. The gain still needs finite mined raw ore, CuE, water and additional smelting payments.
- The legacy production docs were updated so the former x1 copper cap no longer misstates the author's new balance.
