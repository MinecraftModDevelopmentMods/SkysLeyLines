package zone.moddev.mc.skysleylines.probe;

import java.nio.file.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import zone.moddev.mc.skysleylines.server.LeyInspection;
import zone.moddev.mc.skysleylines.api.*;

/** Intentionally separate consumer used only in labelled disposable probe runtimes. */
@Mod("leyruntimeprobe")
public final class LeyRuntimeProbe {
    private static final ResourceLocation AETHER=ResourceLocation.parse("leyruntimeprobe:aether"), ELECTRIC=ResourceLocation.parse("leyruntimeprobe:electric"), MANA=ResourceLocation.parse("leyruntimeprobe:mana");
    private static final PowerRequest.Consumer PLANT=new PowerRequest.Consumer(ResourceLocation.parse("leyruntimeprobe:plant"));
    private static final PowerRequest.Consumer DENIED=new PowerRequest.Consumer(ResourceLocation.parse("leyruntimeprobe:denied"));
    private static int posts, reentrancies;
    private static NexusPowerService current;
    public LeyRuntimeProbe(IEventBus bus) {
        bus.addListener((FMLCommonSetupEvent e)->e.enqueueWork(()->{
            LeyVision.registerStyle(ResourceLocation.parse("leyruntimeprobe:goggles"),
                    new LeyVisionStyle(LeyVisionStyle.Pattern.CRACKLE, 0xffb54b, 0xff683b, 0.85f, 1.25f));
            LeyLinesApi.paradigms().register(new Paradigm<>(ResourceLocation.parse("leyruntimeprobe:technomancy"), "child-owned-config",
                List.of(new Paradigm.Currency(AETHER,()->MagicAmount.of(Long.getLong("skysleylines.probeRate",1000))),new Paradigm.Currency(ELECTRIC,()->MagicAmount.of(10000)))));
            LeyLinesApi.paradigms().register(new Paradigm<>(ResourceLocation.parse("leyruntimeprobe:mage"), null,
                List.of(new Paradigm.Currency(MANA,()->MagicAmount.of(10)))));
        }));
        NeoForge.EVENT_BUS.addListener((NexusTransactionEvent.Pre e)->{
            var r=e.proposed().request();
            if(r.consumer().equals(DENIED)) e.setCanceled(true);
            else if(current!=null) {
                var nested=current.convertAt(r.x(),r.y(),r.z(),AETHER,MagicAmount.ONE,PLANT);
                require(nested.status()==PowerResult.Status.REENTRANT,"Callback mutated the ledger"); reentrancies++;
            }
        });
        NeoForge.EVENT_BUS.addListener((NexusTransactionEvent.Post e)->posts++);
        NeoForge.EVENT_BUS.addListener(LeyRuntimeProbe::started);
        NeoForge.EVENT_BUS.addListener(LeyRuntimeProbe::commands);
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("leyprobe").requires(source -> source.hasPermission(2))
                .then(Commands.literal("recoverypermission").requires(source -> source.hasPermission(4)).executes(c -> {
                    var source = c.getSource();
                    var dispatcher = source.getServer().getCommands().getDispatcher();
                    var command = "skysleylines account recover leyruntimeprobe:aether 1";
                    try {
                        dispatcher.execute(command, source.withPermission(2));
                        throw new IllegalStateException("Permission level 2 reached currency recovery");
                    } catch (CommandSyntaxException expected) {
                        dispatcher.execute(command, source);
                    }
                    source.sendSystemMessage(net.minecraft.network.chat.Component.literal("LEY_RECOVERY_PERMISSION_PASS"));
                    return 1;
                }))
                .then(Commands.literal("style")
                        .then(Commands.argument("source", ResourceLocationArgument.id())
                                .then(Commands.argument("style", ResourceLocationArgument.id())
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(c -> {
                                                    LeyVision.grant(c.getSource().getPlayerOrException(),
                                                            ResourceLocationArgument.getId(c, "source"),
                                                            IntegerArgumentType.getInteger(c, "ticks"),
                                                            ResourceLocationArgument.getId(c, "style"));
                                                    return 1;
                                                })))))
                .then(Commands.literal("revoke").then(Commands.argument("source", ResourceLocationArgument.id())
                        .executes(c -> {
                            LeyVision.revoke(c.getSource().getPlayerOrException(), ResourceLocationArgument.getId(c, "source"));
                            return 1;
                        })))
                .then(Commands.literal("fund").executes(c -> {
                    var source = c.getSource();
                    var pos = BlockPos.containing(source.getPosition());
                    var power = LeyLinesApi.forLevel(source.getLevel()).power();
                    var aether = power.convertAt(pos.getX(), pos.getY(), pos.getZ(), AETHER, MagicAmount.of(5), PLANT);
                    var electric = power.convertAt(pos.getX(), pos.getY(), pos.getZ(), ELECTRIC, MagicAmount.of(2), PLANT);
                    return aether.succeeded() && electric.succeeded() ? 1 : 0;
                })));
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("skysleylines.runtimeProbe")) return;
        MinecraftServer server = event.getServer();
        try {
            ServerLevel level=server.overworld();
            var api=LeyLinesApi.forLevel(level);
            require(api.profile().spacingBlocks()==2048,"Saved profile was replaced by changed installation defaults");
            int before=level.getChunkSource().getLoadedChunksCount();
            Nexus node=api.field().nearestNexus(0,0,Math.min(4096,api.profile().spacingBlocks()*2)).orElseThrow();
            int x=(int)Math.round(node.x()), z=(int)Math.round(node.z());
            LeyColumn column=api.field().sampleColumn(x,z);
            require(column.active(),"Nexus terrain missing");
            int y=column.naturalSurfaceY();
            int natural=level.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1;
            require(y==natural,"Base height convention differs");
            double strength=column.naturalStrengthAt(y);
            require(strength>0.4,"Weak/absent nexus");
            require(column.naturalStrengthAt(y+64)==strength/2,"Vertical half strength failed");
            require(!api.field().sample(x,y+128,z).canDrawPower(),"Power accessible outside vertical field");
            var quote=api.power().quoteAt(x,y,z).orElseThrow();
            current=api.power();
            String phase=System.getProperty("skysleylines.probePhase","fresh");
            if(phase.equals("fresh")) {
                require(quote.account().raw().equals(MagicAmount.of(quote.capacity())),"Fresh nexus not full");
                require(current.convertAt(x,y,z,AETHER,MagicAmount.ONE,DENIED).status()==PowerResult.Status.CANCELLED,"Cancelled payment committed");
                require(current.convertAt(x,y,z,AETHER,MagicAmount.of(5),PLANT).succeeded(),"Aether conversion rejected");
                require(current.quoteAt(x,y,z).orElseThrow().account().currency(AETHER)==5000,"Five raw did not credit 5000 aether");
                require(current.convertAt(x,y,z,ELECTRIC,MagicAmount.of(2),PLANT).succeeded(),"Electric conversion rejected");
                require(current.returnToRawAt(x,y,z,ELECTRIC,10000,PLANT).succeeded(),"Return rejected");
                require(current.spendAt(x,y,z,AETHER,1000,PLANT).succeeded(),"Aether consumer rejected");
                require(current.convertAt(x,y,z,AETHER,MagicAmount.of(quote.capacity()),PLANT).status()==PowerResult.Status.INSUFFICIENT_RAW,"Competing consumer overspent");
                require(current.convertAt(x,y,z,MANA,MagicAmount.of(quote.capacity()-6),PLANT).succeeded(),"Mage paradigm conversion rejected");
                var a=current.quoteAt(x,y,z).orElseThrow().account();
                require(a.raw().isZero()&&a.total().equals(MagicAmount.of(quote.capacity()-1)),"Shared capacity arithmetic differs");
                require(posts==5&&reentrancies==5,"Pre/post event count differs");
            } else {
                require(quote.account().currency(AETHER)==4000,"Reload discarded aether");
                require(quote.account().currency(ELECTRIC)==10000,"Reload discarded electric");
                require(quote.account().currency(MANA)==(quote.capacity()-6)*10L,"Reload discarded mana");
                require(current.rates().get(AETHER).equals(MagicAmount.of(1000)),"Configured rate silently replaced saved world rate");
                if(Boolean.getBoolean("skysleylines.probeMigrate")) {
                    var path=server.getWorldPath(LevelResource.ROOT).resolve("data/skysleylines.dat");
                    long revision=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()).getCompound("data").getLong("revision");
                    var beforeMigration=current.quoteAt(x,y,z).orElseThrow().account();
                    require(server.getCommands().getDispatcher().execute("skysleylines rates preview",server.createCommandSourceStack())==1,"Migration preview failed");
                    require(server.getCommands().getDispatcher().execute("skysleylines rates apply "+revision,server.createCommandSourceStack())==1,"Migration apply failed");
                    var afterMigration=current.quoteAt(x,y,z).orElseThrow().account();
                    require(afterMigration.currency(AETHER)==8000,"Migration did not redenominate aether");
                    require(afterMigration.total().equals(beforeMigration.total()),"Migration changed raw-equivalent value");
                    require(current.rates().get(AETHER).equals(MagicAmount.of(2000)),"Migration did not install configured rate");
                }
            }
            var meter = LeyInspection.snapshot(level, new BlockPos(x, y, z));
            require(meter.naturalStrength() == strength, "Meter differs from field sampler");
            var balance = current.quoteAt(x, y, z).orElseThrow().account();
            require(meter.reserve().raw().equals(balance.raw().toString())
                    && meter.reserve().total().equals(balance.total().toString()), "Meter differs from ledger quote");
            require(meter.reserve().currencies().stream().anyMatch(c -> c.id().equals(AETHER.toString())
                    && c.balance() == balance.currency(AETHER) && c.installed()), "Meter lost the aether row");
            require(api.field().sample(x,y,z).naturalStrength()==strength,"Consumption changed natural field");
            require(api.power().quoteAt(x,y+128,z).isEmpty(),"Vertical boundary quote exists");
            boolean lineOnlyChecked=false;
            for(int q=0;q<4096&&!lineOnlyChecked;q+=256) {
                var line=api.field().nearestLine(q,0,256);
                if(line.isEmpty()) continue;
                int lx=(int)Math.round(line.get().x()), lz=(int)Math.round(line.get().z());
                var lc=api.field().sampleColumn(lx,lz);
                if(lc.active()&&lc.lineStrength()>0&&lc.nexus().isEmpty()) {
                    require(api.power().quoteAt(lx,lc.naturalSurfaceY(),lz).isEmpty(),"Line alone provided shared power");
                    var lineMeter = LeyInspection.snapshot(level, new BlockPos(lx, lc.naturalSurfaceY(), lz));
                    require(lineMeter.active() && lineMeter.lineStrength() > 0 && lineMeter.reserve() == null,
                            "Meter did not distinguish a line-only field");
                    System.out.println("LEY_PROBE_LINE " + lx + " " + lc.naturalSurfaceY() + " " + lz);
                    lineOnlyChecked=true;
                }
            }
            require(lineOnlyChecked,"No line-only control was exercised");
            var nether=LeyLinesApi.forLevel(server.getLevel(Level.NETHER));
            require(!nether.field().sample(0,64,0).active(),"Nether active unexpectedly");
            var netherMeter = LeyInspection.snapshot(server.getLevel(Level.NETHER), new BlockPos(0, 64, 0));
            require(!netherMeter.active() && netherMeter.reserve() == null, "Inactive dimension produced a meter account");
            boolean rejected=CompletableFuture.supplyAsync(()->{
                try {api.power().quoteAt(x,y,z);return false;}catch(IllegalStateException expected){return true;}
            }).join();
            require(rejected,"Worker reserve access accepted");
            require(CompletableFuture.supplyAsync(()->api.field().sample(x,y,z)).join().naturalStrength()==strength,"Worker sampling differs");
            int after=level.getChunkSource().getLoadedChunksCount();
            require(before==after,"Ley queries loaded chunks");
            String result="phase="+phase+"\nnexusX="+x+"\nnexusY="+y+"\nnexusZ="+z+"\nstrength="+strength
                    +"\ncapacity="+quote.capacity()+"\naccountBefore="+quote.account()+"\naccountAfter="+api.power().quoteAt(x,y,z).orElseThrow().account()
                    +"\nloadedChunksBefore="+before+"\nloadedChunksAfter="+after+"\nspacing="+api.profile().spacingBlocks()+"\npass=true\n";
            Files.writeString(Path.of("ley-probe-"+phase+".properties"),result);
            System.out.println("LEY_RUNTIME_PROBE_PASS "+phase+" "+x+","+y+","+z);
        } catch(Throwable failure) {
            failure.printStackTrace();
            try {Files.writeString(Path.of("ley-probe-FAILED.txt"),failure.toString());}catch(Exception ignored){}
        } finally { server.halt(false); }
    }
}
