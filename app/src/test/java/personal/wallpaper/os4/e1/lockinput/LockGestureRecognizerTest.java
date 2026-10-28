package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LockGestureRecognizerTest {
    private LockGestureRecognizer detector(){return new LockGestureRecognizer(300,250,10);}
    private int event(LockGestureRecognizer d,int action,long t,int...ids){
        float[] x=new float[ids.length],y=new float[ids.length];for(int i=0;i<ids.length;i++){x[i]=i*40;y[i]=40;}return d.onEvent(action,t,ids.length,ids,x,y);
    }
    private int tap(LockGestureRecognizer d,long t){event(d,0,t,1);return event(d,1,t+20,1);}
    private int two(LockGestureRecognizer d,long t){event(d,0,t,1);event(d,5,t+5,1,2);event(d,6,t+20,1,2);return event(d,1,t+25,1);}
    @Test public void doubleWaitsForTripleWindow(){LockGestureRecognizer d=detector();tap(d,100);assertEquals(0,tap(d,200));assertEquals(0,d.onTime(519));assertEquals(1,d.onTime(520));}
    @Test public void tripleNeverFirstEmitsNext(){LockGestureRecognizer d=detector();tap(d,100);tap(d,200);assertEquals(0,event(d,0,300,1));assertEquals(2,event(d,1,320,1));assertEquals(0,d.onTime(1000));}
    @Test public void twoFingerDoubleIsOneEdit(){LockGestureRecognizer d=detector();assertEquals(0,two(d,100));assertEquals(3,two(d,220));assertEquals(0,d.onTime(1000));}
    @Test public void singleExpiresWithoutAction(){LockGestureRecognizer d=detector();tap(d,100);assertEquals(0,d.onTime(420));}
    @Test public void movementCancelsWholeSeries(){LockGestureRecognizer d=detector();tap(d,100);event(d,0,200,1);d.onEvent(2,210,1,new int[]{1},new float[]{20},new float[]{40});event(d,1,220,1);assertEquals(0,d.onTime(1000));}
    @Test public void cancellationDropsPendingDouble(){LockGestureRecognizer d=detector();tap(d,100);tap(d,200);event(d,3,230,1);assertEquals(0,d.onTime(1000));}
    @Test public void thirdPointerCancels(){LockGestureRecognizer d=detector();two(d,100);d.onEvent(5,220,3,new int[]{1,2,3},new float[]{0,0,0},new float[]{0,0,0});assertEquals(0,d.onTime(1000));}
    @Test public void unknownActionCancels(){LockGestureRecognizer d=detector();tap(d,100);event(d,0,200,1);event(d,9,210,1);assertEquals(0,d.onTime(1000));}
    @Test public void longPressCancels(){LockGestureRecognizer d=detector();tap(d,100);event(d,0,200,1);event(d,1,451,1);assertEquals(0,d.onTime(1000));}
    @Test public void changingPointerIdentityCancels(){LockGestureRecognizer d=detector();event(d,0,100,1);event(d,1,120,2);assertEquals(0,d.onTime(1000));}
    @Test public void mixesSingleAndTwoFingerDoNotFormPair(){LockGestureRecognizer d=detector();tap(d,100);two(d,200);assertEquals(0,d.onTime(1000));}
    @Test public void timeoutBetweenTwoTapsStartsNewSeries(){LockGestureRecognizer d=detector();tap(d,100);tap(d,500);assertEquals(0,d.onTime(1000));}
    @Test public void safetyResetDropsPending(){LockGestureRecognizer d=detector();tap(d,100);tap(d,200);d.reset();assertEquals(0,d.onTime(1000));}
    @Test public void backwardsTimeCancels(){LockGestureRecognizer d=detector();event(d,0,100,1);event(d,1,99,1);assertEquals(0,d.onTime(1000));}
    @Test public void nanRejected(){LockGestureRecognizer d=detector();d.onEvent(0,100,1,new int[]{1},new float[]{Float.NaN},new float[]{1});assertEquals(0,d.onTime(1000));}
    @Test public void duplicateIdsRejected(){LockGestureRecognizer d=detector();event(d,0,100,1);event(d,5,110,1,1);assertEquals(0,d.onTime(1000));}
    @Test public void heldThirdTapDoesNotEmitDoubleWhileTouching(){LockGestureRecognizer d=detector();tap(d,100);tap(d,200);event(d,0,500,1);assertEquals(0,d.onTime(520));assertEquals(2,event(d,1,530,1));}
    @Test public void emitsOnlyOnceAfterDouble(){LockGestureRecognizer d=detector();tap(d,100);tap(d,200);assertEquals(1,d.onTime(520));assertEquals(0,d.onTime(521));}
    @Test public void missingPointerUpCannotBecomeTwoFingerTap(){LockGestureRecognizer d=detector();event(d,0,100,1);event(d,5,105,1,2);event(d,1,120,1);assertEquals(0,two(d,220));assertEquals(0,d.onTime(1000));}
    @Test public void wrongRemainingPointerCannotCompleteTap(){LockGestureRecognizer d=detector();event(d,0,100,1);event(d,5,105,1,2);event(d,6,110,1,2);event(d,1,120,2);assertEquals(0,two(d,220));}
    @Test public void pointerCannotDisappearWithoutUp(){LockGestureRecognizer d=detector();event(d,0,100,1);event(d,5,105,1,2);event(d,2,110,1);assertEquals(0,two(d,220));}
    @Test public void farApartTapsDoNotCombine(){LockGestureRecognizer d=detector();tap(d,100);d.onEvent(0,200,1,new int[]{1},new float[]{80},new float[]{40});d.onEvent(1,220,1,new int[]{1},new float[]{80},new float[]{40});assertEquals(0,d.onTime(1000));}
    @Test public void overflowTimeRejected(){LockGestureRecognizer d=detector();event(d,0,Long.MAX_VALUE-5,1);assertEquals(0,d.onTime(Long.MAX_VALUE));}
    @Test public void liftingFirstFingerAlsoAllowsTwoFingerDouble(){
        LockGestureRecognizer d=detector();
        for(long t:new long[]{100,220}){
            event(d,0,t,1);event(d,5,t+5,1,2);
            d.onEvent(6,t+15,2,new int[]{1,2},new float[]{0,40},new float[]{40,40},0);
            int result=d.onEvent(1,t+25,1,new int[]{2},new float[]{40},new float[]{40},0);
            assertEquals(t==100?0:3,result);
        }
    }
}
