package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Core tournament engine. Integrations with external minigame plugins are intentionally separate. */
public final class GameshowPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String[] GAMES = {"Was ist das?!", "Vier Antworten", "Wolfsfell Grand Prix", "Zielwasser", "Schätz mal!", "Wortduell", "Parkour", "Mystery-Chests", "KitUHC 1vs1"};
    private static final List<String> GAME1_ITEMS = List.of(
        "Diamant","Goldbarren","Eisenschwert","Apfel","Bogen","Smaragd","Enderperle","Karotte","Spitzhacke","TNT",
        "Kupferbarren","Feuerkugel","Brot","Keks","Feder","Schleimball","Lapis","Eimer","Schere","Redstone",
        "Buch","Kohle","Knochen","Schneeball","Ei","Fackel","Ziegelstein","Uhr","Netheritbarren","Kompass");
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
    private boolean finaleStarted = false, manualFinalists = false;
    private final Set<UUID> persistentTeamChat = new HashSet<>();
    private final Set<String> leggingsRewards = new HashSet<>();
    private final List<WordQuestion> wordQuestions = new ArrayList<>();
    private final List<WordQuestion> wordSequence = new ArrayList<>();
    private final Map<String,String> wordAnswers = new HashMap<>();
    private final Map<String,Set<String>> wordUsed = new HashMap<>();
    private boolean wordAccepting = false;
    private int wordRound = 0, wordGeneration = 0;
    private record WordQuestion(String prompt,Set<String> answers) {}

    private final Map<UUID,Integer> individual = new HashMap<>();
    private final Map<String,Integer> quizScores = new HashMap<>();
    private final Map<String,String> responses = new HashMap<>();
    private final Map<UUID,ItemStack[]> game2Inventories = new HashMap<>();
    private File game2BackupFile;
    private final NamespacedKey game2Key = new NamespacedKey(this,"game2_answer");
    private boolean game2Accepting=false, game2Revealing=false;
    private String game2Right="";
    private int game2LastSecond=-1;
    private final Map<String,Integer> estimates = new HashMap<>();
    private final List<EstimateQuestion> estimateBank=new ArrayList<>();
    private final List<EstimateQuestion> estimateSequence=new ArrayList<>();
    private boolean estimateAccepting=false, estimateRevealing=false;
    private int estimateGeneration=0;
    private static final int ESTIMATE_ANSWER_SECONDS=8;
    private static final int ESTIMATE_REVEAL_SECONDS=10;
    private record EstimateQuestion(String difficulty,String prompt,int answer) {}
    private final Map<String,Integer> archeryHits = new HashMap<>();
    private GameFourTarget gameFour;
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
    private GameThreeRace gameThree;
    private boolean game1QuestionActive=false;
    private long game1RoundEnds=0;
    private long game1RemainingOnBuzz=0;

    @Override public void onEnable() {
        saveDefaultConfig(); stateFile = new File(getDataFolder(),"state.yml"); game2BackupFile=new File(getDataFolder(),"game2-inventory-backups.yml");loadGame2Backups();loadState();gameThree=new GameThreeRace(this);gameFour=new GameFourTarget(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("wg")).setExecutor(this);
        Objects.requireNonNull(getCommand("wg")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("t")).setExecutor(this);
        Objects.requireNonNull(getCommand("tc")).setExecutor(this);
        loadQuestions();
        loadEstimateQuestions();
        loadWordQuestions();
        // Game 1 images are loaded from resources/game1; no unrelated question entries.
        for(String answer:GAME1_ITEMS)questions.add(new QuizQuestion(1,"Errate das Minecraft-Item auf dem Bildschirm!",answer,new String[0]));
        Bukkit.getScheduler().runTaskLater(this, gameOneScreen::load, 40L);
        Bukkit.getScheduler().runTaskLater(this, () -> { clearLegacyNeonDisplays(); restoreRegionNeon(); refreshAllArmor(); refreshBuzzerColors(); }, 20L);
        clock = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this,this::updateDisplays,40L,40L);
        getLogger().info("Wolfsfell Gameshow 0.1 loaded; " + teams.size() + " teams");
    }
    void gameFourHit(String team,Player player,int hits){archeryHits.put(team,hits);individual.merge(player.getUniqueId(),1,Integer::sum);if(hits==10)archeryFinishTimes.put(team,System.currentTimeMillis());}
    void gameFourCompleted(){running=false;for(var entry:archeryHits.entrySet())raw.computeIfAbsent(entry.getKey(),k->new HashMap<>()).put(4,entry.getValue());rankRound(Bukkit.getConsoleSender(),4);saveState();broadcast("§dZielwasser beendet! Ergebnisse gespeichert.");}
    void completeGameThree(Map<String,Integer> scores){
        for(var entry:scores.entrySet())raw.computeIfAbsent(entry.getKey(),k->new HashMap<>()).put(3,entry.getValue());
        running=false;rankRound(Bukkit.getConsoleSender(),3);broadcast("§dSpiel 3 automatisch abgeschlossen.");saveState();
    }
    @Override public void onDisable() {wordGeneration++;wordAccepting=false; restoreGame2Inventories();if(gameThree!=null)gameThree.stop();if(gameFour!=null)gameFour.stop();if(clock!=null)clock.cancel(); for(List<UUID> ids:neonEdges.values())for(UUID id:ids){org.bukkit.entity.Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();} saveState(); }
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
                Location[] zone=t==null?null:cages.get("selection:"+t.toLowerCase(Locale.ROOT));if(zone!=null)near=allowed&&inside(p.getLocation(),zone[0],zone[1]);
                if(!near){selectionTicks.remove(p.getUniqueId());continue;}
                if(finaleStarted && !finalists.contains(t)){selectionTicks.remove(p.getUniqueId());continue;}
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
        if(game==2 && running && game2Accepting){int sec=(int)Math.max(0,(answerDeadline-System.currentTimeMillis()+999)/1000);if(sec!=game2LastSecond){game2LastSecond=sec;gameOneScreen.showQuiz2(questionNumber,currentPrompt,choices,sec,responses.size(),selected.size(),null);}}
        if(game==2 && answerDeadline>0 && System.currentTimeMillis()>=answerDeadline) {
            answerDeadline=0; resolveQuestion();
        }
        if(game==7) {
            int left=Math.max(0,getConfig().getInt("settings.parkour-start-points",12)-elapsed()/Math.max(1,getConfig().getInt("settings.parkour-loss-every-seconds",15)));
            if(elapsed()%5==0 && System.currentTimeMillis()%1000<60) for(String t:finalists) for(UUID id:teams.getOrDefault(t,new LinkedHashSet<>())) {
                Player p=Bukkit.getPlayer(id);if(p!=null)p.sendActionBar("§dPARKOUR §7| §f"+left+" mögliche Punkte §7| §e"+elapsed()+"s");
            }
            if(left==0){ for(String t:finalists)parkourScores.putIfAbsent(t,0); stopParkour(); }
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
    @EventHandler public void onJoin(PlayerJoinEvent e) { Bukkit.getScheduler().runTaskLater(this,()->{restoreGame2Inventory(e.getPlayer());updateDisplays();},20); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { if(e.getPlayer().getUniqueId().equals(buzzed))buzzed=null; }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        if(e.isCancelled())return;
        if(game==6 && running && finaleStarted){
            Player participant=e.getPlayer();String wordTeam=team(participant.getUniqueId());
            if(wordTeam!=null && finalists.contains(wordTeam) && Objects.equals(selected.get(wordTeam),participant.getUniqueId())){
                e.setCancelled(true);String typed=e.getMessage();
                Bukkit.getScheduler().runTask(this,()->{if(!wordAccepting){say(participant,"§eNoch Lesezeit! Warte auf den Startton.");return;}submitWord(participant,typed);});return;
            }
        }
        if(!running || !(game==1 || game==5))return;
        Player p=e.getPlayer();String t=team(p.getUniqueId());
        if(t==null)return;
        UUID captain=selected.entrySet().stream().filter(entry->entry.getKey().equalsIgnoreCase(t)).map(Map.Entry::getValue).findFirst().orElse(null);
        if(!Objects.equals(captain,p.getUniqueId()))return;
        if(game==1 && (!p.getUniqueId().equals(buzzed) || System.currentTimeMillis()>buzzDeadline))return;
        if(game==5 && (!estimateAccepting || System.currentTimeMillis()>=answerDeadline))return;
        if(game!=1 && answerDeadline==0)return;
        String msg=e.getMessage().trim();e.setCancelled(true);
        Bukkit.getScheduler().runTask(this,()->{
            if(!running)return;
            if(game==5 && (!estimateAccepting || System.currentTimeMillis()>=answerDeadline))return;
            if(game==1){
                if(!p.getUniqueId().equals(buzzed) || System.currentTimeMillis()>buzzDeadline)return;
                if(msg.equalsIgnoreCase(correctAnswer)){
                    int pts=gameOneScreen.points();
                    quizScores.merge(t,pts,Integer::sum);individual.merge(p.getUniqueId(),pts,Integer::sum);updateDisplays();
                    broadcast("§a"+t+" – RICHTIG! §a+"+pts+" Punkte (Stufe "+gameOneScreen.stage()+"/7) §7("+p.getName()+") §f– "+correctAnswer);
                    buzzed=null;buzzDeadline=0;game1QuestionActive=false;gameOneScreen.reveal();Bukkit.getScheduler().runTaskLater(this,()->{if(running&&game==1)nextQuestion();},60L);
                } else {broadcast("§cFalsch! §7"+t+" darf diese Runde nicht mehr buzzern.");buzzed=null;buzzDeadline=0;game1RoundEnds=System.currentTimeMillis()+game1RemainingOnBuzz;gameOneScreen.resume();}
            }else if(game==2){
                String v=msg.toUpperCase(Locale.ROOT);
                if(!List.of("A","B","C","D").contains(v)){say(p,"Antworte mit A, B, C oder D.");return;}
                responses.putIfAbsent(t,v);say(p,"Antwort "+v+" gespeichert.");
            }else{
                try{int n=Integer.parseInt(msg);estimates.put(t,n);say(p,"§aDeine geheime Schätzung: §f"+n);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,0.65f,1.7f);}
                catch(NumberFormatException x){say(p,"Bitte eine ganze Zahl schreiben.");}
            }
        });
    }
    @EventHandler public void onTarget(ProjectileHitEvent e) {
        if(gameFour!=null || !running || game!=4 || !(e.getEntity() instanceof Arrow arrow) || !(arrow.getShooter() instanceof Player p) || e.getHitBlock()==null)return;
        String t=team(p.getUniqueId());if(t==null || !Objects.equals(selected.get(t),p.getUniqueId()))return;
        String target=key(e.getHitBlock().getLocation());Set<String> allowed=archeryTargets.getOrDefault(t,Set.of());
        if(!allowed.contains(target))return;
        allowed.remove(target);archeryHits.merge(t,1,Integer::sum);individual.merge(p.getUniqueId(),1,Integer::sum);
        e.getHitBlock().getWorld().spawnParticle(Particle.HAPPY_VILLAGER,e.getHitBlock().getLocation().add(.5,.5,.5),10,.25,.25,.25);
        p.sendMessage("§aTreffer! §7"+archeryHits.get(t)+"/10");
        p.playSound(p.getLocation(),"wolfsfell.hit",0.8f,1f);
        if(archeryHits.get(t)>=10){archeryFinishTimes.putIfAbsent(t,System.currentTimeMillis());broadcast("§d"+t+" §7hat alle Zielscheiben getroffen!");}
    }

    private boolean isGame2Candidate(Player p){return game==2 && running && selected.containsValue(p.getUniqueId());}
    private void prepareGame2Inventories(){
        for(UUID id:selected.values()){
            Player p=Bukkit.getPlayer(id);if(p==null)continue;
            game2Inventories.putIfAbsent(id,p.getInventory().getContents().clone());saveGame2Backups();
            for(int i=0;i<4;i++){
                Material material=switch(i){case 0->Material.RED_DYE;case 1->Material.BLUE_DYE;case 2->Material.YELLOW_DYE;default->Material.LIME_DYE;};
                ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();
                meta.setDisplayName("§l"+(char)('A'+i)+" §7– Antwort wählen");
                meta.getPersistentDataContainer().set(game2Key,PersistentDataType.STRING,""+(char)('A'+i));
                item.setItemMeta(meta);p.getInventory().setItem(i,item);
            }
            p.updateInventory();
        }
    }
    private void saveGame2Backups(){
        if(game2BackupFile==null)return;
        YamlConfiguration y=new YamlConfiguration();
        for(var entry:game2Inventories.entrySet())y.set("players."+entry.getKey(),Arrays.asList(entry.getValue()));
        try{y.save(game2BackupFile);}catch(IOException ex){getLogger().severe("Game2 inventory backup failed: "+ex.getMessage());}
    }
    private void loadGame2Backups(){
        if(game2BackupFile==null || !game2BackupFile.exists())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(game2BackupFile);
        ConfigurationSection sec=y.getConfigurationSection("players");if(sec==null)return;
        for(String k:sec.getKeys(false))try{
            List<?> items=y.getList("players."+k);if(items==null)continue;
            ItemStack[] snapshot=new ItemStack[41];for(int i=0;i<Math.min(snapshot.length,items.size());i++)if(items.get(i) instanceof ItemStack item)snapshot[i]=item;
            game2Inventories.put(UUID.fromString(k),snapshot);
        }catch(IllegalArgumentException ignored){}
    }
    private void restoreGame2Inventory(Player p){
        ItemStack[] original=game2Inventories.remove(p.getUniqueId());
        if(original==null)return;
        p.getInventory().setContents(original);p.updateInventory();saveGame2Backups();
    }
    private void restoreGame2Inventories(){
        for(UUID id:new HashSet<>(game2Inventories.keySet())){
            Player p=Bukkit.getPlayer(id);if(p!=null)restoreGame2Inventory(p);
        }
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
    public void onGame2Answer(PlayerInteractEvent e){
        if(!isGame2Candidate(e.getPlayer()) || e.getHand()!=EquipmentSlot.HAND)return;
        if(e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        ItemStack item=e.getItem();if(item==null || !item.hasItemMeta())return;
        String choice=item.getItemMeta().getPersistentDataContainer().get(game2Key,PersistentDataType.STRING);
        if(choice==null)return;
        e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);
        Player p=e.getPlayer();String t=team(p.getUniqueId());
        if(!game2Accepting || answerDeadline<=System.currentTimeMillis()){say(p,"Antwortzeit vorbei.");return;}
        if(t==null || !Objects.equals(selected.get(t),p.getUniqueId()))return;
        if(responses.putIfAbsent(t,choice)==null){say(p,"§aAntwort "+choice+" gespeichert!");p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,1f,1.4f);gameOneScreen.showQuiz2(questionNumber,currentPrompt,choices,(int)Math.max(0,(answerDeadline-System.currentTimeMillis()+999)/1000),responses.size(),selected.size(),null);}
        else say(p,"Deine Antwort ist bereits gespeichert.");
    }
    @EventHandler public void onGame2Inventory(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p && isGame2Candidate(p))e.setCancelled(true);}
    @EventHandler public void onGame2Drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p && isGame2Candidate(p))e.setCancelled(true);}
    @EventHandler public void onGame2Drop(PlayerDropItemEvent e){if(isGame2Candidate(e.getPlayer()))e.setCancelled(true);}

    private void estimateSound(Sound sound,float pitch){
        for(Player participant:Bukkit.getOnlinePlayers())participant.playSound(participant.getLocation(),sound,0.65f,pitch);
    }
    private void loadEstimateQuestions(){
        estimateBank.clear();
        File f=new File(getDataFolder(),"questions-game5.yml");
        if(!f.exists())saveResource("questions-game5.yml",false);
        YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
        for(Map<?,?> row:y.getMapList("questions"))try{
            String difficulty=String.valueOf(row.get("difficulty")).toLowerCase(Locale.ROOT);
            String prompt=String.valueOf(row.get("question"));
            int answer=Integer.parseInt(String.valueOf(row.get("answer")));
            if(Set.of("test","medium","hard","expert").contains(difficulty) && !prompt.isBlank() && estimateBank.stream().noneMatch(existing->existing.prompt().equalsIgnoreCase(prompt)))estimateBank.add(new EstimateQuestion(difficulty,prompt,answer));
        }catch(Exception ex){getLogger().warning("Game 5: invalid question: "+ex.getMessage());}
        getLogger().info("Game 5: "+estimateBank.size()+" questions loaded");
    }
    private void startEstimates(CommandSender sender){
        if(selected.isEmpty()){say(sender,"Game 5: Keine Kandidaten ausgewählt. /wg candidate set <Team> <Spieler>");return;}
        for(UUID id:selected.values())if(Bukkit.getPlayer(id)==null){say(sender,"Game 5: Alle ausgewählten Kandidaten müssen online sein.");return;}
        loadEstimateQuestions();
        estimateSequence.clear();
        for(String level:List.of("test","medium","hard","expert")){
            List<EstimateQuestion> pool=new ArrayList<>();for(EstimateQuestion q:estimateBank)if(q.difficulty().equals(level))pool.add(q);
            Collections.shuffle(pool,random);
            int needed=level.equals("test")?2:level.equals("medium")?3:level.equals("hard")?4:3;
            if(pool.size()<needed){say(sender,"Game 5: Zu wenige Fragen in Kategorie "+level+" (benötigt: "+needed+").");return;}
            estimateSequence.addAll(pool.subList(0,needed));
        }
        estimateGeneration++;running=true;game=5;questionNumber=0;quizScores.clear();estimates.clear();
        selectionEnabled=false;estimateAccepting=false;estimateRevealing=false;answerDeadline=0;
        startMillis=System.currentTimeMillis();broadcast("§dSCHÄTZ MAL! §72 Proberunden ohne Punkte, dann 10 Wertungsfragen!");
        estimateSound(Sound.BLOCK_NOTE_BLOCK_BELL,1.0f);
        int gen=estimateGeneration;Bukkit.getScheduler().runTaskLater(this,()->nextEstimate(gen),40L);saveState();
    }
    private void nextEstimate(int gen){
        if(gen!=estimateGeneration||!running||game!=5)return;
        if(questionNumber>=estimateSequence.size()){
            estimateAccepting=false;estimateRevealing=false;answerDeadline=0;running=false;
            gameOneScreen.idle();
            for(var entry:quizScores.entrySet())raw.computeIfAbsent(entry.getKey(),k->new HashMap<>()).put(5,entry.getValue());
            rankRound(Bukkit.getConsoleSender(),5);broadcast("§dSchätz mal beendet! 10 Wertungsfragen ausgewertet.");saveState();return;
        }
        EstimateQuestion q=estimateSequence.get(questionNumber++);estimates.clear();
        currentPrompt=q.prompt();correctAnswer=Integer.toString(q.answer());
        estimateAccepting=true;estimateRevealing=false;answerDeadline=System.currentTimeMillis()+ESTIMATE_ANSWER_SECONDS*1000L;
        boolean test=questionNumber<=2;
        broadcast("§d"+(test?"TESTFRAGE "+questionNumber+"/2":"FRAGE "+(questionNumber-2)+"/10")+" §f"+currentPrompt);
        broadcast("§7Ausgewählte Kandidaten: 8 Sekunden! Antworten sind geheim.");
        estimateSound(Sound.BLOCK_NOTE_BLOCK_PLING,1.1f);
        for(int sec=ESTIMATE_ANSWER_SECONDS;sec>=1;sec--){final int remaining=sec;Bukkit.getScheduler().runTaskLater(this,()->{
            if(gen==estimateGeneration&&running&&game==5&&estimateAccepting){
                gameOneScreen.showEstimate(questionNumber,test,currentPrompt,remaining,estimates.size(),selected.size(),null);
                if(remaining<=3)estimateSound(Sound.BLOCK_NOTE_BLOCK_HAT,remaining==1?1.65f:1.25f);
            }
        },(ESTIMATE_ANSWER_SECONDS-sec)*20L);}
        Bukkit.getScheduler().runTaskLater(this,()->resolveEstimate(gen,q,test),ESTIMATE_ANSWER_SECONDS*20L);
    }
    private void resolveEstimate(int gen,EstimateQuestion q,boolean test){
        if(gen!=estimateGeneration||!running||game!=5)return;
        estimateAccepting=false;estimateRevealing=true;answerDeadline=0;
        gameOneScreen.showEstimate(questionNumber,test,q.prompt(),0,estimates.size(),selected.size(),q.answer());
        broadcast("§aRICHTIGE ANTWORT: §f"+q.answer()+(test?" §7(Test – keine Punkte)":""));
        estimateSound(Sound.ENTITY_PLAYER_LEVELUP,1.25f);
        TreeMap<Long,List<String>> groups=new TreeMap<>();
        for(var entry:estimates.entrySet())groups.computeIfAbsent(Math.abs((long)entry.getValue()-q.answer()),k->new ArrayList<>()).add(entry.getKey());
        int rank=0;
        for(var group:groups.entrySet()){
            int base=rank==0?2:rank==1?1:0;
            for(String team:group.getValue()){
                int value=estimates.get(team);int pts=test?0:base+(value==q.answer()?1:0);
                broadcast("§7"+team+" §f"+value+" §8(Abstand "+group.getKey()+")"+(pts>0?" §a+"+pts+"P":""));
                if(pts>0){quizScores.merge(team,pts,Integer::sum);UUID id=selected.get(team);if(id!=null)individual.merge(id,pts,Integer::sum);}
            }
            rank++;
        }
        if(groups.isEmpty())broadcast("§7Niemand hat eine gültige Schätzung abgegeben.");
        updateDisplays();saveState();
        // Halte die Auflösung lange sichtbar, damit die Teilnehmer kurz durchatmen können.
        Bukkit.getScheduler().runTaskLater(this,()->{
            if(gen!=estimateGeneration||!running||game!=5||!estimateRevealing)return;
            estimateSound(Sound.BLOCK_NOTE_BLOCK_BELL,0.85f);
            nextEstimate(gen);
        },ESTIMATE_REVEAL_SECONDS*20L);
    }
    private void loadQuestions() {
        File f=new File(getDataFolder(),"questions.yml");if(!f.exists())saveResource("questions.yml",false);
        YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
        for(Map<?,?> row:y.getMapList("questions"))try{
            int g=Integer.parseInt(String.valueOf(row.get("game")));
            String prompt=String.valueOf(row.get("question")),answer=String.valueOf(row.get("answer"));
            Object w=row.get("wrong");String[] wrong=w instanceof List<?> list?list.stream().map(String::valueOf).toArray(String[]::new):new String[0];
            if(g==5)questions.add(new QuizQuestion(g,prompt,answer,wrong));
        }catch(Exception ignored){}
        // Game 2 has its own graded question bank, independent of an older questions.yml.
        File g2=new File(getDataFolder(),"game2-questions.yml");
        if(!g2.exists())saveResource("game2-questions.yml",false);
        YamlConfiguration y2=YamlConfiguration.loadConfiguration(g2);
        for(Map<?,?> row:y2.getMapList("questions"))try{
            String prompt=String.valueOf(row.get("question")),answer=String.valueOf(row.get("answer"));
            Object wrong=row.get("wrong");
            if(wrong instanceof List<?> list && list.size()==3)questions.add(new QuizQuestion(2,prompt,answer,list.stream().map(String::valueOf).toArray(String[]::new)));
        }catch(Exception ex){getLogger().warning("Invalid game2 question: "+ex.getMessage());}
        getLogger().info("Loaded "+questions.size()+" quiz entries");
    }
    private int quizDifficulty(String prompt){
        // Questions tagged [LEICHT], [MITTEL], [SCHWER] in questions.yml.
        if(prompt.startsWith("[SCHWER] "))return 3;
        if(prompt.startsWith("[MITTEL] "))return 2;
        return 1;
    }
    private void nextQuestion(){
        if(questionNumber >= (game==2?15:10)){running=false;game1QuestionActive=false;answerDeadline=0;game2Accepting=false;game2Revealing=false;restoreGame2Inventories();gameOneScreen.idle();broadcast("§d"+questionNumber+" Fragen beendet! §7Mit /wg finishround die Punkte speichern.");saveState();return;}
        List<QuizQuestion> pool=questions.stream().filter(q->q.game()==game&&!usedQuestions.contains(q.game()+":"+(q.game()==2?q.prompt():q.answer())) && (game!=1 || GAME1_ITEMS.contains(q.answer()))).toList();
        if(game==2){
            int level=questionNumber<4?1:questionNumber<11?2:3;
            List<QuizQuestion> graded=pool.stream().filter(q->quizDifficulty(q.prompt())==level).toList();
            if(!graded.isEmpty())pool=graded;
        }
        if(pool.isEmpty()){broadcast("§dFragenpool für Spiel "+game+" aufgebraucht.");running=false;if(game==2)restoreGame2Inventories();return;}
        QuizQuestion q=pool.get(random.nextInt(pool.size()));usedQuestions.add(q.game()+":"+(q.game()==2?q.prompt():q.answer()));questionNumber++;
        currentPrompt=q.prompt().replaceFirst("^\\[(LEICHT|MITTEL|SCHWER)\\] ","");correctAnswer=q.answer();responses.clear();estimates.clear();buzzed=null;buzzDeadline=0;attemptedBuzzers.clear();
        if(game==2){List<String> opts=new ArrayList<>();opts.add(q.answer());opts.addAll(Arrays.asList(q.wrong()));Collections.shuffle(opts);choices=opts.toArray(String[]::new);}
        answerDeadline=game==1?0:System.currentTimeMillis()+(game==2?7000L:7000L);
        if(game==2){game2Accepting=true;game2Revealing=false;game2LastSecond=-1;gameOneScreen.showQuiz2(questionNumber,currentPrompt,choices,7,0,selected.size(),null);}
        broadcast("§dFrage "+questionNumber+": §f"+currentPrompt);
        if(game==2)for(int i=0;i<4;i++)broadcast("§7"+(char)('A'+i)+") §f"+choices[i]);
        if(game==5)broadcast("§7Schätzung im Chat eingeben!");
        if(game==1){game1QuestionActive=true;game1RoundEnds=System.currentTimeMillis()+30000L;game1RemainingOnBuzz=0;gameOneScreen.show(correctAnswer);broadcast("§7Team-Buzzer drücken! 5 Sekunden für die Antwort, ein Versuch pro Spieler.");}
    }
    private void resolveQuestion(){
        if(game==2){
            game2Accepting=false;game2Revealing=true;
            String right="";for(int i=0;i<4;i++)if(choices[i].equals(correctAnswer))right=""+(char)('A'+i);
            game2Right=right;gameOneScreen.showQuiz2(questionNumber,currentPrompt,choices,0,responses.size(),selected.size(),right);
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
        if(running && game==2)Bukkit.getScheduler().runTaskLater(this,()->{if(running && game==2)nextQuestion();},120L);
        updateDisplays();saveState();
    }
    private void updateDisplays(){
        gameOneScreen.updateLeaderboard();
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
    String wideTeamColor(String t){return teamColors.getOrDefault(t,"PURPLE");}
    String widePlayerColor(UUID id){String t=team(id);return t==null?"WHITE":wideTeamColor(t);}
    List<String> wideTeams(){List<String> r=new ArrayList<>(teams.keySet());r.sort(Comparator.comparingInt((String t)->qualifying.getOrDefault(t,0)+(running && game==1?quizScores.getOrDefault(t,0):0)).reversed().thenComparing(t->t));return r;}
    int wideTeamPoints(String t){return qualifying.getOrDefault(t,0)+(running && game==1?quizScores.getOrDefault(t,0):0);}
    List<UUID> widePlayers(){List<UUID> r=new ArrayList<>(individual.keySet());r.sort(Comparator.comparingInt((UUID id)->individual.getOrDefault(id,0)).reversed());return r;}
    int widePlayerPoints(UUID id){return individual.getOrDefault(id,0);}
    String widePlayerName(UUID id){return Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("Spieler");}
    String wideTitle(){return running && game==1?"GAME 1 - ITEM QUIZ":"WOLFSFELL GAMESHOW";}
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
    // Place each elected contestant safely on the saved selection spawn at show start.
    // The existing selection region is used as a fallback when no exact spawn was marked.
    private void teleportCandidatesToSelection(){
        for(var entry:selected.entrySet()){
            Player player=Bukkit.getPlayer(entry.getValue());
            if(player==null || !player.isOnline())continue;
            String team=entry.getKey();
            Location target=locations.get("selection:"+team.toLowerCase(Locale.ROOT));
            if(target==null)target=locations.get("selection:"+team);
            Location[] region=cages.get("selection:"+team.toLowerCase(Locale.ROOT));
            if(target!=null && region!=null && !insideHorizontal(target,region[0],region[1]))target=null;
            if(target==null && region!=null){
                World w=region[0].getWorld();
                if(w!=null && w.equals(region[1].getWorld())){
                    int x=(region[0].getBlockX()+region[1].getBlockX())/2;
                    int z=(region[0].getBlockZ()+region[1].getBlockZ())/2;
                    int low=Math.min(region[0].getBlockY(),region[1].getBlockY());
                    int high=Math.max(region[0].getBlockY(),region[1].getBlockY());
                    for(int y=high+2;y>=low;y--){
                        if(w.getBlockAt(x,y-1,z).getType().isSolid()
                            && w.getBlockAt(x,y,z).isPassable()
                            && w.getBlockAt(x,y+1,z).isPassable()){
                            target=new Location(w,x+.5,y,z+.5);break;
                        }
                    }
                }
            }
            if(target==null){getLogger().warning("Kein sicherer Auswahlpunkt für "+team+"; /wg set selection:"+team);continue;}
            Location safe=target.clone();
            if(!safe.getBlock().isPassable() || !safe.clone().add(0,1,0).getBlock().isPassable()){
                getLogger().warning("Auswahlpunkt blockiert: "+team+". /wg set selection:"+team);continue;
            }
            safe.setYaw(player.getLocation().getYaw());safe.setPitch(player.getLocation().getPitch());
            player.teleport(safe);
            player.sendMessage("§dGameshow §7Du wurdest zu deinem Buzzer-Platz teleportiert!");
        }
    }

    // Custom 3D models from the separate resource pack. ItemDisplay has no
    // interaction hitbox: the original stone button still receives the click.
    private String buzzerModelColor(String team){
        String color=teamColors.getOrDefault(team,"PURPLE").toLowerCase(Locale.ROOT);
        return switch(color){
            case "red","blue","green","yellow","orange","pink","cyan","white","purple" -> color;
            default -> "purple";
        };
    }
    private void refreshBuzzerColors(){
        for(World world:Bukkit.getWorlds()){
            for(BlockDisplay display:world.getEntitiesByClass(BlockDisplay.class))
                if(display.getScoreboardTags().contains("wg_buzzer_color"))display.remove();
            for(ItemDisplay display:world.getEntitiesByClass(ItemDisplay.class))
                if(display.getScoreboardTags().contains("wg_buzzer_color"))display.remove();
        }
        for(var entry:buzzerBlocks.entrySet()){
            String[] coords=entry.getValue().split(":");
            if(coords.length!=4)continue;
            try{
                World world=Bukkit.getWorld(UUID.fromString(coords[0]));
                if(world==null)continue;
                int x=Integer.parseInt(coords[1]),y=Integer.parseInt(coords[2]),z=Integer.parseInt(coords[3]);
                Block block=world.getBlockAt(x,y,z);
                if(!block.getType().name().endsWith("BUTTON"))continue;
                String model="buzzer_"+buzzerModelColor(entry.getKey());
                ItemStack item=new ItemStack(Material.PAPER);
                ItemMeta meta=item.getItemMeta();
                meta.setItemModel(new NamespacedKey("wolfsfell",model));
                item.setItemMeta(meta);
                // Floating display directly above the saved button.
                world.spawn(block.getLocation().add(.5,.35,.5),ItemDisplay.class,display->{
                    display.setItemStack(item);
                    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    display.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(0,0,0),new org.joml.AxisAngle4f(),
                        new org.joml.Vector3f(.72f,.72f,.72f),new org.joml.AxisAngle4f()));
                    display.addScoreboardTag("wg_buzzer_color");
                    display.setPersistent(false);
                });
            }catch(IllegalArgumentException ignored){}
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
        game=y.getInt("game",0);running=false;selectionEnabled=false;studioLocked=y.getBoolean("studio-locked",false);finalists=y.getStringList("finalists");finaleStarted=y.getBoolean("finale-started",false);manualFinalists=y.getBoolean("manual-finalists",false);leggingsRewards.addAll(y.getStringList("leggings-rewards"));
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
        YamlConfiguration y=new YamlConfiguration();y.set("game",game);y.set("finalists",finalists);y.set("finale-started",finaleStarted);y.set("manual-finalists",manualFinalists);y.set("leggings-rewards",new ArrayList<>(leggingsRewards));
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
    private void beginFinale(CommandSender sender){
        if(finaleStarted){say(sender,"Finale wurde bereits eröffnet.");return;}
        if(!manualFinalists || finalists.size()!=2){
            List<String> order=new ArrayList<>(teams.keySet());order.removeIf(t->teams.get(t).isEmpty());
            order.sort(Comparator.comparingInt((String t)->qualifying.getOrDefault(t,0)).reversed().thenComparing(t->t));
            if(order.size()<2){say(sender,"Mindestens zwei Teams mit Mitgliedern erforderlich.");return;}
            if(order.size()>2 && Objects.equals(qualifying.get(order.get(1)),qualifying.get(order.get(2)))){
                say(sender,"Gleichstand um Platz 2! /wg finalist <Team1> <Team2>, danach /wg finishround.");return;
            }
            finalists=new ArrayList<>(order.subList(0,2));
        }
        finaleStarted=true;advantages.clear();leggingsRewards.clear();
        for(String t:finalists)advantages.put(t,0);
        List<String> eliminated=new ArrayList<>(teams.keySet());eliminated.removeAll(finalists);
        broadcast("§c§lAUSGESCHIEDENE TEAMS: §7"+(eliminated.isEmpty()?"Keine":String.join(", ",eliminated)));
        String a=finalists.get(0),b=finalists.get(1);
        broadcast("§5§lFINALE! §f"+a+" §dVS §f"+b+" §7– Vorteilspunkte sammeln!");
        for(Player player:Bukkit.getOnlinePlayers()){
            String t=team(player.getUniqueId());
            if(t!=null && !finalists.contains(t))player.setGameMode(GameMode.SPECTATOR);
            else if(t!=null && finalists.contains(t) && player.getGameMode()==GameMode.SPECTATOR)player.setGameMode(GameMode.ADVENTURE);
            player.sendTitle("§d"+a+" §fVS §d"+b,"§eDAS FINALE – VORTEILE SAMMELN!",10,100,20);
            player.playSound(player.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.8f,1f);
        }
        selected.clear();selectionEnabled=false;studioLocked=true;
        saveState();say(sender,"Finalteams beraten sich. /wg game 6, dann /wg select on für die Spielerwahl.");
    }
    private void loadWordQuestions(){
        wordQuestions.clear();File file=new File(getDataFolder(),"questions-game6.yml");
        if(!file.exists())saveResource("questions-game6.yml",false);
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        for(Map<?,?> row:y.getMapList("questions")){
            Object prompt=row.get("question"),answers=row.get("answers");
            if(!(prompt instanceof String q) || !(answers instanceof List<?> list))continue;
            Set<String> allowed=new HashSet<>();for(Object answer:list)if(answer instanceof String a)allowed.add(normalizeWord(a));
            if(!allowed.isEmpty())wordQuestions.add(new WordQuestion(q,allowed));
        }
    }
    private String normalizeWord(String a){return a.strip().toLowerCase(Locale.ROOT).replace('ä','a').replace('ö','o').replace('ü','u').replace("ß","ss").replaceAll("[ _-]+"," ");}
    private void startWordDuell(CommandSender sender){
        if(!finaleStarted||finalists.size()!=2){say(sender,"Erst /wg finishround nach Game 5 durchführen.");return;}
        if(running){say(sender,"Es läuft bereits ein Spiel.");return;}
        for(String t:finalists){UUID id=selected.get(t);if(id==null||!teams.getOrDefault(t,new LinkedHashSet<>()).contains(id)||Bukkit.getPlayer(id)==null){say(sender,"Für "+t+" fehlt der ausgewählte Online-Kandidat: /wg select on oder /wg candidate set "+t+" <Spieler>.");return;}}
        if(wordQuestions.size()<13){say(sender,"Mindestens 13 gültige Fragen in questions-game6.yml nötig.");return;}
        wordSequence.clear();wordSequence.addAll(wordQuestions.subList(0,13));wordAnswers.clear();wordUsed.clear();
        wordRound=0;wordAccepting=false;wordGeneration++;game=6;running=true;selectionEnabled=false;
        broadcast("§d§lWORTDUELL STARTET! §7Drei Sekunden Lesezeit, dann antworten!");
        nextWordRound(wordGeneration);
    }
    private void nextWordRound(int generation){
        if(!running||game!=6||generation!=wordGeneration)return;
        if(wordRound>=13){completeWordDuell("Beide Finalisten haben alle 13 Runden bestanden!",new HashSet<>(finalists));return;}
        wordRound++;wordAnswers.clear();wordAccepting=false;
        WordQuestion q=wordSequence.get(wordRound-1);
        for(Player p:Bukkit.getOnlinePlayers()){p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,0.7f,1.1f);}
        broadcast("§d§lWORTDUELL §fRunde "+wordRound+"/13 §7– 3 Sekunden lesen!");
        gameOneScreen.showWordDuell(wordRound,q.prompt(),3,false);
        Bukkit.getScheduler().runTaskLater(this,()->{
            if(!running||generation!=wordGeneration)return;
            wordAccepting=true;
            int seconds=14-wordRound;
            for(Player p:Bukkit.getOnlinePlayers())p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BELL,0.8f,1.3f);
            for(int left=seconds;left>=1;left--){final int remaining=left;
                Bukkit.getScheduler().runTaskLater(this,()->{
                    if(!running||generation!=wordGeneration||!wordAccepting)return;
                    gameOneScreen.showWordDuell(wordRound,q.prompt(),remaining,true);
                    if(remaining<=3)for(Player p:Bukkit.getOnlinePlayers())p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,0.65f,1.2f);
                },(seconds-left)*20L);
            }
            Bukkit.getScheduler().runTaskLater(this,()->finishWordRound(generation),seconds*20L);
        },60L);
    }
    private void submitWord(Player p,String input){
        if(!wordAccepting||!running||game!=6)return;
        String t=team(p.getUniqueId());if(t==null||!finalists.contains(t)||!Objects.equals(selected.get(t),p.getUniqueId()))return;
        if(wordAnswers.containsKey(t)){say(p,"§aAntwort ist bereits eingeloggt.");return;}
        String answer=normalizeWord(input);
        boolean used=wordUsed.getOrDefault(t,Set.of()).contains(answer);
        boolean valid=wordSequence.get(wordRound-1).answers().contains(answer)&&!used;
        for(Player op:Bukkit.getOnlinePlayers())if(op.hasPermission("wg.admin"))op.sendMessage("§8[§dWortduell OP§8] §f"+p.getName()+": "+input+" §7→ "+(valid?"§aRICHTIG":used?"§cWIEDERHOLUNG":"§cUNGÜLTIG"));
        if(!valid){say(p,used?"§cSchon benutzt! Probiere ein anderes Wort.":"§cUngültig! Achte auf Schreibweise und Begriff.");p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,0.7f,0.7f);return;}
        wordAnswers.put(t,answer);wordUsed.computeIfAbsent(t,k->new HashSet<>()).add(answer);
        say(p,"§a✔ "+input+" – Richtig eingeloggt!");p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,0.6f,1.7f);
    }
    private void finishWordRound(int generation){
        if(!running||game!=6||generation!=wordGeneration)return;
        wordAccepting=false;
        Set<String> surviving=new HashSet<>(wordAnswers.keySet());
        if(surviving.size()<2){
            completeWordDuell(surviving.isEmpty()?"Beide ausgeschieden! Beide erhalten den Vorteil.":"Ein Finalist hat überlebt!",surviving.isEmpty()?new HashSet<>(finalists):surviving);return;
        }
        broadcast("§aBeide haben Runde "+wordRound+" geschafft!");
        gameOneScreen.showWordDuell(wordRound,"BEIDE ÜBERLEBEN!",0,false);
        Bukkit.getScheduler().runTaskLater(this,()->nextWordRound(generation),40L);
    }
    private void completeWordDuell(String reason,Set<String> winners){
        if(!running||game!=6)return;wordAccepting=false;running=false;wordGeneration++;
        for(String t:winners){if(leggingsRewards.add(t)){advantages.merge(t,1,Integer::sum);}}
        broadcast("§6§lWORTDUELL BEENDET! §f"+reason);
        broadcast("§dNetherite-Hose (Schutz I, Haltbarkeit I) für: §f"+String.join(" & ",winners)+" §7– Vorteil gespeichert.");
        gameOneScreen.idle();saveState();
    }
    private void stopWordDuell(){wordGeneration++;wordAccepting=false;if(game==6){running=false;gameOneScreen.idle();}}
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
    private void teamMessage(Player sender,String text){
        String t=team(sender.getUniqueId());if(t==null){say(sender,"Du bist in keinem Team.");return;}
        if(text.isBlank()){say(sender,"/t <Nachricht>");return;}
        String line="§8[§dTeam "+t+"§8] §f"+sender.getName()+"§7: "+text;
        for(Player recipient:Bukkit.getOnlinePlayers())if(t.equals(team(recipient.getUniqueId())) || recipient.hasPermission("wg.admin"))recipient.sendMessage(line);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void privateTeamChat(AsyncPlayerChatEvent e){
        if(!persistentTeamChat.contains(e.getPlayer().getUniqueId()))return;
        String t=team(e.getPlayer().getUniqueId());
        if(game==6 && running && t!=null && finalists.contains(t) && Objects.equals(selected.get(t),e.getPlayer().getUniqueId()))return;
        // Team chat never leaves the plugin; send only from the server thread.
        e.setCancelled(true);Player speaker=e.getPlayer();String text=e.getMessage();
        Bukkit.getScheduler().runTask(this,()->teamMessage(speaker,text));
    }
    private boolean commandTeamChat(CommandSender sender,Command command,String[] args){
        if(!(sender instanceof Player p)){say(sender,"Nur Spieler können Teamchat nutzen.");return true;}
        if(command.getName().equalsIgnoreCase("t")) {teamMessage(p,String.join(" ",args));return true;}
        if(args.length==0){say(p,"/tc on|off");return true;}
        if(args[0].equalsIgnoreCase("on")){persistentTeamChat.add(p.getUniqueId());say(p,"Teamchat eingeschaltet.");}
        else if(args[0].equalsIgnoreCase("off")){persistentTeamChat.remove(p.getUniqueId());say(p,"Öffentlicher Chat eingeschaltet.");}
        else say(p,"/tc on|off");return true;
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(command.getName().equalsIgnoreCase("t")||command.getName().equalsIgnoreCase("tc"))return commandTeamChat(sender,command,args);
        if(args.length==0 || args[0].equalsIgnoreCase("help")){
            say(sender,"/wg team create|delete|color|join|leave|list | /wg wand region | /wg wand neon <Team> | /wg region save cage:<Team>");
            say(sender,"/wg studio lock|unlock | /wg region save selection:<Team> | /wg select on/off");
            say(sender,"/wg neon save <Team> | /wg neon selection <Team> | /wg neon clear <Team> | /wg neon cleanup");
            say(sender,"/wg reset scores | /wg reset game1 | /wg candidate set <Team> <Spieler> | /wg candidates | /wg screen set|test|remove | /wg buzzer set <Team> (Knopf ansehen) | /wg game <1-9> | /wg start | /wg stop | /wg status | /wg tp <Ort>");
            say(sender,"/wg raw <Team> <Spiel 1-5> <Wert> | /wg rank | /wg qualify | /wg finalist <A> <B>");
            say(sender,"/wg basket <Team> <0-6> | /wg parkour finish | /wg chest add|clear|shuffle|stop");
            say(sender,"GAME 3: /wg wand game3 | /wg tp game 3 | /wg game3 start|stop | /wg screen idle");
            say(sender,"GAME 4: /wg wand game4 | /wg game4 spectator | /wg tp game 4 | /wg game4 start|stop|reset");
            say(sender,"GAME 5: /wg game5 start|stop|reload|skip");
            say(sender,"/wg points <Team> | /wg points add <Team> <Zahl> | /wg spend <Team> <Kosten> <Notiz>");return true;
        }
        String cmd=args[0].toLowerCase(Locale.ROOT);
        if(cmd.equals("game6")&&admin(sender)){if(args.length>=2){switch(args[1].toLowerCase(Locale.ROOT)){
            case "start" -> {startWordDuell(sender);return true;}
            case "stop" -> {stopWordDuell();say(sender,"Wortduell gestoppt.");return true;}
            case "reload" -> {loadWordQuestions();say(sender,"Wortduell-Fragen geladen: "+wordQuestions.size());return true;}
        }}say(sender,"/wg game6 start|stop|reload");return true;}
        if(cmd.equals("game5")&&admin(sender)){if(args.length>=2){switch(args[1].toLowerCase(Locale.ROOT)){case "start"->{startEstimates(sender);return true;}case "stop"->{estimateGeneration++;estimateAccepting=false;answerDeadline=0;running=false;gameOneScreen.idle();say(sender,"Schätz mal gestoppt.");return true;}case "reload"->{loadEstimateQuestions();say(sender,"Game-5-Fragen neu geladen: "+estimateBank.size());return true;}case "skip"->{if(game==5&&running){estimateGeneration++;int gen=estimateGeneration;estimateAccepting=false;answerDeadline=0;Bukkit.getScheduler().runTaskLater(this,()->nextEstimate(gen),10L);}return true;}}}say(sender,"/wg game5 start|stop|reload|skip");return true;}
        if(cmd.equals("game4")&&admin(sender)){if(args.length>=2&&(args[1].equalsIgnoreCase("line1")||args[1].equalsIgnoreCase("line2"))){if(sender instanceof Player p)gameFour.line(p,args[1].equalsIgnoreCase("line1")?1:2);else say(sender,"Nur ingame möglich.");return true;}if(args.length>=2&&args[1].equalsIgnoreCase("spectator")){if(sender instanceof Player p)gameFour.spectator(p);else say(sender,"Nur ingame möglich.");return true;}if(args.length>=2){switch(args[1].toLowerCase(Locale.ROOT)){case "start" -> {game=4;archeryHits.clear();archeryFinishTimes.clear();running=gameFour.start(sender);if(running){startMillis=System.currentTimeMillis();saveState();}return true;}case "stop" -> {gameFour.stop();running=false;return true;}case "reset" -> {gameFour.reset();archeryHits.clear();return true;}}}say(sender,"/wg game4 spectator|line1|line2|start|stop|reset");return true;}
        if(cmd.equals("game3")&&admin(sender)){if(args.length>=2&&args[1].equalsIgnoreCase("start")){gameThree.start(sender);return true;}if(args.length>=2&&args[1].equalsIgnoreCase("stop")){gameThree.stop();say(sender,"Rennen gestoppt.");return true;}if(args.length>=2&&args[1].equalsIgnoreCase("debug")){gameThree.toggleDebug(sender);return true;}if(args.length>=3&&args[1].equalsIgnoreCase("laps")){try{gameThree.setLaps(sender,Integer.parseInt(args[2]));}catch(NumberFormatException ex){say(sender,"/wg game3 laps <1-20>");}return true;}say(sender,"/wg game3 start|stop|debug|laps <1-20>");return true;}
        if(cmd.equals("screen")&&admin(sender)) {
            if(!(sender instanceof Player p))return true;
            if(args.length>=2 && args[1].equalsIgnoreCase("remove")){
                int count=gameOneScreen.removeScreen(p);say(sender,"Bildschirm entfernt: "+count+" zugehörige Itemframes. Gespeicherte Position gelöscht.");return true;
            }
            if(args.length>=2 && args[1].equalsIgnoreCase("idle")){gameOneScreen.load();gameOneScreen.idle();say(sender,"Startbildschirm mit Twitch und YouTube aktualisiert.");return true;}
            if(args.length>=2 && args[1].equalsIgnoreCase("set")){gameOneScreen.setup(p);return true;}
            if(args.length>=2 && args[1].equalsIgnoreCase("test")){
                gameOneScreen.show("Diamant");
                for(int i=1;i<=8;i++)Bukkit.getScheduler().runTaskLater(this,()->{
                    if(!running)gameOneScreen.advance();
                },i*40L);
                return true;
            }
            say(sender,"/wg screen set (unteren linken Block der 33x9-Wand ansehen) | /wg screen test");return true;
        }
        if(cmd.equals("wand")&&admin(sender)) {
            if(!(sender instanceof Player p))return true;
            if(args.length>=2&&args[1].equalsIgnoreCase("game3")){gameThree.wand(p);return true;}
            if(args.length>=2&&args[1].equalsIgnoreCase("game4")){gameFour.wand(p);return true;}
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
                teamColors.put(t,c);refreshAllArmor();refreshBuzzerColors();saveState();say(sender,"Teamfarbe "+t+" = "+c);return true;
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
                        finalists.clear();finaleStarted=false;manualFinalists=false;leggingsRewards.clear();selected.clear();answerDeadline=0;game1RoundEnds=0;
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
                case "next" -> {if(!running || !List.of(1,2).contains(game))break;nextQuestion();return true;}
                case "finishround" -> {
                    if(game>=1&&game<=5){
                        if(game==1||game==2||game==5)for(String t:teams.keySet())if(participatedInRound(t,game))raw.computeIfAbsent(t,k->new HashMap<>()).put(game,quizScores.getOrDefault(t,0));
                        if(game==3)for(var entry:gameThree.results().entrySet())raw.computeIfAbsent(entry.getKey(),k->new HashMap<>()).put(3,entry.getValue());
                        if(game==4)for(String t:teams.keySet())if(!teams.get(t).isEmpty() && archeryHits.containsKey(t))raw.computeIfAbsent(t,k->new HashMap<>()).put(game,archeryHits.getOrDefault(t,0));
                        running=false;if(game==3)gameThree.stop();if(game==2){game2Accepting=false;restoreGame2Inventories();}rankRound(sender,game);broadcast("§dSpiel "+game+" abgeschlossen.");
                        if(game==5){estimateGeneration++;estimateAccepting=false;answerDeadline=0;beginFinale(sender);}
                        return true;
                    }break;
                }
                case "candidate" -> {
                    if(args.length<4 || !args[1].equalsIgnoreCase("set")){
                        say(sender,"/wg candidate set <Team> <Spieler>");return true;
                    }
                    String targetTeam=teams.keySet().stream().filter(k->k.equalsIgnoreCase(args[2])).findFirst().orElse(null);
                    Player candidate=Bukkit.getPlayerExact(args[3]);
                    if(targetTeam==null || candidate==null){say(sender,"Team unbekannt oder Spieler offline.");return true;}
                    if(finaleStarted&&!finalists.contains(targetTeam)){say(sender,"Nur Finalteams dürfen sich auswählen.");return true;}
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
                    buzzerBlocks.put(args[2],key(b.getLocation()));refreshBuzzerColors();saveState();say(sender,"Physischer Buzzer für "+args[2]+" gespeichert.");return true;
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
                case "tp" -> {if(args.length>=3&&args[1].equalsIgnoreCase("game")){int g=Integer.parseInt(args[2]);if(g==3){game=3;studioLocked=false;gameThree.teleport(sender,selected,new HashMap<>(),teamColors);saveState();return true;}if(g==4){game=4;studioLocked=false;gameFour.teleport(sender,selected,new ArrayList<>(teams.keySet()));saveState();return true;}Location destination=locations.get("game"+g+"-spectator");if(destination==null){say(sender,"Spiel "+g+" hat noch keinen Teleport-Setup.");return true;}for(Player online:Bukkit.getOnlinePlayers())online.teleport(destination);return true;}if(!(sender instanceof Player p)||args.length<2)break;Location l=locations.get(args[1].toLowerCase(Locale.ROOT));if(l==null){say(sender,"Ort nicht gesetzt.");return true;}p.teleport(l);return true;}
                case "select" -> {if(args.length<2)break;
                    boolean enable=args[1].equalsIgnoreCase("on");
                    selectionEnabled=enable;selectionTicks.clear();
                    if(enable){selected.clear();}
                    else {returnUnselectedToCages();studioLocked=true;}
                    saveState();say(sender,"Spielerwahl "+(selectionEnabled?"aktiv":"aus"));return true;}
                case "game" -> {if(game==6)stopWordDuell();estimateGeneration++;estimateAccepting=false;answerDeadline=0;if(game==4)gameFour.stop();if(game==3)gameThree.stop();if(game==2)restoreGame2Inventories();if(args.length<2)break;int g=Integer.parseInt(args[1]);if(g<1||g>9)break;if(game==g){say(sender,"Spiel "+g+" ist bereits gewählt. Kandidaten bleiben erhalten ("+selected.size()+" registriert).");return true;}running=false;game=g;selected.clear();selectionTicks.clear();buzzed=null;attemptedBuzzers.clear();say(sender,"Spiel "+game+": "+GAMES[game-1]+". Kandidaten für das neue Spiel neu wählen.");saveState();return true;}
                case "start" -> {if(game==6){startWordDuell(sender);return true;}if(game==5){startEstimates(sender);return true;}if(game==3){gameThree.start(sender);return true;}if(game==4){archeryHits.clear();archeryFinishTimes.clear();running=gameFour.start(sender);if(running){startMillis=System.currentTimeMillis();broadcast("§dSTART: "+GAMES[game-1]);saveState();}return true;}if(game==0){say(sender,"Zuerst /wg game <1-9>.");return true;}if(game>=6&&finalists.size()!=2){say(sender,"Finalteams fehlen: /wg qualify oder /wg finalist.");return true;}if(game==8){if(mysteryChestValues.size()!=50){say(sender,"Bitte zuerst 50 Truhen markieren.");return true;}setupMystery(sender);}if(game==7){if(!locations.containsKey("parkour-finish")){say(sender,"Parkour-Ziel fehlt.");return true;}parkourScores.clear();parkourFinishes.clear();}running=true;startMillis=System.currentTimeMillis();selectionEnabled=false;if(game==1||game==2){teleportCandidatesToSelection();}if(game==2){prepareGame2Inventories();}if(game==1){say(sender,"Game 1 startet mit "+selected.size()+" registrierten Kandidaten. /wg candidates zeigt die Zuordnung.");if(selected.isEmpty())say(sender,"ACHTUNG: Keine Kandidaten gewählt! Bitte /wg candidate set <Team> <Spieler> nutzen.");}
                    if(game==1||game==2||game==5){game1QuestionActive=false;quizScores.clear();usedQuestions.clear();questionNumber=0;gameOneScreen.idle();Bukkit.getScheduler().runTaskLater(this,this::nextQuestion,30L);}
                    if(game==3){raceProgress.clear();raceFinishes.clear();}
                    if(game==4){archeryHits.clear();archeryFinishTimes.clear();}
                    broadcast("§dSTART: "+GAMES[game-1]);saveState();return true;}
                case "stop" -> {if(game==6){stopWordDuell();say(sender,"Wortduell gestoppt.");return true;}if(game==5){estimateGeneration++;estimateAccepting=false;answerDeadline=0;}if(game==4)gameFour.stop();if(game==3){gameThree.stop();running=false;gameOneScreen.idle();say(sender,"Rennen gestoppt.");return true;}if(game==2){game2Accepting=false;game2Revealing=false;answerDeadline=0;restoreGame2Inventories();}if(game==7&&running){stopParkour();return true;}if(game==8&&running){finishMystery();return true;}running=false;gameOneScreen.idle();broadcast("Spiel gestoppt.");saveState();return true;}
                case "raw" -> {if(args.length<4)break;String t=args[1];int n=Integer.parseInt(args[2]),v=Integer.parseInt(args[3]);if(!teams.containsKey(t)||n<1||n>5)break;raw.computeIfAbsent(t,k->new HashMap<>()).put(n,v);say(sender,"Rohwertung "+t+" Spiel "+n+" = "+v);rankRound(sender,n);return true;}
                case "rank" -> {rankRound(sender,5);return true;}
                case "qualify" -> {finalizeTeams(sender);return true;}
                case "finalist" -> {if(args.length<3||!teams.containsKey(args[1])||!teams.containsKey(args[2])||args[1].equals(args[2]))break;finalists=new ArrayList<>(List.of(args[1],args[2]));manualFinalists=true;finaleStarted=false;advantages.clear();for(String t:finalists)advantages.put(t,0);broadcast("Finale: "+finalists);saveState();return true;}
                case "basket" -> {say(sender,"Basketball wurde durch Wortduell ersetzt.");return true;}
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
        if(args.length==1)return Arrays.stream(new String[]{"screen","game3","game5","game6","help","status","studio","team","wand","region","neon","set","tp","select","game","start","stop","raw","rank","qualify","finalist","basket","parkour","chest","points","spend","buzzer","candidate","candidates","reset","next","finishround","target","checkpoint","shop"}).filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("game3"))return List.of("start","stop","debug","laps");
        if(args.length==2&&args[0].equalsIgnoreCase("game6"))return List.of("start","stop","reload");
        if(args.length==2&&args[0].equalsIgnoreCase("screen"))return List.of("set","test","remove","idle");
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
        if(args.length==2&&args[0].equalsIgnoreCase("wand"))return List.of("region","game3");
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
