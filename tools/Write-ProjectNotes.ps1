param(
    [string]$NotesRoot = 'C:\Users\Owner\Documents\Obsidian\Better Docs\Modding Development\Minecraft Modding\Settlements',
    [string]$AssetsRoot = 'C:\Users\Owner\OneDrive - University of Cincinnati\Classes\College Fall 2026\Z_Development\Settlements_Assets'
)
$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$notes = [ordered]@{}
$notes['01 Procedure.md'] = @'
- [x] Rename the mod to **Settlements**, with **MechGaming** as its author and `settlements` as its mod ID.
- [x] Keep the project on Minecraft 1.12.2, Java 8 and Forge 2860-compatible APIs for RLCraft 2.9.3.
- [x] Generate metadata, recipes, language files, blockstates and placeholder references from Java DataGen.
- [x] Register the settlement flag and developer tool.
- [x] Implement persistent server-owned claims, willing-player proposals, membership, inbox and zones.
- [x] Add virtual planner controls, menus, boundaries and missing-block outlines.
- [x] Add configurable NPC rooms/trades, optional raids, blueprints and agreed wars.
- [ ] Complete the interactive RLCraft and two-client checks in the project's `docs/Verification.md` before distributing a release.

Sources

- [[The main idea]]
- [[UI and Controls]]
- [[Utility]]

Procedure folders follow the Endless Sands numbering. Brainstorming notes are deliberately empty. Only categories used by this mod are created; there are no fuel, food, ore or native-tag folders.

Art ownership

- No textures or 3D geometry were created. Generated placeholders reference existing Minecraft artwork.
- Working art and media belong in the supplied Settlements_Assets folder. The asset notes there explain replacement paths.
- Xaero map compatibility is deferred at MechGaming's explicit request. The independent overlay remains available.
'@
$notes['02 Blocks\01 Procedure.md'] = @'
- [ ] Describe the block's one purpose and choose its registry name before adding it.
- [ ] Register the block and its item in `registry/RegistryHandler`.
- [ ] Keep placement costs, permissions and claim changes on the server.
- [ ] Add its display name, blockstate reference, inventory model reference and recipe to `SettlementsDataGenerator`.
- [ ] Reference existing vanilla artwork until the artist supplies a replacement; do not create textures or geometry.
- [ ] Run `tools/Build.ps1 -Tasks runData`, then build the mod.
- [ ] Test placement, breaking, ownership, other protection events, saving/reloading and dimension changes.
- [ ] Write the numbered block note after this procedure and leave Brainstorming empty.
'@
$notes['02 Blocks\03 Settlement Flag.md'] = @'
Registered

- `settlements:settlement_flag`, display name **Settlement Flag**.
- Registered in `RegistryHandler`, with `SettlementFlagBlock` and `FlagItem`.

Purpose

- The anchor of a player-founded settlement. A proposal becomes a claim only after server validation, enough flags and enough willing online players inside its area.
- Breaking an active anchor directly is denied. The leader uses the confirmed Disband action; consumed flags are not refunded.

Crafting

- Four wool and two sticks; generated shaped recipe.

Placeholder

- Existing vanilla white wool block and inventory model.
- Add final overrides under `src/main/resources/assets/settlements`; change the generator's reference when replacing the block appearance.

Test

- [ ] Found a settlement, save and reload, then confirm its area and flag remain.
- [ ] Verify a visitor, piston, explosion and fluid cannot remove the anchor.
- [ ] Confirm denied placement consumes no flags.
'@
$notes['11 Tools\01 Procedure.md'] = @'
- [ ] Register the tool in `RegistryHandler` with an unambiguous registry name.
- [ ] Validate every privileged action on the server, even if the client hides its controls.
- [ ] Add the name and replaceable item-model reference in Java DataGen.
- [ ] Document right-click, left-click and sneaking behavior in the tool note.
- [ ] Test an operator, a normal player, a stale selection, another dimension and an unloaded target.
- [ ] Rebuild and run the RLCraft control-conflict check. Leave Brainstorming empty.
'@
$notes['11 Tools\03 Developer Tool.md'] = @'
Registered

- `settlements:dev_tool`, display name **Settlement Developer Tool**.
- Obtain with `/give @p settlements:dev_tool`; actions require operator permission level 2.
- Placeholder: existing Minecraft blaze rod sprite. No texture was created.

Controls

- Right-click two corners to create the selected allow/restrict zone.
- Left-click cycles build/spawn zones. Sneaking cycles allow/restrict.
- Right-click air opens the current zone editor; sneak-right-click opens server settings.
- Corners must be nearby and loaded. The server checks limits and dimensions.

Rules

- Later zones override earlier ones.
- Advanced per-zone settings include build prevention, decay and a block registry-name blacklist.
- Only newly placed restricted blocks enter the decay queue; existing terrain is left alone.

Test

- [ ] A normal player cannot create zones or change server settings.
- [ ] Overlapping allow/restrict zones produce the expected last-zone result.
- [ ] Bucket placement and multiple-block placement obey zone rules.
'@
$notes['15 Mobs\01 Procedure.md'] = @'
- [ ] Use `EntitySettler` for definition-controlled trades and its existing vanilla villager renderer for the placeholder.
- [ ] Define the NPC ID, name, room limits, required block registry IDs and trades in Java DataGen.
- [ ] Generate the definition and install it in the server's `config/settlements/npcs` directory.
- [ ] Keep move-in, room validation, trades and persistence on the server.
- [ ] Load definitions after all mod blocks/items are registered.
- [ ] Use bounded loaded-room scans; never force chunks or replace container contents.
- [ ] Test move-in, invalid housing, death, chunk unload/reload, world reload and multiplayer trade use.
- [ ] Add its numbered note and leave Brainstorming empty. Do not create textures or entity geometry.
'@
$notes['15 Mobs\03 Settlement Residents.md'] = @'
Purpose

- Terraria-style room requirements attract configurable settlement residents.
- Registered entity `settlements:settler` extends the vanilla villager, with a name, persistent identity and definition-controlled trades. It uses the existing vanilla villager renderer and artwork; no entity geometry was authored.

Default settler

- ID `settler`; generated default copied to `config/settlements/npcs/settler.json` if missing.
- Enclosed, loaded room inside the claim with empty standing space, bed, chest and wooden door.
- A chest substitutes for the barrel mentioned in the idea because Minecraft 1.12.2 has no vanilla barrel.
- Default trade: one emerald for three bread.
- Register while standing in the room with `/settlements room add settler`, or use the menu.

Customization

- Add a definition in `SettlementsDataGenerator`; room requirements and trades can use installed mods' registry names.
- Optional small `structureBlocks` arrangements require administrator opt-in. Tile entities are excluded from automatic structure placement.
- Existing server definitions are not overwritten by generated defaults.

Test

- [ ] Validate an enclosed room, then remove a required block and watch the resident move out.
- [ ] Kill a resident and verify replacement is delayed rather than duplicated.
- [ ] Unload/reload a resident's chunk and confirm its identity persists.
'@
$notes['99 Mechanics\01 Procedure.md'] = @'
- [ ] Describe the mechanic as one simple rule the player can understand.
- [ ] List what starts it, what changes and what the player sees.
- [ ] Choose the smallest ordinary Forge event or service that can implement it.
- [ ] Keep ownership, resources, permissions, world changes and outcomes on the server.
- [ ] Persist data in the overworld's `WorldSavedData` with explicit dimension IDs.
- [ ] Validate client requests for permission, distance, loaded chunks, size and action rate.
- [ ] Add configuration, clear feedback and replaceable vanilla-art placeholders where needed.
- [ ] Generate resource data from Java; do not handwrite resource JSON.
- [ ] Test success, rejection, stale/offline players, saving/reloading and dedicated-server loading.
- [ ] Add a numbered mechanic note and leave Brainstorming empty.
'@
$notes['99 Mechanics\03 Settlement Founding.md'] = @'
Rule

- A settlement needs a flag, a permitted area and enough willing players.

Procedure

- Craft a flag, press K, choose virtual slot 1 and right-click a supporting block.
- Name the proposal. Other players stand inside and support it using virtual slot 2.
- The server counts only eligible online supporters standing inside, checks every part of the claim against build zones, validates protection hooks and consumes flags only on successful establishment.
- Proposals expire, can be cancelled and cannot overlap other claims/proposals.

Configuration

- `radius`, `requiredFlags`, `requiredPlayers`, proposal lifetime and world limits.
- `firstSettlementDistance` can constrain later settlements relative to the first settlement.

Implementation

- `SettlementService`, `SettlementData`, `Settlement`, `Proposal`, `ZonePolicy`.
- Claims are square X/Z columns through the world height and isolated by dimension.

Test

- [ ] Test restricted islands inside an otherwise allowed area, overlapping claims and stale supporters.
- [ ] Verify no flags are consumed by a rejected proposal.
'@
$notes['99 Mechanics\04 Membership and Inbox.md'] = @'
Rule

- Leaders decide who joins established settlements; players explicitly agree to new proposals.

Procedure

- A visitor stands in an accepting settlement and requests membership using the raised-hand slot or menu.
- The leader receives a notice and accepts or denies the request in the K Settlements menu while inside the settlement.
- Recruitment can be closed. Non-owner members can leave. Leaders disband through an explicit confirmation.
- Accepted membership and the inbox persist through world reloads.
- When ownership is unified by agreed annexation, the linked claim areas keep their geometry and share owner/membership; menus select an individual owned area.

Protection

- Ordinary visitors cannot build, break, use protected containers, or harm protected animals/decorations.
- Hostile mob combat remains available. Operators retain administrator access.
- Normal Forge protection hooks are respected; this is not a promise that every external mod's custom world mutation route is intercepted.

Test

- [ ] Use two clients to test requests, approvals, denied actions and leaving.
- [ ] Repeat after save/reload and after one player disconnects.
'@
$notes['99 Mechanics\05 Planner and Controls.md'] = @'
Rule

- The planner is a virtual interface, never a replacement inventory.

Controls

- K is the **Settlements** key. Outside an established settlement it toggles the flag planner; inside an established settlement it opens that settlement's menu, including when the planner was equipped. Proposals still use the planner.
- J toggles boundaries. F keeps its vanilla offhand action.
- 1 and 2, or the wheel, switch virtual flag/raised-hand slots.
- Right-click proposes, supports or requests membership.
- Q, Escape, inventory/chat actions, death, dimension/world change and inventory changes release the planner.
- The Settlements and boundary keys can be rebound. RLCraft may use K/J; choose unique bindings.

Visuals

- Requested vanilla hotbar/health/armor/food/experience displays are hidden only while planning. Other mods' custom HUDs are not globally cancelled.
- Green/red zone borders, gray claims, gold proposals, green ready-player outlines and cyan missing-block wireframes use ordinary rendering.
- Slot icons reuse vanilla artwork. No custom texture or geometry was created.

Test

- [ ] Hold a real usable item, enter/exit planning and verify nothing is used, dropped or moved.
- [ ] Test remapped keys, held keys, wheel input and opening chat/inventory.
'@
$notes['99 Mechanics\06 Build and Spawn Zones.md'] = @'
Rule

- Administrators control where settlements can form, where building is restricted and where hostile spawning is allowed.

Procedure

- Use the developer tool or `/settlements zone corner <build|spawn> <allow|restrict>` twice.
- Later matching zones override earlier zones. Set the unzoned-land default in live settings.
- Optional rules prevent building, blacklist particular blocks or decay newly placed restricted blocks.
- Advanced rules apply per zone. Zone deletion and name/policy editing are operator-only.
- Spawn-ring distance and required allowed spawning zones can constrain new settlements and optional raids.

Compatibility

- Uses ordinary Forge events, with no global replacement of RLCraft mob AI.
- Xaero integration is explicitly deferred. J provides the independent in-world boundary overlay.

Test

- [ ] Check overlap precedence, default policies, buckets, multiple-block placement and dimensions.
- [ ] Verify decay only removes the queued matching state and does not delete existing terrain or containers.
'@
$notes['99 Mechanics\07 Settlement Raids.md'] = @'
Rule

- Optional tagged raids attack an occupied settlement in bounded waves.

Procedure

- Enable `mobsAttackSettlement`. Start a raid from the menu or `/settlements raid start`.
- Configure installed hostile entity IDs, wave count, mobs per wave, ring distances and reward.
- A positive interval in `settlements-features.cfg` enables automatic raids; zero is manual only.
- Optional destruction and climbing placement each require their own configuration and `mobGriefing`.
- Ordinary RLCraft monsters keep their original AI. Tagged raid entities alone receive settlement-directed behavior.

Without ruin

- Enable `raidsWithoutRuin` and save a loaded blueprint first.
- Only matching ordinary blocks actually present at raid start are eligible for free restoration.
- Restorable blocks yield no harvest materials/experience, preventing duplication. Containers and multipart blocks are excluded.
- Missing loaded blocks are restored in bounded batches; there is no whole-world rollback.

Test

- [ ] Test no safe spawn position, player disconnects, timeout, victory, stop and server restart.
- [ ] Verify rewards and restoration cannot be farmed from stale blueprints.
'@
$notes['99 Mechanics\08 Blueprints and Repairs.md'] = @'
Rule

- A bounded explicit blueprint can show and repair missing ordinary building blocks.

Procedure

- The leader stands in the claim and saves a cube using `/settlements blueprint save [radius]`.
- Use Preview/materials in the menu; J or planner mode shows cyan missing-block wireframes through walls.
- Repair consumes matching inventory materials for missing air positions and respects placement events, entity collision and spawn protection.
- Saving a new blueprint replaces that claim's previous blueprint.

Limits

- Entire capture must be loaded, within world height, inside the selected claim and below the configured volume cap.
- No container contents, tile-entity NBT, doors, beds, fluids, gravity blocks, piston machinery or entity snapshots.
- Occupied or changed positions are not overwritten.
- Automatic event restoration uses a frozen present-at-start subset; it is not a free inventory repair command.

Test

- [ ] Remove a captured block, verify its preview/material cost and repair it in survival.
- [ ] Verify a container, door and bed are excluded, and an occupied position remains untouched.
'@
$notes['99 Mechanics\09 Agreed Wars.md'] = @'
Rule

- Two leaders accept frozen terms and a fixed participant roster before an optional war starts.

Procedure

- Enable `warsWithoutRuin` and save both selected claim areas' blueprints.
- Offer a time period and shared-lives or recorded-bed objective. The receiving leader accepts or declines.
- Members explicitly opt in/out. Roster changes reset both leaders' ready confirmation.
- Both leaders start after reviewing terms and participants.
- Nonparticipants retain PvP protection against war participants. Configurable ghost protection also prevents damage while opted-out members are inside their own active-war claim; normal danger remains elsewhere.
- The event ends on its objective, time limit, surrender or invalidated agreement. Ordinary frozen blueprint blocks are repaired without restoring inventories.

Stakes and ownership

- Optional agreed inventory stakes use a registry ID, metadata and item count, excluding custom NBT. They are collected only when both sides start.
- Escrow, results and offline payouts/refunds persist. Draw/cancellation returns each side's stake.
- Optional explicit annexation unifies ownership/membership while retaining the distinct claim areas and their saved data.
- Arbitrary whole-chest escrow and protected private-room selection are not implemented.

Limits

- Blueprints cover explicit ordinary-block cubes, not entire worlds or inventories. Vanilla war-objective beds have a separate orientation/color-only paired restoration queue and restore when their positions and floor are available.
- The participant exemption does not grant general exploration immunity or flight.

Test

- [ ] Test altered rosters, offline participants, rejected offers, timeouts, death and bed conditions.
- [ ] Verify both leaders see the same wager/annexation terms before starting.
- [ ] Test stake refunds and offline payout after restart in a disposable world.
'@
$notes['99 Mechanics\10 DataGen and Placeholders.md'] = @'
Rule

- Mod-owned resource data is generated from Java source, and artwork remains replaceable.

Procedure

- Edit `src/datagen/java/com/mechgaming/settlements/datagen/SettlementsDataGenerator.java`.
- Run `tools/Build.ps1 -Tasks runData`; outputs go into `src/generated/resources`.
- Build automatically runs DataGen and checks that output matches the source.
- Metadata, recipes, legacy `.lang`, blockstates, vanilla model references and the starter NPC definition are generated.
- Minecraft 1.12.2 has no modern DataGenerator API or native JSON item/block tags. Do not add modern-version files the game cannot read.
- Put intentional ready-to-load overrides under `src/main/resources`. Keep editable art/media sources in the supplied external Settlements_Assets directory.

Placeholders

- Flag: existing Minecraft white wool model.
- Developer tool: existing Minecraft blaze rod icon.
- Residents: `EntitySettler` with the existing vanilla villager renderer and artwork.
- Planner/boundaries/blueprints: ordinary UI and wireframe rendering.
- No textures or 3D geometry were created.
'@
$notes['99 Mechanics\11 RLCraft Compatibility.md'] = @'
Target

- Local instance: RLCraft **2.9.3**, Minecraft **1.12.2**, Forge **14.23.5.2860**; 180 installed mod jars at inspection.
- Development compiles on Java 8 and Forge 2864, while the mod's minimum Forge requirement is 2860.
- Same Settlements jar is required on server and clients.

Approach

- No hard dependencies on First Aid, Lycanites, Ice and Fire, Reskillable or other pack mods.
- No coremod/mixin and no global replacement mob AI.
- Planner suppresses selected vanilla HUD parts only. Check First Aid and other custom HUD layouts in the actual profile.
- Custom NPC trades and raid entity lists can reference installed registry IDs.
- Rebind conflicting K/J controls in the RLCraft profile; the mod no longer registers an F binding.
- Xaero compatibility is deferred at MechGaming's explicit request.

Verification

- Java build, deterministic DataGen and NBT/packet/state checks run through Gradle.
- Recorded validation: 55 state/policy/packet/consent checks passed; the isolated copied RLCraft profile completed Forge initialization with 164 active server-side mod containers, including Settlements, and process exit code 0.
- The isolated Forge 2860 smoke helper leaves the actual instance unchanged and tests production construction/pre-init/init/post-init using an unstarted server object. It starts no world or listener and does not exercise server-starting callbacks.
- Complete live-world, full-pack HUD, entity interaction and two-client verification remains required before release; use `docs/Verification.md`.
'@
$notes['99 Mechanics\12 Saving and Server Authority.md'] = @'
Rule

- The server owns persistent settlement state and validates every requested mutation.

Saved records

- `settlements_world`: claims, proposals, zones, membership, inboxes and queued placed-block decay.
- `settlements_features`: NPC rooms, explicit blueprints, raids, wars, pending restoration and payouts.
- Records live in overworld `WorldSavedData` and carry dimension IDs.
- Static client snapshots never determine ownership or grant operator permissions.

Checks

- Network handlers schedule work on the logical server thread.
- Actions check permission, loaded positions, dimensions, distance, counts, size limits and rate limits.
- Placement/replacement costs happen only after successful validation; disband requires confirmation.
- Blueprint capture/restoration omits container inventories and unsafe block types.

Test

- [ ] Save/quit/reopen during proposals, pending inbox requests, raids, wars, decay and offline payouts.
- [ ] Verify a client cannot change someone else's claim by selecting a different UUID.
'@

New-Item -ItemType Directory -Path $NotesRoot -Force | Out-Null
$folders = @('', '02 Blocks', '11 Tools', '15 Mobs', '99 Mechanics')
foreach ($folder in $folders) {
    $directory = if ($folder) { Join-Path $NotesRoot $folder } else { $NotesRoot }
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $brainstorm = Join-Path $directory '00 Brainstorming.md'
    if (-not (Test-Path -LiteralPath $brainstorm)) { [System.IO.File]::WriteAllText($brainstorm, '', $utf8) }
}
foreach ($entry in $notes.GetEnumerator()) {
    $destination = Join-Path $NotesRoot $entry.Key
    if (Test-Path -LiteralPath $destination) {
        # Original idea notes are never listed above. Preserve any previous generated-note copy.
        $backup = Join-Path $projectRoot ('build\note-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '\' + $entry.Key)
        New-Item -ItemType Directory -Path (Split-Path -Parent $backup) -Force | Out-Null
        Copy-Item -LiteralPath $destination -Destination $backup
    }
    [System.IO.File]::WriteAllText($destination, $entry.Value.Trim() + "`n", $utf8)
}

New-Item -ItemType Directory -Path $AssetsRoot -Force | Out-Null
$assetReadme = @'
# Settlements assets

Mod: **Settlements**. Author: **MechGaming**.

This directory is for editable artwork, texture sources, models supplied by the artist, screenshots, audio and other media that do not belong in the code project. Existing shortcuts are preserved.

No textures, raster images, entity meshes or block geometry were created for the current implementation. All placeholders reuse existing Minecraft artwork. See `Placeholder Manifest.md` for replacement paths.

Keep layered/source art here. Copy only approved runtime assets into the mod's `src/main/resources/assets/settlements` directory. Java DataGen owns generated JSON and `.lang`; add overrides in `src/main/resources` instead of editing `src/generated/resources`.
'@
$manifest = @'
# Placeholder manifest

| Feature | Current reference | Replacement location |
| --- | --- | --- |
| Settlement flag world block | Existing `minecraft:block/white_wool` geometry and texture | Override `assets/settlements/blockstates/settlement_flag.json` and reference an artist-supplied runtime model, or change its generated reference |
| Settlement flag inventory | Generated parent reference to existing `minecraft:block/white_wool` | `assets/settlements/models/item/settlement_flag.json` override, generated from the provider |
| Developer tool icon | Existing `minecraft:items/blaze_rod` sprite | Artist texture `assets/settlements/textures/items/dev_tool.png`, then change the generator's `layer0` reference |
| Resident NPC | `EntitySettler` extends the vanilla villager and uses its existing renderer and skin | An artist-supplied runtime renderer/asset integration; no custom entity model currently exists |
| Planner/raised hand | Existing Minecraft item sprites and ordinary UI drawing | Client UI references can be replaced after the artist supplies an approved icon |
| Boundaries / blueprint holograms | Colored wireframes drawn in code | Client rendering colors and line styles; no media file required |

The Java DataGen source creates references and data only. It creates no texture files or original 3D geometry. Runtime JSON filenames above are generated or intentional resource overrides; author resource changes through the generator.

Code project: `C:\Users\Owner\Downloads\Modded Minecraft\Settlements\Settlements-1.12.2-Forge`.
'@
[System.IO.File]::WriteAllText((Join-Path $AssetsRoot 'README.md'), $assetReadme.Trim() + "`n", $utf8)
[System.IO.File]::WriteAllText((Join-Path $AssetsRoot 'Placeholder Manifest.md'), $manifest.Trim() + "`n", $utf8)
Write-Output "Wrote $($notes.Count) procedure/feature notes, preserved empty brainstorming notes, and wrote two asset reference notes."
