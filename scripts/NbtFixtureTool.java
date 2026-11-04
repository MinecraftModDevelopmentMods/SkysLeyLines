import java.nio.file.Path;
import net.minecraft.nbt.*;

/** Edits disposable validation fixtures only. Compile against this target's mapped Minecraft jar. */
public final class NbtFixtureTool {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[1]).toAbsolutePath().normalize();
        Path allowed=Path.of(args[2]).toAbsolutePath().normalize();
        if(!file.startsWith(allowed)) throw new IllegalArgumentException("Fixture escaped validation root");
        CompoundTag root=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());
        if(args[0].equals("inspect-ley")) {
            System.out.println(root.getCompound("data"));
            return;
        } else if(args[0].equals("visual")) {
            CompoundTag data=root.getCompound("Data");
            data.putBoolean("allowCommands",true);
            data.putInt("GameType",1);
            data.putInt("SpawnX",Integer.parseInt(args[3]));
            data.putInt("SpawnY",Integer.parseInt(args[4]));
            data.putInt("SpawnZ",Integer.parseInt(args[5]));
            CompoundTag rules=data.getCompound("GameRules"); rules.putString("spawnRadius","0"); data.put("GameRules",rules);
        } else if(args[0].equals("visual-player")) {
            CompoundTag data=root.getCompound("Data");
            data.putBoolean("allowCommands",true);
            data.putLong("DayTime",6000);
            data.putBoolean("raining",false); data.putBoolean("thundering",false);
            data.getCompound("GameRules").putString("doDaylightCycle","false");
            CompoundTag player=data.getCompound("Player");
            ListTag pos=new ListTag();
            pos.add(DoubleTag.valueOf(Double.parseDouble(args[3])));
            pos.add(DoubleTag.valueOf(Double.parseDouble(args[4])));
            pos.add(DoubleTag.valueOf(Double.parseDouble(args[5])));
            player.put("Pos",pos);
            ListTag rotation=new ListTag(); rotation.add(FloatTag.valueOf(0)); rotation.add(FloatTag.valueOf(15));
            player.put("Rotation",rotation);
            player.getCompound("abilities").putBoolean("flying",true);
            player.getCompound("abilities").putBoolean("mayfly",true);
        } else if(args[0].equals("unsupported")) {
            root.getCompound("data").putInt("schema",99);
        } else throw new IllegalArgumentException("Unknown fixture mode");
        NbtIo.writeCompressed(root,file);
        System.out.println("Prepared disposable "+args[0]+" fixture: "+file);
    }
}
