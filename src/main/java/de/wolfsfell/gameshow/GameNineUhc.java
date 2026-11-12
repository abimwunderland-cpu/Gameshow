package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Kit-UHC finale: protects original arena while allowing temporary building. */
final class GameNineUhc implements Listener {
    private final GameshowPlugin plugin;
    private final Set<String> placed = new HashSet<>();
    private final Set<String> fluids = new HashSet<>();
    private final Map<UUID, SavedPlayer> backups = new HashMap<>();
    private final Map<UUID, Location> fighterSpawns = new HashMap<>();
    private final Set<UUID> fighters = new HashSet<>();
    private BukkitTask countdown;
    private boolean prepared, active, finished;
    private int sequence;
    private String winner;
    private Boolean previousPvp;
    private World pvpWorld;

    private record SavedPlayer(ItemStack[] storage, ItemStack[] armor, ItemStack offhand,
                               GameMode mode, double health, int food, float saturation,
                               boolean allowFlight, boolean flying, Location original) {}

    GameNineUhc(GameshowPlugin plugin){this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);}
    private void tell(CommandSender sender,String message){sender.sendMessage("§d[Kit-UHC] §f"+message);}
    private void announce(String message){Bukkit.broadcastMessage("§d§l[WOLFSFELL FINALE] §r"+message);}
    private Location point(String key){return plugin.uhcLocation("game9-"+key);}
    boolean isActive(){return prepared||active;}
    private String key(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private boolean inside(Location location){
        Location a=point("corner-a"),b=point("corner-b");
        if(a==null||b==null||location==null||location.getWorld()==null||!a.getWorld().equals(b.getWorld())||!location.getWorld().equals(a.getWorld()))return false;
        return location.getBlockX()>=Math.min(a.getBlockX(),b.getBlockX())&&location.getBlockX()<=Math.max(a.getBlockX(),b.getBlockX())
            &&location.getBlockZ()>=Math.min(a.getBlockZ(),b.getBlockZ())&&location.getBlockZ()<=Math.max(a.getBlockZ(),b.getBlockZ());
    }
    private boolean configured(CommandSender s){
        Location a=point("corner-a"),b=point("corner-b"),x=point("spawn1"),y=point("spawn2"),z=point("spectator");
        if(a==null||b==null||x==null||y==null||z==null){tell(s,"Setup fehlt: /wg wand game9 (2 Ecken) und /wg set game9-spawn1, game9-spawn2, game9-spectator.");return false;}
        if(!a.getWorld().equals(b.getWorld())||!inside(x)||!inside(y)){tell(s,"Beide Spawns müssen innerhalb der markierten Arena in derselben Welt liegen.");return false;}
        // Barrier observation platforms may be directly above the arena.
        if(z.getWorld()==null){tell(s,"Ungültiger Zuschauerpunkt.");return false;}
        if(!plugin.uhcFinaleStarted()||plugin.uhcFinalists().size()!=2){tell(s,"Zuerst /wg finishround nach Spiel 5 durchführen.");return false;}
        return true;
    }
    private List<Player> selected(CommandSender s){
        List<Player> out=new ArrayList<>();
        for(String team:plugin.uhcFinalists()){
            UUID id=plugin.uhcSelected(team);
            Player p=id==null?null:Bukkit.getPlayer(id);
            if(p==null||!plugin.uhcMembers(team).contains(id)){tell(s,"Für "+team+" fehlt ein gewählter Online-Kämpfer. /wg select on und Teamkandidat wählen.");return List.of();}
            out.add(p);
        }
        if(out.size()!=2||out.get(0).equals(out.get(1))){tell(s,"Es müssen zwei unterschiedliche Kämpfer sein.");return List.of();}
        return out;
    }
    void teleport(CommandSender s){
        if(!configured(s))return;
        if(isActive()){tell(s,"Kampf läuft schon.");return;}
        List<Player> duel=selected(s);if(duel.size()!=2)return;
        for(int i=0;i<duel.size();i++){
            Player p=duel.get(i);
            Location spawn=point("spawn"+(i+1));
            if(p.getGameMode()==GameMode.SPECTATOR)p.setGameMode(GameMode.ADVENTURE);
            p.teleport(spawn);p.setFallDistance(0);
        }
        Location stand=point("spectator");
        for(Player p:Bukkit.getOnlinePlayers())if(!duel.contains(p)){
            p.teleport(stand);
            if(!p.hasPermission("wg.admin"))p.setGameMode(GameMode.SPECTATOR);
        }
        tell(s,"Kämpfer am Spawn. Zuschauer auf der Tribüne. /wg game9 start startet das Finale.");
    }
    void start(CommandSender s){
        if(isActive()){tell(s,"Kit-UHC läuft bereits.");return;}
        if(!configured(s))return;
        List<Player> duel=selected(s);if(duel.size()!=2)return;
        cleanup();backups.clear();fighters.clear();fighterSpawns.clear();
        prepared=true;active=false;finished=false;winner=null;int run=++sequence;
        plugin.uhcBegin();
        pvpWorld=point("spawn1").getWorld();previousPvp=pvpWorld.getPVP();
        pvpWorld.setPVP(true);
        for(int i=0;i<duel.size();i++){
            Player p=duel.get(i);fighters.add(p.getUniqueId());
            PlayerInventory inv=p.getInventory();
            backups.put(p.getUniqueId(),new SavedPlayer(inv.getStorageContents().clone(),inv.getArmorContents().clone(),inv.getItemInOffHand().clone(),p.getGameMode(),p.getHealth(),p.getFoodLevel(),p.getSaturation(),p.getAllowFlight(),p.isFlying(),p.getLocation().clone()));
            inv.clear();inv.setArmorContents(new ItemStack[4]);inv.setItemInOffHand(new ItemStack(Material.AIR));
            p.getActivePotionEffects().forEach(effect->p.removePotionEffect(effect.getType()));
            p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(false);p.setHealth(Math.min(20,p.getMaxHealth()));p.setFoodLevel(20);p.setSaturation(20);p.setFireTicks(0);p.setFallDistance(0);
            Location spawn=point("spawn"+(i+1));fighterSpawns.put(p.getUniqueId(),spawn.clone());p.teleport(spawn);
            equip(p,plugin.uhcFinalists().get(i));
        }
        Location tribune=point("spectator");
        for(Player p:Bukkit.getOnlinePlayers())if(!fighters.contains(p.getUniqueId())){
            p.teleport(tribune);
            if(!p.hasPermission("wg.admin"))p.setGameMode(GameMode.SPECTATOR);
        }
        announce("§eKIT-UHC §7| §f"+plugin.uhcFinalists().get(0)+" §dVS §f"+plugin.uhcFinalists().get(1));
        countdown=Bukkit.getScheduler().runTaskTimer(plugin,new Runnable(){int ticks=5;
            @Override public void run(){if(run!=sequence||!prepared){if(countdown!=null)countdown.cancel();return;}
                if(ticks>0){for(Player p:Bukkit.getOnlinePlayers()){p.sendTitle("§e§l"+ticks,"§7KIT-UHC FINALE",0,22,0);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,0.75f,1.1f+(5-ticks)*0.13f);}ticks--;return;}
                prepared=false;active=true;countdown.cancel();countdown=null;
                for(Player p:Bukkit.getOnlinePlayers()){p.sendTitle("§a§lFIGHT!","§fMöge das bessere Team gewinnen!",0,35,10);p.playSound(p.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,0.45f,1.5f);}
                announce("§a§lDER KAMPF HAT BEGONNEN!");
            }
        },0L,20L);
    }
    private ItemStack enchanted(Material m,Enchantment ench,int lvl){ItemStack item=new ItemStack(m);if(ench!=null)item.addUnsafeEnchantment(ench,lvl);return item;}
    private void equip(Player p,String team){
        PlayerInventory i=p.getInventory();
        // Armor array order is boots, leggings, chestplate, helmet.
        boolean betterLegs=plugin.uhcLeggings(team),betterSword=plugin.uhcSword(team),potions=plugin.uhcPotions(team);
        i.setArmorContents(new ItemStack[]{enchanted(Material.DIAMOND_BOOTS,Enchantment.PROTECTION,1),
            enchanted(betterLegs?Material.NETHERITE_LEGGINGS:Material.DIAMOND_LEGGINGS,Enchantment.PROTECTION,1),
            enchanted(Material.DIAMOND_CHESTPLATE,Enchantment.PROTECTION,1),enchanted(Material.DIAMOND_HELMET,Enchantment.PROTECTION,1)});
        if(betterLegs)i.getLeggings().addUnsafeEnchantment(Enchantment.UNBREAKING,1);
        i.setItem(0,enchanted(betterSword?Material.NETHERITE_SWORD:Material.DIAMOND_SWORD,Enchantment.SHARPNESS,1));
        i.setItem(1,new ItemStack(Material.DIAMOND_AXE));
        i.setItem(2,enchanted(Material.DIAMOND_PICKAXE,Enchantment.EFFICIENCY,2));
        i.setItem(3,new ItemStack(Material.BOW));i.getItem(3).addUnsafeEnchantment(Enchantment.POWER,1);
        i.setItem(4,new ItemStack(Material.GOLDEN_APPLE,5));
        i.setItem(5,new ItemStack(Material.COBWEB,8));
        i.setItem(6,new ItemStack(Material.COBBLESTONE,64));
        i.setItem(7,new ItemStack(Material.WATER_BUCKET));
        i.setItem(8,new ItemStack(Material.LAVA_BUCKET));
        i.setItem(9,new ItemStack(Material.WATER_BUCKET));
        i.setItem(10,new ItemStack(Material.ARROW,16));
        i.setItem(11,new ItemStack(Material.COOKED_BEEF,16));
        i.setItemInOffHand(new ItemStack(Material.SHIELD));
        if(potions)for(int n=0;n<3;n++){
            ItemStack item=new ItemStack(Material.POTION);
            PotionMeta meta=(PotionMeta)item.getItemMeta();meta.setBasePotionType(PotionType.STRONG_HEALING);item.setItemMeta(meta);
            i.setItem(12+n,item);
        }
        p.updateInventory();
    }
    void status(CommandSender s){tell(s,"Status: "+(active?"Kampf läuft":prepared?"Countdown":finished?"beendet":"bereit")+" | Sieger: "+(winner==null?"offen":winner)+" | temporäre Blöcke: "+placed.size()+" | Flüssigkeiten: "+fluids.size());}
    void stop(CommandSender s){if(!isActive()){tell(s,"Kein aktiver Kampf.");return;}finish(null);tell(s,"Finale abgebrochen, kein Sieger eingetragen.");}
    void shutdown(){if(pvpWorld!=null&&previousPvp!=null)pvpWorld.setPVP(previousPvp);sequence++;if(countdown!=null)countdown.cancel();cleanup();for(Player p:Bukkit.getOnlinePlayers())restore(p);prepared=false;active=false;}
    private void restore(Player p){SavedPlayer old=backups.remove(p.getUniqueId());if(old==null)return;
        PlayerInventory inv=p.getInventory();inv.clear();inv.setStorageContents(old.storage());inv.setArmorContents(old.armor());inv.setItemInOffHand(old.offhand());
        p.setGameMode(old.mode());p.setAllowFlight(old.allowFlight());if(old.allowFlight())p.setFlying(old.flying());
        p.setHealth(Math.min(p.getMaxHealth(),Math.max(1,old.health())));p.setFoodLevel(old.food());p.setSaturation(old.saturation());p.setFireTicks(0);
        p.updateInventory();
    }
    private void finish(String winning){
        if(!active&&!prepared)return;
        active=false;prepared=false;finished=true;sequence++;winner=winning;
        if(countdown!=null){countdown.cancel();countdown=null;}
        Location center=winning==null?null:Bukkit.getPlayer(plugin.uhcSelected(winning))==null?null:Bukkit.getPlayer(plugin.uhcSelected(winning)).getLocation().clone();
        cleanup();
        for(UUID id:fighters){Player p=Bukkit.getPlayer(id);if(p!=null&&!p.isDead())p.setGameMode(GameMode.ADVENTURE);}
        if(pvpWorld!=null && previousPvp!=null)pvpWorld.setPVP(previousPvp);
        pvpWorld=null;previousPvp=null;
        plugin.uhcEnd(winning);
        if(winning!=null)plugin.uhcMarkGameNineCompleted();
        if(winning!=null){
            announce("§6§l"+winning.toUpperCase(Locale.ROOT)+" GEWINNT DIE WOLFSFELL GAMESHOW!");
            for(Player p:Bukkit.getOnlinePlayers()){
                p.sendTitle("§6§l"+winning+" §e§lGEWINNT!","§fCHAMPIONS DER WOLFSFELL GAMESHOW",10,100,25);
                p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1);
            }
            if(center!=null){
                int offset=0;
                for(UUID id:plugin.uhcMembers(winning)){
                    Player p=Bukkit.getPlayer(id);if(p==null||fighters.contains(id))continue;
                    Location spot=center.clone().add(1.5+(offset%2)*1.5,0,(offset/2)*1.5);offset++;
                    if(!inside(spot))spot=center.clone();
                    p.setGameMode(GameMode.ADVENTURE);p.teleport(spot);
                }
                for(int t=0;t<3;t++)Bukkit.getScheduler().runTaskLater(plugin,()->{
                    if(center.getWorld()==null)return;
                    Firework f=center.getWorld().spawn(center.clone().add(0,7,0),Firework.class);
                    FireworkMeta m=f.getFireworkMeta();m.addEffect(FireworkEffect.builder().withColor(Color.PURPLE,Color.YELLOW).with(FireworkEffect.Type.BALL_LARGE).flicker(true).build());m.setPower(1);f.setFireworkMeta(m);
                },t*20L);
            }
        }else announce("§cKit-UHC wurde abgebrochen – kein Gewinner.");
        // Restore fight inventory once celebration is visible. Original items are never dropped.
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            for(UUID id:new HashSet<>(fighters)){Player p=Bukkit.getPlayer(id);if(p!=null){restore(p);if(winning!=null){if(p.getUniqueId().equals(plugin.uhcSelected(winning)))p.setGameMode(GameMode.ADVENTURE);else {p.setGameMode(GameMode.SPECTATOR);Location stand=point("spectator");if(stand!=null)p.teleport(stand);}}}}
            fighters.clear();fighterSpawns.clear();
        },60L);
    }
    private void cleanup(){
        // Remove only tracked player-placed blocks and tracked fluids, never preexisting arena blocks.
        for(String id:new HashSet<>(fluids)){Block b=lookup(id);if(b!=null&&(b.getType()==Material.WATER||b.getType()==Material.LAVA))b.setType(Material.AIR,false);}
        for(String id:new HashSet<>(placed)){Block b=lookup(id);if(b!=null)b.setType(Material.AIR,false);}
        fluids.clear();placed.clear();
    }
    private Block lookup(String id){try{String[] parts=id.split(":");World w=Bukkit.getWorld(UUID.fromString(parts[0]));return w==null?null:w.getBlockAt(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));}catch(Exception ignored){return null;}}
    private boolean participant(Player p){return fighters.contains(p.getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){
        if(!isActive()||!inside(e.getBlockPlaced().getLocation()))return;
        if(!participant(e.getPlayer())||!active){e.setCancelled(true);return;}
        placed.add(key(e.getBlockPlaced()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){
        if(!isActive()||!inside(e.getBlock().getLocation()))return;
        if(!participant(e.getPlayer())||!active||!placed.contains(key(e.getBlock()))){e.setCancelled(true);return;}
        placed.remove(key(e.getBlock()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){
        if(!isActive())return;
        Block target=e.getBlockClicked().getRelative(e.getBlockFace());
        if(inside(target.getLocation())){if(!participant(e.getPlayer())||!active||target.getType()!=Material.AIR)e.setCancelled(true);else fluids.add(key(target));}
        else if(participant(e.getPlayer()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){
        if(!isActive()||!inside(e.getBlockClicked().getLocation()))return;
        if(!participant(e.getPlayer())||!active||!fluids.contains(key(e.getBlockClicked())))e.setCancelled(true);
        else fluids.remove(key(e.getBlockClicked()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void flow(BlockFromToEvent e){
        if(!isActive())return;
        boolean from=inside(e.getBlock().getLocation()),to=inside(e.getToBlock().getLocation());
        if(from&&!to){e.setCancelled(true);return;}
        if(from&&to&&(e.getBlock().isLiquid())){
            if(!fluids.contains(key(e.getBlock()))|| (e.getToBlock().getType()!=Material.AIR && !fluids.contains(key(e.getToBlock())) && !placed.contains(key(e.getToBlock())))){e.setCancelled(true);return;}
            // Only water/lava that originated from a player's bucket may flow.
            fluids.add(key(e.getToBlock()));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(isActive()&&inside(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(isActive()&&inside(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spread(BlockSpreadEvent e){if(isActive()&&inside(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pistonExtend(BlockPistonExtendEvent e){if(isActive()&&e.getBlocks().stream().anyMatch(b->inside(b.getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pistonRetract(BlockPistonRetractEvent e){if(isActive()&&e.getBlocks().stream().anyMatch(b->inside(b.getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){if(isActive())e.blockList().removeIf(b->inside(b.getLocation()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explodeBlock(BlockExplodeEvent e){if(isActive())e.blockList().removeIf(b->inside(b.getLocation()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p)||!isActive())return;
        if(participant(p)){if(!active)e.setCancelled(true);return;}
        if(inside(p.getLocation()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pvp(EntityDamageByEntityEvent e){
        if(!isActive()||!(e.getEntity() instanceof Player victim))return;
        Player attacker=e.getDamager() instanceof Player p?p:e.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p?p:null;
        if(attacker!=null&&(!participant(attacker)||!participant(victim)||!active))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void move(PlayerMoveEvent e){
        if(!isActive()||!participant(e.getPlayer())||e.getTo()==null)return;
        if(!active){Location original=fighterSpawns.get(e.getPlayer().getUniqueId());if(original!=null&&(!e.getFrom().getWorld().equals(e.getTo().getWorld())||e.getFrom().distanceSquared(e.getTo())>0.01))e.setTo(e.getFrom());return;}
        if(!inside(e.getTo())){e.setTo(e.getFrom());if(!inside(e.getFrom()))e.getPlayer().teleport(fighterSpawns.get(e.getPlayer().getUniqueId()));}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent e){
        if(!isActive()||!participant(e.getEntity()))return;
        e.setKeepInventory(true);e.getDrops().clear();e.setKeepLevel(true);
        UUID victim=e.getEntity().getUniqueId();
        String won=null;
        for(String t:plugin.uhcFinalists())if(!Objects.equals(plugin.uhcSelected(t),victim))won=t;
        String team=won;
        Bukkit.getScheduler().runTask(plugin,()->finish(team));
        Bukkit.getScheduler().runTaskLater(plugin,()->{Player p=Bukkit.getPlayer(victim);if(p!=null&&p.isDead())p.spigot().respawn();},2L);
    }
    @EventHandler public void quit(PlayerQuitEvent e){if(active&&participant(e.getPlayer())){
        UUID victim=e.getPlayer().getUniqueId();String won=null;for(String t:plugin.uhcFinalists())if(!Objects.equals(plugin.uhcSelected(t),victim))won=t;
        String team=won;Bukkit.getScheduler().runTask(plugin,()->finish(team));
    }}
    @EventHandler public void join(PlayerJoinEvent e){
        if(backups.containsKey(e.getPlayer().getUniqueId())&&!isActive())Bukkit.getScheduler().runTaskLater(plugin,()->restore(e.getPlayer()),5L);
    }
}
