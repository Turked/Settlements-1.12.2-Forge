# Verification

## Automated checks

- `generateResources` generates mod-owned resource data from Java source and imports supplied artist model exports.
- `verifyGeneratedResources` verifies deterministic output against the generator.
- `verifySettlementState` exercises actual Minecraft compressed NBT and packet codecs: geometry edges, dimensions, claim identity, membership, inbox, proposal expiry/support, zones, whole-world reload, radius bounds, packet round trips and oversized request rejection.
- `build` compiles with Java 8 against Forge 1.12.2 and reobfuscates the installable jar.

Run `.\gradlew.bat build` with a Java 8 JDK to compile, generate resources, run the checks, and produce the reobfuscated jar. This command does not launch Minecraft or run the lifecycle tests described below.

## Recorded results — 2026-10-18

- Java 8 `build`: passed; installable jar reobfuscated.
- State/packet checks: 26 passed.
- Zone policy/invalid-state checks: 18 passed.
- War-consent/deferred-state checks: 11 passed.
- Seven generated JSON documents parsed successfully; mod identity/author, recipe IDs and Minecraft asset references verified.
- Standalone Forge 2860 production lifecycle: passed, process exit code 0. External developer tools exercised construction, pre-init, init, post-init, registry freezing and generated NPC definition installation using an unstarted server object.
- RLCraft 2.9.3 production lifecycle: passed, process exit code 0. External developer tools copied the profile's 180 mod jars/configs into an isolated test folder; Forge selected and initialized 164 server-side mod containers, including Settlements. No world, network listener or server-starting callbacks were run. These initialization tests are separate from the Gradle checks.
- Packaged jar inspection: correct Settlements/MechGaming metadata and runtime classes only. No original textures or 3D geometry were generated.
- K control revision: Java 8 build/reobfuscation, all 55 existing state/policy/packet/consent checks and deterministic DataGen passed again. The generated control name is now Settlements. The updated client key behavior still needs the interactive checks below; the earlier server lifecycle runs do not exercise client controls.

## Interactive checks before distributing a release

- [ ] Load the jar in a separate RLCraft 2.9.3 test profile and reach the main menu.
- [ ] Create a disposable world; check the two registry entries, generated recipe, names and Minecraft asset references.
- [ ] Rebind conflicting K/J controls. Check that the binding is named Settlements, K toggles the flag outside claims, and K opens the current claim's menu for owners/members/visitors even after walking in with the planner equipped. Proposals should still use the planner. Verify F releases an equipped planner and swaps the real offhand normally.
- [ ] Test virtual slots and Q/E/Escape/chat exit without moving or dropping real items, including a remapped Settlements key.
- [ ] Found a settlement; save/quit/reopen; verify claim, owner, members, flag and settings persist.
- [ ] Use two clients to test willing-player thresholds, stale/offline supporters, inbox approval/rejection and nonmember denial.
- [ ] Test zones at negative coordinates, dimension changes, overlapping zones, bucket use, multiple-block placement and restricted-area decay.
- [ ] Verify operator-only actions are rejected for ordinary players, including crafted network requests.
- [ ] Register an enclosed NPC room; remove required furniture; check move-in, move-out, trades and replacement after death.
- [ ] Test blueprint material costs and missing-block preview; verify containers and multipart blocks are excluded.
- [ ] Enable raids in the disposable world; verify spawn ring/zones, reward, opt-in destruction and restoration without duplicate materials.
- [ ] Test leader agreement, roster changes, PvP opt-out, lives/beds conditions, timeouts and restart persistence with two settlements.

Compilation, mod initialization and model references do not establish live-world compatibility with every interaction between the 180 installed RLCraft mods. Keep the exact tested scope with release notes.

## Project cleanup — 2026-10-19

- Java 8 clean build and reobfuscation passed after renaming the namespace to `com.MechGaming.settlements`.
- All 55 existing state, policy, packet and consent checks passed; DataGen verified eight resources, including seven JSON documents.
- The production jar contains 49 runtime classes under `com/MechGaming/settlements`, with no stale package paths, PowerShell, example-mod classes or test helpers.
- The renamed production jar passed external Forge 2860 construction, pre-init, init and post-init validation with exit code 0. The check started no world or listener and did not test gameplay.
- Personal tooling, lifecycle-test helpers and their output are stored outside the project. The project builds directly through the Gradle wrapper.

## Flag model integration — 2026-10-19

- Java 8 build and reobfuscation passed. All 55 existing state, policy, packet and consent checks passed.
- DataGen verified nine deterministic resources, including eight JSON documents. Minecraft's actual 1.12 model parser accepted the supplied flag model; geometry, UVs, face rotations and supported display transforms match the artist export.
- The supplied model export and red fabric PNG were copied unchanged. Only generated model references and unsupported legacy metadata were adapted; no new artwork was authored.
- The headless texture check exercised Minecraft's resource-path resolver, custom sprite registration/loading and atlas stitcher with a neighboring vanilla oak sprite. It verified the runtime nearest-neighbor pixel mapping, mip-aligned dimensions, normalized UV coverage and separate atlas placement.
- Actual GPU upload, mipmap rendering and the flag's appearance in the world, planner and inventory still require an in-game check. The interactive RLCraft and multiplayer checklist above remains open.
