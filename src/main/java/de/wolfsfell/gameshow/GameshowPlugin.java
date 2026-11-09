package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
    private static final int[] RANK_POINTS = {100,80,65,50,40,30,20,10};
    private final LinkedHashMap<String, LinkedHashSet<UUID>> teams = new LinkedHashMap<>();
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

    @Override public void onEnable() {
        saveDefaultConfig(); stateFile = new File(getDataFolder(),"state.yml"); loadState();
        Bukkit.getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("wg")).setExecutor(this);
        Objects.requireNonNull(getCommand("wg")).setTabCompleter(this);
        loadQuestions();
        clock = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this,this::updateDisplays,40L,40L);
        getLogger().info("Wolfsfell Gameshow 0.1 loaded; " + teams.size() + " teams");
    }
    @Override public void onDisable() { if(clock!=null)clock.cancel(); saveState(); }
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
            if(mark!=null) for(Player p:Bukkit.getOnlinePlayers()) {
                String t=team(p.getUniqueId());
                boolean allowed=t!=null && (game<=5 || finalTeam(t));
                boolean near=allowed && p.getWorld().equals(mark.getWorld()) && p.getLocation().distanceSquared(mark)<=Math.pow(getConfig().getDouble("settings.selection-radius",1.5),2);
                if(!near){selectionTicks.remove(p.getUniqueId());continue;}
                int ticks=selectionTicks.merge(p.getUniqueId(),1,Integer::sum);
                if(ticks==getConfig().getInt("settings.selection-hold-ticks",20)) {
                    selected.put(t,p.getUniqueId()); broadcast("§d"+p.getName()+" §7ist für §f"+t+" §7ausgewählt!"); saveState();
                }
            }
        }

        if(!running)return;
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
        if(!running || game!=7 || e.getTo()==null)return;
        String t=team(e.getPlayer().getUniqueId());if(t==null || !finalTeam(t) || !Objects.equals(selected.get(t),e.getPlayer().getUniqueId()) || parkourFinishes.containsKey(t))return;
        Location goal=locations.get("parkour-finish");if(goal==null || !goal.getWorld().equals(e.getTo().getWorld()) || e.getTo().distanceSquared(goal)>2.25)return;
        int pts=Math.max(0,getConfig().getInt("settings.parkour-start-points",12)-elapsed()/Math.max(1,getConfig().getInt("settings.parkour-loss-every-seconds",15)));
        if(parkourFinishes.isEmpty())pts+=getConfig().getInt("settings.parkour-win-bonus",2);
        parkourFinishes.put(t,System.currentTimeMillis());parkourScores.put(t,pts);
        broadcast("§a"+e.getPlayer().getName()+" §7beendet Parkour: §e+"+pts+" Vorteilspunkte");
        if(parkourFinishes.size()>=finalists.size())stopParkour();else saveState();
    }
    private void stopParkour(){if(!running || game!=7)return;for(String t:finalists)advantages.merge(t,parkourScores.getOrDefault(t,0),Integer::sum);running=false;broadcast("Parkour beendet! Vorteile wurden gutgeschrieben.");saveState();}
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
        if(t==null || !Objects.equals(selected.get(t),p.getUniqueId()))return;
        if(game==1 && !p.getUniqueId().equals(buzzed))return;
        if(game!=1 && answerDeadline==0)return;
        String msg=e.getMessage().trim();e.setCancelled(true);
        Bukkit.getScheduler().runTask(this,()->{
            if(!running)return;
            if(game==1){
                if(msg.equalsIgnoreCase(correctAnswer)){
                    int pts=Math.max(1,3-(int)Math.min(2,(System.currentTimeMillis()-startMillis)/15000));
                    quizScores.merge(t,pts,Integer::sum);individual.merge(p.getUniqueId(),pts,Integer::sum);
                    broadcast("§aRichtig! §f"+p.getName()+" §7("+t+") §a+"+pts+" §7– "+correctAnswer);
                    buzzed=null;nextQuestion();
                } else {broadcast("§cFalsch! §7"+t+" hat wieder freigegeben.");buzzed=null;}
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
        if(archeryHits.get(t)>=10)broadcast("§d"+t+" §7hat alle Zielscheiben getroffen!");
    }
    private void loadQuestions() {
        File f=new File(getDataFolder(),"questions.yml");if(!f.exists())saveResource("questions.yml",false);
        YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
        for(Map<?,?> row:y.getMapList("questions"))try{
            int g=Integer.parseInt(String.valueOf(row.get("game")));
            String prompt=String.valueOf(row.get("question")),answer=String.valueOf(row.get("answer"));
            Object w=row.get("wrong");String[] wrong=w instanceof List<?> list?list.stream().map(String::valueOf).toArray(String[]::new):new String[0];
            if((g==2 && wrong.length==3) || g==1 || g==5)questions.add(new QuizQuestion(g,prompt,answer,wrong));
        }catch(Exception ignored){}
        getLogger().info("Loaded "+questions.size()+" quiz entries");
    }
    private void nextQuestion(){
        List<QuizQuestion> pool=questions.stream().filter(q->q.game()==game&&!usedQuestions.contains(q.prompt())).toList();
        if(pool.isEmpty()){broadcast("§dFragenpool für Spiel "+game+" aufgebraucht.");running=false;return;}
        QuizQuestion q=pool.get(random.nextInt(pool.size()));usedQuestions.add(q.prompt());questionNumber++;
        currentPrompt=q.prompt();correctAnswer=q.answer();responses.clear();estimates.clear();buzzed=null;
        if(game==2){List<String> opts=new ArrayList<>();opts.add(q.answer());opts.addAll(Arrays.asList(q.wrong()));Collections.shuffle(opts);choices=opts.toArray(String[]::new);}
        answerDeadline=game==1?0:System.currentTimeMillis()+getConfig().getInt("settings.quiz-answer-seconds",12)*1000L;
        broadcast("§dFrage "+questionNumber+": §f"+currentPrompt);
        if(game==2)for(int i=0;i<4;i++)broadcast("§7"+(char)('A'+i)+") §f"+choices[i]);
        if(game==5)broadcast("§7Schätzung im Chat eingeben!");
        if(game==1)broadcast("§7Mit /wg buzz buzzern, danach im Chat antworten!");
    }
    private void resolveQuestion(){
        if(game==2){
            String right="";for(int i=0;i<4;i++)if(choices[i].equals(correctAnswer))right=""+(char)('A'+i);
            broadcast("§aRichtige Antwort: §f"+right+" §7– "+correctAnswer);
            for(String t:teams.keySet()){
                String v=responses.getOrDefault(t,"–");broadcast("§7"+t+": §f"+v);
                if(v.equals(right)){quizScores.merge(t,1,Integer::sum);UUID id=selected.get(t);if(id!=null)individual.merge(id,1,Integer::sum);}
            }
        }else if(game==5){
            int correct;try{correct=Integer.parseInt(correctAnswer);}catch(NumberFormatException ex){broadcast("Ungültige Schätzfrage.");return;}
            List<Map.Entry<String,Integer>> order=new ArrayList<>(estimates.entrySet());
            order.sort(Comparator.comparingLong(x->Math.abs((long)x.getValue()-correct)));
            broadcast("§aRichtige Antwort: §f"+correct);
            for(int i=0;i<order.size();i++){
                var entry=order.get(i);int pts=Math.max(0,3-i);
                broadcast("§7"+entry.getKey()+": §f"+entry.getValue()+(pts>0?" §a+"+pts:""));
                if(pts>0){quizScores.merge(entry.getKey(),pts,Integer::sum);UUID id=selected.get(entry.getKey());if(id!=null)individual.merge(id,pts,Integer::sum);}
            }
        }
        if(running)Bukkit.getScheduler().runTaskLater(this,()->{if(running && (game==2||game==5))nextQuestion();},80L);
    }
    private void updateDisplays(){
        for(Player p:Bukkit.getOnlinePlayers()){
            String mine=team(p.getUniqueId());
            StringBuilder tab=new StringBuilder("§5§lWOLFSFELL GAMESHOW§r\n§7Twitch: §dtwitch.tv/wolfsfelltv\n§7YouTube: §dWolfsfellTVHD\n");
            for(var e:teams.entrySet()){
                tab.append("§d").append(e.getKey()).append("§7: ");
                tab.append(String.join(", ",e.getValue().stream().map(u->Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse("?")).toList())).append("\n");
            }
            p.setPlayerListHeaderFooter(tab.toString(),"§dLive täglich 17 Uhr §7| §fWolfsfellTV");
            String suffix=mine==null?"":(" §8[§d"+mine+"§8]");p.setPlayerListName("§f"+p.getName()+suffix);
            List<String> ranks=new ArrayList<>(teams.keySet());ranks.sort(Comparator.comparingInt((String t)->qualifying.getOrDefault(t,0)).reversed());
            StringBuilder side=new StringBuilder("§5§lGAMESHOW\n§7Spiel "+game+"/9\n§dTOP 5 TEAMS\n");
            for(int i=0;i<Math.min(5,ranks.size());i++)side.append("§f").append(i+1).append(". ").append(ranks.get(i)).append(" §d").append(qualifying.getOrDefault(ranks.get(i),0)).append("\n");
            if(mine!=null)side.append("§7Du: #").append(ranks.indexOf(mine)+1).append(" §f").append(qualifying.getOrDefault(mine,0)).append("\n");
            List<UUID> people=new ArrayList<>(individual.keySet());people.sort(Comparator.comparingInt((UUID id)->individual.getOrDefault(id,0)).reversed());
            side.append("§dTOP 5 SPIELER\n");for(int i=0;i<Math.min(5,people.size());i++)side.append("§f").append(i+1).append(". ").append(Optional.ofNullable(Bukkit.getOfflinePlayer(people.get(i)).getName()).orElse("?" )).append(" §d").append(individual.get(people.get(i))).append("\n");
            side.append("§7Du: #").append(people.indexOf(p.getUniqueId())+1).append(" §f").append(individual.getOrDefault(p.getUniqueId(),0));
            // Sidebar scoreboard is refreshed with unique line entries; limit is 15 lines.
            Scoreboard board=Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj=board.registerNewObjective("wg","dummy","§5§lWOLFSFELL");obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            String[] lines=side.toString().split("\n");int n=Math.min(15,lines.length);
            for(int i=0;i<n;i++)obj.getScore(lines[i].substring(0,Math.min(35,lines[i].length()))+"§"+"0123456789abcdef".charAt(i)).setScore(n-i);
            p.setScoreboard(board);
        }
    }
    private void loadState(){
        if(!stateFile.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(stateFile);
        game=y.getInt("game",0);running=false;selectionEnabled=false;finalists=y.getStringList("finalists");
        ConfigurationSection ts=y.getConfigurationSection("teams");if(ts!=null)for(String t:ts.getKeys(false)){
            LinkedHashSet<UUID> members=new LinkedHashSet<>();for(String id:ts.getStringList(t))try{members.add(UUID.fromString(id));}catch(IllegalArgumentException ignored){}teams.put(t,members);
        }
        ConfigurationSection loc=y.getConfigurationSection("locations");if(loc!=null)for(String k:loc.getKeys(false)){Location l=loc.getLocation(k);if(l!=null)locations.put(k,l);}
        ConfigurationSection q=y.getConfigurationSection("qualifying");if(q!=null)for(String t:q.getKeys(false))qualifying.put(t,q.getInt(t));
        ConfigurationSection rr=y.getConfigurationSection("raw");if(rr!=null)for(String t:rr.getKeys(false)){ConfigurationSection r=rr.getConfigurationSection(t);if(r!=null)for(String n:r.getKeys(false))try{raw.computeIfAbsent(t,k->new HashMap<>()).put(Integer.parseInt(n),r.getInt(n));}catch(NumberFormatException ignored){}}
        ConfigurationSection a=y.getConfigurationSection("advantages");if(a!=null)for(String t:a.getKeys(false))advantages.put(t,a.getInt(t));
        ConfigurationSection sel=y.getConfigurationSection("selected");if(sel!=null)for(String t:sel.getKeys(false))try{selected.put(t,UUID.fromString(sel.getString(t,"")));}catch(IllegalArgumentException ignored){}
        ConfigurationSection chest=y.getConfigurationSection("chests");if(chest!=null)for(String k:chest.getKeys(false))mysteryChestValues.put(k,chest.getInt(k));
        openedChests.addAll(y.getStringList("opened-chests"));
        ConfigurationSection ids=y.getConfigurationSection("individual");if(ids!=null)for(String k:ids.getKeys(false))try{individual.put(UUID.fromString(k),ids.getInt(k));}catch(Exception ignored){}
        ConfigurationSection target=y.getConfigurationSection("targets");if(target!=null)for(String t:target.getKeys(false))archeryTargets.put(t,new HashSet<>(target.getStringList(t)));
        ConfigurationSection cp=y.getConfigurationSection("race-checkpoints");if(cp!=null)for(String k:cp.getKeys(false)){List<Location> ls=new ArrayList<>();for(Object obj:cp.getList(k,List.of()))if(obj instanceof Location l)ls.add(l);raceCheckpoints.put(k,ls);}
        ConfigurationSection buy=y.getConfigurationSection("purchases");if(buy!=null)for(String t:buy.getKeys(false)){ConfigurationSection sec=buy.getConfigurationSection(t);if(sec!=null)for(String k:sec.getKeys(false))purchases.computeIfAbsent(t,x->new HashMap<>()).put(k,sec.getInt(k));}
    }
    private void saveState(){
        YamlConfiguration y=new YamlConfiguration();y.set("game",game);y.set("finalists",finalists);
        for(var e:teams.entrySet())y.set("teams."+e.getKey(),e.getValue().stream().map(UUID::toString).toList());
        for(var e:locations.entrySet())y.set("locations."+e.getKey(),e.getValue());
        for(var e:qualifying.entrySet())y.set("qualifying."+e.getKey(),e.getValue());
        for(var e:advantages.entrySet())y.set("advantages."+e.getKey(),e.getValue());
        for(var e:raw.entrySet())for(var r:e.getValue().entrySet())y.set("raw."+e.getKey()+"."+r.getKey(),r.getValue());
        for(var e:selected.entrySet())y.set("selected."+e.getKey(),e.getValue().toString());
        // Keys contain UUIDs and colons, which are safe YAML path segment characters.
        for(var e:mysteryChestValues.entrySet())y.set("chests."+e.getKey(),e.getValue());
        y.set("opened-chests",new ArrayList<>(openedChests));
        for(var e:individual.entrySet())y.set("individual."+e.getKey(),e.getValue());
        for(var e:archeryTargets.entrySet())y.set("targets."+e.getKey(),new ArrayList<>(e.getValue()));
        for(var e:raceCheckpoints.entrySet())y.set("race-checkpoints."+e.getKey(),e.getValue());
        for(var e:purchases.entrySet())for(var item:e.getValue().entrySet())y.set("purchases."+e.getKey()+"."+item.getKey(),item.getValue());
        try{y.save(stateFile);}catch(IOException ex){getLogger().severe("State save failed: "+ex.getMessage());}
    }
    private void rankRound(CommandSender s,int n){
        if(n<1||n>5){say(s,"Nur Vorrundenspiele 1-5.");return;}
        List<String> order=new ArrayList<>(teams.keySet());order.sort((a,b)->Integer.compare(raw.getOrDefault(b,Map.of()).getOrDefault(n,Integer.MIN_VALUE),raw.getOrDefault(a,Map.of()).getOrDefault(n,Integer.MIN_VALUE)));
        if(order.stream().anyMatch(t->!raw.getOrDefault(t,Map.of()).containsKey(n))){say(s,"Für jedes Team muss zuerst /wg raw <Team> "+n+" <Wert> gesetzt werden.");return;}
        for(String t:teams.keySet())qualifying.put(t,0);
        for(int round=1;round<=5;round++) {
            final int r=round; if(teams.keySet().stream().anyMatch(t->!raw.getOrDefault(t,Map.of()).containsKey(r)))continue;
            List<String> sorted=new ArrayList<>(teams.keySet());sorted.sort((a,b)->Integer.compare(raw.get(b).get(r),raw.get(a).get(r)));
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
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length==0 || args[0].equalsIgnoreCase("help")){
            say(sender,"/wg team create <Name> | /wg team join <Name> [Spieler] | /wg team list");
            say(sender,"/wg set <selection|studio|parkour-start|parkour-finish|kituhc> | /wg select on/off");
            say(sender,"/wg game <1-9> | /wg start | /wg stop | /wg status | /wg tp <Ort>");
            say(sender,"/wg raw <Team> <Spiel 1-5> <Wert> | /wg rank | /wg qualify | /wg finalist <A> <B>");
            say(sender,"/wg basket <Team> <0-6> | /wg parkour finish | /wg chest add|clear|shuffle|stop");
            say(sender,"/wg points <Team> | /wg points add <Team> <Zahl> | /wg spend <Team> <Kosten> <Notiz>");return true;
        }
        String cmd=args[0].toLowerCase(Locale.ROOT);
        if(cmd.equals("status")){say(sender,"Spiel "+game+(game>0?" ("+GAMES[game-1]+")":"")+", aktiv="+running+", Spielerwahl="+selectionEnabled+", Finalisten="+finalists);for(String t:teams.keySet())say(sender,t+" ("+teams.get(t).size()+"/3) | Vorrunde "+qualifying.getOrDefault(t,0)+" | Vorteile "+advantages.getOrDefault(t,0));return true;}
        if(cmd.equals("team")&&args.length>=2){
            if(args[1].equalsIgnoreCase("list")){for(var e:teams.entrySet())say(sender,e.getKey()+": "+e.getValue().stream().map(u->Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse(u.toString())).toList());return true;}
            if(!admin(sender))return true;
            if(args[1].equalsIgnoreCase("create")&&args.length>=3){String t=args[2];if(!t.matches("[a-zA-Z0-9_-]{1,20}")||teams.containsKey(t)||teams.size()>=getConfig().getInt("settings.max-teams",8)){say(sender,"Ungültiger Name, Team vorhanden oder 8 Teams erreicht.");return true;}teams.put(t,new LinkedHashSet<>());say(sender,"Team "+t+" erstellt.");saveState();return true;}
            if(args[1].equalsIgnoreCase("join")&&args.length>=3){String t=args[2];if(!teams.containsKey(t)){say(sender,"Team nicht vorhanden.");return true;}Player p=args.length>=4?Bukkit.getPlayerExact(args[3]):sender instanceof Player pl?pl:null;if(p==null){say(sender,"Spieler muss online sein.");return true;}if(teams.get(t).size()>=getConfig().getInt("settings.team-size",3)&&!teams.get(t).contains(p.getUniqueId())){say(sender,"Team voll.");return true;}teams.values().forEach(set->set.remove(p.getUniqueId()));teams.get(t).add(p.getUniqueId());say(sender,p.getName()+" -> "+t);saveState();return true;}
            if(args[1].equalsIgnoreCase("leave")&&args.length>=3){Player p=Bukkit.getPlayerExact(args[2]);if(p!=null){teams.values().forEach(set->set.remove(p.getUniqueId()));saveState();}return true;}
        }
        if(cmd.equals("buzz")){
            if(!(sender instanceof Player p) || !running || game!=1)return true;
            String t=team(p.getUniqueId());if(t==null||!Objects.equals(selected.get(t),p.getUniqueId()))return true;
            if(buzzed==null){buzzed=p.getUniqueId();broadcast("§dBUZZ! §f"+p.getName()+" §7darf antworten!");p.playSound(p.getLocation(),"wolfsfell.buzzer",1f,1f);}return true;
        }
        if(cmd.equals("shop")){
            if(sender instanceof Player p)openShop(p);return true;
        }
        if(!admin(sender))return true;
        try {
            switch(cmd){
                case "next" -> {if(!running || !List.of(1,2,5).contains(game))break;nextQuestion();return true;}
                case "finishround" -> {
                    if(game>=1&&game<=5){
                        if(game==1||game==2||game==5)for(String t:teams.keySet())raw.computeIfAbsent(t,k->new HashMap<>()).put(game,quizScores.getOrDefault(t,0));
                        if(game==3)for(String t:teams.keySet())raw.computeIfAbsent(t,k->new HashMap<>()).put(game,raceFinishes.containsKey(t)?(1000-(int)(raceFinishes.get(t)-startMillis)/1000):raceProgress.getOrDefault(t,0));
                        if(game==4)for(String t:teams.keySet())raw.computeIfAbsent(t,k->new HashMap<>()).put(game,archeryHits.getOrDefault(t,0));
                        running=false;rankRound(sender,game);broadcast("§dSpiel "+game+" abgeschlossen.");return true;
                    }break;
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
                case "select" -> {if(args.length<2)break;selectionEnabled=args[1].equalsIgnoreCase("on");selectionTicks.clear();say(sender,"Spielerwahl "+(selectionEnabled?"aktiv":"aus"));return true;}
                case "game" -> {if(args.length<2)break;int g=Integer.parseInt(args[1]);if(g<1||g>9)break;running=false;game=g;selected.clear();selectionTicks.clear();say(sender,"Spiel "+game+": "+GAMES[game-1]);saveState();return true;}
                case "start" -> {if(game==0){say(sender,"Zuerst /wg game <1-9>.");return true;}if(game>=6&&finalists.size()!=2){say(sender,"Finalteams fehlen: /wg qualify oder /wg finalist.");return true;}if(game==8){if(mysteryChestValues.size()!=50){say(sender,"Bitte zuerst 50 Truhen markieren.");return true;}setupMystery(sender);}if(game==7){if(!locations.containsKey("parkour-finish")){say(sender,"Parkour-Ziel fehlt.");return true;}parkourScores.clear();parkourFinishes.clear();}running=true;startMillis=System.currentTimeMillis();selectionEnabled=false;
                    if(game==1||game==2||game==5){quizScores.clear();usedQuestions.clear();questionNumber=0;Bukkit.getScheduler().runTaskLater(this,this::nextQuestion,30L);}
                    if(game==3){raceProgress.clear();raceFinishes.clear();}
                    if(game==4){archeryHits.clear();}
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
        if(args.length==1)return Arrays.stream(new String[]{"help","status","team","set","tp","select","game","start","stop","raw","rank","qualify","finalist","basket","parkour","chest","points","spend","buzz","next","finishround","target","checkpoint","shop"}).filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2&&args[0].equalsIgnoreCase("target"))return List.of("add");
        if(args.length==2&&args[0].equalsIgnoreCase("checkpoint"))return List.of("add","clear");
        if(args.length==2&&args[0].equalsIgnoreCase("team"))return List.of("create","join","leave","list");
        if(args.length==2&&args[0].equalsIgnoreCase("set"))return List.of("selection","studio","parkour-start","parkour-finish","kituhc","race-start","race-finish");
        if(args.length==2&&args[0].equalsIgnoreCase("select"))return List.of("on","off");
        if(args.length==2&&args[0].equalsIgnoreCase("chest"))return List.of("add","clear","shuffle","retire");
        if(args.length==2&&args[0].equalsIgnoreCase("game"))return List.of("1","2","3","4","5","6","7","8","9");
        if(args.length==2&&List.of("basket","raw","points","spend","finalist").contains(args[0].toLowerCase(Locale.ROOT)))return new ArrayList<>(teams.keySet());
        return List.of();
    }
}
