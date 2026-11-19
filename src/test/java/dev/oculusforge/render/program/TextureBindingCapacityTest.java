package dev.oculusforge.render.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextureBindingCapacityTest {
    @Test void acceptsThirteenThroughSixteenOnlyWithTrackingAndDeviceSupport() throws Exception {
        for (int count = 13; count <= 16; count++) {
            TextureBindingCapacity.validate(count, count, 1, 16, 16, 16, 32);
            int requested = count;
            assertThrows(ShaderCompatibilityException.class,
                () -> TextureBindingCapacity.validate(requested, requested, 1, 12, 16, 16, 32));
        }
    }

    @Test void allThreeDeviceLimitsAndTheCpuBoundAreEnforced() {
        assertThrows(ShaderCompatibilityException.class, () -> TextureBindingCapacity.validate(17, 17, 0, 32, 32, 32, 64));
        assertThrows(ShaderCompatibilityException.class, () -> TextureBindingCapacity.validate(16, 16, 0, 16, 15, 16, 32));
        assertThrows(ShaderCompatibilityException.class, () -> TextureBindingCapacity.validate(16, 1, 16, 16, 16, 15, 32));
        assertThrows(ShaderCompatibilityException.class, () -> TextureBindingCapacity.validate(16, 16, 16, 16, 16, 16, 31));
        assertThrows(ShaderCompatibilityException.class, () -> TextureBindingCapacity.validate(16, 8, 8, 16, 16, 16, 15));
    }

    @Test void gpuProbePreparesSixteenDistinctImagesAndAVertexRead() throws Exception {
        var resource = getClass().getClassLoader().getResource("texture-units-16");
        assertNotNull(resource);
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("texture-probe", "Texture probe", java.nio.file.Path.of(resource.toURI()),
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var program = prepared.programs().getLast();
        assertEquals(16, program.fragment().samplers().size());
        assertEquals(java.util.stream.IntStream.range(0,16).boxed().toList(),
            program.fragment().samplers().stream().map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex).toList());
        assertTrue(program.vertexSource().contains("texture(colortex15,"));
        assertEquals(41472000L, prepared.variants().get("shaders").buffers().scratchBytes(1920,1080));
    }
}
