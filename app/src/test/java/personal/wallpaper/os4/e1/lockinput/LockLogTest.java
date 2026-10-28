package personal.wallpaper.os4.e1.lockinput;
import org.junit.Test;
import static org.junit.Assert.*;
public final class LockLogTest {
    @Test public void frameworkFailureCannotPreventAndroidMirror(){int[] calls={0};LockLog.deliver(()->{throw new LinkageError();},()->calls[0]++);assertEquals(1,calls[0]);}
    @Test public void androidFailureCannotEscapeInputCallback(){int[] calls={0};LockLog.deliver(()->calls[0]++,()->{throw new AssertionError();});assertEquals(1,calls[0]);}
    @Test public void bothBackendsFailWithoutEscaping(){LockLog.deliver(()->{throw new Error();},()->{throw new Error();});}
    @Test public void normalBackendsBothRunOnce(){int[] calls={0,0};LockLog.deliver(()->calls[0]++,()->calls[1]++);assertArrayEquals(new int[]{1,1},calls);}
}
