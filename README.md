# Settlements

A Minecraft 1.12.2 Forge mod by **MechGaming**, being made for **RLCraft 2.9.3**.

Every settlement needs a flag. Place yours down, claim an area, and start building your base. Other players can ask to join, NPCs can move in, and you can turn on raids or wars if that's what you want.

**Starting a settlement**

1. Craft a Settlement Flag with 4 wool and 2 sticks.
2. Press **K** outside a settlement to equip the flag planner.
3. Select slot **1**, look at the ground, and right-click to name your settlement.
4. If the server requires more players, they stand inside the proposed area and right-click with slot **2**, the raised hand, selected.

The server checks the area, flags, and willing players before making it official. Flags are only used when the settlement is created.

**Controls**

- **K** is the Settlements button. Outside a settlement it equips or unequips the planner. Inside one it opens that settlement's menu.
- **1 / 2** or the scroll wheel switches between the flag and raised hand.
- **Right-click** with the planner to propose a settlement or support one.
- **J** shows settlement boundaries.
- **F** is still your normal offhand swap.

You can change the keys in Controls. Check for conflicts with your other RLCraft keybinds.

**What you can do**

- Claim a base and decide who gets to join. Leaders have an inbox for requests.
- Set allowed and restricted building areas with an admin tool.
- Bring in NPCs by building rooms that meet their requirements. Think Terraria. The default room needs a bed, chest, and wooden door.
- Save a blueprint, see missing blocks, and repair them using inventory materials.
- Turn on settlement raids, with optional repairs afterwards.
- Agree to wars with another settlement. Both leaders agree to the terms and members choose whether to participate.

Raids and wars are off by default. Xaero's map support is on hold for now.

**Current state**

Still in development. The build and automated checks pass, and the mod has passed an isolated RLCraft loading test. Full in-game and multiplayer testing is still needed.

The flag, tools, and NPCs use existing Minecraft art for now. Those references can be swapped out when the final assets are ready.

**Working on the mod**

Use a **Java 8 JDK** and the included Gradle wrapper. Install the same mod jar on the server and clients.

```powershell
.\tools\Build.ps1
.\tools\Build.ps1 -Tasks genIntellijRuns
```

Resource files come from Java DataGen. Edit the generator and run `tools/Build.ps1 -Tasks runData` instead of editing generated JSON.

- [Setup, configuration, and feature details](docs/Guide.md)
- [Testing checklist](docs/Verification.md)

**Ownership**

Copyright © 2026 MechGaming. All rights reserved.

This repository is source-available for viewing only. No permission is granted to copy, redistribute, modify, reupload, publish, or include this mod or its assets in modpacks or derivative projects without explicit written permission.

See [LICENSE](LICENSE).
