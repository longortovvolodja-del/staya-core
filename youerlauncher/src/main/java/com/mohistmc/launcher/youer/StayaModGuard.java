package com.mohistmc.launcher.youer;

import com.mohistmc.launcher.youer.util.JarModifier;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

/**
 * Staya mod guard.
 *
 * On startup, statically checks every jar in mods/ (reads zip entries,
 * never tries to load the mod) and disables the ones that cannot run on a
 * server: client-only, Fabric-only, core conflict or duplicate of a
 * built-in feature. Disabled jars are moved to mods/staya-disabled/ and
 * the reason is printed to the console log.
 */
public class StayaModGuard {

    private static final File DISABLED_DIR = new File("mods/staya-disabled");

    private static final Map<String, Reason> MOD_BLACKLIST = new HashMap<>() {{
        put("org.spongepowered.common.applaunch.AppLaunch", Reason.CORE_CONFLICT);
        //put("me.wesley1808.servercore.common.ServerCore", Reason.DUPLICATE_FEATURE);
        put("i18nupdatemod.I18nUpdateMod", Reason.CLIENT_ONLY);
        put("dev.tr7zw.skinlayers.SkinLayersMod", Reason.CLIENT_ONLY);
        put("com.biel.mod.mixin.VelocityMixin", Reason.DUPLICATE_FEATURE);
        put("optifine.Differ", Reason.CLIENT_ONLY);
        //put("org.embeddedt.modernfix.ModernFix", Reason.DUPLICATE_FEATURE);
        put("ca.spottedleaf.moonrise.neoforge.MoonriseNeoForge", Reason.DUPLICATE_FEATURE);
        put("me.steinborn.krypton.mod.server.KryptonServerInitializer", Reason.DUPLICATE_FEATURE);
        put("me.steinborn.krypton.mod.KryptonBootstrap", Reason.DUPLICATE_FEATURE);
        put("org.thinkingstudio.krypton_foxified.KryptonFoxified", Reason.DUPLICATE_FEATURE);
        put("one.pkg.mod.krypton_fnp.NeoModBootstrap", Reason.DUPLICATE_FEATURE);
        put("one.pkg.kfnp.NeoModBootstrap", Reason.DUPLICATE_FEATURE);
        put("net.caffeinemc.mods.lithium.neoforge.LithiumNeoForgeMod", Reason.DUPLICATE_FEATURE);
        put("net.caffeinemc.mods.sodium.service.SodiumWorkarounds", Reason.CLIENT_ONLY);
        put("me.jellysquid.mods.lithium.common.LithiumMod", Reason.DUPLICATE_FEATURE);
        //put("com.bawnorton.neruina.Neruina", Reason.DUPLICATE_FEATURE);
        put("ca.spottedleaf.starlight.common.ScalableLuxEntrypoint", Reason.DUPLICATE_FEATURE);
        //put("me.drex.antixray.neoforge.AntiXrayMod", Reason.DUPLICATE_FEATURE);
        put("dev.uncandango.alltheleaks.AllTheLeaks", Reason.DUPLICATE_FEATURE);
        put("com.yshs.searchonmcmod.SearchOnMcmod", Reason.CLIENT_ONLY);
        put("eu.midnightdust.cullleaves.neoforge.CullLeavesClientForge", Reason.CLIENT_ONLY);
        put("net.xolt.freecam.forge.FreecamForge", Reason.CLIENT_ONLY);
        put("com.buuz135.smithingtemplateviewer.SmithingTemplateViewer", Reason.CLIENT_ONLY);
        put("com.leclowndu93150.particular.Main", Reason.CLIENT_ONLY);
        put("dev.imb11.sounds.loaders.neoforge.SoundsNeoForge", Reason.CLIENT_ONLY);
        put("me.drex.crashexploitfixer.neoforge.CrashExploitFixerNeoforge", Reason.DUPLICATE_FEATURE);
        put("me.pepperbell.continuity.client.ContinuityClient", Reason.CLIENT_ONLY);
        put("link.e4mc.neoforge.E4mcClientNeoForge", Reason.DUPLICATE_FEATURE);
        put("org.adde0109.pcf.PCFNeo", Reason.DUPLICATE_FEATURE);
        put("com.leclowndu93150.threadtweak.ThreadTweak", Reason.DUPLICATE_FEATURE);
        put("ru.vidtu.ksyxis.platform.KNeoForge", Reason.DUPLICATE_FEATURE);
        //put("carpet.CarpetServer", Reason.DUPLICATE_FEATURE);
        put("io.github.reserveword.imblocker.IMBlocker", Reason.CLIENT_ONLY);
        put("snownee.pdgamerules.PDGameRulesMod", Reason.DUPLICATE_FEATURE);
        put("com.xinian.KryptonHybrid.kryptonhybrid", Reason.DUPLICATE_FEATURE);
        put("com.wfphantom.stfudisconnect.STFUDisconnect", Reason.DUPLICATE_FEATURE);
        put("io.github.steveplays28.noisium.neoforge.NoisiumNeoForge", Reason.DUPLICATE_FEATURE);
        put("fi.dy.masa.tweakeroo.Tweakeroo", Reason.CLIENT_ONLY);
        put("gg.essential.container.loader.stage0.EssentialLoader", Reason.CLIENT_ONLY);
        put("net.jaiz.coolrain.CoolRain", Reason.CLIENT_ONLY);
        put("de.keksuccino.drippyloadingscreen.DrippyLoadingScreenNeoForge", Reason.CLIENT_ONLY);
        put("com.mr_toad.lib.core.ToadLib", Reason.CLIENT_ONLY);
        put("io.github.jamalam360.shut_up_gl_error.neoforge.ShutUpGLErrorNeoForge", Reason.CLIENT_ONLY);
        put("com.github.newvisualkeybing.NewVisualKeybing", Reason.CLIENT_ONLY);
        put("top.leonx.irisflw.IrisFlwNeoForge", Reason.CLIENT_ONLY);
        put("com.leclowndu93150.particlerain.ParticleRainClient", Reason.CLIENT_ONLY);
    }};

    public static void run() {
        if (!isEnabled()) {
            System.out.println("[Staya] Проверка модов отключена (staya-mod-guard-enabled=false)");
            return;
        }
        File modsDir = new File("mods");
        if (!modsDir.exists()) {
            modsDir.mkdir();
        }
        int checked = 0, disabled = 0, fixed = 0;
        File[] jars = modsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                try {
                    int result = checkMod(jar);
                    checked++;
                    if (result == 1) {
                        disabled++;
                    } else if (result == 2) {
                        fixed++;
                    }
                } catch (Exception e) {
                    System.out.println("[Staya] Проверка модов: не удалось проверить "
                        + jar.getName() + ": " + e.getMessage());
                }
            }
        }
        System.out.println("[Staya] Проверка модов: проверено " + checked
            + ", отключено " + disabled + ", исправлено " + fixed);
    }

    /** @return 0 = ok, 1 = disabled, 2 = fixed in place */
    private static int checkMod(File jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            if (zip.getEntry("de/bluecolored/bluemap/forge/ForgeMod.class") != null) {
                zip.close();
                JarModifier.removeDirectoryFromJar(jar.getAbsolutePath(), "META-INF/services");
                System.out.println("[Staya] Мод исправлен: " + jar.getName()
                    + " (убран META-INF/services)");
                return 2;
            }
            Reason reason = null;
            for (Map.Entry<String, Reason> e : MOD_BLACKLIST.entrySet()) {
                if (zip.getEntry(e.getKey().replace('.', '/') + ".class") != null) {
                    reason = e.getValue();
                    break;
                }
            }
            if (reason == null && zip.getEntry("META-INF/fabric.mod.json") != null
                && zip.getEntry("META-INF/neoforge.mods.toml") == null
                && zip.getEntry("META-INF/mods.toml") == null) {
                reason = Reason.FABRIC_ONLY;
            }
            if (reason == null) {
                return 0;
            }
            disable(jar, reason);
            return 1;
        }
    }

    private static void disable(File jar, Reason reason) {
        if (!DISABLED_DIR.isDirectory() && !DISABLED_DIR.mkdirs()) {
            System.out.println("[Staya] Мод отключён: " + jar.getName() + " (" + reason.label()
                + ") — не удалось создать mods/staya-disabled");
            return;
        }
        try {
            Files.move(jar.toPath(), new File(DISABLED_DIR, jar.getName()).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[Staya] Мод отключён: " + jar.getName() + " (" + reason.label() + ")");
        } catch (IOException e) {
            System.out.println("[Staya] Мод отключён: " + jar.getName() + " (" + reason.label()
                + ") — не удалось переместить: " + e.getMessage());
        }
    }

    private static boolean isEnabled() {
        File file = new File("server.properties");
        if (!file.isFile()) {
            return true;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException e) {
            return true;
        }
        return Boolean.parseBoolean(props.getProperty("staya-mod-guard-enabled", "true"));
    }

    public enum Reason {
        CORE_CONFLICT("конфликт с ядром"),
        DUPLICATE_FEATURE("дублирует функции ядра"),
        CLIENT_ONLY("мод только для клиента"),
        FABRIC_ONLY("мод только для Fabric"),
        UNKNOWN("неизвестная причина");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
