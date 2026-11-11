package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;

final class GameFourTarget implements Listener {
    private final GameshowPlugin plugin;
    private final NamespacedKey wandKey;
    private final File file;
    private final Map<String,Location> spawns=new HashMap<>();
    private final Map<String,Set<String>> targets=new HashMap<>();
    private final Map<String,Set<String>> remaining=new HashMap<>();
    private final Map<UUID,Deque<String>> history=new HashMap<>();
    private final Map<String,UUID> candidates=new LinkedHashMap<>();
    private final Set<UUID> spectators=new HashSet<>();
    private Location spectator, lineA, lineB;
    private final Map<String,BlockData> removedBlocks=new HashMap<>();
    private BukkitTask countdown;
    private boolean active=false, staged=false;
    GameFourTarget(GameshowPlugin plugin){this.plugin=plugin;wandKey=new NamespacedKey(plugin,"game4_wand");file=new File(plugin.getDataFolder(),"game4.yml");load();Bukkit.getPluginManager().registerEvents(this,plugin);}
    private String key(Location l){return l.getWorld().getUID()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();}
    private void msg(CommandSender s,String m){s.sendMessage("§d[Zielwasser] §f"+m);}
    void wand(Player p){ItemStack item=new ItemStack(Material.BLAZE_ROD);ItemMeta meta=item.getItemMeta();meta.setDisplayName("§dGame 4 Setup-Stab");meta.getPersistentDataContainer().set(wandKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);p.getInventory().addItem(item);msg(p,"Linksklick: Spawn 1-8 | Rechtsklick: Ziele 1-80 (je 10 pro Bahn) | Schleichen+Linksklick: letzten Eintrag rückgängig. Zuschauer: /wg game4 spectator");}
    void line(Player p,int point){if(point==1)lineA=p.getLocation().clone();else lineB=p.getLocation().clone();save();msg(p,"Schießlinie Punkt "+point+" gespeichert. Pfeile können hindurchfliegen.");}
    void spectator(Player p){spectator=p.getLocation().clone();save();msg(p,"Zuschauer-Spawn für Game 4 gespeichert.");}
    private int targetCount(){return targets.values().stream().mapToInt(Set::size).sum();}
    private String lane(int i){return Integer.toString(i);}
    @EventHandler(priority=EventPriority.HIGH) public void click(PlayerInteractEvent e){Player p=e.getPlayer();ItemStack held=e.getItem();if(held==null||!held.hasItemMeta()||!held.getItemMeta().getPersistentDataContainer().has(wandKey,PersistentDataType.BYTE))return;
        e.setCancelled(true);Action a=e.getAction();
        if(p.isSneaking()&&(a==Action.LEFT_CLICK_AIR||a==Action.LEFT_CLICK_BLOCK)){
            Deque<String> h=history.get(p.getUniqueId());if(h==null||h.isEmpty()){msg(p,"Nichts rückgängig zu machen.");return;}
            String last=h.pop();if(last.startsWith("S:")){spawns.remove(last.substring(2));}else{String[] parts=last.split(":",3);Set<String> set=targets.get(parts[1]);if(set!=null)set.remove(parts[2]);}
            save();msg(p,"Letzte Markierung rückgängig gemacht.");return;
        }
        Block b=e.getClickedBlock();if(b==null)return;
        if(a==Action.LEFT_CLICK_BLOCK){int next=spawns.size()+1;if(next>8){msg(p,"Alle 8 Spawns gesetzt. Schleichen+Linksklick zum Rückgängigmachen.");return;}String id=lane(next);spawns.put(id,b.getLocation().add(.5,1,.5));history.computeIfAbsent(p.getUniqueId(),k->new ArrayDeque<>()).push("S:"+id);save();msg(p,"Kandidaten-Spawn §e"+next+"/8 §fgespeichert.");}
        if(a==Action.RIGHT_CLICK_BLOCK){int total=targetCount();if(total>=80){msg(p,"Alle 80 Ziele gesetzt.");return;}String k=key(b.getLocation());for(Set<String> set:targets.values())if(set.contains(k)){msg(p,"Dieser Block ist bereits als Ziel markiert.");return;}
            int index=total/10+1;String id=lane(index);targets.computeIfAbsent(id,x->new LinkedHashSet<>()).add(k);history.computeIfAbsent(p.getUniqueId(),x->new ArrayDeque<>()).push("T:"+id+":"+k);save();msg(p,"Bahn §e"+index+" §f– Ziel §e"+(total%10+1)+"/10 §fgespeichert (gesamt "+(total+1)+"/80).");}
    }
    void teleport(CommandSender sender,Map<String,UUID> chosen,List<String> teamOrder){if(lineA==null||lineB==null){msg(sender,"Schießlinie fehlt: /wg game4 line1 und /wg game4 line2 setzen.");return;}if(spectator==null){msg(sender,"Zuschauer-Spawn fehlt! /wg game4 spectator am gewünschten Platz ausführen.");return;}
        Map<String,Integer> lanes=new LinkedHashMap<>();int i=0;for(String team:teamOrder){i++;lanes.put(team,i);}
        for(String team:chosen.keySet())if(!spawns.containsKey(lane(lanes.getOrDefault(team,-1)))){msg(sender,"Spawn fehlt für "+team+" (Bahn "+lanes.getOrDefault(team,-1)+").");return;}
        stop();candidates.putAll(chosen);staged=true;
        for(Player p:Bukkit.getOnlinePlayers()){String team=null;for(var entry:candidates.entrySet())if(entry.getValue().equals(p.getUniqueId())){team=entry.getKey();break;}if(team!=null){int track=lanes.get(team);p.teleport(spawns.get(lane(track)));p.sendTitle("§dZIELWASSER","§fBahn "+track+" – warte auf den Start",10,50,10);}else{p.teleport(spectator);spectators.add(p.getUniqueId());}}
        teamLanes.clear();teamLanes.putAll(lanes);msg(sender,"Kandidaten auf Bahnen 1-8 und Zuschauer in die Zuschauerbox teleportiert.");
    }
    private final Map<String,Integer> teamLanes=new HashMap<>();
    boolean start(CommandSender sender){if(!staged){msg(sender,"Erst /wg tp game 4 verwenden.");return false;}if(active||countdown!=null){msg(sender,"Spiel läuft bereits.");return false;}
        for(String team:candidates.keySet())if(targets.getOrDefault(lane(teamLanes.getOrDefault(team,-1)),Set.of()).size()!=10){msg(sender,"Team "+team+" hat "+targets.getOrDefault(lane(teamLanes.getOrDefault(team,-1)),Set.of()).size()+"/10 Ziele. Bitte vervollständigen.");return false;}
        restoreTargets();remaining.clear();for(var e:candidates.entrySet())remaining.put(e.getKey(),new HashSet<>(targets.get(lane(teamLanes.get(e.getKey())))));
        for(UUID id:candidates.values()){Player p=Bukkit.getPlayer(id);if(p!=null){p.getInventory().addItem(new ItemStack(Material.BOW));p.getInventory().addItem(new ItemStack(Material.ARROW,64));}}
        countdown=Bukkit.getScheduler().runTaskTimer(plugin,new Runnable(){int sec=5;public void run(){if(sec>0){for(UUID id:candidates.values()){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendTitle("§e"+sec,"§7Bogen bereithalten!",0,22,0);}sec--;return;}countdown.cancel();countdown=null;active=true;for(UUID id:candidates.values()){Player p=Bukkit.getPlayer(id);if(p!=null)p.sendTitle("§aLOS!","§fTriff deine 10 Ziele!",0,35,10);}Bukkit.broadcastMessage("§d[Zielwasser] §aLOS! §f10 Ziele pro Team.");}},0L,20L);
        msg(sender,"Countdown gestartet: 5 Sekunden. Kandidaten haben Bogen und Pfeile erhalten.");return true;
    }
    private double side(Location l){return (lineB.getX()-lineA.getX())*(l.getZ()-lineA.getZ())-(lineB.getZ()-lineA.getZ())*(l.getX()-lineA.getX());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void movement(PlayerMoveEvent e){
        if(!staged||lineA==null||lineB==null||e.getTo()==null||e.getFrom().getWorld()!=lineA.getWorld()||e.getTo().getWorld()!=lineA.getWorld()||!candidates.containsValue(e.getPlayer().getUniqueId()))return;
        if(!active){if(e.getFrom().distanceSquared(e.getTo())>0 && (e.getFrom().getBlockX()!=e.getTo().getBlockX()||e.getFrom().getBlockZ()!=e.getTo().getBlockZ())){Location keep=e.getFrom().clone();keep.setYaw(e.getTo().getYaw());keep.setPitch(e.getTo().getPitch());e.setTo(keep);}return;}
        String team=null;for(var ent:candidates.entrySet())if(ent.getValue().equals(e.getPlayer().getUniqueId())){team=ent.getKey();break;}
        Location spawn=spawns.get(lane(teamLanes.getOrDefault(team,-1)));
        if(spawn==null||spawn.getWorld()!=lineA.getWorld())return;
        double allowed=side(spawn), next=side(e.getTo());
        if(Math.abs(allowed)>0.01 && next*allowed<0){Location keep=e.getFrom().clone();keep.setYaw(e.getTo().getYaw());keep.setPitch(e.getTo().getPitch());e.setTo(keep);}
    }
    private Block targetBlock(String k){try{String[] a=k.split(":");World w=Bukkit.getWorld(UUID.fromString(a[0]));return w==null?null:w.getBlockAt(Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]));}catch(Exception ex){return null;}}
    private void restoreTargets(){for(var e:removedBlocks.entrySet()){Block b=targetBlock(e.getKey());if(b!=null)b.setBlockData(e.getValue(),false);}removedBlocks.clear();}
    @EventHandler(priority=EventPriority.HIGHEST) public void hit(ProjectileHitEvent e){if(!active||!(e.getEntity() instanceof Arrow arrow)||!(arrow.getShooter() instanceof Player p)||e.getHitBlock()==null)return;
        String team=null;for(var entry:candidates.entrySet())if(entry.getValue().equals(p.getUniqueId())){team=entry.getKey();break;}if(team==null)return;
        String k=key(e.getHitBlock().getLocation());Set<String> left=remaining.get(team);if(left==null||!left.remove(k))return;
        Block struck=e.getHitBlock();removedBlocks.putIfAbsent(k,struck.getBlockData().clone());struck.setType(Material.AIR,false);arrow.remove();
        int count=10-left.size();plugin.gameFourHit(team,p,count);Location pos=e.getHitBlock().getLocation().add(.5,.5,.5);pos.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,pos,15,.3,.3,.3);p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,1f,1.2f);p.sendActionBar("§dZIELWASSER §f"+count+"§7/10 Treffer");
        if(left.isEmpty()){Bukkit.broadcastMessage("§d[Zielwasser] §e"+team+" §fhat alle 10 Ziele getroffen!");active=false;staged=false;plugin.gameFourCompleted();}
    }
    void stop(){if(countdown!=null){countdown.cancel();countdown=null;}active=false;staged=false;candidates.clear();spectators.clear();remaining.clear();}
    void reset(){stop();restoreTargets();}
    void save(){YamlConfiguration y=new YamlConfiguration();y.set("spectator",spectator);y.set("lineA",lineA);y.set("lineB",lineB);for(var e:spawns.entrySet())y.set("spawns."+e.getKey(),e.getValue());for(var e:targets.entrySet())y.set("targets."+e.getKey(),new ArrayList<>(e.getValue()));try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Game 4 save failed: "+ex.getMessage());}}
    void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);spectator=y.getLocation("spectator");lineA=y.getLocation("lineA");lineB=y.getLocation("lineB");if(y.isConfigurationSection("spawns"))for(String t:y.getConfigurationSection("spawns").getKeys(false))spawns.put(t,y.getLocation("spawns."+t));if(y.isConfigurationSection("targets"))for(String t:y.getConfigurationSection("targets").getKeys(false))targets.put(t,new LinkedHashSet<>(y.getStringList("targets."+t)));}
}
