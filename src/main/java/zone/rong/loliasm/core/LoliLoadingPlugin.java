package zone.rong.loliasm.core;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.SystemUtils;
import zone.rong.loliasm.UnsafeLolis;
import zone.rong.loliasm.config.LoliConfig;
import zone.rong.loliasm.LoliLogger;
import zone.rong.loliasm.spark.LoliSparker;
import zone.rong.loliasm.vanillafix.crashes.DeobfuscatingRewritePolicy;
import zone.rong.loliasm.vanillafix.crashes.StacktraceDeobfuscator;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@IFMLLoadingPlugin.Name("LoliASM")
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
public class LoliLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public static final String VERSION = "4.13";

    public static final boolean isDeobf = FMLLaunchHandler.isDeobfuscatedEnvironment();

    static {
        if (!isDeobf && (LoliConfig.instance.sparkProfileCoreModLoading || LoliConfig.instance.sparkProfileEntireGameLoad)) {
            File modsFolder = new File(Launch.minecraftHome, "mods");
            for (File file : modsFolder.listFiles()) {
                if (file.isDirectory()) {
                    continue;
                }
                File toAddToCp = null;
                try (ZipFile jar = new ZipFile(file)) {
                    ZipEntry sparkProto = jar.getEntry("spark/spark.proto");
                    if (sparkProto != null) {
                        toAddToCp = file;
                    }
                } catch (IOException ignored) { }
                if (toAddToCp != null) {
                    try {
                        Launch.classLoader.addURL(toAddToCp.toURI().toURL());
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    // public static final boolean isModDirectorInstalled = LoliReflector.doesTweakExist("net.jan.moddirector.launchwrapper.ModDirectorTweaker");
    public static final boolean isVMOpenJ9 = SystemUtils.JAVA_VM_NAME.toLowerCase(Locale.ROOT).contains("openj9");
    public static final boolean isClient = FMLLaunchHandler.side() == Side.CLIENT;

    private static void initStacktraceDeobfuscator() {
        File modDir = new File(Launch.minecraftHome, "config/loliasm");
        modDir.mkdirs();

        // Initialize StacktraceDeobfuscator
        LoliLogger.instance.info("Initializing StacktraceDeobfuscator");
        try {
            File mappings = new File(modDir, "methods-stable_39.csv");
            if (mappings.exists()) {
                LoliLogger.instance.info("Found MCP method mappings: " + mappings.getName());
            } else {
                LoliLogger.instance.info("Downloading MCP method mappings to: " + mappings.getName());
            }
            StacktraceDeobfuscator.init(mappings);
        } catch (Exception e) {
            LoliLogger.instance.error("Failed to get MCP data!", e);
        }
        LoliLogger.instance.info("Done initializing StacktraceDeobfuscator");

        // Install the log exception deobfuscation rewrite policy
        DeobfuscatingRewritePolicy.install();
    }

    public LoliLoadingPlugin() {
        LoliLogger.instance.info("Lolis are on the {}-side.", isClient ? "client" : "server");
        LoliLogger.instance.info("Lolis are preparing and loading in mixins since Rongmario's too lazy to write pure ASM at times despite the mod being called 'LoliASM'");
        if (LoliConfig.instance.sparkProfileCoreModLoading) {
            LoliSparker.start("coremod");
        }
        if (LoliConfig.instance.sparkProfileEntireGameLoad) {
            LoliSparker.start("game");
        }
        if (LoliConfig.instance.removeForgeSecurityManager) {
            UnsafeLolis.removeFMLSecurityManager();
        }
        boolean needToDGSFFFF = isVMOpenJ9 && SystemUtils.IS_JAVA_1_8;
        int buildAppendIndex = SystemUtils.JAVA_VERSION.indexOf("_");
        if (needToDGSFFFF && buildAppendIndex != -1) {
            if (needToDGSFFFF = (Integer.parseInt(SystemUtils.JAVA_VERSION.substring(buildAppendIndex + 1)) < 265)) {
                for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                    if (arg.equals("-Xjit:disableGuardedStaticFinalFieldFolding")) {
                        needToDGSFFFF = false;
                        break;
                    }
                }
                if (needToDGSFFFF) {
                    LoliLogger.instance.fatal("LoliASM notices that you're using Eclipse OpenJ9 {}!", SystemUtils.JAVA_VERSION);
                    LoliLogger.instance.fatal("This OpenJ9 version is outdated and contains a critical bug: https://github.com/eclipse-openj9/openj9/issues/8353");
                    LoliLogger.instance.fatal("Either use '-Xjit:disableGuardedStaticFinalFieldFolding' as part of your java arguments, or update OpenJ9!");
                }
            }
        }
        if(LoliConfig.instance.crashReportImprovements) {
            initStacktraceDeobfuscator();
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return "zone.rong.loliasm.core.LoliFMLCallHook";
    }

    @Override
    public void injectData(Map<String, Object> data) { }

    @Override
    public String getAccessTransformerClass() {
        return "zone.rong.loliasm.core.LoliTransformer";
    }

    @Override
    public List<String> getMixinConfigs() {
        return isClient ? Arrays.asList(
                "mixins.devenv.json",
                "mixins.vfix_bugfixes.json",
                "mixins.vfix_crashes.json",
                "mixins.internal.json",
                "mixins.vanities.json",
                "mixins.registries.json",
                "mixins.stripitemstack.json",
                "mixins.lockcode.json",
                "mixins.recipes.json",
                "mixins.misc_fluidregistry.json",
                "mixins.forgefixes.json",
                "mixins.capability.json",
                "mixins.singletonevents.json",
                "mixins.efficienthashing.json",
                "mixins.bucket.json",
                "mixins.rendering.json",
                "mixins.datastructures_modelmanager.json",
                "mixins.screenshot.json",
                "mixins.ondemand_sprites.json",
                "mixins.searchtree_vanilla.json") :
                Arrays.asList(
                        "mixins.devenv.json",
                        "mixins.vfix_bugfixes.json",
                        "mixins.internal.json",
                        "mixins.vanities.json",
                        "mixins.registries.json",
                        "mixins.stripitemstack.json",
                        "mixins.lockcode.json",
                        "mixins.recipes.json",
                        "mixins.misc_fluidregistry.json",
                        "mixins.forgefixes.json",
                        "mixins.capability.json",
                        "mixins.singletonevents.json",
                        "mixins.efficienthashing.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if (FMLLaunchHandler.isDeobfuscatedEnvironment() && "mixins.devenv.json".equals(mixinConfig)) {
            return true;
        }
        if (isClient) {
            switch (mixinConfig) {
                case "mixins.vfix_crashes.json":
                    return LoliConfig.instance.crashReportImprovements;
                case "mixins.bucket.json":
                    return LoliConfig.instance.reuseBucketQuads;
                case "mixins.rendering.json":
                    return LoliConfig.instance.optimizeSomeRendering;
                case "mixins.datastructures_modelmanager.json":
                    return LoliConfig.instance.moreModelManagerCleanup;
                case "mixins.screenshot.json":
                    return LoliConfig.instance.releaseScreenshotCache;
                case "mixins.ondemand_sprites.json":
                    return LoliConfig.instance.onDemandAnimatedTextures;
                case "mixins.searchtree_vanilla.json":
                    return LoliConfig.instance.replaceSearchTreeWithJEISearching;
            }
        }
        switch (mixinConfig) {
            case "mixins.vfix_bugfixes.json":
                return LoliConfig.instance.fixVanillaBugs;
            case "mixins.registries.json":
                return LoliConfig.instance.optimizeRegistries;
            case "mixins.stripitemstack.json":
                return LoliConfig.instance.stripNearUselessItemStackFields;
            case "mixins.lockcode.json":
                return LoliConfig.instance.lockCodeCanonicalization;
            case "mixins.recipes.json":
                return LoliConfig.instance.optimizeFurnaceRecipeStore;
            case "mixins.misc_fluidregistry.json":
                return LoliConfig.instance.quickerEnableUniversalBucketCheck;
            case "mixins.forgefixes.json":
                return LoliConfig.instance.fixFillBucketEventNullPointerException || LoliConfig.instance.fixTileEntityOnLoadCME;
            case "mixins.capability.json":
                return LoliConfig.instance.delayItemStackCapabilityInit;
            case "mixins.singletonevents.json":
                return LoliConfig.instance.makeEventsSingletons;
            case "mixins.efficienthashing.json":
                return LoliConfig.instance.efficientHashing;
        }
        return true;
    }

}