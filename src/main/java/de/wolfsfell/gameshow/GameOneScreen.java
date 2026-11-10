package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;

/** Persistent 33x9 map wall. Left/right leaderboards, 9x9 central item. */
final class GameOneScreen {
    private static final int COLS=33, ROWS=9, TILE=128, WIDTH=COLS*TILE, HEIGHT=ROWS*TILE;
    private final GameshowPlugin plugin;
    private final List<MapView> views=new ArrayList<>();
    private final List<ItemFrame> frames=new ArrayList<>();
    private BufferedImage source, display;
    private String current="";
    private boolean quiz2=false;
    private String quiz2Prompt="",quiz2Correct=null;
    private String[] quiz2Choices=new String[4];
    private int quiz2Number,quiz2Seconds,quiz2Count,quiz2Total;
    private int phase=-1;
    private boolean frozen=false, active=false;
    private long lastChange=0;
    private String lastSignature="";
    GameOneScreen(GameshowPlugin plugin){this.plugin=plugin;}
    int stage(){return Math.max(1,Math.min(7,phase+1));}
    int points(){return 8-stage();}
    void load(){
        File file=new File(plugin.getDataFolder(),"wide-screen.yml");
        if(!file.exists())return;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
        List<String> ids=y.getStringList("frames");
        for(String id:ids)try{
            UUID uuid=UUID.fromString(id);
            for(World world:Bukkit.getWorlds()){
                var entity=world.getEntity(uuid);
                if(entity instanceof ItemFrame frame){frames.add(frame);break;}
            }
        }catch(Exception ignored){}
        // Frames must be loaded in their chunk for automatic restoration.
        if(frames.size()!=COLS*ROWS){frames.clear();return;}
        bindExisting();idle();
    }
    void setup(Player player){
        Block origin=player.getTargetBlockExact(10);
        if(origin==null){player.sendMessage("§cSchau auf den unteren linken Block deiner 33x9-Wand.");return;}
        BlockFace face=player.getFacing().getOppositeFace();
        if(face!=BlockFace.NORTH&&face!=BlockFace.SOUTH&&face!=BlockFace.EAST&&face!=BlockFace.WEST){player.sendMessage("§cBitte gerade vor der Wand stehen.");return;}
        BlockFace right=switch(face){case NORTH->BlockFace.WEST;case SOUTH->BlockFace.EAST;case EAST->BlockFace.NORTH;default->BlockFace.SOUTH;};
        for(int y=0;y<ROWS;y++)for(int x=0;x<COLS;x++){
            Block backing=origin.getRelative(right,x).getRelative(BlockFace.UP,y);
            if(!backing.getType().isSolid()||!backing.getRelative(face).getType().isAir()){
                player.sendMessage("§c33x9-Wand blockiert bei Spalte "+(x+1)+", Zeile "+(y+1));return;
            }
        }
        clear();
        World world=origin.getWorld();
        for(int y=ROWS-1;y>=0;y--)for(int x=0;x<COLS;x++){
            Block backing=origin.getRelative(right,x).getRelative(BlockFace.UP,y);
            Location loc=backing.getRelative(face).getLocation().add(.5,.5,.5);
            ItemFrame frame=world.spawn(loc,ItemFrame.class,f->{f.setFacingDirection(face,true);f.setFixed(true);f.setInvulnerable(true);});
            frames.add(frame);
        }
        bindExisting();saveFrames();idle();
        player.sendMessage("§a33x9-Screen eingerichtet: 297 Maps. Team-Ranking links, Item Mitte, Spieler rechts.");
    }
    private void bindExisting(){
        views.clear();
        for(int i=0;i<frames.size();i++){
            ItemFrame frame=frames.get(i);
            if(!frame.isValid())continue;
            MapView view=Bukkit.createMap(frame.getWorld());
            view.getRenderers().forEach(view::removeRenderer);
            final int tx=i%COLS,ty=i/COLS;
            view.addRenderer(new MapRenderer(false){@Override public void render(MapView map,MapCanvas canvas,Player viewer){
                if(display!=null)canvas.drawImage(0,0,display.getSubimage(tx*TILE,ty*TILE,TILE,TILE));
            }});
            ItemStack item=new ItemStack(Material.FILLED_MAP);
            MapMeta meta=(MapMeta)item.getItemMeta();meta.setMapView(view);item.setItemMeta(meta);frame.setItem(item);
            views.add(view);
        }
    }
    private void saveFrames(){
        YamlConfiguration y=new YamlConfiguration();
        y.set("frames",frames.stream().map(f->f.getUniqueId().toString()).toList());
        try{y.save(new File(plugin.getDataFolder(),"wide-screen.yml"));}catch(Exception ex){plugin.getLogger().warning("Screen save: "+ex.getMessage());}
    }
    void clear(){for(ItemFrame frame:frames)if(frame.isValid())frame.remove();frames.clear();views.clear();display=null;}
    void idle(){quiz2=false;active=false;frozen=false;phase=-1;source=null;current="";lastSignature="";render();}
    void show(String answer){
        current=answer;phase=-1;frozen=false;active=true;lastChange=0;
        try(var stream=plugin.getResource("game1/"+answer+".png")){
            source=stream==null?null:ImageIO.read(stream);
            if(source==null)plugin.getLogger().warning("Kein Itembild: "+answer);
        }catch(Exception ex){source=null;plugin.getLogger().warning("Game1 image: "+ex.getMessage());}
        advance();
    }
    void freeze(){frozen=true;render();}
    void resume(){frozen=false;lastChange=System.currentTimeMillis();render();}
    void reveal(){phase=7;render();}
    void advance(){if(!active||source==null||frozen)return;long now=System.currentTimeMillis();if(phase>=6||now-lastChange<4000)return;lastChange=now;phase++;render();}
    void showQuiz2(int number,String prompt,String[] options,int seconds,int count,int total,String correct){
        quiz2=true;active=false;quiz2Number=number;quiz2Prompt=prompt;quiz2Choices=options.clone();quiz2Seconds=seconds;quiz2Count=count;quiz2Total=total;quiz2Correct=correct;render();
    }
    private void centered(Graphics2D g,String value,int y,int maxWidth,int maxSize,Color color){
        int size=maxSize;while(size>24){g.setFont(new Font("SansSerif",Font.BOLD,size));if(g.getFontMetrics().stringWidth(value)<=maxWidth)break;size-=2;}
        int width=g.getFontMetrics().stringWidth(value);text(g,value,12*TILE+(9*TILE-width)/2,y,size,color);
    }
    void updateLeaderboard(){
        String signature=plugin.wideTeams().toString()+plugin.widePlayers().toString()+plugin.wideTitle();
        for(String team:plugin.wideTeams())signature+=plugin.wideTeamPoints(team)+";";
        for(UUID id:plugin.widePlayers())signature+=plugin.widePlayerPoints(id)+";";
        if(!signature.equals(lastSignature)){lastSignature=signature;render();}
    }
    private static void text(Graphics2D g,String str,int x,int y,int size,Color color){
        g.setFont(new Font("SansSerif",Font.BOLD,size));g.setColor(new java.awt.Color(0,0,0,200));g.drawString(str,x+3,y+3);g.setColor(color);g.drawString(str,x,y);
    }
    private void panel(Graphics2D g,int x,int width,String title){
        g.setColor(new java.awt.Color(27,22,43));g.fillRoundRect(x+18,20,width-36,HEIGHT-40,34,34);
        g.setColor(new java.awt.Color(157,89,236));g.setStroke(new BasicStroke(7));g.drawRoundRect(x+18,20,width-36,HEIGHT-40,34,34);
        text(g,title,x+45,105,49,new java.awt.Color(233,211,255));
        g.setColor(new java.awt.Color(108,69,151));g.fillRect(x+45,135,width-90,4);
    }
    private void render(){
        if(views.isEmpty())return;
        BufferedImage canvas=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=canvas.createGraphics();
        g.setColor(new java.awt.Color(14,12,26));g.fillRect(0,0,WIDTH,HEIGHT);
        int leftWidth=12*TILE,rightStart=21*TILE;
        panel(g,0,leftWidth,"TEAM LEADERBOARD");panel(g,rightStart,12*TILE,"SPIELER LEADERBOARD");
        List<String> teams=plugin.wideTeams();
        for(int i=0;i<Math.min(8,teams.size());i++){
            String team=teams.get(i);int y=230+i*106;
            if(i<3){g.setColor(new java.awt.Color(65,46,90));g.fillRoundRect(48,y-57,leftWidth-95,86,18,18);}
            text(g,(i+1)+".  "+team,65,y,47,java.awt.Color.WHITE);
            text(g,String.valueOf(plugin.wideTeamPoints(team)),leftWidth-230,y,49,new java.awt.Color(246,203,102));
        }
        List<UUID> people=plugin.widePlayers();
        for(int i=0;i<Math.min(8,people.size());i++){
            UUID id=people.get(i);int y=230+i*106;
            if(i<3){g.setColor(new java.awt.Color(65,46,90));g.fillRoundRect(rightStart+48,y-57,12*TILE-95,86,18,18);}
            text(g,(i+1)+".  "+plugin.widePlayerName(id),rightStart+65,y,43,java.awt.Color.WHITE);
            text(g,String.valueOf(plugin.widePlayerPoints(id)),WIDTH-230,y,49,new java.awt.Color(246,203,102));
        }
        int cx=leftWidth,cy=0,centerWidth=9*TILE;
        g.setColor(new java.awt.Color(36,26,58));g.fillRect(cx,cy,centerWidth,HEIGHT);
        g.setColor(new java.awt.Color(162,93,240));g.setStroke(new BasicStroke(8));g.drawRect(cx+12,12,centerWidth-24,HEIGHT-24);
        if(quiz2){
            centered(g,"FRAGE "+quiz2Number+" / 15",105,1000,62,Color.WHITE);
            centered(g,quiz2Correct==null?"ZEIT: "+quiz2Seconds+"s":"AUFLOESUNG",180,1000,64,new Color(255,213,109));
            String[] words=quiz2Prompt.split(" ");StringBuilder line=new StringBuilder();int y=275;
            for(String word:words){String trial=line.length()==0?word:line+" "+word;
                if(trial.length()>32 && line.length()>0){centered(g,line.toString(),y,1030,45,Color.WHITE);y+=54;line=new StringBuilder(word);}else line=new StringBuilder(trial);
            }
            if(line.length()>0)centered(g,line.toString(),y,1030,45,Color.WHITE);
            for(int i=0;i<4;i++){
                int yy=490+i*112;String label=(char)('A'+i)+") "+quiz2Choices[i];
                boolean right=quiz2Correct!=null && quiz2Correct.equals(""+(char)('A'+i));
                g.setColor(right?new Color(37,119,69):new Color(69,47,100));g.fillRoundRect(cx+65,yy-70,centerWidth-130,94,20,20);
                centered(g,label,yy,centerWidth-180,48,Color.WHITE);
            }
            centered(g,quiz2Count+" / "+quiz2Total+" ANTWORTEN",HEIGHT-75,1050,45,new Color(219,184,255));
        }else if(active && source!=null){
            int pixels=switch(Math.max(0,Math.min(phase,7))){case 0->2;case 1->3;case 2->4;case 3->6;case 4->10;case 5->18;case 6->32;default->64;};
            BufferedImage small=new BufferedImage(pixels,pixels,BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg=small.createGraphics();sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);sg.drawImage(source,0,0,pixels,pixels,null);sg.dispose();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(small,cx+120,170,centerWidth-120,centerWidth-120,null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            text(g,"STUFE "+stage()+" / 7",cx+310,90,68,java.awt.Color.WHITE);
            text(g,"RICHTIG: +"+points()+" PUNKTE",cx+185,HEIGHT-65,55,new java.awt.Color(255,217,99));
            if(frozen)text(g,"BUZZ!",cx+410,165,62,new java.awt.Color(255,130,191));
        }else{
            text(g,"WOLFSFELL",cx+230,480,82,java.awt.Color.WHITE);
            text(g,"GAMESHOW",cx+280,580,72,new java.awt.Color(194,129,255));
            text(g,"BEREIT FUER GAME 1",cx+190,720,45,new java.awt.Color(230,219,245));
        }
        g.dispose();display=canvas;
        for(Player p:Bukkit.getOnlinePlayers())for(MapView view:views)p.sendMap(view);
    }
}
