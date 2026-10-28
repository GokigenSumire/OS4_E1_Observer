package personal.wallpaper.os4.e1.lockinput;

import android.os.Parcel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 实际Android Parcel的UTF16大小与生产header，不以ASCII字节冒Binder字节。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31,manifest=Config.NONE)
public final class LockDiagnosticWireTest {
    @Test public void longestRoiRowsUseRealUtf16PageWithinOriginalParcelBudget()throws Exception{
        LockDiagnosticRing ring=new LockDiagnosticRing();String token=ring.start(0).token;
        int code=RoiDiagnosticDetail.code(false,true,RoiDiagnosticDetail.FINGERPRINT,LayoutDiagnosticDetail.BRIGHTNESS,LayoutDiagnosticDetail.BRIGHTNESS);
        for(int i=0;i<3;i++)assertTrue(ring.record(token,0,0,code,Long.MAX_VALUE));LockDiagnosticRing.State state=ring.stop(token,1);Parcel parcel=Parcel.obtain();
        try{Method encoder=LockInputBroker.class.getDeclaredMethod("writeDiagnosticPage",Parcel.class,LockDiagnosticRing.State.class,java.util.List.class);encoder.setAccessible(true);encoder.invoke(null,parcel,state,ring.page(token,state.revision,0,1));assertTrue(parcel.dataSize()<=1024);}finally{parcel.recycle();}
    }
    @Test public void threeLongestRowsAndActualHeaderFitBinderBudget()throws Exception{
        LockDiagnosticRing ring=new LockDiagnosticRing();String token=ring.start(0).token;
        int cause=LockDiagnosticCause.code("UNKNOWN_mLaunchEmergencyActionWhenFinishedWaking");
        for(int i=0;i<3;i++)assertTrue(ring.record(token,0,0,cause,Long.MAX_VALUE));
        LockDiagnosticRing.State state=ring.stop(token,1);Parcel parcel=Parcel.obtain();
        try{
            Method encoder=LockInputBroker.class.getDeclaredMethod("writeDiagnosticPage",Parcel.class,LockDiagnosticRing.State.class,java.util.List.class);encoder.setAccessible(true);encoder.invoke(null,parcel,state,ring.page(token,state.revision,0,1));
            assertTrue(parcel.dataSize()<=1024);parcel.setDataPosition(0);parcel.readException();assertEquals(token,parcel.readString());assertEquals(state.revision,parcel.readLong());assertEquals(0,parcel.readInt());
            assertEquals(LockDiagnosticRing.USER_STOP,parcel.readInt());assertEquals(3,parcel.readInt());assertEquals(state.bytes,parcel.readInt());assertEquals(3,parcel.readInt());
            int bytes=0;for(int i=0;i<3;i++){String row=parcel.readString();bytes+=row.length()+1;assertTrue(row.matches("[A-Za-z0-9_= .:-]+"));}assertEquals(state.bytes,bytes);assertEquals(0,parcel.dataAvail());
        }finally{parcel.recycle();}
    }
    @Test public void firstRecordDoesNotNeedScreenToStayOnForTenSeconds(){LockDiagnosticRing ring=new LockDiagnosticRing();String token=ring.start(100).token;assertTrue(ring.record(token,100,0,LockDiagnosticCause.code("UNKNOWN_FLOW_PRIMARY_SHOWING"),1));LockDiagnosticRing.State done=ring.stop(token,101);assertEquals(1,done.rows);}
    public static final class Flow {public Object value;Flow(Object value){this.value=value;}public Object getValue(){return value;}}
    private String flowFailure(Object flow,String owner)throws Exception{
        Method method=LockSurfaceSnapshot.class.getDeclaredMethod("flowBool",Object.class,String.class);method.setAccessible(true);
        try{method.invoke(null,flow,owner);fail();return "";}catch(java.lang.reflect.InvocationTargetException expected){var field=expected.getCause().getClass().getDeclaredField("key");field.setAccessible(true);return (String)field.get(expected.getCause());}
    }
    @Test public void missingPrimaryFlowNamesItsFixedOwner()throws Exception{assertEquals("FLOW_PRIMARY_SHOWING",flowFailure(null,"PRIMARY_SHOWING"));}
    @Test public void wrongAodValueNamesItsFixedOwner()throws Exception{assertEquals("FLOW_AOD",flowFailure(new Flow("false"),"AOD"));}
    @Test public void validBooleanFlowKeepsOriginalGuardSemantics()throws Exception{Method method=LockSurfaceSnapshot.class.getDeclaredMethod("flowBool",Object.class,String.class);method.setAccessible(true);assertEquals(Boolean.TRUE,method.invoke(null,new Flow(true),"PRIMARY_SOON"));assertEquals(Boolean.FALSE,method.invoke(null,new Flow(false),"CONTROL_CENTER"));}
}
