# Verification

## Automated checks

- `generateResources` generates all mod-owned resource data from Java source.
- `verifyGeneratedResources` verifies deterministic output against the generator.
- `verifySettlementState` exercises actual Minecraft compressed NBT and packet codecs: geometry edges, dimensions, claim identity, membership, inbox, proposal expiry/support, zones, whole-world reload, radius bounds, packet round trips and oversized request rejection.
- `build` compiles with Java 8 against Forge 1.12.2 and reobfuscates the installable jar.
- `tools/SmokeTest-Forge.ps1` loads the production jar against installed Forge 2860 through construction, pre-init, init and post-init, using a test-only LaunchWrapper target and an unstarted server object. It creates no server world or listener, and does not exercise server-starting callbacks. Run logs identify the exact scope and result.

## Recorded results — 2026-10-18

- Java 8 `build`: passed; installable jar reobfuscated.
- State/packet checks: 26 passed.
- Zone policy/invalid-state checks: 18 passed.
- War-consent/deferred-state checks: 11 passed.
- Seven generated JSON documents parsed successfully; mod identity/author, recipe IDs and actual Minecraft placeholder asset references verified.
- Standalone Forge 2860 production lifecycle: passed, process exit code 0. Construction, pre-init, init, post-init, registry freezing and generated NPC definition installation completed.
- Standalone lifecycle logs: `build/smoke-forge2860/20261018-211540-2276bfdb`.
- Local RLCraft 2.9.3 production lifecycle: passed, process exit code 0. Copied the profile's 180 mod jars/configs into an isolated test folder; Forge selected and successfully initialized 164 server-side mod containers, including Settlements. The harness used Forge's logical server thread group. No world, listener or server-starting callbacks were run.
- RLCraft lifecycle logs/results: `build/smoke-rlcraft2860/20261018-212209-fe9d9f5d`.
- Packaged jar inspection: correct Settlements/MechGaming metadata; zero Example Mod, verification or smoke-helper classes. No original textures or 3D geometry were generated.
- K control revision: Java 8 build/reobfuscation, all 55 existing state/policy/packet/consent checks and deterministic DataGen passed again. The generated control name is now Settlements. The updated client key behavior still needs the interactive checks below; the earlier server lifecycle runs do not exercise client controls.

## Interactive checks before distributing a release

- [ ] Load the jar in a separate RLCraft 2.9.3 test profile and reach the main menu.
- [ ] Create a disposable world; check the two registry entries, generated recipe, names and placeholder references.
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
