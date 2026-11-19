package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Structural checks against the exact loaded Forge classes, not a Mixin application test. */
class RuntimeMixinContractTest {
    private static final String BASE="org/spongepowered/asm/mixin/";
    private static final String INJECT=BASE+"injection/";

    @TestFactory Stream<DynamicTest> everyConfiguredMixinMatchesItsActualTargets() throws Exception {
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("oculus.mixins.json"));
            var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            var config=JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(config.get("required").getAsBoolean());
            assertEquals(1,config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            String prefix=config.get("package").getAsString().replace('.','/')+"/";
            var tests=new ArrayList<DynamicTest>();
            for(var name:config.getAsJsonArray("client")) tests.add(DynamicTest.dynamicTest(name.getAsString(),
                ()->verify(read(prefix+name.getAsString()))));
            assertEquals(15,tests.size(),"Update audit coverage deliberately when mixins change");
            return tests.stream();
        }
    }

    @Test void negativeControlRejectsTheOldFloatCameraCallback() throws Exception {
        var node=read("dev/oculusforge/mixin/CameraMixin");
        var callback=node.methods.stream().filter(m->m.name.equals("oculus$captureEye")).findFirst().orElseThrow();
        assertTrue(callback.desc.contains("Lnet/minecraft/client/DeltaTracker;"));
        callback.desc=callback.desc.replace("Lnet/minecraft/client/DeltaTracker;","F");
        assertThrows(AssertionError.class,()->verify(node));
    }

    @Test void negativeControlRejectsMissingRedirectSites() throws Exception {
        var node=read("dev/oculusforge/mixin/SkyRendererMixin");
        var redirect=annotation(node.methods.getLast().visibleAnnotations,INJECT+"Redirect");
        if(redirect==null)redirect=annotation(node.methods.getLast().invisibleAnnotations,INJECT+"Redirect");
        assertNotNull(redirect);
        var at=(AnnotationNode)value(redirect,"at");
        int index=at.values.indexOf("target");assertTrue(index>=0);
        at.values.set(index+1,((String)at.values.get(index+1)).replace("setPipeline(","missingPipeline("));
        assertThrows(AssertionError.class,()->verify(node));
    }

    private void verify(ClassNode mixin) throws Exception {
        var meta=annotation(all(mixin.visibleAnnotations,mixin.invisibleAnnotations),BASE+"Mixin");
        assertNotNull(meta,mixin.name);assertEquals(Boolean.FALSE,value(meta,"remap"));
        var targets=new ArrayList<String>();
        Object classes=value(meta,"value");
        if(classes instanceof List<?> list)for(Object target:list)targets.add(((Type)target).getInternalName());
        for(String target:strings(value(meta,"targets")))targets.add(target.replace('.','/'));
        assertFalse(targets.isEmpty(),mixin.name);
        for(String name:targets) {
            var target=read(name);
            for(var field:mixin.fields) {
                var annotations=all(field.visibleAnnotations,field.invisibleAnnotations);
                if(annotation(annotations,BASE+"Shadow")!=null) {
                    var actual=field(target,field.name);assertNotNull(actual,name+" shadow "+field.name);
                    assertEquals(actual.desc,field.desc);assertEquals(actual.access&Opcodes.ACC_STATIC,field.access&Opcodes.ACC_STATIC);
                    if(annotation(annotations,BASE+"Final")!=null)assertNotEquals(0,actual.access&Opcodes.ACC_FINAL);
                }
            }
            for(var handler:mixin.methods)for(var ann:all(handler.visibleAnnotations,handler.invisibleAnnotations)) {
                String kind=ann.desc;
                if(kind.equals("L"+BASE+"gen/Invoker;")) {
                    var matches=select(target,List.of((String)value(ann,"value")));
                    assertEquals(1,matches.size(),handler.name);assertEquals(matches.getFirst().desc,handler.desc);
                } else if(kind.equals("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;")) {
                    for(var actual:select(target,strings(value(ann,"method")))) {
                        var args=new ArrayList<>(List.of(Type.getArgumentTypes(actual.desc)));
                        args.add(Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation"));
                        assertArrayEquals(args.toArray(Type[]::new),Type.getArgumentTypes(handler.desc),handler.name);
                        assertEquals(Type.getReturnType(actual.desc),Type.getReturnType(handler.desc));
                        sameStatic(actual,handler);
                    }
                } else if(kind.startsWith("L"+INJECT)&&!kind.equals("L"+INJECT+"At;")) {
                    checkInjector(target,handler,ann);
                }
            }
        }
    }

    private void checkInjector(ClassNode target,MethodNode handler,AnnotationNode ann) {
        var selected=select(target,strings(value(ann,"method")));int count=0;
        String kind=ann.desc.substring(ann.desc.lastIndexOf('/')+1,ann.desc.length()-1);
        for(var actual:selected) {
            sameStatic(actual,handler);
            var handlerArgs=Type.getArgumentTypes(handler.desc);var actualArgs=Type.getArgumentTypes(actual.desc);
            if(kind.equals("ModifyConstant")) {
                Object constants=value(ann,"constant");
                var constant=constants instanceof List<?> list?(AnnotationNode)list.getFirst():(AnnotationNode)constants;
                int number=(Integer)Objects.requireNonNull(value(constant,"intValue"));
                for(var instruction:actual.instructions)if(integer(instruction,number))count++;
                assertEquals("(I)I",handler.desc);
                continue;
            }
            Object atValue=value(ann,"at");
            var points=atValue instanceof List<?> list?list:List.of(Objects.requireNonNull(atValue));
            for(Object point:points) {
                var at=(AnnotationNode)point;String position=(String)value(at,"value");
                if(kind.equals("Inject")) {
                    assertEquals(Type.VOID_TYPE,Type.getReturnType(handler.desc));
                    int callback=-1;
                    for(int i=0;i<handlerArgs.length;i++)if(handlerArgs[i].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.CallbackInfo"))callback=i;
                    assertTrue(callback>=0,handler.name);assertEquals(handlerArgs.length-1,callback);
                    assertTrue(callback==0||callback==actualArgs.length,handler.name+" target argument count");
                    for(int i=0;i<callback;i++)assertEquals(actualArgs[i],handlerArgs[i],handler.name+" target argument "+i);
                    String expected=Type.getReturnType(actual.desc).equals(Type.VOID_TYPE)?"CallbackInfo":"CallbackInfoReturnable";
                    assertEquals("org.spongepowered.asm.mixin.injection.callback."+expected,handlerArgs[callback].getClassName());
                } else if(kind.equals("ModifyVariable")) {
                    assertEquals(1,handlerArgs.length);assertEquals(handlerArgs[0],Type.getReturnType(handler.desc));
                    assertEquals(Boolean.TRUE,value(ann,"argsOnly"),"Only argument-variable injections are audited");
                    assertTrue(Arrays.asList(actualArgs).contains(handlerArgs[0]),handler.name);
                }
                if(position.equals("HEAD"))count++;
                else if(position.equals("RETURN")) {
                    for(var instruction:actual.instructions)if(instruction.getOpcode()>=Opcodes.IRETURN&&instruction.getOpcode()<=Opcodes.RETURN)count++;
                } else if(position.equals("INVOKE")) {
                    String targetRef=(String)value(at,"target");assertNotNull(targetRef);
                    int semicolon=targetRef.indexOf(';');String owner=targetRef.substring(1,semicolon);
                    String method=targetRef.substring(semicolon+1);int paren=method.indexOf('(');
                    String methodName=method.substring(0,paren),descriptor=method.substring(paren);
                    for(var instruction:actual.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)
                        &&call.name.equals(methodName)&&call.desc.equals(descriptor)) {
                        count++;
                        if(kind.equals("Redirect")) {
                            var consumed=new ArrayList<Type>();
                            if(call.getOpcode()!=Opcodes.INVOKESTATIC)consumed.add(Type.getObjectType(owner));
                            consumed.addAll(List.of(Type.getArgumentTypes(descriptor)));
                            assertEquals(Type.getReturnType(descriptor),Type.getReturnType(handler.desc));
                            assertTrue(handlerArgs.length==consumed.size()||handlerArgs.length==consumed.size()+actualArgs.length,handler.name);
                            for(int i=0;i<consumed.size();i++)assertEquals(consumed.get(i),handlerArgs[i],handler.name+" redirected argument "+i);
                            if(handlerArgs.length>consumed.size())for(int i=0;i<actualArgs.length;i++)
                                assertEquals(actualArgs[i],handlerArgs[consumed.size()+i],handler.name+" enclosing argument "+i);
                        } else if(kind.equals("ModifyArg")) {
                            int index=(Integer)value(ann,"index");
                            assertArrayEquals(new Type[]{Type.getArgumentTypes(descriptor)[index]},handlerArgs);
                            assertEquals(handlerArgs[0],Type.getReturnType(handler.desc));
                        } else fail("Unaudited INVOKE injector: "+kind);
                    }
                } else fail("Unaudited injection point: "+position);
            }
        }
        int require=value(ann,"require") instanceof Integer n?n:1;
        assertTrue(count>=require,handler.name+" has "+count+" sites, requires "+require);
        if(value(ann,"expect") instanceof Integer expected)assertEquals(expected,count,handler.name);
        if(value(ann,"allow") instanceof Integer allow&&allow>=1)assertTrue(count<=allow,handler.name+" exceeds allowed sites");
    }

    private boolean integer(AbstractInsnNode insn,int expected) {
        if(insn instanceof IntInsnNode number)return number.operand==expected;
        if(insn instanceof LdcInsnNode number)return Integer.valueOf(expected).equals(number.cst);
        return insn.getOpcode()>=Opcodes.ICONST_M1&&insn.getOpcode()<=Opcodes.ICONST_5&&insn.getOpcode()-Opcodes.ICONST_0==expected;
    }

    private void sameStatic(MethodNode actual,MethodNode handler) {
        assertEquals(actual.access&Opcodes.ACC_STATIC,handler.access&Opcodes.ACC_STATIC,handler.name+" staticness");
    }
    private List<MethodNode> select(ClassNode target,List<String> selectors) {
        assertFalse(selectors.isEmpty(),target.name);var matches=new ArrayList<MethodNode>();
        for(String selector:selectors) {
            var found=target.methods.stream().filter(m->selector.equals(m.name)||selector.equals(m.name+m.desc)).toList();
            assertFalse(found.isEmpty(),target.name+" missing "+selector);matches.addAll(found);
        }
        return matches;
    }
    private FieldNode field(ClassNode target,String name) throws Exception {
        var found=target.fields.stream().filter(f->f.name.equals(name)).findFirst();
        return found.isPresent()?found.get():target.superName==null?null:field(read(target.superName),name);
    }
    private ClassNode read(String internalName) throws Exception {
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(internalName+".class"),internalName)) {
            var node=new ClassNode(Opcodes.ASM9);new ClassReader(input).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return node;
        }
    }
    private List<AnnotationNode> all(List<AnnotationNode> a,List<AnnotationNode> b) {
        var result=new ArrayList<AnnotationNode>();if(a!=null)result.addAll(a);if(b!=null)result.addAll(b);return result;
    }
    private AnnotationNode annotation(List<AnnotationNode> annotations,String name) {
        return annotations==null?null:annotations.stream().filter(a->a.desc.equals("L"+name+";")).findFirst().orElse(null);
    }
    private Object value(AnnotationNode annotation,String name) {
        if(annotation==null||annotation.values==null)return null;
        for(int i=0;i<annotation.values.size();i+=2)if(name.equals(annotation.values.get(i)))return annotation.values.get(i+1);
        return null;
    }
    private List<String> strings(Object value) {
        if(value instanceof String s)return List.of(s);
        if(value instanceof List<?> list)return list.stream().map(String.class::cast).toList();
        return List.of();
    }
}
