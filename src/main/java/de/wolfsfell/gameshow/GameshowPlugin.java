package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Core tournament engine. Integrations with external minigame plugins are intentionally separate. */
public final class GameshowPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String[] GAMES = {"Was ist das?!", "Vier Antworten", "Wolfsfell Grand Prix", "Zielwasser", "Schätz mal!", "Streetball 1vs1", "Parkour", "Mystery-Chests", "KitUHC 1vs1"};
    private static final List<String> GAME1_ITEMS = List.of(
        "Diamant","Goldbarren","Eisenschwert","Apfel","Bogen","Smaragd","Enderperle","Karotte","Spitzhacke","TNT");
    private static final int[] RANK_POINTS = {100,80,65,50,40,30,20,10};
    private final LinkedHashMap<String, LinkedHashSet<UUID>> teams = new LinkedHashMap<>();
    private final Map<String, String> teamColors = new HashMap<>();
    private final Map<UUID, String> wandModes = new HashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>(), pos2 = new HashMap<>();
    private final Map<String, Location[]> cages = new HashMap<>();
    private final Map<String, Set<String>> neon = new HashMap<>();
    private final Map<String, UUID> neonDisplays = new HashMap<>();
    private final Map<String, List<UUID>> neonEdges = new HashMap<>();
    private final Map<String,String> neonLights = new HashMap<>();
    private final Map<String, Location[]> neonRegions = new HashMap<>();
    private final Set<UUID> neonRegionDisplays = new HashSet<>();
    private boolean studioLocked = false;
    private final String[] palette = {"PURPLE","BLUE","RED","GREEN","YELLOW","ORANGE","PINK","CYAN"};
    private final NamespacedKey wandKey = new NamespacedKey(this,"setup_wand");
    private final Map<String,Integer> qualifying = new HashMap<>(), advantages = new HashMap<>();
    private final Map<String,Map<Integer,Integer>> raw = new HashMap<>();
    private final Map<String,Location> locations = new HashMap<>();
    private final Map<String,UUID> selected = new HashMap<>();
    private final Map<UUID,Integer> selectionTicks = new HashMap<>();
    private final Map<String,Integer> mysteryScores = new HashMap<>();
    private final Map<String,Boolean> mysteryStopped = new HashMap<>();
    private final Map<String,Integer> parkourScores = new HashMap<>();
    private final Map<String,Long> parkourFinishes = new HashMap<>();
    private final Map<String,Integer> basketballScores = new HashMap<>();
    private final Map<String,Integer> mysteryChestValues = new HashMap<>();
    private final Set<String> openedChests = new HashSet<>();
    private List<String> finalists = new ArrayList<>();
    private final Map<UUID,Integer> individual = new HashMap<>();
    private final Map<String,Integer> quizScores = new HashMap<>();
    private final Map<String,String> responses = new HashMap<>();
    private final Map<String,Integer> estimates = new HashMap<>();
    private final Map<String,Integer> archeryHits = new HashMap<>();
    private final Map<String,Set<String>> archeryTargets = new HashMap<>();
    private final Map<String,List<Location>> raceCheckpoints = new HashMap<>();
    private final Map<String,Integer> raceProgress = new HashMap<>();
    private final Map<String,Long> raceFinishes = new HashMap<>();
    private final Map<String,Map<String,Integer>> purchases = new HashMap<>();
    private final List<QuizQuestion> questions = new ArrayList<>();
    private final Random random = new Random();
    private String currentPrompt = "", correctAnswer = "";
    private String[] choices = new String[4];
    private UUID buzzed = null;
    private final Map<String,String> buzzerBlocks = new HashMap<>();
    private final Set<UUID> attemptedBuzzers = new HashSet<>();
    private long buzzDeadline = 0;
    private final Map<String,Long> archeryFinishTimes = new HashMap<>();
    private long answerDeadline = 0;
    private int questionNumber = 0;
    private final Set<String> usedQuestions = new HashSet<>();
    private final Map<String,Integer> rankLast = new HashMap<>();
    private final Map<UUID,Long> lastTabUpdate = new HashMap<>();
    private record QuizQuestion(int game, String prompt, String answer, String[] wrong) {}
    private File stateFile;
    private int game = 0;
    private boolean running = false, selectionEnabled = false;
    private long startMillis = 0;
    private BukkitTask clock;
    private final GameOneScreen gameOneScreen = new GameOneScreen(this);
    private boolean game1QuestionActive=false;
    private long game1RoundEnds=0;
    private long game1RemainingOnBuzz=0;

    @Override public void onEnable() {
        saveDefaultConfig(); stateFile = new File(getDataFolder(),"state.yml"); loadState();
        Bukkit.getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("wg")).setExecutor(this);
        Objects.requireNonNull(getCommand("wg")).setTabCompleter(this);
        loadQuestions();
        // Game 1 images are loaded from resources/game1; no unrelated question entries.
        for(String answer:GAME1_ITEMS)questions.add(new QuizQuestion(1,"Errate das Minecraft-Item auf dem Bildschirm!",answer,new String[0]));
        gameOneScreen.load();
        Bukkit.getScheduler().runTaskLater(this, () -> { clearLegacyNeonDisplays(); restoreRegionNeon(); refreshAllArmor(); }, 20L);
        clock = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this,this::updateDisplays,40L,40L);
        getLogger().info("Wolfsfell Gameshow 0.1 loaded; " + teams.size() + " teams");
    }
    @Override public void onDisable() { if(clock!=null)clock.cancel(); for(List<UUID> ids:neonEdges.values())for(UUID id:ids){org.bukkit.entity.Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();} gameOneScreen.clear(); saveState(); }
    private String key(Location l) { return l.getWorld().getUID()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ(); }
    private String team(UUID u) { for(var e:teams.entrySet()) if(e.getValue().contains(u))return e.getKey(); return null; }
    private void say(CommandSender s,String msg) { s.sendMessage(ChatColor.LIGHT_PURPLE+"[Gameshow] "+ChatColor.WHITE+msg); }
    private void broadcast(String msg) { Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE+"[Gameshow] "+ChatColor.WHITE+msg); }
    private boolean admin(CommandSender s) { if(s.hasPermission("wg.admin"))return true; say(s,"Keine Berechtigung.");return false; }
    private int elapsed() { return (int)((System.currentTimeMillis()-startMillis)/1000); }
    private boolean finalTeam(String t) { return finalists.contains(t); }
    private void tick() {
        if(selectionEnabled) {
            Location mark = locations.get("selection");
            if(mark!=null || cages.keySet().stream().anyMatch(k->k.startsWith("selection:"))) for(Player p:Bukkit.getOnlinePlayers()) {
                String t=team(p.getUniqueId());
                boolean allowed=t!=null && (game<=5 || finalTeam(t));
                boolean near=allowed && mark!=null && p.getWorld().equals(mark.getWorld()) && p.getLocation().distanceSquared(mark)<=Math.pow(getConfig().getDouble("settings.selection-radius",1.5),2);
                Location[] zone=cages.get("selection:"+t.toLowerCase(Locale.ROOT));if(zone!=null)near=allowed&&inside(p.getLocation(),zone[0],zone[1]);
                if(!near){selectionTicks.remove(p.getUniqueId());continue;}
                if(selected.containsKey(t)){selectionTicks.remove(p.getUniqueId());continue;}
                int ticks=selectionTicks.merge(p.getUniqueId(),1,Integer::sum);
                if(ticks>=getConfig().getInt("settings.selection-hold-ticks",20) && !selected.containsKey(t)) {
                    selected.put(t,p.getUniqueId()); selectionTicks.remove(p.getUniqueId()); broadcast("§d"+p.getName()+" §7ist für §f"+t+" §7ausgewählt!"); saveState();
                }
            }
        }

        if(!running)return;
        if(game==1 && game1QuestionActive && buzzed==null)gameOneScreen.advance();
        if(game==1 && game1QuestionActive && buzzed==null && System.currentTimeMillis()>=game1RoundEnds){game1QuestionActive=false;gameOneScreen.reveal();broadcast("§eRunde abgelaufen! Antwort: "+correctAnswer);Bukkit.getScheduler().runTaskLater(this,()->{if(running&&game==1)nextQuestion();},60L);}
        if(game==1 && buzzed!=null && System.currentTimeMillis()>=buzzDeadline){
            broadcast("§c5 Sekunden vorbei! §7Buzzer wieder frei.");buzzed=null;buzzDeadline=0;game1RoundEnds=System.currentTimeMillis()+game1RemainingOnBuzz;gameOneScreen.resume();
        }
        if((game==2 || game==5) && answerDeadline>0 && System.currentTimeMillis()>=answerDeadline) {
            answerDeadline=0; resolveQuestion();
        }
        if(game==7) {
            int left=Math.max(0,getConfig().getInt("settings.parkour-start-points",12)-elapsed()/Math.max(1,getConfig().getInt("settings.parkour-loss-every-seconds",15)));
            if(elapsed()%5==0 && System.currentTimeMillis()%1000<60) for(String t:finalists) for(UUID id:teams.getOrDefault(t,new LinkedHashSet<>())) {
                Player p=Bukkit.getPlayer(id);if(p!=null)p.sendActionBar("§dPARKOUR §7| §f"+left+" mögliche Punkte §7| §e"+elapsed()+"s");
            }
            if(left==0){ for(String t:finalists)parkourScores.putIfAbsent(t,0); stopParkour(); }
        }
        if(game==3) for(Player p:Bukkit.getOnlinePlayers()) {
            String t=team(p.getUniqueId());
            if(t==null || !Objects.equals(selected.get(t),p.getUniqueId()) || raceFinishes.containsKey(t))continue;
            List<Location> cp=raceCheckpoints.getOrDefault("main",List.of());int n=raceProgress.getOrDefault(t,0);
            if(n<cp.size() && p.getWorld().equals(cp.get(n).getWorld()) && p.getLocation().distanceSquared(cp.get(n))<16) {
                raceProgress.put(t,n+1);p.sendMessage("§dCheckpoint §f"+(n+1)+"/"+cp.size());
            }
            Location finish=locations.get("race-finish");
            if(finish!=null && n>=cp.size() && p.getWorld().equals(finish.getWorld()) && p.getLocation().distanceSquared(finish)<16) {
                raceFinishes.put(t,System.currentTimeMillis());broadcast(t+" erreicht das Ziel!");
            }
        }
        if(game==8 && elapsed()>=getConfig().getInt("settings.mystery-duration-seconds",300))finishMystery();
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if(e.getTo()==null)return;
        // During Game 1 the elected player must stay on the marked selection platform.
        // This check also applies to operators who are actively playing, but never to
        // moderators who have not been elected as a contestant.
        if(running && game==1 && !selectionEnabled){
            String playingTeam=team(e.getPlayer().getUniqueId());
            if(playingTeam!=null && Objects.equals(selected.get(playingTeam),e.getPlayer().getUniqueId())){
                Location[] spot=cages.get("selection:"+playingTeam.toLowerCase(Locale.ROOT));
                if(spot!=null && !insideHorizontal(e.getTo(),spot[0],spot[1])){
                    if(e.getFrom().getBlockX()!=e.getTo().getBlockX() || e.getFrom().getBlockZ()!=e.getTo().getBlockZ())e.setTo(e.getFrom());
                    return;
                }
            }
        }
        if(studioLocked && !e.getPlayer().hasPermission("wg.admin")) {
            String assigned=team(e.getPlayer().getUniqueId());
            if(assigned!=null){
                Location[] home=cages.get("cage:"+assigned.toLowerCase(Locale.ROOT));
                Location[] choice=cages.get("selection:"+assigned.toLowerCase(Locale.ROOT));
                boolean canChoose=selectionEnabled && (!selected.containsKey(assigned) || Objects.equals(selected.get(assigned),e.getPlayer().getUniqueId()));
                boolean selectedPlayer=Objects.equals(selected.get(assigned),e.getPlayer().getUniqueId());
                // During selection, every eligible team member may walk to the central spot.
                // Once selected, only the captain may leave their cage.
                boolean allowed=home==null || inside(e.getTo(),home[0],home[1]) ||
                    canChoose || selectedPlayer;
                if(!allowed && e.getFrom().getBlock().getLocation().distanceSquared(e.getTo().getBlock().getLocation())>0){
                    e.setTo(e.getFrom());return;
                }
            }
        }
        if(!running || game!=7)return;
        String t=team(e.getPlayer().getUniqueId());if(t==null || !finalTeam(t) || !Objects.equals(selected.get(t),e.getPlayer().getUniqueId()) || parkourFinishes.containsKey(t))return;
        Location goal=locations.get("parkour-finish");if(goal==null || !goal.getWorld().equals(e.getTo().getWorld()) || e.getTo().distanceSquared(goal)>2.25)return;
        int pts=Math.max(0,getConfig().getInt("settings.parkour-start-points",12)-elapsed()/Math.max(1,getConfig().getInt("settings.parkour-loss-every-seconds",15)));
        if(parkourFinishes.isEmpty())pts+=getConfig().getInt("settings.parkour-win-bonus",2);
        parkourFinishes.put(t,System.currentTimeMillis());parkourScores.put(t,pts);
        broadcast("§a"+e.getPlayer().getName()+" §7beendet Parkour: §e+"+pts+" Vorteilspunkte");
        if(parkourFinishes.size()>=finalists.size())stopParkour();else saveState();
    }
    private void stopParkour(){if(!running || game!=7)return;for(String t:finalists)advantages.merge(t,parkourScores.getOrDefault(t,0),Integer::sum);running=false;broadcast("Parkour beendet! Vorteile wurden gutgeschrieben.");saveState();}
    private boolean insideHorizontal(Location point,Location a,Location b){
        return point!=null && point.getWorld().equals(a.getWorld()) &&
            point.getBlockX()>=Math.min(a.getBlockX(),b.getBlockX()) && point.getBlockX()<=Math.max(a.getBlockX(),b.getBlockX()) &&
            point.getBlockZ()>=Math.min(a.getBlockZ(),b.getBlockZ()) && point.getBlockZ()<=Math.max(a.getBlockZ(),b.getBlockZ());
    }
    private void buzz(Player p){
        if(!running || game!=1 || !game1QuestionActive){say(p,"Spiel 1 hat gerade keine aktive Fragerunde.");return;}
        if(buzzed!=null){say(p,"Es hat bereits jemand gebuzzert. Warte auf seine Antwort.");return;}
        String t=team(p.getUniqueId());
        if(t==null){say(p,"Du bist keinem Team zugewiesen.");return;}
        UUID captain=selected.entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(t)).map(Map.Entry::getValue).findFirst().orElse(null);
        if(!Objects.equals(captain,p.getUniqueId())){say(p,"Nicht als Kandidat registriert. Gewählt: "+(captain==null?"niemand":Bukkit.getOfflinePlayer(captain).getName())+". Moderator: /wg candidate set "+t+" "+p.getName());return;}
        if(attemptedBuzzers.contains(p.getUniqueId())){say(p,"Du hast in dieser Runde bereits geraten.");return;}
        attemptedBuzzers.add(p.getUniqueId());buzzed=p.getUniqueId();game1RemainingOnBuzz=Math.max(0,game1RoundEnds-System.currentTimeMillis());buzzDeadline=System.currentTimeMillis()+5000L;
        gameOneScreen.freeze();
        broadcast("§dBUZZ! §f"+p.getName()+" §7hat 5 Sekunden für eine Chat-Antwort!");
        p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BELL,1f,1.4f);
    }
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=false) public void onPhysicalBuzzer(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND || e.getClickedBlock()==null || e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        String block=key(e.getClickedBlock().getLocation());
        if(!buzzerBlocks.containsValue(block)){
            if(running && game==1 && e.getClickedBlock().getType().name().endsWith("BUTTON")){
                say(e.getPlayer(),"Dieser Knopf ist nicht als Gameshow-Buzzer gespeichert. Moderator: /wg buzzer set <Team>");
            }
            return;
        }
        // Handle registered buzzers even when another plugin/spawn protection cancels
        // the vanilla block interaction for non-OP players. Never require OP here.
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        String t=team(e.getPlayer().getUniqueId());
        if(t==null){say(e.getPlayer(),"Du bist keinem Team zugeteilt. Moderator: /wg team join <Team> <Spieler>");return;}
        String configured=buzzerBlocks.entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(t)).map(Map.Entry::getValue).findFirst().orElse(null);
        if(!block.equals(configured)){
            String owner=buzzerBlocks.entrySet().stream().filter(entry->block.equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse("unbekannt");
            say(e.getPlayer(),"Das ist der Buzzer von "+owner+", du bist in "+t+". Nutze deinen Team-Buzzer.");return;
        }
        buzz(e.getPlayer());
    }
    @EventHandler public void onChest(PlayerInteractEvent e) {
        if(!running || game!=8 || e.getAction()!=Action.RIGHT_CLICK_BLOCK || e.getClickedBlock()==null)return;
        Block b=e.getClickedBlock();String k=key(b.getLocation());if(!mysteryChestValues.containsKey(k))return;
        e.setCancelled(true);String t=team(e.getPlayer().getUniqueId());
        if(t==null || !finalTeam(t)){say(e.getPlayer(),"Nur Finalteams dürfen Truhen öffnen.");return;}
        if(!Objects.equals(selected.get(t),e.getPlayer().getUniqueId())){say(e.getPlayer(),"Du bist nicht der gewählte Glückspilz.");return;}
        if(mysteryStopped.getOrDefault(t,false)){say(e.getPlayer(),"Dein Team ist bereits ausgestiegen.");return;}
        if(openedChests.contains(k)){say(e.getPlayer(),"Diese Truhe wurde bereits geöffnet.");return;}
        int value=mysteryChestValues.get(k);openedChests.add(k);mysteryScores.merge(t,value,Integer::sum);
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,b.getLocation().add(.5,1,.5),24,.35,.35,.35);
        b.getWorld().playSound(b.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,1f,value>=0?1.6f:.5f);
        broadcast(e.getPlayer().getName()+" öffnet Truhe: §e"+(value>0?"+":"")+value+" §7| "+t+" gesamt: §e"+mysteryScores.get(t));saveState();
        if(openedChests.size()==mysteryChestValues.size())finishMystery();
    }
    private void finishMystery(){if(!running || game!=8)return;running=false;for(String t:finalists)advantages.merge(t,mysteryScores.getOrDefault(t,0),Integer::sum);broadcast("Mystery beendet. Punkte wurden verbucht!");saveState();}
    @EventHandler public void onJoin(PlayerJoinEvent e) { Bukkit.getScheduler().runTaskLater(this,this::updateDisplays,20); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { if(e.getPlayer().getUniqueId().equals(buzzed))buzzed=null; }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        if(!running || !(game==1 || game==2 || game==5))return;
        Player p=e.getPlayer();String t=team(p.getUniqueId());
        if(t==null)return;
        UUID captain=selected.entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(t)).map(Map.Entry::getValue).findFirst().orElse(null);
        if(!Objects.equals(captain,p.getUniqueId()))return;
        if(game==1 && (!p.getUniqueId().equals(buzzed) || System.currentTimeMillis()>buzzDeadline))return;
        if(game!=1 && answerDeadline==0)return;
        String msg=e.getMessage().trim();e.setCancelled(true);
        Bukkit.getScheduler().runTask(this,()->{
            if(!running)return;
            if(game==1){
                if(!p.getUniqueId().equals(buzzed) || System.currentTimeMillis()>buzzDeadline)return;
                if(msg.equalsIgnoreCase(correctAnswer)){
                    int pts=1;
                    quizScores.merge(t,pts,Integer::sum);individual.merge(p.getUniqueId(),pts,Integer::sum);updateDisplays();
                    broadcast("§a"+t+" – RICHTIG! §a+1 Punkt §7("+p.getName()+") §f– "+correctAnswer);
                    buzzed=null;buzzDeadline=0;game1QuestionActive=false;gameOneScreen.reveal();Bukkit.getScheduler().runTaskLater(this,()->{if(running&&game==1)nextQuestion();},60L);
                } else {broadcast("§cFalsch! §7"+t+" darf diese Runde nicht mehr buzzern.");buzzed=null;buzzDeadline=0;game1RoundEnds=System.currentTimeMillis()+game1RemainingOnBuzz;gameOneScreen.resume();}
            }else if(game==2){
                String v=msg.toUpperCase(Locale.ROOT);
                if(!List.of("A","B","C","D").contains(v)){say(p,"Antworte mit A, B, C oder D.");return;}
                responses.putIfAbsent(t,v);say(p,"Antwort "+v+" gespeichert.");
            }else{
                try{int n=Integer.parseInt(msg);estimates.putIfAbsent(t,n);say(p,"Schätzung gespeichert.");}
                catch(NumberFormatException x){say(p,"Bitte eine ganze Zahl schreiben.");}
            }
        });
    }
    @EventHandler public void onTarget(ProjectileHitEvent e) {
        if(!running || game!=4 || !(e.getEntity() instanceof Arrow arrow) || !(arrow.getShooter() instanceof Player p) || e.getHitBlock()==null)return;
        String t=team(p.getUniqueId());if(t==null || !Objects.equals(selected.get(t),p.getUniqueId()))return;
        String target=key(e.getHitBlock().getLocation());Set<String> allowed=archeryTargets.getOrDefault(t,Set.of());
        if(!allowed.contains(target))return;
        allowed.remove(target);archeryHits.merge(t,1,Integer::sum);individual.merge(p.getUniqueId(),1,Integer::sum);
        e.getHitBlock().getWorld().spawnParticle(Particle.HAPPY_VILLAGER,e.getHitBlock().getLocation().add(.5,.5,.5),10,.25,.25,.25);
        p.sendMessage("§aTreffer! §7"+archeryHits.get(t)+"/10");
        p.playSound(p.getLocation(),"wolfsfell.hit",0.8f,1f);
        if(archeryHits.get(t)>=10){archeryFinishTimes.putIfAbsent(t,System.currentTimeMillis());broadcast("§d"+t+" §7hat alle Zielscheiben getroffen!");}
    }
    private void loadQuestions() {
        File f=new File(getDataFolder(),"questions.yml");if(!f.exists())saveResource("questions.yml",false);
        YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
        for(Map<?,?> row:y.getMapList("questions"))try{
            int g=Integer.parseInt(String.valueOf(row.get("game")));
            String prompt=String.valueOf(row.get("question")),answer=String.valueOf(row.get("answer"));
            Object w=row.get("wrong");String[] wrong=w instanceof List<?> list?list.stream().map(String::valueOf).toArray(String[]::new):new String[0];
            if((g==2 && wrong.length==3) || g==5)questions.add(new QuizQuestion(g,prompt,answer,wrong));
        }catch(Exception ignored){}
        getLogger().info("Loaded "+questions.size()+" quiz entries");
    }
    private void nextQuestion(){
        if(questionNumber>=10){gameOneScreen.reveal();running=false;game1QuestionActive=false;answerDeadline=0;broadcast("§d10 Runden beendet! §7Mit /wg finishround die Punkte speichern.");return;}
        List<QuizQuestion> pool=questions.stream().filter(q->q.game()==game&&!usedQuestions.contains(q.game()+":"+q.answer()) && (game!=1 || GAME1_ITEMS.contains(q.answer()))).toList();
        if(pool.isEmpty()){broadcast("§dFragenpool für Spiel "+game+" aufgebraucht.");running=false;return;}
        QuizQuestion q=pool.get(random.nextInt(pool.size()));usedQuestions.add(q.game()+":"+q.answer());questionNumber++;
        currentPrompt=q.prompt();correctAnswer=q.answer();responses.clear();estimates.clear();buzzed=null;buzzDeadline=0;attemptedBuzzers.clear();
        if(game==2){List<String> opts=new ArrayList<>();opts.add(q.answer());opts.addAll(Arrays.asList(q.wrong()));Collections.shuffle(opts);choices=opts.toArray(String[]::new);}
        answerDeadline=game==1?0:System.currentTimeMillis()+7000L;
        broadcast("§dFrage "+questionNumber+": §f"+currentPrompt);
        if(game==2)for(int i=0;i<4;i++)broadcast("§7"+(char)('A'+i)+") §f"+choices[i]);
        if(game==5)broadcast("§7Schätzung im Chat eingeben!");
        if(game==1){game1QuestionActive=true;game1RoundEnds=System.currentTimeMillis()+30000L;game1RemainingOnBuzz=0;gameOneScreen.show(correctAnswer);broadcast("§7Team-Buzzer drücken! 5 Sekunden für die Antwort, ein Versuch pro Spieler.");}
    }
    private void resolveQuestion(){
        if(game==2){
            String right="";for(int i=0;i<4;i++)if(choices[i].equals(correctAnswer))right=""+(char)('A'+i);
            broadcast("§aRichtige Antwort: §f"+right+" §7– "+correctAnswer);
            for(String t:teams.keySet()){
                if(!selected.containsKey(t) || (teams.get(t)==null || teams.get(t).isEmpty()))continue;
                String v=responses.getOrDefault(t,"–");broadcast("§7"+t+": §f"+v);
                if(v.equals(right)){quizScores.merge(t,1,Integer::sum);UUID id=selected.get(t);if(id!=null)individual.merge(id,1,Integer::sum);broadcast("§a"+t+" – RICHTIG! +1 Punkt");}
            }
        }else if(game==5){
            int correct;try{correct=Integer.parseInt(correctAnswer);}catch(NumberFormatException ex){broadcast("Ungültige Schätzfrage.");return;}
            long best=estimates.values().stream().mapToLong(v->Math.abs((long)v-correct)).min().orElse(Long.MAX_VALUE);
            broadcast("§aRichtige Antwort: §f"+correct);
            for(var entry:estimates.entrySet()){
                long diff=Math.abs((long)entry.getValue()-correct);
                int pts=diff==best?(diff==0?2:1):0;
                broadcast("§7"+entry.getKey()+": §f"+entry.getValue()+(pts>0?" §a+"+pts:""));
                if(pts>0){broadcast("§a"+entry.getKey()+" – +"+pts+" Punkt(e)!");quizScores.merge(entry.getKey(),pts,Integer::sum);UUID id=selected.get(entry.getKey());if(id!=null)individual.merge(id,pts,Integer::sum);}
            }
        }
        if(running)Bukkit.getScheduler().runTaskLater(this,()->{if(running && (game==2||game==5))nextQuestion();},80L);
    }
    private void updateDisplays(){
        for(Player p:Bukkit.getOnlinePlayers()){
            String mine=team(p.getUniqueId());
            StringBuilder tab=new StringBuilder("§5§lWOLFSFELL GAMESHOW§r\n§7Twitch: §dtwitch.tv/wolfsfelltv\n§7YouTube: §dWolfsfellTVHD\n");
            for(var e:teams.entrySet()){
                tab.append(chatColor(e.getKey())).append(e.getKey()).append("§7: ");
                tab.append(String.join(", ",e.getValue().stream().map(u->chatColor(e.getKey())+Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse("?")).toList())).append("\n");
            }
            p.setPlayerListHeaderFooter(tab.toString(),"§dLive täglich 17 Uhr §7| §fWolfsfellTV");
            String suffix=mine==null?"":(" §8["+chatColor(mine)+mine+"§8]");p.setPlayerListName((mine==null?"§f":chatColor(mine).toString())+p.getName()+suffix);
            List<String> ranks=new ArrayList<>(teams.keySet());ranks.sort(Comparator.comparingInt((String t)->qualifying.getOrDefault(t,0)).reversed());
            StringBuilder side=new StringBuilder("§5§lGAMESHOW\n§7Spiel "+game+"/9\n");
            if(game==1)side.append("§eQuiz ").append(questionNumber).append("/10 §7| §f").append(quizScores.getOrDefault(mine,0)).append(" Punkte\n");
            side.append("§dTOP 5 TEAMS\n");
            for(int i=0;i<Math.min(5,ranks.size());i++)side.append("§f").append(i+1).append(". ").append(chatColor(ranks.get(i))).append(ranks.get(i)).append(" §d").append(qualifying.getOrDefault(ranks.get(i),0)).append("\n");
            if(mine!=null)side.append("§7Du: #").append(ranks.indexOf(mine)+1).append(" §f").append(qualifying.getOrDefault(mine,0)).append("\n");
            List<UUID> people=new ArrayList<>(individual.keySet());people.sort(Comparator.comparingInt((UUID id)->individual.getOrDefault(id,0)).reversed());
            side.append("§dTOP 5 SPIELER\n");for(int i=0;i<Math.min(5,people.size());i++)side.append("§f").append(i+1).append(". ").append(team(people.get(i))==null?ChatColor.WHITE:chatColor(team(people.get(i)))).append(Optional.ofNullable(Bukkit.getOfflinePlayer(people.get(i)).getName()).orElse("?" )).append(" §d").append(individual.get(people.get(i))).append("\n");
            side.append("§7Du: #").append(people.indexOf(p.getUniqueId())+1).append(" §f").append(individual.getOrDefault(p.getUniqueId(),0));
            // Sidebar scoreboard is refreshed with unique line entries; limit is 15 lines.
            Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard();
            for(var entry:teams.entrySet()){
                Team scoreTeam=board.registerNewTeam("wg"+teams.keySet().stream().toList().indexOf(entry.getKey()));
                scoreTeam.setColor(chatColor(entry.getKey()));
                for(UUID member:entry.getValue()){
                    String name=Bukkit.getOfflinePlayer(member).getName();if(name!=null)scoreTeam.addEntry(name);
                }
            }
            Objective obj=board.registerNewObjective("wg","dummy","§5§lWOLFSFELL");obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            String[] lines=side.toString().split("\n");int n=Math.min(15,lines.length);
            for(int i=0;i<n;i++)obj.getScore(lines[i].substring(0,Math.min(35,lines[i].length()))+"§"+"0123456789abcdef".charAt(i)).setScore(n-i);
            p.setScoreboard(board);
            p.setGlowing(mine!=null);
        }
    }
    private void rankRoundSafe(){
        qualifying.clear();
        for(String t:teams.keySet())qualifying.put(t,0);
        for(int round=1;round<=5;round++){
            final int r=round;
            List<String> sorted=new ArrayList<>(teams.keySet());
            sorted.removeIf(t->teams.get(t).isEmpty() || !raw.getOrDefault(t,Map.of()).containsKey(r));
            sorted.sort((a,b)->Integer.compare(raw.get(b).get(r),raw.get(a).get(r)));
            for(int i=0;i<sorted.size();){
                int j=i+1;
                while(j<sorted.size() && raw.get(sorted.get(i)).get(r).equals(raw.get(sorted.get(j)).get(r)))j++;
                int sum=0;for(int z=i;z<j;z++)sum+=RANK_POINTS[Math.min(z,7)];
                int points=Math.round((float)sum/(j-i));
                for(int z=i;z<j;z++)qualifying.merge(sorted.get(z),points,Integer::sum);
                i=j;
            }
        }
    }
    private void returnUnselectedToCages(){
        for(Player player:Bukkit.getOnlinePlayers()){
            String t=team(player.getUniqueId());
            if(t==null || Objects.equals(selected.get(t),player.getUniqueId()))continue;
            Location home=locations.get("team:"+t.toLowerCase(Locale.ROOT));
            if(home==null)home=locations.get("team:"+t);
            if(home!=null)player.teleport(home);
        }
    }
    private void loadState(){
        if(!stateFile.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(stateFile);
        game=y.getInt("game",0);running=false;selectionEnabled=false;studioLocked=y.getBoolean("studio-locked",false);finalists=y.getStringList("finalists");
        ConfigurationSection ts=y.getConfigurationSection("teams");if(ts!=null)for(String t:ts.getKeys(false)){
            LinkedHashSet<UUID> members=new LinkedHashSet<>();for(String id:ts.getStringList(t))try{members.add(UUID.fromString(id));}catch(IllegalArgumentException ignored){}teams.put(t,members);
        }
        ConfigurationSection tc=y.getConfigurationSection("team-colors");if(tc!=null)for(String t:tc.getKeys(false))teamColors.put(t,tc.getString(t,"PURPLE"));
        ConfigurationSection cg=y.getConfigurationSection("cages");if(cg!=null)for(String id:cg.getKeys(false)){
            Location a=cg.getLocation(id+".pos1"),b=cg.getLocation(id+".pos2");if(a!=null&&b!=null)cages.put(id,new Location[]{a,b});
        }
        ConfigurationSection nl=y.getConfigurationSection("neon-lights");if(nl!=null)for(String k:nl.getKeys(false))neonLights.put(k,nl.getString(k));
        ConfigurationSection ng=y.getConfigurationSection("neon");if(ng!=null)for(String k:ng.getKeys(false))neon.put(k,new HashSet<>(ng.getStringList(k)));
        ConfigurationSection nr=y.getConfigurationSection("neon-regions");if(nr!=null)for(String id:nr.getKeys(false)){Location a=nr.getLocation(id+".pos1"),b=nr.getLocation(id+".pos2");if(a!=null&&b!=null)neonRegions.put(id,new Location[]{a,b});}
        ConfigurationSection loc=y.getConfigurationSection("locations");if(loc!=null)for(String k:loc.getKeys(false)){Location l=loc.getLocation(k);if(l!=null)locations.put(k,l);}
        ConfigurationSection q=y.getConfigurationSection("qualifying");if(q!=null)for(String t:q.getKeys(false))qualifying.put(t,q.getInt(t));
        ConfigurationSection rr=y.getConfigurationSection("raw");if(rr!=null)for(String t:rr.getKeys(false)){ConfigurationSection r=rr.getConfigurationSection(t);if(r!=null)for(String n:r.getKeys(false))try{raw.computeIfAbsent(t,k->new HashMap<>()).put(Integer.parseInt(n),r.getInt(n));}catch(NumberFormatException ignored){}}
        ConfigurationSection a=y.getConfigurationSection("advantages");if(a!=null)for(String t:a.getKeys(false))advantages.put(t,a.getInt(t));
        ConfigurationSection sel=y.getConfigurationSection("selected");if(sel!=null)for(String t:sel.getKeys(false))try{selected.put(t,UUID.fromString(sel.getString(t,"")));}catch(IllegalArgumentException ignored){}
        ConfigurationSection chest=y.getConfigurationSection("chests");if(chest!=null)for(String k:chest.getKeys(false))mysteryChestValues.put(k,chest.getInt(k));
        openedChests.addAll(y.getStringList("opened-chests"));
        ConfigurationSection ids=y.getConfigurationSection("individual");if(ids!=null)for(String k:ids.getKeys(false))try{individual.put(UUID.fromString(k),ids.getInt(k));}catch(Exception ignored){}
        ConfigurationSection buz=y.getConfigurationSection("buzzers");if(buz!=null)for(String t:buz.getKeys(false))buzzerBlocks.put(t,buz.getString(t,""));
        ConfigurationSection target=y.getConfigurationSection("targets");if(target!=null)for(String t:target.getKeys(false))archeryTargets.put(t,new HashSet<>(target.getStringList(t)));
        ConfigurationSection cp=y.getConfigurationSection("race-checkpoints");if(cp!=null)for(String k:cp.getKeys(false)){List<Location> ls=new ArrayList<>();for(Object obj:cp.getList(k,List.of()))if(obj instanceof Location l)ls.add(l);raceCheckpoints.put(k,ls);}
        ConfigurationSection buy=y.getConfigurationSection("purchases");if(buy!=null)for(String t:buy.getKeys(false)){ConfigurationSection sec=buy.getConfigurationSection(t);if(sec!=null)for(String k:sec.getKeys(false))purchases.computeIfAbsent(t,x->new HashMap<>()).put(k,sec.getInt(k));}
    }
    private void saveState(){
        YamlConfiguration y=new YamlConfiguration();y.set("game",game);y.set("finalists",finalists);
        for(var e:teams.entrySet())y.set("teams."+e.getKey(),e.getValue().stream().map(UUID::toString).toList());
        for(var e:locations.entrySet())y.set("locations."+e.getKey(),e.getValue());
        for(var e:teamColors.entrySet())y.set("team-colors."+e.getKey(),e.getValue());
        for(var e:cages.entrySet()){y.set("cages."+e.getKey()+".pos1",e.getValue()[0]);y.set("cages."+e.getKey()+".pos2",e.getValue()[1]);}
        for(var e:neon.entrySet())y.set("neon."+e.getKey(),new ArrayList<>(e.getValue()));
        for(var e:neonRegions.entrySet()){y.set("neon-regions."+e.getKey()+".pos1",e.getValue()[0]);y.set("neon-regions."+e.getKey()+".pos2",e.getValue()[1]);}
        y.set("studio-locked",studioLocked);for(var e:neonLights.entrySet())y.set("neon-lights."+e.getKey(),e.getValue());
        for(var e:qualifying.entrySet())y.set("qualifying."+e.getKey(),e.getValue());
        for(var e:advantages.entrySet())y.set("advantages."+e.getKey(),e.getValue());
        for(var e:raw.entrySet())for(var r:e.getValue().entrySet())y.set("raw."+e.getKey()+"."+r.getKey(),r.getValue());
        for(var e:selected.entrySet())y.set("selected."+e.getKey(),e.getValue().toString());
        // Keys contain UUIDs and colons, which are safe YAML path segment characters.
        for(var e:mysteryChestValues.entrySet())y.set("chests."+e.getKey(),e.getValue());
        y.set("opened-chests",new ArrayList<>(openedChests));
        for(var e:individual.entrySet())y.set("individual."+e.getKey(),e.getValue());
        for(var e:buzzerBlocks.entrySet())y.set("buzzers."+e.getKey(),e.getValue());
        for(var e:archeryTargets.entrySet())y.set("targets."+e.getKey(),new ArrayList<>(e.getValue()));
        for(var e:raceCheckpoints.entrySet())y.set("race-checkpoints."+e.getKey(),e.getValue());
        for(var e:purchases.entrySet())for(var item:e.getValue().entrySet())y.set("purchases."+e.getKey()+"."+item.getKey(),item.getValue());
        try{y.save(stateFile);}catch(IOException ex){getLogger().severe("State save failed: "+ex.getMessage());}
    }
    private boolean participatedInRound(String t,int round){
        if((teams.get(t)==null || teams.get(t).isEmpty()))return false;
        if(round==1 || round==2 || round==5)return selected.containsKey(t) && teams.get(t).contains(selected.get(t));
        return true;
    }
    private void rankRound(CommandSender s,int n){
        if(n<1||n>5){say(s,"Nur Vorrundenspiele 1-5.");return;}
        List<String> order=new ArrayList<>(teams.keySet());order.sort((a,b)->Integer.compare(raw.getOrDefault(b,Map.of()).getOrDefault(n,Integer.MIN_VALUE),raw.getOrDefault(a,Map.of()).getOrDefault(n,Integer.MIN_VALUE)));
        if(order.stream().noneMatch(t->raw.getOrDefault(t,Map.of()).containsKey(n))){say(s,"Noch keine Ergebnisse für Spiel "+n+" gespeichert.");return;}
        for(String t:teams.keySet())qualifying.put(t,0);
        for(int round=1;round<=5;round++) {
            final int r=round; if(teams.keySet().stream().noneMatch(t->raw.getOrDefault(t,Map.of()).containsKey(r)))continue;
            List<String> sorted=new ArrayList<>(teams.keySet());
            sorted.removeIf(t->teams.get(t).isEmpty() || !raw.getOrDefault(t,Map.of()).containsKey(r) || !participatedInRound(t,r));
            sorted.sort((a,b)->Integer.compare(raw.get(b).get(r),raw.get(a).get(r)));
            for(int i=0;i<sorted.size();){int j=i+1;while(j<sorted.size()&&raw.get(sorted.get(i)).get(r).equals(raw.get(sorted.get(j)).get(r)))j++;
                int sum=0;for(int z=i;z<j;z++)sum+=RANK_POINTS[Math.min(z,7)];int points=Math.round((float)sum/(j-i));
                for(int z=i;z<j;z++)qualifying.merge(sorted.get(z),points,Integer::sum);i=j;
            }
        }
        say(s,"Vorrunden-Rangliste aktualisiert (Gleichstände teilen Platzierungspunkte).");saveState();
    }
    private void finalizeTeams(CommandSender s){
        if(teams.size()<2){say(s,"Mindestens zwei Teams erforderlich.");return;}
        for(String t:teams.keySet())for(int r=1;r<=5;r++)if(!raw.getOrDefault(t,Map.of()).containsKey(r)){say(s,"Fehlende Rohwertung: "+t+", Spiel "+r);return;}
        rankRound(s,5);List<String> order=new ArrayList<>(teams.keySet());order.sort((a,b)->{int c=Integer.compare(qualifying.getOrDefault(b,0),qualifying.getOrDefault(a,0));return c!=0?c:a.compareToIgnoreCase(b);});
        if(order.size()>2 && qualifying.get(order.get(1)).equals(qualifying.get(order.get(2)))){say(s,"Gleichstand um Platz 2: erst Stechen durchführen und /wg finalist <Team1> <Team2> verwenden.");return;}
        finalists=new ArrayList<>(order.subList(0,2));advantages.clear();for(String t:finalists)advantages.put(t,0);broadcast("FINALISTEN: §d"+finalists.get(0)+" §7vs §d"+finalists.get(1));saveState();
    }
    private void setupMystery(CommandSender s){
        if(mysteryChestValues.size()!=50){say(s,"Es sind "+mysteryChestValues.size()+" statt 50 Truhen markiert. /wg chest add auf jede Truhe richten.");return;}
        List<Integer> values=new ArrayList<>();List<Integer> v=getConfig().getIntegerList("settings.mystery-values"),counts=getConfig().getIntegerList("settings.mystery-counts");
        for(int i=0;i<Math.min(v.size(),counts.size());i++)for(int j=0;j<counts.get(i);j++)values.add(v.get(i));
        if(values.size()!=50){say(s,"config.yml: Mystery-Verteilung muss genau 50 ergeben.");return;}
        Collections.shuffle(values);List<String> keys=new ArrayList<>(mysteryChestValues.keySet());Collections.sort(keys);
        for(int i=0;i<50;i++)mysteryChestValues.put(keys.get(i),values.get(i));openedChests.clear();mysteryScores.clear();mysteryStopped.clear();
        for(String t:finalists)mysteryScores.put(t,0);say(s,"50 Mystery-Werte zufällig neu verteilt.");saveState();
    }
    private ChatColor chatColor(String t) {
        try { return ChatColor.valueOf(teamColors.getOrDefault(t,"WHITE")); }
        catch (IllegalArgumentException ex) { return ChatColor.WHITE; }
    }
    private Material armorColor(String t) {
        String c=teamColors.getOrDefault(t,"WHITE");
        Material m=Material.matchMaterial(c+"_LEATHER_CHESTPLATE");
        return m==null?Material.LEATHER_CHESTPLATE:m;
    }
    private Color rgb(String c) { return switch(c) {
        case "PURPLE" -> Color.fromRGB(155,89,230); case "BLUE" -> Color.fromRGB(52,130,255);
        case "RED" -> Color.fromRGB(255,65,80); case "GREEN" -> Color.fromRGB(60,220,120);
        case "YELLOW" -> Color.fromRGB(255,220,55); case "ORANGE" -> Color.fromRGB(255,145,45);
        case "PINK" -> Color.fromRGB(255,100,190); case "CYAN" -> Color.fromRGB(50,225,235);
        default -> Color.WHITE;
    }; }
    private void equipTeam(Player p) {
        String t=team(p.getUniqueId()); if(t==null)return;
        ItemStack chest=new ItemStack(Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta m=(org.bukkit.inventory.meta.LeatherArmorMeta)chest.getItemMeta();
        m.setColor(rgb(teamColors.getOrDefault(t,"PURPLE")));
        m.setDisplayName(chatColor(t)+t+" §7· Gameshow");
        chest.setItemMeta(m);p.getInventory().setChestplate(chest);
    }
    private void refreshAllArmor() { for(Player p:Bukkit.getOnlinePlayers())equipTeam(p); }
    private ItemStack setupWand(String mode) {
        ItemStack item=new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta=item.getItemMeta();meta.setDisplayName("§d§lWolfsfell §fSetup-Wand §8["+mode+"]");
        meta.setLore(List.of("§7Linksklick: Position 1", "§7Rechtsklick: Position 2", "§7Neon: /wg neon save <Team>", "§8Nur für Admins"));
        meta.getPersistentDataContainer().set(wandKey,PersistentDataType.STRING,mode);
        item.setItemMeta(meta);return item;
    }
    private String wandMode(ItemStack item) {
        if(item==null||!item.hasItemMeta())return null;
        return item.getItemMeta().getPersistentDataContainer().get(wandKey,PersistentDataType.STRING);
    }
    private Block blockFromKey(String k){
        try {String[] a=k.split(":");World w=Bukkit.getWorld(UUID.fromString(a[0]));return w==null?null:w.getBlockAt(Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]));}
        catch(Exception ex){return null;}
    }
    // Legacy neon: remove only plugin-style glowing BlockDisplays close to saved markers.
    // Never globally remove all BlockDisplays: other map decorations may use them.
    private int clearLegacyNeonDisplays() {
        int removed=0;
        for(String key:neon.keySet()) {
            Block base=blockFromKey(key);if(base==null)continue;
            for(org.bukkit.entity.Entity entity:base.getWorld().getNearbyEntities(base.getLocation().add(.5,.8,.5),1.8,2.0,1.8)) {
                if(!(entity instanceof BlockDisplay display)||!display.isGlowing())continue;
                Material m=display.getBlock().getMaterial();
                if(!m.name().endsWith("STAINED_GLASS")&&!m.name().endsWith("CONCRETE"))continue;
                entity.remove();removed++;
            }
        }
        for(String lightKey:neonLights.values()) {
            Block block=blockFromKey(lightKey);
            if(block!=null&&block.getType()==Material.LIGHT)block.setType(Material.AIR,false);
        }
        neon.clear();neonLights.clear();neonEdges.clear();neonDisplays.clear();
        return removed;
    }
    private Material neonMaterial(String team) {
        return switch(teamColors.getOrDefault(team,"PURPLE").toUpperCase(Locale.ROOT)) {
            case "RED" -> Material.RED_STAINED_GLASS;
            case "BLUE" -> Material.BLUE_STAINED_GLASS;
            case "GREEN" -> Material.LIME_STAINED_GLASS;
            case "YELLOW" -> Material.YELLOW_STAINED_GLASS;
            case "ORANGE" -> Material.ORANGE_STAINED_GLASS;
            case "PINK" -> Material.PINK_STAINED_GLASS;
            case "CYAN" -> Material.CYAN_STAINED_GLASS;
            default -> Material.PURPLE_STAINED_GLASS;
        };
    }
    private void removeRegionDisplays(String team) {
        for(World world:Bukkit.getWorlds())for(org.bukkit.entity.Entity e:world.getEntitiesByClass(BlockDisplay.class)) {
            if(e.getScoreboardTags().contains("wg_neon_region")&&e.getScoreboardTags().contains("wg_neon_"+team.toLowerCase(Locale.ROOT)))e.remove();
        }
    }
    private void addNeonStrip(World world,Material material,String team,double x,double y,double z,float dx,float dy,float dz) {
        if(dx<=0||dy<=0||dz<=0)return;
        BlockDisplay d=world.spawn(new Location(world,x,y,z),BlockDisplay.class,entity->{
            entity.setBlock(Bukkit.createBlockData(material));
            entity.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),new org.joml.Quaternionf(),new org.joml.Vector3f(dx,dy,dz),new org.joml.Quaternionf()));
            entity.setGlowing(true);
            entity.addScoreboardTag("wg_neon_region");
            entity.addScoreboardTag("wg_neon_"+team.toLowerCase(Locale.ROOT));
            entity.setPersistent(true);
        });
        neonRegionDisplays.add(d.getUniqueId());
    }
    private void restoreRegionNeon() {for(String team:neonRegions.keySet())renderNeonRegion(team);}
    // Draw only the silhouette of the highest exposed solid surface in each column.
    // Merge collinear edges, avoid duplicate internal seams and keep all strips flush.
    private void renderNeonRegion(String id) {
        Location[] area=neonRegions.get(id);
        if(area==null||!area[0].getWorld().equals(area[1].getWorld()))return;
        removeRegionDisplays(id);
        World w=area[0].getWorld();
        int x0=Math.min(area[0].getBlockX(),area[1].getBlockX());
        int x1=Math.max(area[0].getBlockX(),area[1].getBlockX());
        int y0=Math.min(area[0].getBlockY(),area[1].getBlockY());
        int y1=Math.max(area[0].getBlockY(),area[1].getBlockY());
        int z0=Math.min(area[0].getBlockZ(),area[1].getBlockZ());
        int z1=Math.max(area[0].getBlockZ(),area[1].getBlockZ());
        if(x1-x0>48||z1-z0>48||y1-y0>16)return;
        String team=id.startsWith("selection:")?id.substring("selection:".length()):id;
        Material m=neonMaterial(team);
        final float t=.032f;
        int width=x1-x0+1,depth=z1-z0+1;
        int[][] heights=new int[width][depth];
        for(int ix=0;ix<width;ix++)for(int iz=0;iz<depth;iz++){
            heights[ix][iz]=Integer.MIN_VALUE;
            for(int y=y1;y>=y0;y--){
                Block b=w.getBlockAt(x0+ix,y,z0+iz);
                if(b.getType().isSolid()&&w.getBlockAt(x0+ix,y+1,z0+iz).isEmpty()){
                    heights[ix][iz]=y;break;
                }
            }
        }
        int strips=0;
        // Edges parallel to X: neighboring column must be lower or empty.
        for(int iz=0;iz<depth&&strips<160;iz++)for(int side=-1;side<=1&&strips<160;side+=2){
            int ix=0;
            while(ix<width&&strips<160){
                int y=heights[ix][iz];
                if(y==Integer.MIN_VALUE||!edgeExposed(heights,ix,iz,0,side,y)) {ix++;continue;}
                int start=ix++;
                while(ix<width&&heights[ix][iz]==y&&edgeExposed(heights,ix,iz,0,side,y))ix++;
                double z=z0+iz+(side==1?1:0)-t/2;
                addNeonStrip(w,m,id,x0+start,y+1.006,z,(float)(ix-start),t,t);
                strips++;
            }
        }
        // Edges parallel to Z.
        for(int ix=0;ix<width&&strips<160;ix++)for(int side=-1;side<=1&&strips<160;side+=2){
            int iz=0;
            while(iz<depth&&strips<160){
                int y=heights[ix][iz];
                if(y==Integer.MIN_VALUE||!edgeExposed(heights,ix,iz,1,side,y)){iz++;continue;}
                int start=iz++;
                while(iz<depth&&heights[ix][iz]==y&&edgeExposed(heights,ix,iz,1,side,y))iz++;
                double x=x0+ix+(side==1?1:0)-t/2;
                addNeonStrip(w,m,id,x,y+1.006,z0+start,t,t,(float)(iz-start));
                strips++;
            }
        }
        // Keep existing lighting, without replacing structural blocks.
        for(int x=x0;x<=x1;x+=4)for(int z=z0;z<=z1;z+=4){
            for(int y=y1+1;y>=y0;y--){
                if(w.getBlockAt(x,y,z).getType()==Material.AIR){placeNeonLight(w,x,y,z);break;}
            }
        }
    }
    private boolean edgeExposed(int[][] heights,int x,int z,int axis,int side,int y){
        int nx=x+(axis==1?side:0),nz=z+(axis==0?side:0);
        return nx<0||nz<0||nx>=heights.length||nz>=heights[0].length||heights[nx][nz]<y;
    }
    private void placeNeonLight(World world,int x,int y,int z){
        Block b=world.getBlockAt(x,y,z);
        if(b.getType()!=Material.AIR)return;
        b.setType(Material.LIGHT,false);
        org.bukkit.block.data.type.Light data=(org.bukkit.block.data.type.Light)b.getBlockData();data.setLevel(15);b.setBlockData(data,false);
        // Track generated light blocks to remove them safely when the region is cleared.
        neonLights.put(key(b.getLocation()),key(b.getLocation()));
    }
    private void clearRegionNeon(String team) {
        removeRegionDisplays(team);
        Location[] area=neonRegions.remove(team);
        if(area==null)return;
        for(String k:new HashSet<>(neonLights.keySet())) {
            Block b=blockFromKey(k);if(b==null)continue;
            Location l=b.getLocation();
            int x=l.getBlockX(),z=l.getBlockZ(),y=l.getBlockY();
            int x0=Math.min(area[0].getBlockX(),area[1].getBlockX()),x1=Math.max(area[0].getBlockX(),area[1].getBlockX())+1;
            int z0=Math.min(area[0].getBlockZ(),area[1].getBlockZ()),z1=Math.max(area[0].getBlockZ(),area[1].getBlockZ())+1;
            int expectedY=Math.max(area[0].getBlockY(),area[1].getBlockY())+1;
            if(l.getWorld().equals(area[0].getWorld())&&x>=x0&&x<=x1&&z>=z0&&z<=z1&&y==expectedY){if(b.getType()==Material.LIGHT)b.setType(Material.AIR,false);neonLights.remove(k);}
        }
    }
    @EventHandler public void setupClick(PlayerInteractEvent e) {
        Player p=e.getPlayer();if(!p.hasPermission("wg.admin"))return;
        String mode=wandMode(e.getItem());if(mode==null)return;
        if(e.getHand()!=EquipmentSlot.HAND)return;
        if(e.getClickedBlock()==null)return;
        e.setCancelled(true);
        Block b=e.getClickedBlock();

        if(e.getAction()==Action.LEFT_CLICK_BLOCK){pos1.put(p.getUniqueId(),b.getLocation());say(p,"§dPOS1 §f"+b.getX()+" "+b.getY()+" "+b.getZ());}
        else if(e.getAction()==Action.RIGHT_CLICK_BLOCK){pos2.put(p.getUniqueId(),b.getLocation());say(p,"§dPOS2 §f"+b.getX()+" "+b.getY()+" "+b.getZ());}
    }
    private boolean inside(Location p,Location a,Location b){
        return p.getWorld().equals(a.getWorld())&&a.getWorld().equals(b.getWorld())
            &&p.getBlockX()>=Math.min(a.getBlockX(),b.getBlockX())&&p.getBlockX()<=Math.max(a.getBlockX(),b.getBlockX())
            &&p.getBlockY()>=Math.min(a.getBlockY(),b.getBlockY())&&p.getBlockY()<=Math.max(a.getBlockY(),b.getBlockY())
            &&p.getBlockZ()>=Math.min(a.getBlockZ(),b.getBlockZ())&&p.getBlockZ()<=Math.max(a.getBlockZ(),b.getBlockZ());
    }
    @EventHandler public void onPlayerEnter(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(this,()->equipTeam(e.getPlayer()),5L);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0 || args[0].equalsIgnoreCase("help")){
            say(sender,"/wg team create|delete|color|join|leave|list | /wg wand region | /wg wand neon <Team> | /wg region save cage:<Team>");
            say(sender,"/wg studio lock|unlock | /wg region save selection:<Team> | /wg select on/off");
            say(sender,"/wg neon save <Team> | /wg neon selection <Team> | /wg neon clear <Team> | /wg neon cleanup");
            say(sender,"/wg reset scores | /wg reset game1 | /wg candidate set <Team> <Spieler> | /wg candidates | /wg screen set|test | /wg buzzer set <Team> (Knopf ansehen) | /wg game <1-9> | /wg start | /wg stop | /wg status | /wg tp <Ort>");
            say(sender,"/wg raw <Team> <Spiel 1-5> <Wert> | /wg rank | /wg qualify | /wg finalist <A> <B>");
            say(sender,"/wg basket <Team> <0-6> | /wg parkour finish | /wg chest add|clear|shuffle|stop");
            say(sender,"/wg points <Team> | /wg points add <Team> <Zahl> | /wg spend <Team> <Kosten> <Notiz>");return true;
        }
        String cmd=args[0].toLowerCase(Locale.ROOT);
        if(cmd.equals("screen")&&admin(sender)) {
            if(!(sender instanceof Player p))return true;
            if(args.length>=2 && args[1].equalsIgnoreCase("set")){gameOneScreen.setup(p);return true;}
            if(args.length>=2 && args[1].equalsIgnoreCase("test")){
                gameOneScreen.show("Diamant");
                for(int i=1;i<=8;i++)Bukkit.getScheduler().runTaskLater(this,()->{
                    if(!running)gameOneScreen.advance();
                },i*40L);
                return true;
            }
            say(sender,"/wg screen set (unteren linken Wandblock ansehen) | /wg screen test");return true;
        }
        if(cmd.equals("wand")&&admin(sender)) {
            if(!(sender instanceof Player p))return true;
            String mode=args.length>=2?args[1].toLowerCase(Locale.ROOT):"region";
            if(mode.equals("neon"))mode="region";
            p.getInventory().addItem(setupWand(mode));say(sender,"Setup-Wand erhalten: "+mode);return true;
        }
        if(cmd.equals("neon")&&admin(sender)) {
            if(args.length>=2&&args[1].equalsIgnoreCase("cleanup")) {
                int count=clearLegacyNeonDisplays();saveState();say(sender,"Alte Neon-Displays entfernt: "+count+". Andere Displays wurden nicht global gelöscht.");return true;
            }
            if(args.length>=3&&args[1].equalsIgnoreCase("clear")) {
                String team=teams.keySet().stream().filter(t->t.equalsIgnoreCase(args[2])).findFirst().orElse(args[2]);
                clearRegionNeon(team);saveState();say(sender,"Neonregion für "+team+" entfernt.");return true;
            }
            if(args.length>=3&&args[1].equalsIgnoreCase("save")&&sender instanceof Player p) {
                String team=teams.keySet().stream().filter(t->t.equalsIgnoreCase(args[2])).findFirst().orElse(null);
                if(team==null){say(sender,"Team existiert nicht.");return true;}
                Location a=pos1.get(p.getUniqueId()),b=pos2.get(p.getUniqueId());
                if(a==null||b==null||!a.getWorld().equals(b.getWorld())){say(sender,"Erst POS1 und POS2 mit /wg wand region markieren.");return true;}
                clearRegionNeon(team);neonRegions.put(team,new Location[]{a.clone(),b.clone()});renderNeonRegion(team);saveState();
                say(sender,"Sichtbare Blockkanten für "+team+" markiert.");return true;
            }
            if(args.length>=3&&args[1].equalsIgnoreCase("selection")&&sender instanceof Player p) {
                String team=teams.keySet().stream().filter(t->t.equalsIgnoreCase(args[2])).findFirst().orElse(null);
                if(team==null){say(sender,"Team existiert nicht.");return true;}
                Location[] zone=cages.get("selection:"+team.toLowerCase(Locale.ROOT));
                if(zone==null){say(sender,"Zuerst /wg region save selection:"+team+" ausführen.");return true;}
                String id="selection:"+team;
                clearRegionNeon(id);neonRegions.put(id,new Location[]{zone[0].clone(),zone[1].clone()});
                renderNeonRegion(id);saveState();say(sender,"Auswahlplatz von "+team+" leuchtet jetzt.");return true;
            }
            say(sender,"/wg neon save <Team> | selection <Team> | clear <Team> | cleanup");return true;
        }
        if(cmd.equals("region")&&admin(sender)&&sender instanceof Player p&&args.length>=3&&args[1].equalsIgnoreCase("save")){
            Location a=pos1.get(p.getUniqueId()),b=pos2.get(p.getUniqueId());
            if(a==null||b==null||!a.getWorld().equals(b.getWorld())){say(sender,"Erst POS1 und POS2 mit der Wand markieren.");return true;}
            String id=args[2].toLowerCase(Locale.ROOT);
            if((id.startsWith("cage:")||id.startsWith("selection:")) && teams.keySet().stream().noneMatch(t->t.equalsIgnoreCase(id.substring(id.indexOf(':')+1)))){say(sender,"Team existiert nicht.");return true;}
            cages.put(id,new Location[]{a.clone(),b.clone()});if(id.startsWith("selection:")){String team=teams.keySet().stream().filter(t->t.equalsIgnoreCase(id.substring(10))).findFirst().orElse(null);if(team!=null&&neonRegions.containsKey("selection:"+team)){clearRegionNeon("selection:"+team);neonRegions.put("selection:"+team,new Location[]{a.clone(),b.clone()});renderNeonRegion("selection:"+team);}}saveState();say(sender,"Region "+id+" gespeichert.");return true;
        }
        if(cmd.equals("studio")&&admin(sender)){
            if(args.length<2){say(sender,"/wg studio lock|unlock|status");return true;}
            if(args[1].equalsIgnoreCase("status")){say(sender,"Studio gesperrt: "+studioLocked+", Auswahl: "+selectionEnabled);return true;}
            studioLocked=args[1].equalsIgnoreCase("lock");selectionEnabled=false;saveState();say(sender,studioLocked?"Studio geschlossen: Teams bleiben in ihren Käfigen.":"Studio offen.");return true;
        }
        if(cmd.equals("status")){say(sender,"Spiel "+game+(game>0?" ("+GAMES[game-1]+")":"")+", aktiv="+running+", Spielerwahl="+selectionEnabled+", Finalisten="+finalists);for(String t:teams.keySet())say(sender,t+" ("+teams.get(t).size()+"/3) | Vorrunde "+qualifying.getOrDefault(t,0)+" | Vorteile "+advantages.getOrDefault(t,0));return true;}
        if(cmd.equals("team")&&args.length>=2){
            if(args[1].equalsIgnoreCase("list")){for(var e:teams.entrySet())say(sender,e.getKey()+": "+e.getValue().stream().map(u->Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse(u.toString())).toList());return true;}
            if(!admin(sender))return true;
            if(args[1].equalsIgnoreCase("delete")&&args.length>=3){
                String t=args[2];if(!teams.containsKey(t)){say(sender,"Team nicht gefunden.");return true;}
                teams.remove(t);qualifying.remove(t);advantages.remove(t);raw.remove(t);selected.remove(t);quizScores.remove(t);
                cages.remove("cage:"+t.toLowerCase(Locale.ROOT));cages.remove("selection:"+t.toLowerCase(Locale.ROOT));locations.remove("team:"+t);finalists.remove(t);
                clearRegionNeon(t);clearRegionNeon("selection:"+t);
                say(sender,"Team "+t+" gelöscht.");saveState();refreshAllArmor();return true;
            }
            if(args[1].equalsIgnoreCase("color")&&args.length>=4){
                String t=args[2],c=args[3].toUpperCase(Locale.ROOT);
                if(!teams.containsKey(t)||!Arrays.asList(palette).contains(c)){say(sender,"Team/Farbe ungültig. Farben: "+String.join(", ",palette));return true;}
                teamColors.put(t,c);refreshAllArmor();saveState();say(sender,"Teamfarbe "+t+" = "+c);return true;
            }
            if(args[1].equalsIgnoreCase("create")&&args.length>=3){String t=args[2];if(!t.matches("[a-zA-Z0-9_-]{1,20}")||teams.containsKey(t)||teams.size()>=getConfig().getInt("settings.max-teams",8)){say(sender,"Ungültiger Name, Team vorhanden oder 8 Teams erreicht.");return true;}teams.put(t,new LinkedHashSet<>());teamColors.put(t,palette[(teams.size()-1)%palette.length]);say(sender,"Team "+t+" erstellt.");saveState();return true;}
            if(args[1].equalsIgnoreCase("join")&&args.length>=3){String t=args[2];if(!teams.containsKey(t)){say(sender,"Team nicht vorhanden.");return true;}Player p=args.length>=4?Bukkit.getPlayerExact(args[3]):sender instanceof Player pl?pl:null;if(p==null){say(sender,"Spieler muss online sein.");return true;}if(teams.get(t).size()>=getConfig().getInt("settings.team-size",3)&&!teams.get(t).contains(p.getUniqueId())){say(sender,"Team voll.");return true;}teams.values().forEach(set->set.remove(p.getUniqueId()));teams.get(t).add(p.getUniqueId());say(sender,p.getName()+" -> "+t);equipTeam(p);saveState();return true;}
            if(args[1].equalsIgnoreCase("leave")&&args.length>=3){Player p=Bukkit.getPlayerExact(args[2]);if(p!=null){teams.values().forEach(set->set.remove(p.getUniqueId()));saveState();}return true;}
        }
        if(cmd.equals("buzz")){
            if(!(sender instanceof Player p) || !running || game!=1)return true;
            String t=team(p.getUniqueId());if(t==null||!Objects.equals(selected.get(t),p.getUniqueId()))return true;
            say(sender,"Bitte den physischen Team-Buzzer anklicken, nicht /wg buzz verwenden.");return true;
        }
        if(cmd.equals("shop")){
            if(sender instanceof Player p)openShop(p);return true;
        }
        if(!admin(sender))return true;
        try {
            switch(cmd){
                case "reset" -> {
                    if(args.length<2)break;
                    if(args[1].equalsIgnoreCase("scores")){
                        running=false;game1QuestionActive=false;buzzed=null;selected.clear();
                        qualifying.clear();raw.clear();individual.clear();quizScores.clear();
                        advantages.clear();basketballScores.clear();parkourScores.clear();mysteryScores.clear();
                        rankLast.clear();questionNumber=0;usedQuestions.clear();attemptedBuzzers.clear();
                        finalists.clear();selected.clear();answerDeadline=0;game1RoundEnds=0;
                        saveState();updateDisplays();say(sender,"Alle Testpunkte zurückgesetzt. Teams, Regionen und Buzzer bleiben.");return true;
                    }
                    if(args[1].equalsIgnoreCase("game1")){
                        running=false;game1QuestionActive=false;buzzed=null;buzzDeadline=0;
                        quizScores.clear();usedQuestions.clear();attemptedBuzzers.clear();questionNumber=0;
                        for(Map<Integer,Integer> scores:raw.values())scores.remove(1);
                        rankRoundSafe();saveState();updateDisplays();
                        say(sender,"Game 1 zurückgesetzt. Für einen kompletten Test-Reset: /wg reset scores.");return true;
                    }
                    break;
                }
                case "next" -> {if(!running || !List.of(1,2,5).contains(game))break;nextQuestion();return true;}
                case "finishround" -> {
                    if(game>=1&&game<=5){
                        if(game==1||game==2||game==5)for(String t:teams.keySet())if(participatedInRound(t,game))raw.computeIfAbsent(t,k->new HashMap<>()).put(game,quizScores.getOrDefault(t,0));
                        if(game==3)for(String t:teams.keySet())if(!teams.get(t).isEmpty() && raceFinishes.containsKey(t))raw.computeIfAbsent(t,k->new HashMap<>()).put(game,raceFinishes.containsKey(t)?(1000-(int)(raceFinishes.get(t)-startMillis)/1000):raceProgress.getOrDefault(t,0));
                        if(game==4)for(String t:teams.keySet())if(!teams.get(t).isEmpty() && archeryHits.containsKey(t))raw.computeIfAbsent(t,k->new HashMap<>()).put(game,archeryHits.getOrDefault(t,0));
                        running=false;rankRound(sender,game);broadcast("§dSpiel "+game+" abgeschlossen.");return true;
                    }break;
                }
                case "candidate" -> {
                    if(args.length<4 || !args[1].equalsIgnoreCase("set")){
                        say(sender,"/wg candidate set <Team> <Spieler>");return true;
                    }
                    String targetTeam=teams.keySet().stream().filter(k->k.equalsIgnoreCase(args[2])).findFirst().orElse(null);
                    Player candidate=Bukkit.getPlayerExact(args[3]);
                    if(targetTeam==null || candidate==null){say(sender,"Team unbekannt oder Spieler offline.");return true;}
                    if(!teams.get(targetTeam).contains(candidate.getUniqueId())){
                        say(sender,candidate.getName()+" gehört nicht zu "+targetTeam+". Erst /wg team join "+targetTeam+" "+candidate.getName());return true;
                    }
                    selected.put(targetTeam,candidate.getUniqueId());selectionTicks.clear();
                    saveState();say(sender,candidate.getName()+" ist jetzt Kandidat für "+targetTeam);
                    return true;
                }
                case "candidates" -> {
                    for(String name:teams.keySet()){
                        UUID id=selected.get(name);
                        say(sender,name+": "+(id==null?"kein Kandidat":Bukkit.getOfflinePlayer(id).getName())+
                            " | Buzzer "+(buzzerBlocks.containsKey(name)?"gesetzt":"FEHLT"));
                    }
                    return true;
                }
                case "buzzer" -> {
                    if(!(sender instanceof Player p)||args.length<3||!args[1].equalsIgnoreCase("set")||!teams.containsKey(args[2]))break;
                    Block b=p.getTargetBlockExact(8);if(b==null){say(sender,"Schaue auf den Knopf.");return true;}
                    if(!b.getType().name().endsWith("BUTTON")){say(sender,"Bitte einen echten Minecraft-Knopf anvisieren.");return true;}
                    buzzerBlocks.put(args[2],key(b.getLocation()));saveState();say(sender,"Physischer Buzzer für "+args[2]+" gespeichert.");return true;
                }
                case "target" -> {
                    if(!(sender instanceof Player p)||args.length<3||!args[1].equalsIgnoreCase("add")||!teams.containsKey(args[2]))break;
                    Block b=p.getTargetBlockExact(12);if(b==null)break;
                    archeryTargets.computeIfAbsent(args[2],k->new HashSet<>()).add(key(b.getLocation()));say(sender,"Ziel für "+args[2]+" gesetzt: "+archeryTargets.get(args[2]).size());saveState();return true;
                }
                case "checkpoint" -> {
                    if(!(sender instanceof Player p)||args.length<2)break;
                    if(args[1].equalsIgnoreCase("add")){raceCheckpoints.computeIfAbsent("main",k->new ArrayList<>()).add(p.getLocation());say(sender,"Checkpoint #"+raceCheckpoints.get("main").size()+" gesetzt.");}
                    if(args[1].equalsIgnoreCase("clear")){raceCheckpoints.remove("main");say(sender,"Checkpoints gelöscht.");}
                    saveState();return true;
                }
                case "set" -> {if(!(sender instanceof Player p)||args.length<2)break;locations.put(args[1].toLowerCase(Locale.ROOT),p.getLocation().clone());say(sender,"Position "+args[1]+" gespeichert.");saveState();return true;}
                case "tp" -> {if(!(sender instanceof Player p)||args.length<2)break;Location l=locations.get(args[1].toLowerCase(Locale.ROOT));if(l==null){say(sender,"Ort nicht gesetzt.");return true;}p.teleport(l);return true;}
                case "select" -> {if(args.length<2)break;
                    boolean enable=args[1].equalsIgnoreCase("on");
                    selectionEnabled=enable;selectionTicks.clear();
                    if(enable){selected.clear();}
                    else {returnUnselectedToCages();studioLocked=true;}
                    saveState();say(sender,"Spielerwahl "+(selectionEnabled?"aktiv":"aus"));return true;}
                case "game" -> {if(args.length<2)break;int g=Integer.parseInt(args[1]);if(g<1||g>9)break;running=false;game=g;selected.clear();selectionTicks.clear();buzzed=null;attemptedBuzzers.clear();say(sender,"Spiel "+game+": "+GAMES[game-1]);saveState();return true;}
                case "start" -> {if(game==0){say(sender,"Zuerst /wg game <1-9>.");return true;}if(game>=6&&finalists.size()!=2){say(sender,"Finalteams fehlen: /wg qualify oder /wg finalist.");return true;}if(game==8){if(mysteryChestValues.size()!=50){say(sender,"Bitte zuerst 50 Truhen markieren.");return true;}setupMystery(sender);}if(game==7){if(!locations.containsKey("parkour-finish")){say(sender,"Parkour-Ziel fehlt.");return true;}parkourScores.clear();parkourFinishes.clear();}running=true;startMillis=System.currentTimeMillis();selectionEnabled=false;
                    if(game==1||game==2||game==5){game1QuestionActive=false;quizScores.clear();usedQuestions.clear();questionNumber=0;Bukkit.getScheduler().runTaskLater(this,this::nextQuestion,30L);}
                    if(game==3){raceProgress.clear();raceFinishes.clear();}
                    if(game==4){archeryHits.clear();archeryFinishTimes.clear();}
                    broadcast("§dSTART: "+GAMES[game-1]);saveState();return true;}
                case "stop" -> {if(game==7&&running){stopParkour();return true;}if(game==8&&running){finishMystery();return true;}running=false;broadcast("Spiel gestoppt.");saveState();return true;}
                case "raw" -> {if(args.length<4)break;String t=args[1];int n=Integer.parseInt(args[2]),v=Integer.parseInt(args[3]);if(!teams.containsKey(t)||n<1||n>5)break;raw.computeIfAbsent(t,k->new HashMap<>()).put(n,v);say(sender,"Rohwertung "+t+" Spiel "+n+" = "+v);rankRound(sender,n);return true;}
                case "rank" -> {rankRound(sender,5);return true;}
                case "qualify" -> {finalizeTeams(sender);return true;}
                case "finalist" -> {if(args.length<3||!teams.containsKey(args[1])||!teams.containsKey(args[2])||args[1].equals(args[2]))break;finalists=List.of(args[1],args[2]);advantages.clear();for(String t:finalists)advantages.put(t,0);broadcast("Finale: "+finalists);saveState();return true;}
                case "basket" -> {if(args.length<3)break;String t=args[1];int v=Integer.parseInt(args[2]);if(game!=6||!finalTeam(t)||v<0||v>6)break;int old=basketballScores.getOrDefault(t,0);basketballScores.put(t,v);advantages.merge(t,v-old,Integer::sum);say(sender,t+" Basketball = "+v+" Vorteilspunkte (Differenz gebucht)");saveState();return true;}
                case "parkour" -> {if(args.length<2||!args[1].equalsIgnoreCase("finish")||!(sender instanceof Player p)||game!=7||!running)break;Location goal=locations.get("parkour-finish");if(goal==null)break;p.teleport(goal);return true;}
                case "chest" -> {if(args.length<2)break;switch(args[1].toLowerCase(Locale.ROOT)){
                    case "add" -> {if(!(sender instanceof Player p))break;Block b=p.getTargetBlockExact(6);if(b==null||!(b.getType()==Material.CHEST||b.getType()==Material.TRAPPED_CHEST)){say(sender,"Schaue auf eine Truhe (max 6 Blöcke).");return true;}mysteryChestValues.putIfAbsent(key(b.getLocation()),0);say(sender,"Truhe markiert: "+mysteryChestValues.size()+"/50");saveState();return true;}
                    case "clear" -> {mysteryChestValues.clear();openedChests.clear();saveState();say(sender,"Truhenmarkierungen gelöscht.");return true;}
                    case "shuffle" -> {setupMystery(sender);return true;}
                    case "stop" -> {if(!(sender instanceof Player p))break;String t=team(p.getUniqueId());if(t!=null)mysteryStopped.put(t,true);return true;}
                    case "retire" -> {if(args.length<3||!finalTeam(args[2]))break;mysteryStopped.put(args[2],true);broadcast(args[2]+" steigt aus und behält seine Mystery-Punkte!");if(finalists.stream().allMatch(t->mysteryStopped.getOrDefault(t,false)))finishMystery();return true;}
                }return true;}
                case "points" -> {if(args.length==2){say(sender,args[1]+" = "+advantages.getOrDefault(args[1],0)+" Vorteilspunkte (ausgebbar: "+Math.max(0,advantages.getOrDefault(args[1],0))+")");return true;}if(args.length>=4&&args[1].equalsIgnoreCase("add")){String t=args[2];if(!teams.containsKey(t))break;advantages.merge(t,Integer.parseInt(args[3]),Integer::sum);saveState();say(sender,"Punkte korrigiert.");return true;}return true;}
                case "spend" -> {if(args.length<4)break;String t=args[1];int cost=Integer.parseInt(args[2]);if(!finalTeam(t)||cost<=0||Math.max(0,advantages.getOrDefault(t,0))<cost){say(sender,"Nicht genügend Punkte.");return true;}advantages.merge(t,-cost,Integer::sum);broadcast(t+" kauft "+String.join(" ",Arrays.copyOfRange(args,3,args.length))+" für "+cost+" Punkte.");saveState();return true;}
            }
        }catch(NumberFormatException ex){say(sender,"Bitte gültige ganze Zahlen verwenden.");return true;}
        say(sender,"Ungültiger Befehl. /wg help");return true;
    }
    private final LinkedHashMap<String,Integer> shopPrices=new LinkedHashMap<>(Map.of(
        "golden_apple",5,"arrow",1,"cobweb",3,"water_bucket",4,"lava_bucket",5,"ender_pearl",8,
        "shield",7,"iron_sword",6,"bow",5,"cooked_beef",2));
    private void openShop(Player p){
        String t=team(p.getUniqueId());if(t==null||!finalTeam(t)){say(p,"Nur Finalteams dürfen einkaufen.");return;}
        Inventory inv=Bukkit.createInventory(null,27,"§5Wolfsfell · KitUHC Shop");int slot=0;
        for(var entry:shopPrices.entrySet()){
            Material m=Material.matchMaterial(entry.getKey());if(m==null)continue;
            ItemStack item=new ItemStack(m);ItemMeta meta=item.getItemMeta();meta.setDisplayName("§f"+entry.getKey().replace('_',' '));meta.setLore(List.of("§d"+entry.getValue()+" Vorteilspunkte","§7Klicken zum Kaufen"));item.setItemMeta(meta);inv.setItem(slot++,item);
        }
        p.openInventory(inv);say(p,"Verfügbar: "+Math.max(0,advantages.getOrDefault(t,0))+" Punkte.");
    }
    @EventHandler public void onShopClick(InventoryClickEvent e){
        if(!e.getView().getTitle().equals("§5Wolfsfell · KitUHC Shop"))return;
        e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null)return;
        String t=team(p.getUniqueId());if(t==null||!finalTeam(t))return;
        String item=e.getCurrentItem().getType().name().toLowerCase(Locale.ROOT);Integer cost=shopPrices.get(item);
        if(cost==null)return;
        if(advantages.getOrDefault(t,0)<cost){say(p,"Nicht genügend Punkte.");return;}
        advantages.merge(t,-cost,Integer::sum);purchases.computeIfAbsent(t,k->new HashMap<>()).merge(item,1,Integer::sum);
        say(p,"Gekauft: "+item+" für "+cost+" Punkte. Rest: "+advantages.get(t));p.playSound(p.getLocation(),"wolfsfell.purchase",1f,1f);saveState();
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
        if(args.length==1)return Arrays.stream(new String[]{"screen","help","status","studio","team","wand","region","neon","set","tp","select","game","start","stop","raw","rank","qualify","finalist","basket","parkour","chest","points","spend","buzzer","candidate","candidates","reset","next","finishround","target","checkpoint","shop"}).filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("candidate"))return List.of("set");
        if(args.length==3&&args[0].equalsIgnoreCase("candidate")&&args[1].equalsIgnoreCase("set"))
            return teams.keySet().stream().filter(t->t.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        if(args.length==4&&args[0].equalsIgnoreCase("candidate")&&args[1].equalsIgnoreCase("set")){
            String chosen=teams.keySet().stream().filter(t->t.equalsIgnoreCase(args[2])).findFirst().orElse(null);
            if(chosen==null)return List.of();
            return teams.get(chosen).stream().map(Bukkit::getPlayer).filter(Objects::nonNull)
                .map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(args[3].toLowerCase(Locale.ROOT))).toList();
        }
        if(args.length==2&&args[0].equalsIgnoreCase("buzzer"))return List.of("set","check");
        if(args.length==3&&args[0].equalsIgnoreCase("buzzer")&&args[1].equalsIgnoreCase("set"))
            return teams.keySet().stream().filter(t->t.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("target"))return List.of("add");
        if(args.length==2&&args[0].equalsIgnoreCase("checkpoint"))return List.of("add","clear");
        if(args.length==2&&args[0].equalsIgnoreCase("team"))return List.of("create","join","leave","delete","color","list");
        if(args.length==2&&args[0].equalsIgnoreCase("wand"))return List.of("region");
        if(args.length==2&&args[0].equalsIgnoreCase("neon"))return List.of("save","selection","clear","cleanup");
        if(args.length==3&&args[0].equalsIgnoreCase("neon"))return new ArrayList<>(teams.keySet());
        if(args.length==2&&args[0].equalsIgnoreCase("region"))return List.of("save");
        if(args.length==3&&args[0].equalsIgnoreCase("wand")&&args[1].equalsIgnoreCase("neon"))return new ArrayList<>(teams.keySet());
        if(args.length==3&&args[0].equalsIgnoreCase("team")&&List.of("delete","color").contains(args[1].toLowerCase(Locale.ROOT)))return new ArrayList<>(teams.keySet());
        if(args.length==4&&args[0].equalsIgnoreCase("team")&&args[1].equalsIgnoreCase("color"))return Arrays.asList(palette);
        if(args.length==2&&args[0].equalsIgnoreCase("set"))return List.of("selection","studio","parkour-start","parkour-finish","kituhc","race-start","race-finish");
        if(args.length==2&&args[0].equalsIgnoreCase("select"))return List.of("on","off");
        if(args.length==2&&args[0].equalsIgnoreCase("chest"))return List.of("add","clear","shuffle","retire");
        if(args.length==2&&args[0].equalsIgnoreCase("game"))return List.of("1","2","3","4","5","6","7","8","9");
        if(args.length==2&&List.of("basket","raw","points","spend","finalist").contains(args[0].toLowerCase(Locale.ROOT)))return new ArrayList<>(teams.keySet());
        return List.of();
    }
}
