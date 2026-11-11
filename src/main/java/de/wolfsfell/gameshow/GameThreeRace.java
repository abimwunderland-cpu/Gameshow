package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.io.File;
import java.util.*;

/** Game 3: two-lap ice boat race. Setup through a single wand. */
final class GameThreeRace implements Listener {
    private final GameshowPlugin plugin;
    private final NamespacedKey boatKey;
    private final File file;
    private final Map<Integer,Location> starts=new HashMap<>();
    private final Map<String,UUID> assigned=new LinkedHashMap<>();
    private final Map<UUID,Integer> laps=new HashMap<>();
    private final Map<UUID,Integer> phase=new HashMap<>();
    private final Map<UUID,Long> lapStart=new HashMap<>();
    private final Map<UUID,Long> lastLapCross=new HashMap<>();
    private final Set<UUID> finished=new HashSet<>();
    private BukkitTask timer;
    private int targetLaps=2;
    private boolean debug=false;
    private boolean completed=false;
    private final Map<UUID,Long> finishes=new LinkedHashMap<>();
    private final Map<UUID,Boolean> armed=new HashMap<>();
    private final Map<UUID,Long> lastCross=new HashMap<>();
    private final Map<UUID,Location> lastPosition=new HashMap<>();
    private final Map<UUID,UUID> boats=new HashMap<>();
    private final Map<UUID,UUID> hullDisplays=new HashMap<>();
    private BukkitTask hullUpdater;
    private final Set<UUID> spectators=new HashSet<>();
    private Location driver,spectator,finishA,finishB,startA,startB,checkpointA,checkpointB,tribuneA,tribuneB;
    private int mode=0, nextStart=1;
    private boolean staged=false, racing=false;
    private BukkitTask countdown;
    private long startMillis;
    private static final String[] MODES={"BOOTE 1-8","FAHRER-WARTEPUNKT","TRIBUENE-SPAWN","TRIBUENE-ECKEN","STARTLINIE-ECKEN","CHECKPOINT-ECKEN","ZIELLINIE-ECKEN"};
    GameThreeRace(GameshowPlugin plugin){this.plugin=plugin;boatKey=new NamespacedKey(plugin,"race_boat");file=new File(plugin.getDataFolder(),"game3-race.yml");load();Bukkit.getPluginManager().registerEvents(this,plugin);}
    private void msg(org.bukkit.command.CommandSender s,String m){s.sendMessage("§d[WG Rennen] §f"+m);}
    private void all(String m){Bukkit.broadcastMessage("§d[WG Rennen] §f"+m);}
    private void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);for(int i=1;i<=8;i++){Location l=y.getLocation("starts."+i);if(l!=null)starts.put(i,l);}driver=y.getLocation("driver");spectator=y.getLocation("spectator");startA=y.getLocation("line.start.a");startB=y.getLocation("line.start.b");checkpointA=y.getLocation("line.checkpoint.a");checkpointB=y.getLocation("line.checkpoint.b");targetLaps=Math.max(1,Math.min(20,y.getInt("laps",2)));finishA=y.getLocation("finish.a");finishB=y.getLocation("finish.b");tribuneA=y.getLocation("tribune.a");tribuneB=y.getLocation("tribune.b");}
    private void save(){YamlConfiguration y=new YamlConfiguration();for(var e:starts.entrySet())y.set("starts."+e.getKey(),e.getValue());y.set("driver",driver);y.set("spectator",spectator);y.set("line.start.a",startA);y.set("line.start.b",startB);y.set("line.checkpoint.a",checkpointA);y.set("line.checkpoint.b",checkpointB);y.set("laps",targetLaps);y.set("finish.a",finishA);y.set("finish.b",finishB);y.set("tribune.a",tribuneA);y.set("tribune.b",tribuneB);try{y.save(file);}catch(Exception ex){plugin.getLogger().warning("Game3 setup save failed: "+ex.getMessage());}}
    void wand(Player p){ItemStack wand=new ItemStack(Material.BLAZE_ROD);ItemMeta m=wand.getItemMeta();m.setDisplayName("§d§lWolfsfell §fGame-3-Wand");m.setLore(List.of("§7Shift + Rechtsklick: Modus wechseln","§7Rechtsklick: Position speichern","§7Linksklick: letzte Markierung entfernen","§8Nur fuer Admins"));m.getPersistentDataContainer().set(new NamespacedKey(plugin,"game3_wand"),PersistentDataType.BYTE,(byte)1);wand.setItemMeta(m);p.getInventory().addItem(wand);msg(p,"Wand erhalten. Modus: "+MODES[mode]);}
    private boolean isWand(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"game3_wand"),PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGH) public void click(PlayerInteractEvent e){Player p=e.getPlayer();if(!p.hasPermission("wg.admin")||!isWand(e.getItem())||e.getHand()!=EquipmentSlot.HAND)return;e.setCancelled(true);boolean right=e.getAction().name().startsWith("RIGHT");if(right&&p.isSneaking()){mode=(mode+1)%MODES.length;msg(p,"Modus: §e"+MODES[mode]);return;}if(!right){undo(p);return;}Location l=p.getLocation().clone();switch(mode){case 0->{starts.put(nextStart,l);msg(p,"Boot "+nextStart+"/8 gespeichert (Blickrichtung uebernommen).");nextStart=nextStart%8+1;}case 1->{driver=l;msg(p,"Fahrer-Wartepunkt gespeichert.");}case 2->{spectator=l;msg(p,"Tribuenen-Spawn gespeichert.");}case 3->{if(tribuneA==null||tribuneB!=null){tribuneA=l;tribuneB=null;msg(p,"Tribuenen-Ecke 1 gespeichert.");}else{tribuneB=l;msg(p,"Tribuenen-Ecke 2 gespeichert.");}}case 4->{if(startA==null||startB!=null){startA=l;startB=null;msg(p,"Startlinie Punkt 1 gespeichert.");}else{startB=l;msg(p,"Startlinie Punkt 2 gespeichert.");}}case 5->{if(checkpointA==null||checkpointB!=null){checkpointA=l;checkpointB=null;msg(p,"Checkpoint Punkt 1 gespeichert.");}else{checkpointB=l;msg(p,"Checkpoint Punkt 2 gespeichert.");}}case 6->{if(finishA==null||finishB!=null){finishA=l;finishB=null;msg(p,"Ziellinie Punkt 1 gespeichert.");}else{finishB=l;msg(p,"Ziellinie Punkt 2 gespeichert.");}}}save();}
    private void undo(Player p){switch(mode){case 0->{nextStart=(nextStart+6)%8+1;starts.remove(nextStart);}case 1->driver=null;case 2->spectator=null;case 3->{if(tribuneB!=null)tribuneB=null;else tribuneA=null;}case 4->{if(startB!=null)startB=null;else startA=null;}case 5->{if(checkpointB!=null)checkpointB=null;else checkpointA=null;}case 6->{if(finishB!=null)finishB=null;else finishA=null;}}save();msg(p,"Letzte Markierung im aktuellen Modus geloescht.");}
    boolean ready(org.bukkit.command.CommandSender s){if(starts.size()!=8||driver==null||spectator==null||startA==null||startB==null||checkpointA==null||checkpointB==null||finishA==null||finishB==null||tribuneA==null||tribuneB==null){msg(s,"Setup unvollstaendig: 8 Bootsplaetze, Fahrer, Tribuene, 2 Tribuenen-Ecken sowie Startlinie, Checkpoint und Ziellinie mit je 2 Punkten markieren.");return false;}if(!finishA.getWorld().equals(finishB.getWorld())||!finishA.getWorld().equals(startA.getWorld())||!finishA.getWorld().equals(startB.getWorld())||!finishA.getWorld().equals(checkpointA.getWorld())||!finishA.getWorld().equals(checkpointB.getWorld())){msg(s,"Zielpunkte muessen in derselben Welt sein.");return false;}return true;}
    void teleport(org.bukkit.command.CommandSender sender,Map<String,UUID> selected,Map<UUID,String> teamLookup,Map<String,String> colors){if(!ready(sender))return;stop();assigned.clear();int n=0;for(var e:selected.entrySet()){Player p=Bukkit.getPlayer(e.getValue());if(p==null)continue;if(n>=8)break;assigned.put(e.getKey(),p.getUniqueId());n++;}if(assigned.isEmpty()){msg(sender,"Keine Kandidaten gewaehlt. /wg candidate set <Team> <Spieler>");return;}for(Player p:Bukkit.getOnlinePlayers()){if(assigned.containsValue(p.getUniqueId())){p.teleport(driver);p.sendMessage("§dDein Boot steht an der Startlinie. Steige in das Boot deines Teams!");}else{p.teleport(spectator);spectators.add(p.getUniqueId());}}spawnBoats(colors);staged=true;msg(sender,assigned.size()+" Fahrer zur Strecke, Zuschauer zur Tribuene teleportiert. /wg game3 start");}
    private void spawnBoats(Map<String,String> colors){removeBoats();int n=0;for(String team:assigned.keySet()){Location at=starts.get(++n);if(at==null)continue;Boat boat=(Boat)at.getWorld().spawnEntity(at,org.bukkit.entity.EntityType.OAK_BOAT);boat.setInvulnerable(true);boat.setPersistent(true);boat.setCustomName("§d"+team);boat.setCustomNameVisible(true);boat.getPersistentDataContainer().set(boatKey,PersistentDataType.STRING,team);boat.addScoreboardTag("wg_game3_boat");boat.setGlowing(true);String col=colors.getOrDefault(team,"PURPLE").toUpperCase(Locale.ROOT);org.bukkit.scoreboard.Scoreboard sb=Bukkit.getScoreboardManager().getMainScoreboard();String key="wgboat"+n;org.bukkit.scoreboard.Team score=sb.getTeam(key);if(score==null)score=sb.registerNewTeam(key);try{score.setColor(ChatColor.valueOf(col));}catch(Exception ignored){score.setColor(ChatColor.LIGHT_PURPLE);}score.addEntry(boat.getUniqueId().toString());boats.put(assigned.get(team),boat.getUniqueId());spawnColoredHull(boat,col);}
        startHullUpdater();}
    private void spawnColoredHull(Boat boat,String color){
        String c=switch(color){case "RED", "DARK_RED"->"red";case "BLUE", "DARK_BLUE"->"blue";case "GREEN", "DARK_GREEN"->"green";case "YELLOW", "GOLD"->"yellow";case "ORANGE"->"orange";case "LIGHT_PURPLE", "PINK"->"pink";case "AQUA", "DARK_AQUA", "CYAN"->"cyan";case "WHITE"->"white";default->"purple";};
        ItemStack item=new ItemStack(Material.PAPER);
        ItemMeta meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("wolfsfell","race_boat_"+c));item.setItemMeta(meta);
        Location loc=boat.getLocation().clone().add(0,0.12,0);
        ItemDisplay display=boat.getWorld().spawn(loc,ItemDisplay.class,d->{d.setItemStack(item);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.addScoreboardTag("wg_game3_hull");d.setInterpolationDuration(2);d.setTeleportDuration(1);d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(0f,-0.18f,0f),new org.joml.Quaternionf(),new org.joml.Vector3f(0.55f,0.25f,0.65f),new org.joml.Quaternionf()));});
        hullDisplays.put(boat.getUniqueId(),display.getUniqueId());
    }
    private void startHullUpdater(){
        if(hullUpdater!=null)hullUpdater.cancel();
        hullUpdater=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(var e:hullDisplays.entrySet()){
                Entity be=Bukkit.getEntity(e.getKey()), de=Bukkit.getEntity(e.getValue());
                if(!(be instanceof Boat boat)||!(de instanceof ItemDisplay display))continue;
                Location at=boat.getLocation().clone().add(0,0.12,0);
                display.teleport(at);
            }
        },1L,1L);
    }
    private void removeBoats(){
        if(hullUpdater!=null){hullUpdater.cancel();hullUpdater=null;}
        for(World w:Bukkit.getWorlds())for(ItemDisplay d:w.getEntitiesByClass(ItemDisplay.class))if(d.getScoreboardTags().contains("wg_game3_hull"))d.remove();
        hullDisplays.clear();for(World w:Bukkit.getWorlds())for(Boat b:w.getEntitiesByClass(Boat.class))if(b.getScoreboardTags().contains("wg_game3_boat"))b.remove();boats.clear();}
    void setLaps(org.bukkit.command.CommandSender s,int n){if(racing||countdown!=null){msg(s,"Rundenzahl waehrend des Rennens gesperrt.");return;}if(n<1||n>20){msg(s,"Erlaubt sind 1 bis 20 Runden.");return;}targetLaps=n;save();msg(s,"Rundenzahl auf "+n+" gesetzt.");}
    void toggleDebug(org.bukkit.command.CommandSender s){debug=!debug;msg(s,"Debug "+(debug?"aktiv":"deaktiviert")+". Fahrer erhalten Actionbar-Meldungen.");}
    void start(org.bukkit.command.CommandSender s){if(!staged||assigned.isEmpty()){msg(s,"Erst /wg tp game 3 ausfuehren.");return;}if(countdown!=null||racing){msg(s,"Rennen laeuft bereits.");return;}for(UUID id:assigned.values()){Player p=Bukkit.getPlayer(id);if(p==null||!(p.getVehicle() instanceof Boat b)||!b.getScoreboardTags().contains("wg_game3_boat")||!Objects.equals(boats.get(id),b.getUniqueId())){msg(s,"Alle Fahrer muessen in ihrem eigenen Teamboot sitzen: "+(p==null?id:p.getName()));return;}}laps.clear();phase.clear();lapStart.clear();lastLapCross.clear();finished.clear();finishes.clear();armed.clear();lastCross.clear();lastPosition.clear();completed=false;final int[] secs={5};countdown=Bukkit.getScheduler().runTaskTimer(plugin,()->{if(secs[0]>0){all("§eSTART IN "+secs[0]+"...");secs[0]--;return;}countdown.cancel();countdown=null;racing=true;startMillis=System.currentTimeMillis();all("§a§lGO! §f"+targetLaps+" Runden! Maximal 7 Minuten.");timer=Bukkit.getScheduler().runTaskTimer(plugin,()->{if(!racing)return;long elapsed=System.currentTimeMillis()-startMillis;long remaining=Math.max(0,420000-elapsed);for(UUID id:assigned.values()){Player p=Bukkit.getPlayer(id);if(p!=null&&!finished.contains(id))p.sendActionBar("§dRennen §f"+format(remaining)+" §7| Runde "+(laps.getOrDefault(id,0)+1)+"/"+targetLaps+(debug?" §e"+phaseName(phase.getOrDefault(id,0)):""));}if(remaining<=0)complete();},0L,10L);},0L,20L);}
    void stop(){if(timer!=null){timer.cancel();timer=null;}if(countdown!=null){countdown.cancel();countdown=null;}racing=false;staged=false;spectators.clear();assigned.clear();removeBoats();}
    private boolean inside(Location p,Location a,Location b){return a!=null&&b!=null&&p.getWorld().equals(a.getWorld())&&p.getWorld().equals(b.getWorld())&&p.getX()>=Math.min(a.getX(),b.getX())-0.5&&p.getX()<=Math.max(a.getX(),b.getX())+0.5&&p.getY()>=Math.min(a.getY(),b.getY())-1&&p.getY()<=Math.max(a.getY(),b.getY())+2&&p.getZ()>=Math.min(a.getZ(),b.getZ())-0.5&&p.getZ()<=Math.max(a.getZ(),b.getZ())+0.5;}
    @EventHandler public void onMove(PlayerMoveEvent e){if(!staged||e.getTo()==null)return;Player p=e.getPlayer();if(spectators.contains(p.getUniqueId())&&!p.hasPermission("wg.admin")&&!inside(e.getTo(),tribuneA,tribuneB)){e.setTo(e.getFrom());return;}if(!assigned.containsValue(p.getUniqueId())||!racing)return;Boat b=p.getVehicle() instanceof Boat boat?boat:null;if(b==null||!Objects.equals(boats.get(p.getUniqueId()),b.getUniqueId()))return;/* handled by vehicle movement */}
    private static String format(long ms){long sec=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%02d:%02d.%03d",sec/60,sec%60,Math.max(0,ms)%1000);}
    private static String phaseName(int ph){return switch(ph){case 0->"START fehlt";case 1->"CHECKPOINT fehlt";case 2->"ZIEL fehlt";default->"START erneut";};}
    /** 2D intersection of the boat's movement segment with a marked finite line. */
    private boolean crosses(Location from,Location to,Location a,Location b){
        if(a==null||b==null||from==null||to==null||!from.getWorld().equals(a.getWorld())||!to.getWorld().equals(a.getWorld()))return false;
        if(Math.abs(to.getY()-a.getY())>6||Math.abs(from.getY()-a.getY())>6)return false;
        double rx=to.getX()-from.getX(),rz=to.getZ()-from.getZ(),sx=b.getX()-a.getX(),sz=b.getZ()-a.getZ();
        double det=rx*sz-rz*sx;if(Math.abs(det)<1e-8)return false;
        double qx=a.getX()-from.getX(),qz=a.getZ()-from.getZ();double t=(qx*sz-qz*sx)/det,u=(qx*rz-qz*rx)/det;
        return t>=0&&t<=1&&u>=-0.10&&u<=1.10;
    }
    private void checkLines(Player p,Location from,Location to){
        UUID id=p.getUniqueId();if(finished.contains(id))return;
        int ph=phase.getOrDefault(id,0);long now=System.currentTimeMillis();
        if(now-lastLapCross.getOrDefault(id,0L)<900)return;
        if(ph==0&&crosses(from,to,startA,startB)){
            phase.put(id,1);lapStart.put(id,now);lastLapCross.put(id,now);
            p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,0.9f,1.1f);if(debug)p.sendMessage("§a[DEBUG] START erkannt → Checkpoint suchen");
        }else if(ph==1&&crosses(from,to,checkpointA,checkpointB)){
            phase.put(id,2);lastLapCross.put(id,now);
            p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,0.8f,1.6f);p.sendActionBar("§a§lCHECKPOINT ERREICHT ✓ §7| Weiter zum Ziel!");p.sendMessage("§a[WG Rennen] §fCheckpoint erreicht! §7Runde "+(laps.getOrDefault(id,0)+1)+"/"+targetLaps);if(debug)p.sendMessage("§a[DEBUG] CHECKPOINT erkannt → Ziel suchen");
        }else if(ph==2&&crosses(from,to,finishA,finishB)){
            int lap=laps.merge(id,1,Integer::sum);long lapTime=now-lapStart.getOrDefault(id,startMillis);
            lastLapCross.put(id,now);
            all("§d"+p.getName()+" §fhat Runde §e"+lap+"/"+targetLaps+" §fgeschafft! §bRundenzeit: "+format(lapTime)+" §7| Gesamt: "+format(now-startMillis));p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.8f,1.2f);
            if(lap>=targetLaps){finishes.put(id,now);finished.add(id);int place=finishes.size();all("§a"+p.getName()+" §fim Ziel! §ePlatz "+place+" §7("+format(now-startMillis)+")");p.sendTitle("§aZIEL!","§ePlatz "+place,5,50,10);removeDriverBoat(id);if(spectator!=null)p.teleport(spectator);spectators.add(id);if(finishes.size()==assigned.size())complete();}
            else {phase.put(id,0);p.sendTitle("§eRUNDE "+(lap+1)+"/"+targetLaps,"§fZur Startlinie fahren!",5,35,10);}
        }else if(debug&&crosses(from,to,finishA,finishB))p.sendMessage("§c[DEBUG] ZIEL ungueltig: "+phaseName(ph));
    }
    private void removeDriverBoat(UUID id){UUID bid=boats.remove(id);if(bid==null)return;UUID did=hullDisplays.remove(bid);Entity display=did==null?null:Bukkit.getEntity(did);if(display!=null)display.remove();Entity boat=Bukkit.getEntity(bid);if(boat!=null)boat.remove();}
    private void complete(){if(completed)return;completed=true;racing=false;if(timer!=null){timer.cancel();timer=null;}all("§dRennen beendet! §fErgebnisse werden automatisch gewertet.");plugin.completeGameThree(results());for(UUID id:assigned.values()){Player p=Bukkit.getPlayer(id);if(p!=null&&spectator!=null){p.teleport(spectator);spectators.add(id);}}stop();}
    @EventHandler public void vehicleMove(VehicleMoveEvent e){
        if(!(e.getVehicle() instanceof Boat b)||!b.getScoreboardTags().contains("wg_game3_boat"))return;
        if(racing){
            for(Entity passenger:b.getPassengers()){
                if(passenger instanceof Player p && assigned.containsValue(p.getUniqueId()) && Objects.equals(boats.get(p.getUniqueId()),b.getUniqueId())){
                    checkLines(p,e.getFrom(),e.getTo());
                    return;
                }
            }
            return;
        }
        if(e.getFrom().distanceSquared(e.getTo())>0.0001){b.setVelocity(new Vector(0,0,0));b.teleport(e.getFrom());}
    }
    @EventHandler public void damage(VehicleDamageEvent e){if(e.getVehicle().getScoreboardTags().contains("wg_game3_boat"))e.setCancelled(true);}
    @EventHandler public void destroy(VehicleDestroyEvent e){if(e.getVehicle().getScoreboardTags().contains("wg_game3_boat"))e.setCancelled(true);}
    @EventHandler public void enter(VehicleEnterEvent e){if(!(e.getVehicle() instanceof Boat b)||!b.getScoreboardTags().contains("wg_game3_boat")||!(e.getEntered() instanceof Player p))return;if(!Objects.equals(boats.get(p.getUniqueId()),b.getUniqueId())){e.setCancelled(true);p.sendMessage("§cDas ist nicht dein Teamboot!");}}
    @EventHandler public void quit(PlayerQuitEvent e){spectators.remove(e.getPlayer().getUniqueId());}
    Map<String,Integer> results(){Map<String,Integer> result=new LinkedHashMap<>();List<UUID> order=new ArrayList<>(assigned.values());order.sort((a,b)->{boolean fa=finishes.containsKey(a),fb=finishes.containsKey(b);if(fa!=fb)return fa?-1:1;if(fa)return Long.compare(finishes.get(a),finishes.get(b));int cmp=Integer.compare(laps.getOrDefault(b,0),laps.getOrDefault(a,0));if(cmp!=0)return cmp;return Integer.compare(phase.getOrDefault(b,0),phase.getOrDefault(a,0));});for(int i=0;i<order.size();i++)for(var e:assigned.entrySet())if(e.getValue().equals(order.get(i)))result.put(e.getKey(),Math.max(0,100-i*10));return result;}
    boolean active(){return staged;}
}
