package dev.oculusforge.shaderpack;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderOptionOverridesTest {
    private static byte[] bytes(String source) { return source.getBytes(StandardCharsets.UTF_8); }
    private static final class MemoryPack implements PackFileSystem {
        final Map<VirtualPath,byte[]> files=new LinkedHashMap<>(); int reads; boolean closed;
        MemoryPack(String... entries) throws Exception {
            for(int i=0;i<entries.length;i+=2)files.put(VirtualPath.parse(entries[i]),bytes(entries[i+1]));
        }
        public Set<VirtualPath> files(){return Set.copyOf(files.keySet());}
        public byte[] read(VirtualPath path){assertFalse(closed);reads++;return files.get(path).clone();}
        public void close(){closed=true;}
    }
    @Test void emptySelectionDoesNotScanOrChangeOwnership() throws Exception {
        var pack=new MemoryPack("shaders/main.fsh","invalid source not scanned");
        assertSame(pack,ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,Map.of()).pack());
        assertEquals(0,pack.reads);assertFalse(pack.closed);
    }
    @Test void switchesAndNumericOptionsRewriteAllDeclarationsWithoutEditingOriginals() throws Exception {
        String input="//#define FEATURE // description\r\n#define MODE 1 //[0 1 2]\r\n#ifdef FEATURE\r\nint yes=MODE;\r\n#endif\r\n";
        var pack=new MemoryPack("shaders/a.glsl",input,"shaders/b.glsl",input);
        var applied=ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,Map.of("FEATURE","true","MODE","2"));
        assertEquals(Set.of("FEATURE"),applied.switches());
        for(var path:pack.files()) {
            String changed=applied.pack().readUtf8(path);
            assertTrue(changed.startsWith("#define FEATURE // description\n#define MODE 2 //[0 1 2]\n"));
            assertTrue(GlslPreprocessor.preprocess(changed,Map.of()).contains("int yes=2;"));
            assertEquals(input,new String(pack.files.get(path),StandardCharsets.UTF_8));
            assertEquals(input.split("\\R",-1).length,changed.split("\\R",-1).length);
        }
        applied.pack().close();assertFalse(pack.closed,"View must not close the borrowed owner");
    }
    @Test void disablingIsAbsenceNotAZeroOrFalseDefinition() throws Exception {
        var pack=new MemoryPack("shaders/a.glsl","#define FEATURE\n#ifdef FEATURE\nwrong\n#else\nright\n#endif");
        String changed=ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,Map.of("FEATURE","false")).pack().readUtf8(VirtualPath.parse("shaders/a.glsl"));
        assertTrue(changed.startsWith("//#define FEATURE"));
        assertEquals(java.util.List.of("right"),GlslPreprocessor.preprocess(changed,Map.of()).lines().map(String::trim)
            .filter(line->!line.isEmpty()&&!line.startsWith("//")).toList());
    }
    @Test void requestedValuesMustBeDeclaredAndUnambiguous() throws Exception {
        var pack=new MemoryPack("shaders/a.glsl","#define MODE 1 //[0 1]\n#define PRIVATE 2\n#define UNUSED\n",
            "shaders/b.glsl","#define CONFLICT 1 //[1 2]\n","shaders/c.glsl","#define CONFLICT 2 //[1 2]\n");
        for(var selected:java.util.List.of(Map.of("UNKNOWN","1"),Map.of("MODE","2"),Map.of("PRIVATE","1"),Map.of("UNUSED","false"),Map.of("CONFLICT","1")))
            assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,selected));
    }
    @Test void blockCommentsAndNonShaderFilesCannotIntroduceOptions() throws Exception {
        var pack=new MemoryPack("shaders/a.glsl","/*\n#define FAKE 1 //[1 2]\n*/\n// #define COMMENTED 1 //[1 2]\n#define REAL 0 //[0 1]\n",
            "shaders/lang/en_us.lang","#define TRANSLATION 0 //[0 1]");
        for(String option:java.util.List.of("FAKE","COMMENTED","TRANSLATION"))
            assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,Map.of(option,"1")));
        assertTrue(ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,Map.of("REAL","1")).pack().readUtf8(VirtualPath.parse("shaders/a.glsl")).contains("#define REAL 1"));
    }
    @Test void valuesCannotInjectSourcesPathsOrRendererIdentity() {
        for(String value:java.util.List.of("1\n#error bad","1;discard","a/b","1 2","#define","\\u0010","x".repeat(65)))
            assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.validateValues(Map.of("MODE",value)));
        for(String name:java.util.List.of("../MODE","MC_VERSION","IS_IRIS","IRIS_FEATURE","OCULUS_TEST","GL_EXT_test","__VERSION__","DISTANT_HORIZONS"))
            assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.validateValues(Map.of(name,"1")));
    }
    @Test void settingsDecodePropertiesButRejectDuplicateAliasesAndBudgets() throws Exception {
        assertEquals(Map.of("MODE","2","FEATURE","false"),ShaderOptionOverrides.parse(bytes("MODE=2\nFEATURE=false\n")));
        assertEquals(Map.of("MODE","2"),ShaderOptionOverrides.parse(bytes("M\\u004fDE=2")));
        for(String source:java.util.List.of("MODE=1\nMODE=2","MODE=1\nM\\u004fDE=2","MODE=\\uGGGG"," ".repeat(16385)))
            assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.parse(bytes(source)));
        var many=new LinkedHashMap<String,String>();for(int i=0;i<129;i++)many.put("OPTION_"+i,"1");
        assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.validateValues(many));
    }
    @Test void sidecarUsesDescriptorFilenameNotIdAndDoesNotWrite(@TempDir Path root) throws Exception {
        var descriptor=new ShaderPackDescriptor("misleading-id","Display",root.resolve("actual.zip"),ShaderPackDescriptor.Kind.ZIP);
        assertEquals(Map.of(),ShaderOptionOverrides.readSidecar(descriptor));
        Path sidecar=root.resolve("actual.zip.txt");Files.writeString(sidecar,"MODE=2\n");
        assertEquals(Map.of("MODE","2"),ShaderOptionOverrides.readSidecar(descriptor));
        assertEquals("MODE=2\n",Files.readString(sidecar));
        var bad=new ShaderPackDescriptor("bad","Bad",root.resolve("directory"),ShaderPackDescriptor.Kind.DIRECTORY);
        Files.createDirectory(root.resolve("directory.txt"));
        assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.readSidecar(bad));
    }
    @Test void scannedSourceSnapshotAndReturnedBytesCannotBeMutated() throws Exception {
        var pack=new MemoryPack("shaders/a.glsl","#define MODE 1 //[1 2]\n");var path=VirtualPath.parse("shaders/a.glsl");
        var view=ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,Map.of("MODE","2")).pack();
        pack.files.put(path,bytes("changed after discovery"));
        byte[] first=view.read(path);first[0]=0;
        assertTrue(view.readUtf8(path).startsWith("#define MODE 2"));
    }
    @Test void sourceDiscoveryIsBounded() throws Exception {
        var blank=new MemoryPack("shaders/a.glsl","\n".repeat(20000)+"//#define FLAG\n#ifdef FLAG\n#endif\n");
        assertTrue(ShaderOptionOverrides.apply(blank,ShaderPackLimits.DEFAULT,Map.of("FLAG","true")).pack()
            .readUtf8(VirtualPath.parse("shaders/a.glsl")).contains("#define FLAG"));
        var pack=new MemoryPack("shaders/a.glsl","#define MODE 1 //[1 2]\n".repeat(4));
        var limits=new ShaderPackLimits(1024,4096,16,20,4,16,32);
        assertThrows(ShaderPackException.class,()->ShaderOptionOverrides.apply(pack,limits,Map.of("MODE","2")));
        String slider=java.util.stream.IntStream.range(0,181).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(" "));
        var large=new MemoryPack("shaders/a.glsl","#define CLOUD_ALT 0 //["+slider+"]\n");
        assertTrue(ShaderOptionOverrides.apply(large,ShaderPackLimits.DEFAULT,Map.of("CLOUD_ALT","180")).pack()
            .readUtf8(VirtualPath.parse("shaders/a.glsl")).contains("#define CLOUD_ALT 180"));
    }
    @Test void propertiesSeeExplicitValuesWithoutGloballyPredefiningShaderMacros() {
        var environment=ShaderOptionOverrides.environment(Map.of("MC_VERSION","260300"),Map.of("FEATURE","false","MODE","2","LITERAL","false"),Set.of("FEATURE"));
        assertEquals(Map.of("MC_VERSION","260300"),environment);
        var properties=ShaderOptionOverrides.forProperties(environment);
        assertEquals("2",properties.get("MODE"));assertEquals("false",properties.get("LITERAL"));assertFalse(properties.containsKey("FEATURE"));
        assertTrue(ShaderOptionOverrides.knownAbsent(properties,"FEATURE"));assertFalse(ShaderOptionOverrides.knownAbsent(properties,"LITERAL"));
        assertThrows(UnsupportedOperationException.class,()->properties.put("MODE","3"));
    }
    @Test void valuedBooleanMacrosRetainTheirTokensRatherThanBecomingSwitches() throws Exception {
        var pack=new MemoryPack("shaders/a.glsl","#define LITERAL true //[false true]\n");
        var applied=ShaderOptionOverrides.apply(pack,ShaderPackLimits.DEFAULT,Map.of("LITERAL","false"));
        assertTrue(applied.switches().isEmpty());assertTrue(applied.pack().readUtf8(VirtualPath.parse("shaders/a.glsl")).startsWith("#define LITERAL false"));
    }
}
