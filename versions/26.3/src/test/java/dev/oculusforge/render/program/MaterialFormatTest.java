package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class MaterialFormatTest {
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.ISO_8859_1);}
    private static MaterialFormatResource.Source source(String text){return ()->Optional.of(new ByteArrayInputStream(bytes(text)));}
    @Test void absentAndEmptyPropertiesDoNotAdvertisePbr() throws Exception {
        for(String text:List.of("","# absent format\n","alpha.cutout=16\nformat=  \n")) {
            assertEquals(MaterialFormat.DEFAULT,MaterialFormat.parse(bytes(text)));
            assertTrue(MaterialFormat.parse(bytes(text)).macros().isEmpty());
        }
        assertEquals(MaterialFormat.DEFAULT,MaterialFormatResource.read(Optional::<InputStream>empty));
    }
    @Test void versionlessAndExplicitLabHaveDifferentImmutableMacroSets() throws Exception {
        var generic=MaterialFormat.parse(bytes("format=lab-pbr"));
        var versioned=MaterialFormat.parse(bytes("format=lab-pbr/1.3"));
        assertEquals(Map.of("MC_TEXTURE_FORMAT_LAB_PBR",""),generic.macros());
        assertEquals(Set.of("MC_TEXTURE_FORMAT_LAB_PBR","MC_TEXTURE_FORMAT_LAB_PBR_1_3"),versioned.macros().keySet());
        assertThrows(UnsupportedOperationException.class,()->versioned.macros().clear());
    }
    @Test void declarationWhitespaceEscapesAndContinuationUsePropertiesRules() throws Exception {
        assertEquals(MaterialFormat.LAB_PBR_1_3,MaterialFormat.parse(bytes("format = lab-\\\npbr/1.3  \n")));
        assertEquals(MaterialFormat.LAB_PBR,MaterialFormat.parse(bytes("format=lab\\u002dpbr\n")));
    }
    @Test void unsupportedVersionsFormatsMalformedEscapesAndOversizeFailClosed() {
        for(String text:List.of("format=lab-pbr/1.2","format=lab-pbr/1.4","format=old-pbr","format=LAB-PBR",
            "format=lab-pbr/1.3/extra","format=\\uGGGG"," ".repeat(4097)))
            assertThrows(ShaderCompatibilityException.class,()->MaterialFormat.parse(bytes(text)),text.substring(0,Math.min(text.length(),50)));
    }
    @Test void atlasAndShaderFormatsUseOneParserWithoutLosingTheVersionTag() throws Exception {
        for(var format:MaterialFormat.values()) {
            String declaration=format==MaterialFormat.DEFAULT?"":"format="+format.label();
            assertEquals(format.atlasFormat(),StaticMaterialAtlasData.parseFormat(bytes(declaration)));
            assertEquals(format.atlasFormat().name(),SpecularAtlasData.parseFormat(bytes(declaration)).name());
        }
        assertEquals(MaterialFormat.LAB_PBR.atlasFormat(),MaterialFormat.LAB_PBR_1_3.atlasFormat());
        assertNotEquals(MaterialFormat.LAB_PBR.macros(),MaterialFormat.LAB_PBR_1_3.macros());
    }
    @Test void resourceReadsCloseTheirStreamAndStopAtTheBound() throws Exception {
        var consumed=new int[1];var closed=new boolean[1];
        var input=new InputStream(){public int read(){consumed[0]++;return ' ';}public void close(){closed[0]=true;}};
        assertThrows(ShaderCompatibilityException.class,()->MaterialFormatResource.read(()->Optional.of(input)));
        assertEquals(4097,consumed[0]);assertTrue(closed[0]);
    }
    @Test void resourceFailuresAreNotMissingFormatFallbacks() {
        assertThrows(ShaderCompatibilityException.class,()->MaterialFormatResource.read(()->{throw new IOException("unreadable winning resource");}));
        assertThrows(ShaderCompatibilityException.class,()->MaterialFormatResource.read(()->{throw new IllegalStateException("closed old resource manager");}));
        assertThrows(ShaderCompatibilityException.class,()->MaterialFormatResource.read(source("format=unknown")));
    }
    @Test void onlyTheWinningResourceManagerEntryIsRead() throws Exception {
        var count=new int[1];
        var resource=new net.minecraft.server.packs.resources.Resource(null,()->new ByteArrayInputStream(bytes("format=lab-pbr/1.3")));
        var manager=(net.minecraft.server.packs.resources.ResourceManager)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class<?>[]{net.minecraft.server.packs.resources.ResourceManager.class},(proxy,method,args)->{
                assertEquals("getResource",method.getName(),"Do not merge lower-priority texture.properties entries");
                assertEquals(MaterialFormatResource.LOCATION,args[0]);count[0]++;return Optional.of(resource);
            });
        assertEquals(MaterialFormat.LAB_PBR_1_3,MaterialFormatResource.read(manager));assertEquals(1,count[0]);
    }
    @Test void formatMismatchCannotActivateOrRestoreAnOldPreparation() throws Exception {
        for(var before:MaterialFormat.values())for(var after:MaterialFormat.values()) {
            if(before==after)assertDoesNotThrow(()->before.requireMatches(after));
            else assertThrows(ShaderCompatibilityException.class,()->before.requireMatches(after));
        }
    }
    @Test void queuedSelectionsReprepareForBothValidAndInvalidContextChanges() {
        assertTrue(MaterialFormatResource.matches(MaterialFormat.DEFAULT,source("")));
        assertFalse(MaterialFormatResource.matches(MaterialFormat.DEFAULT,source("format=lab-pbr")));
        assertFalse(MaterialFormatResource.matches(MaterialFormat.LAB_PBR,source("format=lab-pbr/1.3")));
        assertFalse(MaterialFormatResource.matches(MaterialFormat.LAB_PBR_1_3,source("format=unsupported")));
        assertTrue(MaterialFormatResource.matches(null,source("format=unsupported")),"Report invalid context rather than retry forever");
        assertFalse(MaterialFormatResource.matches(null,source("")),"Recover when an invalid resource becomes valid");
    }
    @Test void selectionErrorsRetainTheirContextRatherThanRetryingForever() {
        var request=new dev.oculusforge.client.ShaderReloadState.Request(3,dev.oculusforge.config.OculusConfig.DEFAULT);
        var failed=new dev.oculusforge.client.OculusClientService.ReloadPreparation(
            new dev.oculusforge.shaderpack.ShaderPackRepository.DiscoveryResult(List.of(),List.of()),null,"Selected pack disappeared",request,0,MaterialFormat.DEFAULT);
        assertTrue(MaterialFormatResource.matches(failed.materialFormat(),source("")));
        assertFalse(MaterialFormatResource.matches(failed.materialFormat(),source("format=lab-pbr")));
    }
    @Test void knownAbsentMaterialMacrosDoNotGuessUnknownShaderOptions() {
        String input="#ifdef MC_TEXTURE_FORMAT_LAB_PBR_1_3\nuniform.float.formatCode=2\n"
            +"#elif defined(MC_TEXTURE_FORMAT_LAB_PBR)\nuniform.float.formatCode=1\n#else\nuniform.float.formatCode=0\n#endif\n";
        for(var format:MaterialFormat.values()) {
            var environment=new HashMap<String,String>();environment.put("OCULUS_POST_PROCESSING","1");environment.putAll(format.macros());
            String selected=CustomUniformProperties.select(input,environment);
            assertTrue(selected.contains("uniform.float.formatCode="+format.ordinal()));
            assertFalse(selected.contains("UNRESOLVED"));
            assertTrue(CustomUniformProperties.select("#ifdef USER_SHADER_OPTION\nuniform.float.value=1\n#endif",environment).contains("UNRESOLVED"));
        }
    }
    @Test void realActivationGuardRunsBeforeGpuPreparationAndListenerUsesIncomingResources() throws Exception {
        var calls=new ArrayList<String>();
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dev/oculusforge/render/program/PackProgramCompiler.class"))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
                    if(!n.equals("compile"))return null;
                    return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf){
                        if(owner.endsWith("MaterialFormat")&&name.equals("requireMatches"))calls.add("format");
                        if(owner.endsWith("TextureBindingCapacity")&&name.equals("requireSupported"))calls.add("gpu");
                    }};
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(List.of("format","gpu"),calls);
        var listenerCalls=new ArrayList<String>();
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dev/oculusforge/client/ShaderPackReloadListener.class"))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf){if(name.equals("prepareReload"))listenerCalls.add(desc);}
                };}
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(1,listenerCalls.size());assertTrue(listenerCalls.getFirst().contains("ResourceManager;"));
    }
}
