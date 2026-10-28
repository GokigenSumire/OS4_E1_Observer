package personal.wallpaper.os4.e1.lockinput;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import personal.wallpaper.os4.e1.NativeSigningFixtures;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

/** 真Broker Session Binder、AMS结构和正式证书鉴别，限定MODULE新增11只可报告编辑结果。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class NativeBrokerEditRoleTest {
    private static final int UID=35100,PID=45100;
    private LockInputBroker broker;
    private IBinder session;
    private LockDiagnosticRing ring;
    private String token;
    private FakeRecord record;
    private File sourceFile;
    @Before public void before()throws Exception{
        Context app=RuntimeEnvironment.getApplication();NativeSigningFixtures.module(app,UID);
        Constructor<LockInputBroker> brokerConstructor=LockInputBroker.class.getDeclaredConstructor();brokerConstructor.setAccessible(true);broker=brokerConstructor.newInstance();
        record=new FakeRecord();record.info=new ApplicationInfo();record.info.packageName=LockProtocol.MODULE_PACKAGE;record.info.uid=UID;record.info.enabled=true;
        set(broker,"ams",new FakeAms(record));set(broker,"context",app);
        Class<?> identityClass=Class.forName(LockInputBroker.class.getName()+"$ProcessIdentity");Constructor<?> identityConstructor=identityClass.getDeclaredConstructors()[0];identityConstructor.setAccessible(true);
        Object identity=identityConstructor.newInstance(PID,UID,7L,LockProtocol.MODULE_PACKAGE,record,new ApplicationInfo(record.info));
        Class<?> sessionClass=Class.forName(LockInputBroker.class.getName()+"$Session");Constructor<?> sessionConstructor=sessionClass.getDeclaredConstructors()[0];sessionConstructor.setAccessible(true);
        session=(IBinder)sessionConstructor.newInstance(broker,identity,new Binder(),new Binder(),LockProtocol.ROLE_MODULE,1L);
        set(broker,"module",session);ring=(LockDiagnosticRing)get(broker,"diagnostic");token=ring.start(SystemClock.elapsedRealtime()).token;
        ShadowBinder.setCallingUid(UID);ShadowBinder.setCallingPid(PID);
    }
    @After public void after()throws Exception{if(broker!=null)((Handler)get(broker,"worker")).getLooper().quit();if(sourceFile!=null)assertTrue(sourceFile.delete());ShadowBinder.setCallingUid(Process.myUid());ShadowBinder.setCallingPid(Process.myPid());}
    private static void set(Object owner,String name,Object value)throws Exception{Field field=LockInputBroker.class.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
    private static Object get(Object owner,String name)throws Exception{Field field=LockInputBroker.class.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private Parcel report(String sessionToken,int stage,int cause,long count)throws Exception{Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{data.writeInterfaceToken(LockProtocol.SESSION);data.writeString(sessionToken);data.writeInt(stage);data.writeInt(cause);data.writeLong(count);data.setDataPosition(0);assertTrue(session.transact(11,data,reply,0));reply.setDataPosition(0);return reply;}finally{data.recycle();}}
    private void rejects(String token,int stage,int cause,long count)throws Exception{Parcel reply=report(token,stage,cause,count);try{reply.readException();fail("MODULE只可在主动诊断中报告固定编辑结果");}catch(SecurityException expected){}finally{reply.recycle();}}
    @Test public void moduleMayReportExactlyFixedEditResultWhileDiagnosticActive()throws Exception{Parcel reply=report(token,0,NativeInputDetail.editCause(1),1);try{reply.readException();assertEquals(0,reply.readInt());assertEquals(0,reply.dataAvail());}finally{reply.recycle();}assertEquals(1,ring.state(SystemClock.elapsedRealtime()).rows);}
    @Test public void moduleCannotForgeSourceOrLoaderStage()throws Exception{rejects(token,1,LockDiagnosticCause.code("SOURCE_FILE_MATCHED_LOCAL_BYTES"),1);rejects(token,2,LockDiagnosticCause.code("DECLARING_LOADER_SOURCE_MATCHED"),1);assertEquals(0,ring.state(SystemClock.elapsedRealtime()).rows);}
    @Test public void moduleCannotReportInputCommandOrRegionAsIfProducer()throws Exception{rejects(token,0,NativeInputDetail.code("NATIVE_COMMAND_SENT"),1);rejects(token,0,NativeInputDetail.code("NATIVE_ALLOWED"),1);assertEquals(0,ring.state(SystemClock.elapsedRealtime()).rows);}
    @Test public void moduleCannotReportGenericLegacyInputState()throws Exception{rejects(token,0,LockDiagnosticCause.code("COMMAND_ACCEPTED"),1);assertEquals(0,ring.state(SystemClock.elapsedRealtime()).rows);}
    @Test public void editResultCannotBeRelabelledHookOrBootstrap()throws Exception{rejects(token,3,NativeInputDetail.editCause(1),1);rejects(token,4,NativeInputDetail.editCause(1),1);}
    @Test public void staleTokenNegativeCountAndUnknownCauseRejected()throws Exception{rejects("wrong-session",0,NativeInputDetail.editCause(1),1);rejects(token,0,NativeInputDetail.editCause(1),-1);rejects(token,0,Integer.MAX_VALUE,1);}
    @Test public void stoppedDiagnosticRejectsEvenProperEditCode()throws Exception{ring.stop(token,SystemClock.elapsedRealtime());rejects(token,0,NativeInputDetail.editCause(1),1);}
    @Test public void wrongKernelUidOrPidCannotUseExistingModuleSession()throws Exception{ShadowBinder.setCallingUid(UID+1);rejects(token,0,NativeInputDetail.editCause(1),1);ShadowBinder.setCallingUid(UID);ShadowBinder.setCallingPid(PID+1);rejects(token,0,NativeInputDetail.editCause(1),1);}
    @Test public void replacedProcessRecordCannotReuseOldModuleCapability()throws Exception{record.sequence=8;rejects(token,0,NativeInputDetail.editCause(1),1);}
    @Test public void resultReportingDoesNotGrantProducerCommandsOrStateMutation()throws Exception{assertFalse(LockDiagnosticRolePolicy.allows(1,3));assertFalse(LockDiagnosticRolePolicy.allows(1,5));assertFalse(LockDiagnosticRolePolicy.allows(1,12));for(int transaction:new int[]{1,3,4,7,10})assertFalse(LockDiagnosticRolePolicy.allows(3,transaction));}
    @Test public void moduleMayReportFixedHostOutcomesWithoutGainingGenericInputAuthority()throws Exception{
        for(String status:new String[]{"COMMITTED","FAILED","STALE_TICKET","CANCELLED","EXPIRED","NO_ENGINE","TARGET_NOT_LOCK","BUSY_EDIT","UNKNOWN_STATE","unknown"})accepted(NativeInputDetail.commandResult(status));
        assertEquals(10,ring.state(SystemClock.elapsedRealtime()).rows);rejects(token,0,NativeInputDetail.code("NATIVE_ENTRY_ROOT"),1);assertEquals(10,ring.state(SystemClock.elapsedRealtime()).rows);
    }
    @Test public void exactProducerMayReportEntryButCannotForgeHostOrEditOutcomes()throws Exception{systemUiSession(LockProtocol.ROLE_PRODUCER);rejectRelayResults();}
    @Test public void diagnosticRoleMayReportEntryButCannotForgeHostOrEditOutcomes()throws Exception{systemUiSession(LockInputBroker.ROLE_DIAGNOSTIC);rejectRelayResults();}
    private void accepted(int cause)throws Exception{Parcel reply=report(token,0,cause,1);try{reply.readException();assertEquals(0,reply.readInt());assertEquals(0,reply.dataAvail());}finally{reply.recycle();}}
    private void rejectRelayResults()throws Exception{
        accepted(NativeInputDetail.code("NATIVE_ENTRY_ROOT"));assertEquals(1,ring.state(SystemClock.elapsedRealtime()).rows);
        int rejected=0;for(int i=0;i<NativeInputDetail.count();i++){int code=NativeInputDetail.BASE+i;if(NativeInputDetail.isRelayResult(code)){rejects(token,0,code,1);rejected++;}}
        assertEquals(31,rejected);assertEquals(1,ring.state(SystemClock.elapsedRealtime()).rows);
        accepted(NativeInputDetail.code("NATIVE_RECOGNIZED_NEXT"));assertEquals(2,ring.state(SystemClock.elapsedRealtime()).rows);
    }
    private void systemUiSession(int role)throws Exception{
        record.uid=1000;record.processName=RuntimeApkIdentity.SYSTEM_UI;record.info.uid=1000;record.info.packageName=RuntimeApkIdentity.SYSTEM_UI;
        sourceFile=File.createTempFile("broker-local-stat-", ".tmp",RuntimeEnvironment.getApplication().getCacheDir());record.info.sourceDir=sourceFile.getCanonicalPath();
        // 新的会话再核当前安装身份；原仅注入stat的外壳不足，补真实PM记录，不绕过校验。
        record.info.flags=ApplicationInfo.FLAG_SYSTEM;
        android.content.pm.PackageInfo installed=new android.content.pm.PackageInfo();installed.packageName=RuntimeApkIdentity.SYSTEM_UI;installed.applicationInfo=new ApplicationInfo(record.info);
        ((org.robolectric.shadows.ShadowPackageManager)org.robolectric.shadow.api.Shadow.extract(RuntimeEnvironment.getApplication().getPackageManager())).installPackage(installed);
        Class<?> identityClass=Class.forName(LockInputBroker.class.getName()+"$ProcessIdentity");Constructor<?> identityConstructor=identityClass.getDeclaredConstructors()[0];identityConstructor.setAccessible(true);
        Object identity=identityConstructor.newInstance(PID,1000,7L,RuntimeApkIdentity.SYSTEM_UI,record,new ApplicationInfo(record.info));
        Class<?> sessionClass=Class.forName(LockInputBroker.class.getName()+"$Session");Constructor<?> sessionConstructor=sessionClass.getDeclaredConstructors()[0];sessionConstructor.setAccessible(true);
        session=(IBinder)sessionConstructor.newInstance(broker,identity,new Binder(),null,role,2L);set(broker,role==LockProtocol.ROLE_PRODUCER?"producer":"diagnosticProducer",session);
        // 仅建立真实stat与本次同AMS记录，用于Session有效性；不称为OEM APK/签名验证。
        set(broker,"sourceProcess",identity);set(broker,"sourceIdentity",new RuntimeApkIdentity.Result(true,true,"TEST_LOCAL_STAT_ONLY",sourceFile.getCanonicalPath(),Os.stat(sourceFile.getCanonicalPath())));
        ShadowBinder.setCallingUid(1000);ShadowBinder.setCallingPid(PID);
    }
    public static final class FakeAms{public final FakePidMap mPidsSelfLocked;FakeAms(FakeRecord record){mPidsSelfLocked=new FakePidMap(record);}}
    public static final class FakePidMap{private final FakeRecord record;FakePidMap(FakeRecord record){this.record=record;}public FakeRecord get(int pid){return pid==PID?record:null;}}
    public static final class FakeRecord{public int mPid=PID,uid=UID;public String processName=LockProtocol.MODULE_PACKAGE;public ApplicationInfo info;long sequence=7;public long getStartSeq(){return sequence;}}
}
