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
