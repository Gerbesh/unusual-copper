# E6B: server-owned armor power arbitration and configurable priorities

Baseline: `origin/main` at `2e08dbb` (2026-10-17). This is an incremental E6 subsystem, not E6/E7 acceptance.

## Implemented

- Each server wearer tick gathers naturally eligible continuous module demands, without applying their effects. A deterministic scheduler ranks priorities 2 (highest) through 6 (lowest), with F3 winning an equal-priority tie over F2.
- Accepted costs must fit the **piece** budgets: helmet 150, chest 650, legs 150, boots 200 CuE/t, plus the **whole set** ceiling 900 CuE/t, and current physical chest battery charge.
- The full accepted cost is debited in **one** server transaction before applying any effects, updating the F2/F3 session, or recording paid-module telemetry. An unaccepted module has no continuous effect or debit. There is no new currency or duplicate battery storage.
- While airborne/eligible, F2 and F3 still obey their existing authorization, world-border and cooldown rules. No Creative Flight permission is granted.
- Jump drive no longer drains idle energy while standing; the separate 2,000 CuE jump event remains.
- A denied module is reported as recently power-limited to its owner's armor screen. The server does not accept any client supplied cost or battery state.
- Each installed module can have a priority **per profile**. The armor console cycles 2–6 (Shift reverses). The server validates the action and preserves old triggers, battery charge and unrelated NBT.
- Old armor presets without a `Priority` field acquire the documented module-specific default automatically; no world migration is required. Malformed priority NBT (wrong type or out of 2–6) is rejected rather than activating an unsafe module.
- Client/server `armor_control` protocol is now **3**; clients and servers need matching mod versions. RU/EN entries are included.

## Verification and manual acceptance

Verified by `gradlew.bat test`, `gradlew.bat compileGameTestJava` and `gradlew.bat jar`. The GameTest additions were **only compiled**, not executed. No Minecraft client, GameTest server, or live server was started.

Manual checks for the owner:
1. Equip same-tier four-piece armor and install physical chest battery; confirm only eligible installed modules consume power.
2. In the K console, change flight to priority 6 and shield to priority 2 in one profile, then switch profiles and back. Confirm per-profile storage and persistence after a save/load.
3. Under resource pressure, only requests fitting slot and global budgets operate; denied ones display the power-limit status. Confirm no sudden use of creative flight rights.
4. Verify idle jump-drive draws 0 CuE/t and an actual boosted jump still draws its 2,000 CuE event price.
5. Verify an old armor item retains its battery and prior presets after update. Test server/client version mismatch on a disposable instance, not the live server.

## Still open

- Per-tick arbitration does not consolidate **event** expenses (impact, shield, mining, jump) with all simultaneous event activity. Each remains its existing independent atomic event payment.
- Current module costs usually stay well below the 900 CuE/t ceiling. Synthetic unit tests exercise competition, while functional over-budget GameTests require future higher-tier modules.
- F4, emergency landing reserve, charging microbuffers, additional modules, full SMP/claims/performance acceptance, and visual review are not implemented or proven by this change.
