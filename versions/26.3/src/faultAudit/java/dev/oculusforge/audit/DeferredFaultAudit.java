package dev.oculusforge.audit;

import com.mojang.logging.LogUtils;
import dev.oculusforge.render.pipeline.*;
import dev.oculusforge.render.program.*;
import dev.oculusforge.render.world.*;
import dev.oculusforge.port.RestartableRenderPass;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.*;
import net.minecraft.client.Minecraft;

/** SOURCE AUDIT ONLY. Never part of main sources, normal launches or JARs. */
public final class DeferredFaultAudit {
    private static final org.slf4j.Logger LOG=LogUtils.getLogger();
    private static final Set<String> FIRED=new HashSet<>();
    private static String active,pending,backend;
    private static int views,draws,copies;
    private static boolean retired;
    private DeferredFaultAudit(){}

    public static void begin(String pack,boolean indirect) {
        active=switch(pack){
            case "oculus-26.3-fault-allocate"->"allocate";
            case "oculus-26.3-fault-draw"->"draw";
            case "oculus-26.3-fault-precommit"->"precommit";
            case "oculus-26.3-fault-copy"->"copy";
            default->null;
        };
        backend=indirect?"indirect":"separate";
        if(FIRED.contains(active+"/"+backend))active=null;
        views=draws=copies=0;
    }
    public static void end(){active=null;}
    public static void beforeView(){if(active!=null&&++views==2&&active.equals("allocate"))fail();}
    public static void afterDraw(){if(active!=null&&++draws==1&&active.equals("draw"))fail();}
    public static void beforeCommit(){if("precommit".equals(active))fail();}
    public static void afterCopy(){if(active!=null&&++copies==1&&active.equals("copy"))fail();}
    private static void fail() {
        String point=active;FIRED.add(point+"/"+backend);pending=point+"/"+backend;active=null;retired=false;
        LOG.warn("OCULUS-FAULT-AUDIT INJECT point={} views={} completedDraws={} issuedCopies={} hdr={}",
            pending,views,draws,copies,WorldSceneCapture.isMain(Minecraft.getInstance().gameRenderer.mainRenderTarget()));
        throw new IllegalStateException("OCULUS_AUDIT_INJECTED_"+point);
    }
    public static void afterBoundary(ShaderPipelineManager manager,RenderPass pass) {
        if(pending==null||retired)return;
        boolean usable=pass instanceof RestartableRenderPass restartable&&restartable.isUsable();
        boolean held=WorldSceneCapture.isMain(Minecraft.getInstance().gameRenderer.mainRenderTarget());
        if(manager.activePipeline()!=null||!usable||!held||DeferredScratchTargets.liveCount()!=0)
            throw new IllegalStateException("AUDIT: failed deferred boundary did not retire/restore safely");
        retired=true;
        LOG.info("OCULUS-FAULT-AUDIT RETIRED point={} mainPassUsable={} hdrLeaseHeld={} worldTexturesBeforeEnd={} scratch={}",
            pending,usable,held,WorldRenderTargets.liveCount(),DeferredScratchTargets.liveCount());
    }
    public static void afterScene() {
        if(pending==null)return;
        if(!retired||WorldRenderTargets.liveCount()!=0||DeferredScratchTargets.liveCount()!=0
            ||PackFinalProgram.liveUniformRings()!=0||WorldMatrixBuffer.liveCount()!=0||PackGpuTexture.liveCount()!=0)
            throw new IllegalStateException("AUDIT: failed deferred generation still owns resources after scene end");
        LOG.info("OCULUS-FAULT-AUDIT CLEAN point={} worldTextures={} deferredTextures={} uniformRings={} packTextures={}",
            pending,WorldRenderTargets.liveCount(),DeferredScratchTargets.liveCount(),
            PackFinalProgram.liveUniformRings()+WorldMatrixBuffer.liveCount(),PackGpuTexture.liveCount());
        pending=null;
    }
}
