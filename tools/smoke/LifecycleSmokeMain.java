package com.mechgaming.settlements.lifecycle;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.server.FMLServerHandler;

/** Loads real production jars without starting a server thread, world or network listener. */
public final class LifecycleSmokeMain {
    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread lifecycle = new Thread(SidedThreadGroups.SERVER, () -> {
            try { runLifecycle(); } catch (Throwable error) { failure.set(error); }
        }, "Server thread");
        lifecycle.start();
        lifecycle.join();
        if (failure.get() != null) throw new RuntimeException("Dedicated-side mod lifecycle failed", failure.get());
    }

    private static void runLifecycle() throws Exception {
        if (FMLCommonHandler.instance().getEffectiveSide() != Side.SERVER) throw new AssertionError("Logical server thread required");
        Class<?> bootstrap = Class.forName("net.minecraft.init.Bootstrap");
        method(bootstrap, "func_151354_b", "register").invoke(null);
        Object server = constructUnstartedServer();
        FMLServerHandler handler = FMLServerHandler.instance();
        handler.getClass().getMethod("beginServerLoading", Class.forName("net.minecraft.server.MinecraftServer")).invoke(handler, server);
        handler.finishServerLoading();
        ModContainer mod = Loader.instance().getIndexedModList().get("settlements");
        if (mod == null || !"Settlements".equals(mod.getName())) throw new AssertionError("Settlements did not load");
        if (!new File("config/settlements/npcs/settler.json").isFile()) throw new AssertionError("Generated NPC resource was not installed during post-init");
        System.out.println("SETTLEMENTS_LIFECYCLE_SMOKE_OK version=" + mod.getVersion() + " loadedMods=" + Loader.instance().getActiveModList().size());
        System.out.println("Scope: dedicated-side production CONSTRUCT/PREINIT/INIT/POSTINIT, registry freeze and resource installation; no server/world startup or gameplay.");
        // Some pack mods keep non-daemon updater/watchdog threads. Use Forge's
        // normal shutdown API after the test completes; never halt a game world.
        FMLCommonHandler.instance().exitJava(0, false);
    }

    private static Object constructUnstartedServer() throws Exception {
        Class<?> fixes = Class.forName("net.minecraft.util.datafix.DataFixesManager");
        Object fixer = method(fixes, "func_188279_a", "createFixer").invoke(null);
        Class<?> authType = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService");
        Object auth = authType.getConstructor(Proxy.class, String.class).newInstance(Proxy.NO_PROXY, UUID.randomUUID().toString());
        Object session = authType.getMethod("createMinecraftSessionService").invoke(auth);
        Object repository = authType.getMethod("createProfileRepository").invoke(auth);
        Class<?> cacheType = Class.forName("net.minecraft.server.management.PlayerProfileCache");
        Object cache = cacheType.getConstructors()[0].newInstance(repository, new File("smoke-usercache.json"));
        Class<?> dedicated = Class.forName("net.minecraft.server.dedicated.DedicatedServer");
        for (Constructor<?> constructor : dedicated.getConstructors()) {
            if (constructor.getParameterTypes().length == 6) {
                return constructor.newInstance(new File("smoke-unstarted-saves"), fixer, auth, session, repository, cache);
            }
        }
        throw new IllegalStateException("Unexpected Minecraft 1.12.2 server constructor");
    }
    private static Method method(Class<?> owner, String productionName, String developmentName) throws Exception {
        try { return owner.getMethod(productionName); }
        catch (NoSuchMethodException ignored) { return owner.getMethod(developmentName); }
    }
}
