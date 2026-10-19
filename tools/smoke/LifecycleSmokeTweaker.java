package com.mechgaming.settlements.smoke;

import net.minecraftforge.fml.common.launcher.FMLServerTweaker;

/** Uses Forge's ordinary dedicated-side class transformers but runs a loading test. */
public final class LifecycleSmokeTweaker extends FMLServerTweaker {
    @Override public String getLaunchTarget() {
        return "com.mechgaming.settlements.lifecycle.LifecycleSmokeMain";
    }
}
