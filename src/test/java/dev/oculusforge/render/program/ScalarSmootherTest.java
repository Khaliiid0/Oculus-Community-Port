package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class ScalarSmootherTest {
    @Test void asymmetricHalfLivesAndSameFrameDeduplication() {
        var filter=new ScalarSmoother(10,20); var world=new Object();
        assertEquals(0,filter.update(world,new Object(),0,0));
        var frame=new Object();
        assertEquals(.5,filter.update(world,frame,1,.5),1e-12);
        assertEquals(.5,filter.update(world,frame,0,30),1e-12);
        assertEquals(.25,filter.update(world,new Object(),0,1),1e-12);
    }
    @Test void zeroHalfLifeIsImmediateOnlyForItsDirection() {
        var filter=new ScalarSmoother(0,10); var world=new Object();
        filter.update(world,new Object(),0,0);
        assertEquals(1,filter.update(world,new Object(),1,0));
        assertEquals(1,filter.update(world,new Object(),0,0));
        assertEquals(.5,filter.update(world,new Object(),0,.5),1e-12);
    }
    @Test void fractionalHistoryIsFrameRateIndependent() {
        for(int steps:new int[]{1,10,60,144,1000}) {
            var filter=new ScalarSmoother(10,10); var world=new Object();
            filter.update(world,new Object(),-3,0); double result=0;
            for(int i=0;i<steps;i++) result=filter.update(world,new Object(),7,.5/steps);
            assertEquals(2,result,1e-10);
        }
    }
    @Test void worldAndExplicitResetDiscardHistory() {
        var filter=new ScalarSmoother(10,10); var world=new Object(); var frame=new Object();
        filter.update(world,frame,1,0);
        assertEquals(9,filter.update(new Object(),frame,9,0));
        filter.reset();
        assertEquals(-2,filter.update(world,frame,-2,0));
    }
    @Test void invalidSamplesDoNotPoisonHistoryEvenOnCachedFrame() {
        var filter=new ScalarSmoother(10,10); var world=new Object(); var frame=new Object();
        filter.update(world,frame,0,0);
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->filter.update(world,frame,bad,1));
            assertThrows(IllegalArgumentException.class,()->filter.update(world,frame,1,bad));
            assertThrows(IllegalArgumentException.class,()->new ScalarSmoother(bad,1));
            assertThrows(IllegalArgumentException.class,()->new ScalarSmoother(1,bad));
        }
        assertThrows(IllegalArgumentException.class,()->filter.update(world,frame,1,-1));
        assertEquals(.5,filter.update(world,new Object(),1,.5),1e-12);
    }
    @Test void finiteExtremesAndLongPauseRemainFinite() {
        var filter=new ScalarSmoother(10,10); var world=new Object();
        filter.update(world,new Object(),-Double.MAX_VALUE,0);
        assertEquals(0,filter.update(world,new Object(),Double.MAX_VALUE,.5));
        assertEquals(Double.MAX_VALUE,filter.update(world,new Object(),Double.MAX_VALUE,Double.MAX_VALUE));
    }
}
