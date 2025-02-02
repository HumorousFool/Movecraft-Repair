package net.countercraft.movecraft.repair;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import net.countercraft.movecraft.repair.config.Config;
import net.countercraft.movecraft.repair.localisation.I18nSupport;
import net.countercraft.movecraft.repair.repair.RepairManager;
import net.countercraft.movecraft.repair.sign.RepairSign;
import net.countercraft.movecraft.repair.utils.WE7Utils;
import net.countercraft.movecraft.repair.utils.WEUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public final class MovecraftRepair extends JavaPlugin {
    private static MovecraftRepair instance;


    public static MovecraftRepair getInstance() {
        return instance;
    }

    private static WorldEditPlugin worldEditPlugin;
    private static Economy economy;

    private WEUtils weUtils;
    private RepairManager repairManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        Config.Debug = getConfig().getBoolean("Debug", false);

        // TODO other languages
        String[] languages = {"en"};
        for (String s : languages) {
            if (!new File(getDataFolder()  + "/localisation/mc-repairlang_"+ s +".properties").exists()) {
                this.saveResource("localisation/mc-repairlang_"+ s +".properties", false);
            }
        }
        Config.Locale = getConfig().getString("Locale", "en");
        I18nSupport.init();


        //load up WorldEdit if it's present
        Plugin wEPlugin = getServer().getPluginManager().getPlugin("WorldEdit");
        if (wEPlugin == null || !(wEPlugin instanceof WorldEditPlugin)) {
            getLogger().log(Level.SEVERE, I18nSupport.getInternationalisedString("Startup - WE Not Found"));
            return;
        }
        getLogger().log(Level.INFO, I18nSupport.getInternationalisedString("Startup - WE Found"));
        worldEditPlugin = (WorldEditPlugin) wEPlugin;


        //  TODO: Move to Movecraft-Economy class within this plugin
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                getLogger().log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Vault Found"));
            }
            else {
                getLogger().log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Vault Not Found"));
                economy = null;
                return;
            }
        } else {
            getLogger().log(Level.INFO, I18nSupport.getInternationalisedString("Startup - Vault Not Found"));
            economy = null;
            return;
        }


        Config.RepairTicksPerBlock = getConfig().getInt("RepairTicksPerBlock", 0);
        Config.RepairMaxPercent = getConfig().getDouble("RepairMaxPercent", 50);
        Config.RepairMoneyPerBlock = getConfig().getDouble("RepairMoneyPerBlock", 0.0);

        String packageName = getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf(".") + 1);
        if (Integer.parseInt(version.split("_")[1]) <= 12) {
            getLogger().log(Level.SEVERE, I18nSupport.getInternationalisedString("Startup - WE Not Found"));
        } else {
            weUtils = new WE7Utils(this);
        }

        repairManager = new RepairManager();
        repairManager.runTaskTimerAsynchronously(this, 0, 1);
        repairManager.convertOldCraftRepairStates();

        getServer().getPluginManager().registerEvents(new RepairSign(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public WorldEditPlugin getWorldEditPlugin() {
        return worldEditPlugin;
    }

    public Economy getEconomy() {
        return economy;
    }

    public RepairManager getRepairManager() {
        return repairManager;
    }

    public WEUtils getWEUtils() {
        return weUtils;
    }
}
