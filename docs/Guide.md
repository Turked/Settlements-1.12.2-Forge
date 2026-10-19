# Settlements setup and feature guide

A Minecraft **1.12.2** Forge mod by **MechGaming**, targeting **RLCraft 2.9.3** and Forge **14.23.5.2860 or later**. Development uses Forge 2864 and Java 8. Install the same Settlements jar on server and clients.

## Build and DataGen

Run these commands from this project in PowerShell:

```powershell
.\tools\Build.ps1
.\tools\Build.ps1 -Tasks runData
.\tools\Build.ps1 -Tasks genIntellijRuns
```

The build creates `build/libs/Settlements-0.1.0.jar`. `runData` is an alias for `generateResources`. `build` regenerates resources and runs the verification tasks automatically. `tools/Build.ps1` selects an installed Java 8 JDK for this command and restores the previous environment afterwards. It also accepts `-JavaHome` for another Java 8 installation.

Minecraft 1.12.2 predates the modern Forge DataGenerator API and native item/block tags. `src/datagen/java/.../SettlementsDataGenerator.java` is the Java 8 DataGen entry point. It generates metadata, the flag recipe, item/model references, blockstates, `en_us.lang`, and a default NPC definition into `src/generated/resources`. Do not edit generated files. Edit the generator or provide an intentional override under `src/main/resources` instead. No unused tag files are generated.

The generator references Minecraft's existing white wool model and blaze rod sprite. The `settlements:settler` entity extends the vanilla villager and uses its existing renderer and artwork, with definition-controlled trades. There are no authored textures or 3D meshes. Raw artwork and media belong in the external Settlements_Assets directory provided by the project owner; only finished runtime overrides belong in `src/main/resources/assets/settlements`.

## Start a settlement

1. Craft a Settlement Flag using four wool and two sticks, or obtain it from the Settlements creative tab.
2. Outside an established settlement, press **K** (**Settlements**) to equip the virtual flag planner. It never swaps or deletes real inventory items.
3. Select slot **1**, look at a solid supporting block, and right-click to name and propose a settlement.
4. The default requirement is one flag and one willing player. Pack administrators can raise both requirements. With multiple players required, other players stand inside the proposal, select virtual slot **2**, and right-click to support it.
5. When enough eligible online supporters are standing inside, the server validates the entire claim, consumes the founder's flags, and places its anchor block.

Proposals expire after the configured time. Flags are consumed only when a settlement is established. Claim areas are dimension-specific squares extending through the full world height. Existing claims keep their radius when the default radius is changed.

Inside an established settlement, press **K** to open that settlement's menu, including when the planner was equipped. Owners can manage membership requests, open/close recruitment and confirm disbanding. Visitors can request membership while inside an accepting settlement. Outside an established settlement, **K** exits an already equipped planner. Proposals still use the planner. **J** toggles the independent boundary overlay: green allowed zones, red restricted zones, gray established claims, gold proposals, and cyan missing blueprint blocks. The planner also shows these boundaries.

The **Settlements** and boundary keys are rebindable under Controls → Settlements. K/J may conflict with RLCraft bindings: assign a unique key in your profile as needed. F keeps its vanilla offhand action. K, Q, Escape, inventory/chat keys, death, world/dimension changes and inventory changes exit planner mode. The planner hides requested vanilla HUD elements; it leaves other mods' custom HUDs alone.

## Administration and zones

Operators with permission level 2 can use `/give @p settlements:dev_tool`. Right-click two loaded corners to create a rectangular zone. Left-click cycles build/spawn zones; sneaking cycles allow/restrict. Right-click air opens the current zone settings, and sneak-right-click opens live mod settings. The command alternatives are available through `/settlements help`.

Later zones override earlier zones in overlapping columns. Build-zone rules can allow/restrict settlement founding, prevent building, blacklist registry-named blocks, or decay **newly placed** blocks after a delay. Existing terrain is not scanned for decay. Advanced settings allow per-zone rules. Spawn zones apply through standard Forge spawn hooks; other mods retain their own spawning and AI rules.

`config/settlements.cfg` controls claims, zones, NPCs and optional feature settings. Changes made through the operator GUI are applied on the server and saved immediately. Changes to claim radius affect new settlements. `config/settlements-features.cfg` holds additional raid entity IDs, automatic raid interval, and administrator-authored NPC structure opt-in; file edits need a restart.

## NPC housing

Build an enclosed room inside a claim, including a closed wooden door, a bed and a chest. Minecraft 1.12.2 has no vanilla barrel, so the default definition uses a chest. Stand in its empty interior and use `/settlements room add settler`, or register it in the settlement menu. Bounded room scans check requirements; valid rooms attract named vanilla villager placeholders with configured trades. The system rechecks rooms periodically, removes residents from invalid rooms and delays replacements after a resident dies.

The default definition is generated and copied to `config/settlements/npcs/settler.json` on first startup. Add new definition outputs in the Java DataGen source to author additional NPCs without handwriting JSON. Definitions support installed registry IDs, room size, required blocks, trades and optional small block arrangements. NPC definitions are loaded after registration so installed mod items and blocks can be used. Generated defaults never overwrite an existing server definition.

## Blueprints and optional defense

`/settlements blueprint save [radius]` records a loaded cube around the leader, entirely inside the claim. The default radius is 8 and the volume is bounded by configuration. Preview shows missing ordinary blocks and a materials list. Inventory repair replaces missing blocks using matching items and respects placement events and spawn protection.

Blueprints intentionally exclude containers, beds, doors, fluids, falling blocks, piston machinery and other unsafe/multipart blocks. They do not copy inventories, entity state, or tile-entity NBT. Occupied positions are not overwritten. This is restoration of the captured ordinary block area, rather than a complete world rollback.

Raids are disabled by default. Enable `mobsAttackSettlement`, then use the menu or `/settlements raid start`. Installed hostile entity registry IDs, wave size and reward can be configured. Ordinary RLCraft mobs retain their normal AI. Only tagged raid mobs receive settlement targets and optional destruction/climbing behavior. Destruction and block placement each require their own opt-in and the world's `mobGriefing` rule. Automatic raids require a positive interval in the feature configuration.

With `raidsWithoutRuin`, save a blueprint before starting. Restoration freezes matching blocks actually present at event start and repairs missing ordinary blocks afterwards, as their chunks load. Event-restorable blocks do not drop materials when harvested, preventing free-restoration duplication. No container contents are restored.

## Agreed wars

Wars are disabled by default. Enable `warsWithoutRuin`, and have both leaders save explicit blueprints. A leader offers frozen terms to another settlement; the other leader accepts. Members then opt in or out. Any roster change resets both leaders' ready confirmation. Both leaders must start the agreed roster before combat begins.

Supported objectives are shared lives or destruction of recorded vanilla bed objectives. Bed objectives record only orientation and dye color; paired beds are restored after the event when their positions and supporting floor are available. Ordinary blueprint restoration keeps its container and multipart exclusions. The war period is bounded.

Nonparticipants retain PvP protection from war participants. The configurable `warGhostProtection` also prevents damage to opted-out members while inside their own active-war claim; it grants no immunity while exploring elsewhere. Use the war menu to review terms, participants, time and results.

Both leaders can agree to a matching inventory wager (one untagged item registry ID/metadata, up to 64 per side). Both inventories are checked before either stake is taken at the start. Draws refund the stakes; victory pays the winning leader. Offline or full-inventory payouts remain saved until delivery. Arbitrary whole-chest escrow is not included.

Optional annexation must be included in the original offer and accepted by both leaders. A victory combines both owners' claim groups and membership under the winner, retaining distinct claim areas, IDs, NPC rooms and blueprints. Select the relevant owned area in the menu. A settlement involved in a war agreement cannot disband or leave to escape agreed terms; cancel a pending offer or surrender an active war first.

## Persistence and compatibility

Settlement and feature records are saved in the overworld's `WorldSavedData`, with explicit dimension IDs. Membership and permission decisions are server-side. Commands and client requests use the same validated service. Clients cannot directly declare ownership, consume resources, or edit operator settings.

There are no required RLCraft-mod dependencies, coremods, mixins, global replacement AI, or Xaero integration. Xaero integration was explicitly deferred by the project owner. Existing RLCraft crafting and HUD mods remain independent; key conflicts should be resolved in the profile's Controls screen.

`tools/SmokeTest-Forge.ps1` performs an isolated Forge 2860 production mod-lifecycle test using locally installed libraries. Its test-only LaunchWrapper target constructs an unstarted server and runs Forge construction, pre-init, init and post-init. It copies test inputs into `build`, leaves the actual RLCraft instance unchanged and starts no world or network listener. Add `-WithRLCraft` to include copies of the local pack's mods/configs. This verifies loading, registry freezing and generated NPC resource installation, **not** live gameplay, server-starting callbacks or multiplayer. See `docs/Verification.md` for the validation record and remaining interactive checks.
