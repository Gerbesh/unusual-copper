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
