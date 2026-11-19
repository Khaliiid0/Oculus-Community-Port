package dev.oculusforge.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RendererSettingsTest {
    @TempDir Path directory;
    private Path path(){return directory.resolve(RendererSettingsStore.FILE_NAME);}
    private RendererSettingsStore store(){return new RendererSettingsStore(path());}
    private RendererBootstrap.Snapshot resolve(Map<String,String> overrides){
        return RendererBootstrap.resolve(this::path,overrides::get);
    }

    @Test void missingPreferenceDefaultsOffWithoutCreatingAnything() throws Exception {
        assertFalse(store().load());assertFalse(Files.exists(path()));
        assertEquals(new RendererBootstrap.Snapshot(false,false,false,false,""),resolve(Map.of()));
    }
    @Test void roundTripDoesNotChangeShaderSelectionFile() throws Exception {
        var other=directory.resolve("oculus.properties");
        Files.writeString(other,"shaderpack=original.zip\nshaders.enabled=true\n");
        store().save(true);assertTrue(store().load());
        store().save(false);assertFalse(store().load());
        assertEquals("shaderpack=original.zip\nshaders.enabled=true\n",Files.readString(other));
        try(var files=Files.list(directory)){assertEquals(2,files.count(),"No temporary file left behind");}
    }
    @Test void malformedBooleanAndUnicodeReject() throws Exception {
        for(String value:List.of("yes","1","TRUE","tru","\\uQQQQ")){
            Files.writeString(path(),"worldRenderer.enabled="+value);
            assertThrows(IOException.class,()->store().load());
            assertFalse(resolve(Map.of()).extended());
        }
    }
    @Test void duplicateAndEscapedAliasReject() throws Exception {
        for(String key:List.of("worldRenderer.enabled","worldRenderer.\\u0065nabled")){
            Files.writeString(path(),"worldRenderer.enabled=true\n"+key+"=false\n");
            assertThrows(IOException.class,()->store().load());
        }
    }
    @Test void boundedReaderRejectsOversize() throws Exception {
        Files.writeString(path(),"#".repeat(4097));
        assertThrows(IOException.class,()->store().load());
        var result=resolve(Map.of());
        assertFalse(result.extended());assertFalse(result.hdr());assertFalse(result.issue().isEmpty());
    }
    @Test void directoriesAreNeverReadOrReplaced() throws Exception {
        Files.createDirectory(path());
        assertThrows(IOException.class,()->store().load());
        assertThrows(IOException.class,()->store().save(true));
        assertTrue(Files.isDirectory(path()));
    }
    @Test void explicitSaveRepairsMalformedOwnedFile() throws Exception {
        Files.writeString(path(),"worldRenderer.enabled=broken");
        store().save(true);assertTrue(store().load());
    }
    @Test void savedOnEnablesBothModesTogether() throws Exception {
        store().save(true);
        assertEquals(new RendererBootstrap.Snapshot(true,true,true,false,""),resolve(Map.of()));
    }
    @Test void decisionNeverChangesUntilNewStartupSnapshot() throws Exception {
        store().save(true);var active=resolve(Map.of());
        store().save(false);
        assertTrue(active.extended());assertTrue(active.hdr());
        assertTrue(active.restartRequired(false));
        assertFalse(resolve(Map.of()).extended());
    }
    @Test void bothOverridesSkipGamePathEntirely() {
        var result=RendererBootstrap.resolve(()->{throw new AssertionError("Must not resolve game path");},
            Map.of(RendererBootstrap.EXTENDED,"false",RendererBootstrap.HDR,"true")::get);
        assertFalse(result.extended());assertTrue(result.hdr());assertTrue(result.overridden());
    }
    @Test void explicitFalseOverrideWinsOverSavedOn() throws Exception {
        store().save(true);
        var result=resolve(Map.of(RendererBootstrap.EXTENDED,"false"));
        assertFalse(result.extended());assertTrue(result.hdr());assertTrue(result.overridden());
        assertFalse(result.restartRequired(true),"Launch overrides cannot be changed by a restart preference alone");
    }
    @Test void unavailableStartupPathFailsClosed() {
        var result=RendererBootstrap.resolve(()->{throw new IllegalStateException("Too early");},key->null);
        assertFalse(result.extended());assertFalse(result.hdr());assertFalse(result.issue().isEmpty());
    }
    @Test void invalidOverrideNeverBecomesTruthy() {
        var result=resolve(Map.of(RendererBootstrap.EXTENDED,"yes",RendererBootstrap.HDR,"TRUE"));
        assertFalse(result.extended());assertFalse(result.hdr());assertTrue(result.overridden());
        assertFalse(result.issue().isEmpty());
    }
    @Test void layoutFieldsRemainStaticFinalAndMatchOneBootstrapDecision() throws Exception {
        for(var field:List.of(dev.oculusforge.render.program.TerrainMeshMode.class.getField("EXTENDED"),
            dev.oculusforge.render.world.SceneColorFormat.class.getField("ENABLED"))){
            assertTrue(Modifier.isStatic(field.getModifiers()));assertTrue(Modifier.isFinal(field.getModifiers()));
        }
        assertEquals(RendererBootstrap.ACTIVE.extended(),dev.oculusforge.render.program.TerrainMeshMode.EXTENDED);
        assertEquals(RendererBootstrap.ACTIVE.hdr(),dev.oculusforge.render.world.SceneColorFormat.ENABLED);
    }
}
