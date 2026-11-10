package de.wolfsfell.gameshow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;

/** Nine-by-nine image wall. One persistent map per item frame, no client mod required. */
final class GameOneScreen {
    private final GameshowPlugin plugin;
    private final List<MapView> views = new ArrayList<>();
    private final List<ItemFrame> frames = new ArrayList<>();
    private BufferedImage source;
    private boolean frozen;
    private int phase = -1;
    private long lastChange;
    private String current="";
    GameOneScreen(GameshowPlugin plugin){this.plugin=plugin;}
    void load(){ /* Frame entities persist in the world; maps are rebound by /wg screen set. */ }
    void setup(Player player){
        Block origin=player.getTargetBlockExact(10);
        if(origin==null){player.sendMessage("§cSchau auf den unteren linken Block der 9x9-Wand.");return;}
        BlockFace face=player.getFacing().getOppositeFace();
        if(face!=BlockFace.NORTH && face!=BlockFace.SOUTH && face!=BlockFace.EAST && face!=BlockFace.WEST){player.sendMessage("§cBitte gerade vor der Wand stehen.");return;}
        // The player's horizontal right determines the display's left-to-right direction.
        BlockFace right=switch(face){case NORTH -> BlockFace.WEST;case SOUTH -> BlockFace.EAST;case EAST -> BlockFace.NORTH;default -> BlockFace.SOUTH;};
        for(int y=0;y<9;y++)for(int x=0;x<9;x++){
            Block backing=origin.getRelative(right,x).getRelative(BlockFace.UP,y);
            if(!backing.getType().isSolid() || !backing.getRelative(face).getType().isAir()){
                player.sendMessage("§cWand nicht frei bei Spalte "+(x+1)+", Zeile "+(y+1)+". 9x9 feste Blöcke mit Luft davor nötig.");return;
            }
        }
        clear();
        World world=origin.getWorld();
        for(int y=8;y>=0;y--)for(int x=0;x<9;x++){
            Block backing=origin.getRelative(right,x).getRelative(BlockFace.UP,y);
            Location loc=backing.getRelative(face).getLocation().add(.5,.5,.5);
            ItemFrame frame=world.spawn(loc,ItemFrame.class, f->{f.setFacingDirection(face,true);f.setFixed(true);f.setInvulnerable(true);});
            MapView view=Bukkit.createMap(world);
            view.getRenderers().forEach(view::removeRenderer);
            final int tx=x,ty=8-y;
            view.addRenderer(new MapRenderer(false){@Override public void render(MapView map,MapCanvas canvas,Player viewer){
                if(display==null)return;
                canvas.drawImage(0,0,display.getSubimage(tx*128,ty*128,128,128));
            }});
            frame.setItem(new ItemStack(Material.FILLED_MAP));
            ItemStack item=frame.getItem();org.bukkit.inventory.meta.MapMeta meta=(org.bukkit.inventory.meta.MapMeta)item.getItemMeta();meta.setMapView(view);item.setItemMeta(meta);frame.setItem(item);
            frames.add(frame);views.add(view);
        }
        player.sendMessage("§a9x9-Gameshow-Bildschirm aufgebaut: 81 Maps. /wg screen test");
    }
    private BufferedImage display;
    void clear(){for(ItemFrame frame:frames)if(frame.isValid())frame.remove();frames.clear();views.clear();display=null;}
    void show(String answer){
        current=answer;phase=-1;frozen=false;lastChange=0;
        try(var stream=plugin.getResource("game1/"+answer+".png")){
            if(stream==null){plugin.getLogger().warning("Kein Game1-Bild für "+answer);return;}
            source=ImageIO.read(stream);
            advance();
        }catch(Exception ex){plugin.getLogger().warning("Game1 image: "+ex.getMessage());}
    }
    void freeze(){frozen=true;}
    void resume(){frozen=false;lastChange=System.currentTimeMillis();}
    void reveal(){phase=8;render();}
    void advance(){if(source==null||frozen)return;long now=System.currentTimeMillis();if(phase>=8||now-lastChange<2000)return;lastChange=now;phase++;render();}
    private void render(){
        if(source==null)return;
        int pixels=switch(Math.min(phase,8)){case 0->2;case 1->3;case 2->4;case 3->6;case 4->8;case 5->12;case 6->20;case 7->32;default->64;};
        BufferedImage small=new BufferedImage(pixels,pixels,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=small.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(source,0,0,pixels,pixels,null);g.dispose();
        BufferedImage big=new BufferedImage(1152,1152,BufferedImage.TYPE_INT_RGB);
        g=big.createGraphics();g.setColor(new Color(23,18,37));g.fillRect(0,0,1152,1152);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(small,0,0,1152,1152,null);g.dispose();display=big;
        for(Player player:Bukkit.getOnlinePlayers())for(MapView map:views)player.sendMap(map);
    }
}
