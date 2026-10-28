package personal.wallpaper.os4.e1.relay;

import android.os.Binder;
import android.os.Parcel;
import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import personal.wallpaper.os4.e1.lockinput.NativeInputDetail;
import personal.wallpaper.os4.e1.NativeSigningFixtures;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

/** 实际私有轮询及真实Parcel；宿主替身按新增已鉴别HOST事务5的真实封包建立。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class NativeRelayEditDiagnosticsTest {
    private LockRelayService service;
    private Host host;
    private Server server;
    @Before public void before()throws Exception{
        service=org.robolectric.Robolectric.buildService(LockRelayService.class).get();host=new Host();server=new Server();
        set("hostBinder",host);set("hostSession","host-session");set("serverSession",server);
        primeKnownSession();
    }
    private void primeKnownSession()throws Exception{set("editDiagnosticSession","host-session");set("editDiagnosticToken",server.token);set("editDiagnosticCursor",0L);set("editDiagnosticUnavailable",false);}
    private void set(String name,Object value)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);field.set(service,value);}
    private Object get(String name)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);return field.get(service);}
    private void poll()throws Exception{Method method=LockRelayService.class.getDeclaredMethod("pollEditDiagnostics");method.setAccessible(true);method.invoke(service);}
    private int code(String name){return NativeInputDetail.code(name);}
    private void assertUnavailable(){assertEquals(List.of(code("NATIVE_EDIT_RESULTS_UNAVAILABLE")),server.codes);}

    @Test public void completeTwelveFixedHostResultsReachOnlyInputDiagnostics()throws Exception{for(int cause=1;cause<=12;cause++)host.rows.add(new long[]{cause,cause});host.latest=12;poll();assertEquals(12,server.codes.size());for(int cause=1;cause<=12;cause++)assertEquals(NativeInputDetail.editCause(cause),(int)server.codes.get(cause-1));assertEquals(12L,get("editDiagnosticCursor"));assertEquals(List.of(0L),host.requestedCursors);}
    @Test public void nextPollReadsOnlyAfterAcknowledgedCursor()throws Exception{host.rows.add(new long[]{1,1});host.latest=1;poll();host.rows.add(new long[]{2,2});host.latest=2;poll();assertEquals(List.of(0L,1L),host.requestedCursors);assertEquals(List.of(NativeInputDetail.editCause(1),NativeInputDetail.editCause(2)),server.codes);}
    @Test public void brokerRejectedReportDoesNotAdvanceCursor()throws Exception{host.rows.add(new long[]{1,1});host.latest=1;server.reportStatus=2;poll();assertEquals(0L,get("editDiagnosticCursor"));server.reportStatus=0;poll();assertEquals(1L,get("editDiagnosticCursor"));assertEquals(2,server.codes.size());}
    @Test public void diagnosticStopNeverQueriesHostOrChangesPlayback()throws Exception{server.active=0;host.latest=1;host.rows.add(new long[]{1,1});poll();assertEquals(0,host.reads);assertTrue(server.codes.isEmpty());assertEquals(0L,get("commandSequence"));assertEquals("host-session",get("hostSession"));}
    @Test public void oldHostWithoutTransactionIsUnavailableNotBusinessFailure()throws Exception{host.available=false;poll();assertUnavailable();assertSame(host,get("hostBinder"));assertSame(server,get("serverSession"));assertEquals("host-session",get("hostSession"));assertEquals(0L,get("commandSequence"));}
    @Test public void unavailableTransportIsReportedOncePerCapture()throws Exception{host.available=false;poll();poll();assertUnavailable();}
    @Test public void unknownCauseCannotBecomeArbitraryInputStage()throws Exception{host.latest=1;host.rows.add(new long[]{1,19});poll();assertUnavailable();assertEquals(0L,get("editDiagnosticCursor"));}
    @Test public void appendedSyncAndNavigationCausesReachOnlyFixedInputDiagnostics()throws Exception{for(int cause=13;cause<=18;cause++)host.rows.add(new long[]{cause-12,cause});host.latest=6;poll();assertEquals(6,server.codes.size());for(int cause=13;cause<=18;cause++)assertEquals(NativeInputDetail.editCause(cause),(int)server.codes.get(cause-13));assertEquals(6L,get("editDiagnosticCursor"));}
    @Test public void unrecognizedSchemaCannotBecomeEditOutcome()throws Exception{host.schema=2;poll();assertUnavailable();}
    @Test public void excessiveCountRejectedBeforeRowsAreRead()throws Exception{host.declaredCount=33;poll();assertUnavailable();}
    @Test public void trailingHostParcelRejectedWithoutPartialReports()throws Exception{host.latest=1;host.rows.add(new long[]{1,1});host.trailing=true;poll();assertUnavailable();}
    @Test public void oversizedReplyRejectedBeforeParsing()throws Exception{host.oversized=true;poll();assertUnavailable();}
    @Test public void sequenceMustIncreaseAndStayWithinLatest()throws Exception{host.latest=2;host.rows.add(new long[]{2,1});host.rows.add(new long[]{1,2});poll();assertUnavailable();assertEquals(0L,get("editDiagnosticCursor"));}
    @Test public void futureSequenceRejectedWithoutFirstRowBeingPublished()throws Exception{host.latest=1;host.rows.add(new long[]{1,1});host.rows.add(new long[]{2,2});poll();assertUnavailable();}
    @Test public void negativeOrDecreasingLatestRejected()throws Exception{set("editDiagnosticCursor",2L);host.latest=1;poll();assertUnavailable();}
    @Test public void explicitTruncationIsRecordedBeforeNewRows()throws Exception{host.truncated=1;host.latest=4;host.rows.add(new long[]{4,1});poll();assertEquals(List.of(code("NATIVE_EDIT_RESULTS_TRUNCATED"),NativeInputDetail.editCause(1)),server.codes);assertEquals(4L,get("editDiagnosticCursor"));}
    @Test public void illegalTruncationFlagCannotPassAsSuccess()throws Exception{host.truncated=2;poll();assertUnavailable();}
    @Test public void changedHostSessionDuringIpcCannotPublishOldRows()throws Exception{host.latest=1;host.rows.add(new long[]{1,1});host.during=()->{try{set("hostSession","replacement");}catch(Exception failure){throw new AssertionError(failure);}};poll();assertFalse(server.codes.contains(NativeInputDetail.editCause(1)));assertEquals(0L,get("editDiagnosticCursor"));}
    @Test public void changedServerDuringIpcCannotReportIntoReplacementChannel()throws Exception{host.latest=1;host.rows.add(new long[]{1,1});host.during=()->{try{set("serverSession",new Server());}catch(Exception failure){throw new AssertionError(failure);}};poll();assertTrue(server.codes.isEmpty());}
    @Test public void missingBindingsCannotStartQueryOrCommand()throws Exception{set("hostSession",null);poll();assertEquals(0,host.reads);assertEquals(0,server.statusReads);assertTrue(server.codes.isEmpty());}
    @Test public void privateReportAdapterRejectsAnyNonResultNativeCode()throws Exception{Method method=LockRelayService.class.getDeclaredMethod("recordEditDiagnostic",android.os.IBinder.class,String.class,int.class,long.class);method.setAccessible(true);assertEquals(false,method.invoke(service,server,server.token,code("NATIVE_COMMAND_SENT"),1L));assertEquals(false,method.invoke(service,server,server.token,code("NATIVE_ALLOWED"),1L));assertTrue(server.codes.isEmpty());}
    @Test public void startPrimesOldCursorBeforeRunningAndKeepsImmediateTwelveNewEvents()throws Exception{
        // Robolectric的默认模拟进程PID为0；真实运行进程必为正，不能因此绕过生产pid>0守卫。
        org.robolectric.shadows.ShadowProcess.setPid(51001);
        NativeSigningFixtures.host(service,32001);server.active=0;host.latest=2;host.rows.add(new long[]{1,1});host.rows.add(new long[]{2,2});
        assertTrue("正式公开证书宿主前置应通过真实PM身份核定",LockCallerAuth.host(service,32001,android.os.Process.myPid()));
        assertEquals("PM实际宿主UID",32001,service.getPackageManager().getApplicationInfo(LockProtocol.HOST_PACKAGE,0).uid);
        Method baseline=LockRelayService.class.getDeclaredMethod("editResultBaseline",android.os.IBinder.class,String.class);baseline.setAccessible(true);
        assertEquals("真实HOST旧结果游标前置",2L,baseline.invoke(service,host,"host-session"));
        host.requestedCursors.clear();host.reads=0;
        set("editDiagnosticToken","");set("editDiagnosticSession","");server.afterStart=()->{for(int cause=1;cause<=12;cause++)host.rows.add(new long[]{cause+2,cause});host.latest=14;};
        Method method=LockRelayService.class.getDeclaredMethod("requestDiagnosticWithEditBaseline",String.class,android.os.IBinder.class);method.setAccessible(true);
        DiagnosticClient.Result started=(DiagnosticClient.Result)method.invoke(service,"START",server);assertTrue(started.success);assertTrue(started.active);assertEquals(2L,get("editDiagnosticCursor"));
        poll();assertEquals(List.of(0L,2L),host.requestedCursors);assertEquals(12,server.codes.size());for(int cause=1;cause<=12;cause++)assertEquals(NativeInputDetail.editCause(cause),(int)server.codes.get(cause-1));assertEquals(14L,get("editDiagnosticCursor"));
    }
    @Test public void missingPreparedCaptureReportsObservationGapInsteadOfClaimingOldRowsNew()throws Exception{set("editDiagnosticToken","");host.latest=1;host.rows.add(new long[]{1,1});poll();assertEquals(List.of(code("NATIVE_EDIT_BASELINE_UNAVAILABLE")),server.codes);assertEquals(1L,get("editDiagnosticCursor"));}
    @Test public void replacedOwnerReportsGapAndDoesNotPublishOldOwnerHistory()throws Exception{set("editDiagnosticSession","old-host-owner");host.latest=1;host.rows.add(new long[]{1,1});poll();assertEquals(List.of(code("NATIVE_EDIT_BASELINE_UNAVAILABLE")),server.codes);assertEquals("host-session",get("editDiagnosticSession"));}


    private static final class Host extends Binder{
        int schema=1,truncated,reads,declaredCount=-1;long latest;boolean available=true,trailing,oversized;Runnable during;
        final List<long[]> rows=new ArrayList<>();final List<Long> requestedCursors=new ArrayList<>();
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){assertEquals(LockProtocol.EDIT_DIAGNOSTICS,code);assertEquals(0,flags);assertTrue(data.dataSize()<=1024);data.enforceInterface(LockProtocol.HOST);assertEquals("host-session",data.readString());long after=data.readLong();requestedCursors.add(after);assertEquals(0,data.dataAvail());reads++;if(!available)return false;if(during!=null)during.run();reply.writeNoException();if(oversized){reply.writeString("x".repeat(1024));return true;}List<long[]> selected=new ArrayList<>();for(long[] row:rows)if(row[0]>after)selected.add(row);reply.writeInt(schema);reply.writeLong(latest);reply.writeInt(truncated);reply.writeInt(declaredCount<0?selected.size():declaredCount);for(long[] row:selected){reply.writeLong(row[0]);reply.writeInt((int)row[1]);}if(trailing)reply.writeInt(99);return true;}
    }
    private static final class Server extends Binder{
        String token="native-diagnostic-token";int active=1,statusReads,reportStatus;Runnable afterStart;final List<Integer> codes=new ArrayList<>();
        @Override protected boolean onTransact(int transaction,Parcel data,Parcel reply,int flags){assertEquals(0,flags);data.enforceInterface(LockProtocol.SESSION);if(transaction==DiagnosticClient.STATE||transaction==DiagnosticClient.START){assertEquals(0,data.dataAvail());statusReads++;if(transaction==DiagnosticClient.START){active=1;token="new-native-diagnostic-token";if(afterStart!=null)afterStart.run();}reply.writeNoException();reply.writeString(token);reply.writeLong(1);reply.writeInt(active);reply.writeInt(active==1?1:2);reply.writeInt(0);reply.writeInt(0);return true;}assertEquals(11,transaction);assertEquals(token,data.readString());assertEquals(0,data.readInt());int cause=data.readInt();assertTrue(NativeInputDetail.isEditResult(cause));codes.add(cause);assertTrue(data.readLong()>=0);assertEquals(0,data.dataAvail());reply.writeNoException();reply.writeInt(reportStatus);return true;}
    }
}
