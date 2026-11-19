package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CameraHistoryTest {
    @Test void firstFrameUsesActualCurrentCameraRatherThanAnOrigin() {
        var history=new CameraHistory();
        var first=history.update(new Object(),new Object(),-12.25,100.5,30_000_000.125);
        assertSame(first.position(),first.previous());
        assertEquals(-12.25,first.previous().x());
        assertEquals(30_000_000.125,first.previous().z());
    }

    @Test void nativeAndMultiplePostConsumersShareOneImmutableSnapshot() {
        var history=new CameraHistory();var world=new Object();var f0=new Object();var f1=new Object();
        var first=history.update(world,f0,1,2,3);
        var second=history.update(world,f1,4,5,6);
        for(int i=0;i<10;i++)assertSame(second,history.update(world,f1,4,5,6));
        assertSame(first.position(),second.previous());
        assertEquals(1,second.previous().x());
        assertEquals(4,second.position().x());
        assertEquals(1,first.position().x());
    }

    @Test void wrappingOrEqualValuedFrameCountersDoNotMergeDifferentFrames() {
        var history=new CameraHistory();var world=new Object();
        history.update(world,new AnimationClock.Snapshot(0,0,0),1,2,3);
        var next=history.update(world,new AnimationClock.Snapshot(0,0,0),7,8,9);
        assertEquals(1,next.previous().x());
    }

    @Test void worldIdentityAndExplicitReloadResetNeverReuseAnOldCamera() {
        var history=new CameraHistory();var a=new String("world");var b=new String("world");
        history.update(a,new Object(),1,2,3);
        var next=history.update(b,new Object(),10,20,30);
        assertSame(next.position(),next.previous());
        history.reset();history.reset();
        next=history.update(b,new Object(),40,50,60);
        assertSame(next.position(),next.previous());
        next=history.update(a,new Object(),70,80,90);
        assertSame(next.position(),next.previous());
    }

    @Test void conflictingOrInvalidExtractionsDoNotPoisonTheNextFrame() {
        var history=new CameraHistory();var world=new Object();var frame=new Object();
        var first=history.update(world,frame,1,2,3);
        assertThrows(IllegalStateException.class,()->history.update(world,frame,5,2,3));
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->history.update(new Object(),new Object(),value,2,3));
        assertThrows(NullPointerException.class,()->history.update(null,new Object(),8,9,10));
        assertThrows(NullPointerException.class,()->history.update(world,null,8,9,10));
        assertSame(first,history.update(world,frame,1,2,3));
        assertEquals(first.position(),history.update(world,new Object(),8,9,10).previous());
    }

    @Test void inactiveVariantsCannotKeepAnOldWorldAliveAndResetReleasesTheFrame() throws Exception {
        var history=new CameraHistory();var world=new Object();
        history.update(world,new Object(),1,2,3);
        var owner=CameraHistory.class.getDeclaredField("world");owner.setAccessible(true);
        assertInstanceOf(java.lang.ref.WeakReference.class,owner.get(history));
        assertSame(world,((java.lang.ref.WeakReference<?>)owner.get(history)).get());
        history.reset();
        assertNull(((java.lang.ref.WeakReference<?>)owner.get(history)).get());
        for(String name:java.util.List.of("frame","current")) {
            var field=CameraHistory.class.getDeclaredField(name);field.setAccessible(true);assertNull(field.get(history));
        }
    }
}
