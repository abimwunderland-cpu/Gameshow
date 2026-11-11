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
    private final Map<UUID,Long> finishes=new LinkedHashMap<>();
    private final Map<UUID,Boolean> armed=new HashMap<>();
    private final Map<UUID,Long> lastCross=new HashMap<>();
    private final Map<UUID,Location> lastPosition=new HashMap<>();
    private final Map<UUID,UUID> boats=new HashMap<>();
    private final Map<UUID,UUID> hullDisplays=new HashMap<>();
    private BukkitTask hullUpdater;
    private final Set<UUID> spectators=new HashSet<>();
    private Location driver,spectator,finishA,finishB,tribuneA,tribuneB;
    private int mode=0, nextStart=1;
    private boolean staged=false, racing=false;
    private BukkitTask countdown;
    private long startMillis;
    private static final String[] MODES={"BOOTE 1-8","FAHRER-WARTEPUNKT","TRIBUENE-SPAWN","TRIBUENE-ECKEN","ZIELLINIE-ECKEN"};
    GameThreeRace(GameshowPlugin plugin){this.plugin=plugin;boatKey=new NamespacedKey(plugin,"race_boat");file=new File(plugin.getDataFolder(),"game3-race.yml");load();Bukkit.getPluginManager().registerEvents(this,plugin);}
    private void msg(org.bukkit.command.CommandSender s,String m){s.sendMessage("§d[WG Rennen] §f"+m);}
    private void all(String m){Bukkit.broadcastMessage("§d[WG Rennen] §f"+m);}
    private void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);for(int i=1;i<=8;i++){Location l=y.getLocation("starts."+i);if(l!=null)starts.put(i,l);}driver=y.getLocation("driver");spectator=y.getLocation("spectator");finishA=y.getLocation("finish.a");finishB=y.getLocation("finish.b");tribuneA=y.getLocation("tribune.a");tribuneB=y.getLocation("tribune.b");}
    private void save(){YamlConfiguration y=new YamlConfiguration();for(var e:starts.entrySet())y.set("starts."+e.getKey(),e.getValue());y.set("driver",driver);y.set("spectator",spectator);y.set("finish.a",finishA);y.set("finish.b",finishB);y.set("tribune.a",tribuneA);y.set("tribune.b",tribuneB);try{y.save(file);}catch(Exception ex){plugin.getLogger().warning("Game3 setup save failed: "+ex.getMessage());}}
    void wand(Player p){ItemStack wand=new ItemStack(Material.BLAZE_ROD);ItemMeta m=wand.getItemMeta();m.setDisplayName("§d§lWolfsfell §fGame-3-Wand");m.setLore(List.of("§7Shift + Rechtsklick: Modus wechseln","§7Rechtsklick: Position speichern","§7Linksklick: letzte Markierung entfernen","§8Nur fuer Admins"));m.getPersistentDataContainer().set(new NamespacedKey(plugin,"game3_wand"),PersistentDataType.BYTE,(byte)1);wand.setItemMeta(m);p.getInventory().addItem(wand);msg(p,"Wand erhalten. Modus: "+MODES[mode]);}
    private boolean isWand(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"game3_wand"),PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGH) public void click(PlayerInteractEvent e){Player p=e.getPlayer();if(!p.hasPermission("wg.admin")||!isWand(e.getItem())||e.getHand()!=EquipmentSlot.HAND)return;e.setCancelled(true);boolean right=e.getAction().name().startsWith("RIGHT");if(right&&p.isSneaking()){mode=(mode+1)%MODES.length;msg(p,"Modus: §e"+MODES[mode]);return;}if(!right){undo(p);return;}Location l=p.getLocation().clone();switch(mode){case 0->{starts.put(nextStart,l);msg(p,"Boot "+nextStart+"/8 gespeichert (Blickrichtung uebernommen).");nextStart=nextStart%8+1;}case 1->{driver=l;msg(p,"Fahrer-Wartepunkt gespeichert.");}case 2->{spectator=l;msg(p,"Tribuenen-Spawn gespeichert.");}case 3->{if(tribuneA==null||tribuneB!=null){tribuneA=l;tribuneB=null;msg(p,"Tribuenen-Ecke 1 gespeichert.");}else{tribuneB=l;msg(p,"Tribuenen-Ecke 2 gespeichert.");}}case 4->{if(finishA==null||finishB!=null){finishA=l;finishB=null;msg(p,"Ziellinie Punkt 1 gespeichert.");}else{finishB=l;msg(p,"Ziellinie Punkt 2 gespeichert.");}}}save();}
    private void undo(Player p){switch(mode){case 0->{nextStart=(nextStart+6)%8+1;starts.remove(nextStart);}case 1->driver=null;case 2->spectator=null;case 3->{if(tribuneB!=null)tribuneB=null;else tribuneA=null;}case 4->{if(finishB!=null)finishB=null;else finishA=null;}}save();msg(p,"Letzte Markierung im aktuellen Modus geloescht.");}
    boolean ready(org.bukkit.command.CommandSender s){if(starts.size()!=8||driver==null||spectator==null||finishA==null||finishB==null||tribuneA==null||tribuneB==null){msg(s,"Setup unvollstaendig: 8 Bootsplaetze, Fahrer, Tribuene, 2 Tribuenen-Ecken und 2 Zielpunkte markieren.");return false;}if(!finishA.getWorld().equals(finishB.getWorld())){msg(s,"Zielpunkte muessen in derselben Welt sein.");return false;}return true;}
    void teleport(org.bukkit.command.CommandSender sender,Map<String,UUID> selected,Map<UUID,String> teamLookup,Map<String,String> colors){if(!ready(sender))return;stop();assigned.clear();int n=0;for(var e:selected.entrySet()){Player p=Bukkit.getPlayer(e.getValue());if(p==null)continue;if(n>=8)break;assigned.put(e.getKey(),p.getUniqueId());n++;}if(assigned.isEmpty()){msg(sender,"Keine Kandidaten gewaehlt. /wg candidate set <Team> <Spieler>");return;}for(Player p:Bukkit.getOnlinePlayers()){if(assigned.containsValue(p.getUniqueId())){p.teleport(driver);p.sendMessage("§dDein Boot steht an der Startlinie. Steige in das Boot deines Teams!");}else{p.teleport(spectator);spectators.add(p.getUniqueId());}}spawnBoats(colors);staged=true;msg(sender,assigned.size()+" Fahrer zur Strecke, Zuschauer zur Tribuene teleportiert. /wg game3 start");}
    private void spawnBoats(Map<String,String> colors){removeBoats();int n=0;for(String team:assigned.keySet()){Location at=starts.get(++n);if(at==null)continue;Boat boat=(Boat)at.getWorld().spawnEntity(at,org.bukkit.entity.EntityType.OAK_BOAT);boat.setInvulnerable(true);boat.setPersistent(true);boat.setCustomName("§d"+team);boat.setCustomNameVisible(true);boat.getPersistentDataContainer().set(boatKey,PersistentDataType.STRING,team);boat.addScoreboardTag("wg_game3_boat");boat.setGlowing(true);String col=colors.getOrDefault(team,"PURPLE").toUpperCase(Locale.ROOT);org.bukkit.scoreboard.Scoreboard sb=Bukkit.getScoreboardManager().getMainScoreboard();String key="wgboat"+n;org.bukkit.scoreboard.Team score=sb.getTeam(key);if(score==null)score=sb.registerNewTeam(key);try{score.setColor(ChatColor.valueOf(col));}catch(Exception ignored){score.setColor(ChatColor.LIGHT_PURPLE);}score.addEntry(boat.getUniqueId().toString());boats.put(assigned.get(team),boat.getUniqueId());spawnColoredHull(boat,col);}
        startHullUpdater();}
    private void spawnColoredHull(Boat boat,String color){
        String c=switch(color){case "RED", "DARK_RED"->"red";case "BLUE", "DARK_BLUE"->"blue";case "GREEN", "DARK_GREEN"->"green";case "YELLOW", "GOLD"->"yellow";case "ORANGE"->"orange";case "LIGHT_PURPLE", "PINK"->"pink";case "AQUA", "DARK_AQUA", "CYAN"->"cyan";case "WHITE"->"white";default->"purple";};
        ItemStack item=new ItemStack(Material.PAPER);
        ItemMeta meta=item.getItemMeta();meta.setItemModel(new NamespacedKey("wolfsfell","race_boat_"+c));item.setItemMeta(meta);
        Location loc=boat.getLocation().clone().add(0,0.28,0);
        ItemDisplay display=boat.getWorld().spawn(loc,ItemDisplay.class,d->{d.setItemStack(item);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(false);d.addScoreboardTag("wg_game3_hull");d.setInterpolationDuration(2);d.setTeleportDuration(1);});
        hullDisplays.put(boat.getUniqueId(),display.getUniqueId());
    }
    private void startHullUpdater(){
        if(hullUpdater!=null)hullUpdater.cancel();
        hullUpdater=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(var e:hullDisplays.entrySet()){
                Entity be=Bukkit.getEntity(e.getKey()), de=Bukkit.getEntity(e.getValue());
                if(!(be instanceof Boat boat)||!(de instanceof ItemDisplay display))continue;
                Location at=boat.getLocation().clone().add(0,0.28,0);
                display.teleport(at);
            }
        },1L,1L);
    }
    private void removeBoats(){
        if(hullUpdater!=null){hullUpdater.cancel();hullUpdater=null;}
        for(World w:Bukkit.getWorlds())for(ItemDisplay d:w.getEntitiesByClass(ItemDisplay.class))if(d.getScoreboardTags().contains("wg_game3_hull"))d.remove();
        hullDisplays.clear();for(World w:Bukkit.getWorlds())for(Boat b:w.getEntitiesByClass(Boat.class))if(b.getScoreboardTags().contains("wg_game3_boat"))b.remove();boats.clear();}
    void start(org.bukkit.command.CommandSender s){if(!staged||assigned.isEmpty()){msg(s,"Erst /wg tp game 3 ausfuehren.");return;}if(countdown!=null||racing){msg(s,"Rennen laeuft bereits.");return;}for(UUID id:assigned.values()){Player p=Bukkit.getPlayer(id);if(p==null||!(p.getVehicle() instanceof Boat b)||!b.getScoreboardTags().contains("wg_game3_boat")||!Objects.equals(boats.get(id),b.getUniqueId())){msg(s,"Alle Fahrer muessen in ihrem eigenen Teamboot sitzen: "+(p==null?id:p.getName()));return;}}laps.clear();finishes.clear();armed.clear();lastCross.clear();lastPosition.clear();final int[] secs={5};countdown=Bukkit.getScheduler().runTaskTimer(plugin,()->{if(secs[0]>0){all("§eSTART IN "+secs[0]+"...");secs[0]--;return;}countdown.cancel();countdown=null;racing=true;startMillis=System.currentTimeMillis();all("§a§lGO! §f2 Runden!");},0L,20L);}
    void stop(){if(countdown!=null){countdown.cancel();countdown=null;}racing=false;staged=false;spectators.clear();assigned.clear();removeBoats();}
    private boolean inside(Location p,Location a,Location b){return a!=null&&b!=null&&p.getWorld().equals(a.getWorld())&&p.getWorld().equals(b.getWorld())&&p.getX()>=Math.min(a.getX(),b.getX())-0.5&&p.getX()<=Math.max(a.getX(),b.getX())+0.5&&p.getY()>=Math.min(a.getY(),b.getY())-1&&p.getY()<=Math.max(a.getY(),b.getY())+2&&p.getZ()>=Math.min(a.getZ(),b.getZ())-0.5&&p.getZ()<=Math.max(a.getZ(),b.getZ())+0.5;}
    @EventHandler public void onMove(PlayerMoveEvent e){if(!staged||e.getTo()==null)return;Player p=e.getPlayer();if(spectators.contains(p.getUniqueId())&&!p.hasPermission("wg.admin")&&!inside(e.getTo(),tribuneA,tribuneB)){e.setTo(e.getFrom());return;}if(!assigned.containsValue(p.getUniqueId())||!racing)return;Boat b=p.getVehicle() instanceof Boat boat?boat:null;if(b==null||!Objects.equals(boats.get(p.getUniqueId()),b.getUniqueId()))return;checkFinish(p,e.getTo());}
    private void checkFinish(Player p,Location position){Location old=lastPosition.put(p.getUniqueId(),position.clone());if(old==null||finishA==null||finishB==null)return;double dx=finishB.getX()-finishA.getX(),dz=finishB.getZ()-finishA.getZ();double length=Math.hypot(dx,dz);if(length<0.1)return;double ox=old.getX()-finishA.getX(),oz=old.getZ()-finishA.getZ(),nx=position.getX()-finishA.getX(),nz=position.getZ()-finishA.getZ();double sideOld=dx*oz-dz*ox,sideNew=dx*nz-dz*nx;double proj=(nx*dx+nz*dz)/(length*length);if(Math.abs(sideNew)>10*length)armed.put(p.getUniqueId(),true);if(!Boolean.TRUE.equals(armed.get(p.getUniqueId())))return;if(proj<0||proj>1||sideOld>=0||sideNew<0||!position.getWorld().equals(finishA.getWorld()))return;long now=System.currentTimeMillis();if(now-lastCross.getOrDefault(p.getUniqueId(),0L)<5000)return;lastCross.put(p.getUniqueId(),now);armed.put(p.getUniqueId(),false);int lap=laps.merge(p.getUniqueId(),1,Integer::sum);if(lap==1){p.sendTitle("§eRUNDE 2/2","§fWeiterfahren!",5,35,10);}else if(lap==2){finishes.put(p.getUniqueId(),now);int place=finishes.size();all("§d"+p.getName()+" §ferreicht Platz §e"+place+"§f! §7("+String.format(Locale.ROOT,"%.1f",(now-startMillis)/1000d)+"s)");p.sendTitle("§aZIEL!","§ePlatz "+place,5,50,10);if(finishes.size()==assigned.size()){racing=false;all("§aAlle Fahrer im Ziel! §f/wg finishround speichert die Wertung.");}}}
    @EventHandler public void vehicleMove(VehicleMoveEvent e){if(!(e.getVehicle() instanceof Boat b)||!b.getScoreboardTags().contains("wg_game3_boat"))return;if(racing){if(!b.getPassengers().isEmpty()&&b.getPassengers().get(0) instanceof Player p&&assigned.containsValue(p.getUniqueId()))checkFinish(p,e.getTo());return;}if(e.getFrom().distanceSquared(e.getTo())>0.0001){b.setVelocity(new Vector(0,0,0));b.teleport(e.getFrom());}}
    @EventHandler public void damage(VehicleDamageEvent e){if(e.getVehicle().getScoreboardTags().contains("wg_game3_boat"))e.setCancelled(true);}
    @EventHandler public void destroy(VehicleDestroyEvent e){if(e.getVehicle().getScoreboardTags().contains("wg_game3_boat"))e.setCancelled(true);}
    @EventHandler public void enter(VehicleEnterEvent e){if(!(e.getVehicle() instanceof Boat b)||!b.getScoreboardTags().contains("wg_game3_boat")||!(e.getEntered() instanceof Player p))return;if(!Objects.equals(boats.get(p.getUniqueId()),b.getUniqueId())){e.setCancelled(true);p.sendMessage("§cDas ist nicht dein Teamboot!");}}
    @EventHandler public void quit(PlayerQuitEvent e){spectators.remove(e.getPlayer().getUniqueId());}
    Map<String,Integer> results(){Map<String,Integer> result=new HashMap<>();List<Map.Entry<UUID,Long>> order=new ArrayList<>(finishes.entrySet());order.sort(Map.Entry.comparingByValue());for(int i=0;i<order.size();i++){UUID id=order.get(i).getKey();for(var a:assigned.entrySet())if(a.getValue().equals(id))result.put(a.getKey(),100-(i*10));}return result;}
    boolean active(){return staged;}
}
