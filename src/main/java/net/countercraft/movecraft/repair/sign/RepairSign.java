package net.countercraft.movecraft.repair.sign;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.mapUpdater.update.UpdateCommand;
import net.countercraft.movecraft.repair.MovecraftRepair;
import net.countercraft.movecraft.repair.config.Config;
import net.countercraft.movecraft.repair.localisation.I18nSupport;
import net.countercraft.movecraft.repair.repair.Repair;
import net.countercraft.movecraft.repair.repair.RepairManager;
import net.countercraft.movecraft.repair.utils.MovecraftRepairLocation;
import net.countercraft.movecraft.repair.utils.Pair;
import net.countercraft.movecraft.repair.utils.UpdateCommandsQueuePair;
import net.countercraft.movecraft.repair.utils.WEUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;


public class RepairSign implements Listener {
    private final String HEADER = "Repair:";
    private static final ArrayList<Character> ILLEGAL_CHARACTERS = new ArrayList<>();//{};
    private final HashMap<UUID, Long> playerInteractTimeMap = new HashMap<>();//Players must be assigned by the UUID, or NullPointerExceptions are thrown
    static {
        ILLEGAL_CHARACTERS.add('/');
        ILLEGAL_CHARACTERS.add('\\');
        ILLEGAL_CHARACTERS.add(':');
        ILLEGAL_CHARACTERS.add('*');
        ILLEGAL_CHARACTERS.add('?');
        ILLEGAL_CHARACTERS.add('\"');
        ILLEGAL_CHARACTERS.add('<');
        ILLEGAL_CHARACTERS.add('>');
        ILLEGAL_CHARACTERS.add('|');
    }
    @EventHandler
    public void onSignChange(SignChangeEvent event){
        if (!ChatColor.stripColor(event.getLine(0)).equalsIgnoreCase(HEADER)){
            return;
        }
        //Clear the repair sign if second line is empty
        if (event.getLine(1).isEmpty()){
            event.getPlayer().sendMessage("You must specify a repair state name on second line");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event){
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        BlockState state = event.getClickedBlock().getState();
        if (!(state instanceof Sign)) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        String signText = ChatColor.stripColor(sign.getLine(0));
        if (signText == null) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            signRightClick(event);
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            signLeftClick(event);
        }

    }

    private void signLeftClick(PlayerInteractEvent event){
        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(HEADER) || sign.getLine(0) == null){
            return;
        }
        if (Config.RepairTicksPerBlock == 0) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Repair functionality is disabled or WorldEdit was not detected"));
            return;
        }
        PlayerCraft pCraft = CraftManager.getInstance().getCraftByPlayer(event.getPlayer());
        if (pCraft == null) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("You must be piloting a craft"));
            return;
        }
        if (!event.getPlayer().hasPermission("movecraft." + pCraft.getType().getCraftName() + ".repair")){
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }

        WEUtils weUtils = MovecraftRepair.getInstance().getWEUtils();
        event.setCancelled(true);
        if (weUtils.saveCraftRepairState(pCraft, sign)) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Repair - State saved"));
            return;
        }
        event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Repair - Could not save file"));
    }

    private void signRightClick(PlayerInteractEvent event){
        Sign sign = (Sign) event.getClickedBlock().getState();
        Player p = event.getPlayer();
        PlayerCraft pCraft = CraftManager.getInstance().getCraftByPlayer(p);
        if (!sign.getLine(0).equalsIgnoreCase(HEADER)){
            return;
        }
        if (pCraft == null){
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("You must be piloting a craft"));
            return;
        }

        if (Config.RepairTicksPerBlock == 0) {
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Repair functionality is disabled or WorldEdit was not detected"));
            return;
        }

        if (!event.getPlayer().hasPermission("movecraft." + pCraft.getType().getCraftName() + ".repair")){
            event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return;
        }

        String repairName = event.getPlayer().getUniqueId().toString();
        repairName += "_";
        repairName += ChatColor.stripColor(sign.getLine(1));
        WEUtils weUtils = MovecraftRepair.getInstance().getWEUtils();
        Clipboard clipboard = weUtils.loadCraftRepairStateClipboard(pCraft, sign);


        if (clipboard == null){
            p.sendMessage(I18nSupport.getInternationalisedString("Repair - State not found"));
            return;
        } //if clipboard is not null
        long numDifferentBlocks = weUtils.getNumDiffBlocks(repairName);
        boolean secondClick = false;
        if (playerInteractTimeMap.containsKey(p.getUniqueId())) {
            if (System.currentTimeMillis() - playerInteractTimeMap.get(p.getUniqueId()) < 5000) {
                    secondClick = true;
            }
        }
        HashMap<Pair<Material, Byte>, Double> numMissingItems = weUtils.getMissingBlocks(repairName);
        ArrayDeque<MovecraftRepairLocation> locMissingBlocks = weUtils.getMissingBlockLocations(repairName);
        int totalSize = locMissingBlocks.size() + pCraft.getHitBox().size();
        if (secondClick){
            // check all the chests for materials for the repair
            HashMap<Material, ArrayList<InventoryHolder>> chestsToTakeFrom = new HashMap<>(); // typeid, list of chest inventories
            boolean enoughMaterial = true;
            for (Pair<Material, Byte> type : numMissingItems.keySet()) {
                long longRemQty = Math.round(numMissingItems.get(type));
                int remainingQty = (int) longRemQty;
                ArrayList<InventoryHolder> chests = new ArrayList<>();
                for (MovecraftLocation loc : pCraft.getHitBox()) {
                    Block b = pCraft.getWorld().getBlockAt(loc.getX(), loc.getY(), loc.getZ());
                    if ((b.getType() == Material.CHEST) || (b.getType() == Material.TRAPPED_CHEST)) {
                        InventoryHolder inventoryHolder = (InventoryHolder) b.getState();
                        if (inventoryHolder.getInventory().contains(type.getLeft()) && remainingQty > 0) {
                            HashMap<Integer, ? extends ItemStack> foundItems = inventoryHolder.getInventory().all(type.getLeft());
                            // count how many were in the chest
                            int numfound = 0;
                            for (ItemStack istack : foundItems.values()) {
                                //Check data value if it is coal
                                if (istack.getType().equals(Material.COAL) && istack.getData().getData() != type.getRight()){
                                    continue;
                                }
                                numfound += istack.getAmount();
                            }
                            remainingQty -= numfound;
                            chests.add(inventoryHolder);
                        }
                    }
                }
                if (remainingQty > 0) {
                    if (type.getLeft().equals(Material.COAL)) {
                        event.getPlayer().sendMessage(String.format(I18nSupport.getInternationalisedString("Repair - Need more of material") + ": %s - %d", type.getRight() == 1 ? "charcoal" : "coal", remainingQty));
                    } else {
                        event.getPlayer().sendMessage(String.format(I18nSupport.getInternationalisedString("Repair - Need more of material") + ": %s - %d", type.getLeft().name().toLowerCase().replace("_", " "), remainingQty));
                    }

                    enoughMaterial = false;
                } else {
                    chestsToTakeFrom.put(type.getLeft(), chests);
                }
            }
            if (MovecraftRepair.getInstance().getEconomy() != null && enoughMaterial) {
                double moneyCost = numDifferentBlocks * Config.RepairMoneyPerBlock;
                if (MovecraftRepair.getInstance().getEconomy().has(event.getPlayer(), moneyCost)) {
                    MovecraftRepair.getInstance().getEconomy().withdrawPlayer(event.getPlayer(), moneyCost);
                } else {
                    event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Economy - Not Enough Money"));
                    enoughMaterial = false;
                }
            }
            if (enoughMaterial) {
                // we know we have enough materials to make the repairs, so remove the materials from the chests
                for (Pair<Material, Byte> type : numMissingItems.keySet()) {
                    int remainingQty = (int) Math.round(numMissingItems.get(type));
                    for (InventoryHolder inventoryHolder : chestsToTakeFrom.get(type.getLeft())) {
                        HashMap<Integer, ? extends ItemStack> foundItems = inventoryHolder.getInventory().all(type.getLeft());
                        for (ItemStack istack : foundItems.values()) {
                            if (istack.getType().equals(Material.COAL) && istack.getData().getData() != type.getRight()){
                                continue;
                            }
                            if (istack.getAmount() <= remainingQty) {
                                remainingQty -= istack.getAmount();
                                inventoryHolder.getInventory().removeItem(istack);
                            } else {
                                istack.setAmount(istack.getAmount() - remainingQty);
                                remainingQty = 0;
                            }
                        }
                    }
                }

                double cost = numDifferentBlocks * Config.RepairMoneyPerBlock;
                Movecraft.getInstance().getLogger().info(String.format(I18nSupport.getInternationalisedString("Repair - Repair Has Begun"),event.getPlayer().getName(),cost));
                final UpdateCommandsQueuePair updateCommandsPair = weUtils.getUpdateCommands(clipboard, sign.getWorld(), locMissingBlocks);
                final LinkedList<UpdateCommand> updateCommands = updateCommandsPair.getUpdateCommands();
                final LinkedList<UpdateCommand> updateCommandsFragileBlocks = updateCommandsPair.getUpdateCommandsFragileBlocks();
                if (!updateCommands.isEmpty() || !updateCommandsFragileBlocks.isEmpty()) {
                    final Craft releaseCraft = pCraft;
                    CraftManager.getInstance().removePlayerFromCraft(pCraft);
                    RepairManager repairManager = MovecraftRepair.getInstance().getRepairManager();
                    repairManager.getRepairs().add(new Repair(sign.getLine(1), releaseCraft, updateCommands, updateCommandsFragileBlocks,  p.getUniqueId(), numDifferentBlocks, sign.getLocation()));
                }
            }
        } else {
            float percent = ((float) numDifferentBlocks / (float) totalSize) * 100;
            p.sendMessage(I18nSupport.getInternationalisedString("Repair - Total damaged blocks") + ": " + numDifferentBlocks);
            p.sendMessage(I18nSupport.getInternationalisedString("Repair - Percentage of craft") + ": " + percent);
            if (percent > Config.RepairMaxPercent){
                p.sendMessage(I18nSupport.getInternationalisedString("Repair - Failed Craft Too Damaged"));
                return;
            }
            if (numDifferentBlocks != 0) {
                event.getPlayer().sendMessage(I18nSupport.getInternationalisedString("Repair - Supplies needed"));
                for (Pair<Material, Byte> blockType : numMissingItems.keySet()) {
                    if (blockType.getLeft().equals(Material.COAL)) {
                        event.getPlayer().sendMessage(String.format("%s : %d", blockType.getRight() == 1 ? "charcoal" : "coal" , Math.round(numMissingItems.get(blockType))));
                    } else {
                        event.getPlayer().sendMessage(String.format("%s : %d", blockType.getLeft().name().toLowerCase().replace("_", " "), Math.round(numMissingItems.get(blockType))));
                    }
                }
                long durationInSeconds = numDifferentBlocks * Config.RepairTicksPerBlock / 20;
                event.getPlayer().sendMessage(String.format(I18nSupport.getInternationalisedString("Repair - Seconds to complete repair") + ": %d", durationInSeconds));
                int moneyCost = (int) (numDifferentBlocks * Config.RepairMoneyPerBlock);
                event.getPlayer().sendMessage(String.format(I18nSupport.getInternationalisedString("Repair - Money to complete repair") + ": %d", moneyCost));
                playerInteractTimeMap.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
            }
        }
    }
}
