package personal.wallpaper.os4.e1.lockinput;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;
import static org.junit.Assert.*;

/** 真实PM/Session与已核AMS字段结构；不冒手机跨进程执行或来源DEX已核。 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,manifest=Config.NONE)
public final class InstalledProducerAdmissionTest {
    private LockInputBroker broker;private Context context;private ApplicationInfo info;private Record record;
    private ShadowPackageManager pm;private Class<?> identityType;private Constructor<?> identityConstructor;
    @Before public void before()throws Exception{
        context=RuntimeEnvironment.getApplication();pm=Shadow.extract(context.getPackageManager());
        Constructor<LockInputBroker> ctor=LockInputBroker.class.getDeclaredConstructor();ctor.setAccessible(true);broker=ctor.newInstance();
        set(broker,"context",context);identityType=Class.forName(LockInputBroker.class.getName()+"$ProcessIdentity");identityConstructor=identityType.getDeclaredConstructors()[0];identityConstructor.setAccessible(true);
        info=new ApplicationInfo();info.uid=10225;info.packageName=RuntimeApkIdentity.SYSTEM_UI;info.enabled=true;info.flags=ApplicationInfo.FLAG_SYSTEM;
        info.sourceDir=File.createTempFile("producer-installed-", ".apk",context.getCacheDir()).getCanonicalPath();
        record=new Record();record.info=new ApplicationInfo(info);install(info);set(broker,"ams",new Ams(record,context));
    }
    @After public void after()throws Exception{((Handler)LockInputBroker.field(broker.getClass(),"worker").get(broker)).getLooper().quitSafely();}
    private void install(ApplicationInfo app){PackageInfo pkg=new PackageInfo();pkg.packageName=app.packageName;pkg.applicationInfo=new ApplicationInfo(app);pm.installPackage(pkg);}
    private void set(Object owner,String name,Object value)throws Exception{LockInputBroker.field(owner.getClass(),name).set(owner,value);}
    private Object identity(ApplicationInfo app,String process)throws Exception{return identityConstructor.newInstance(6888,app.uid,7L,process,record,new ApplicationInfo(app));}
    private boolean allowed(ApplicationInfo app,String process)throws Exception{Method method=broker.getClass().getDeclaredMethod("installedSystemUi",identityType);method.setAccessible(true);return (Boolean)method.invoke(broker,identity(app,process));}
    private Object session()throws Exception{
        Class<?> type=Class.forName(LockInputBroker.class.getName()+"$Session");Constructor<?> ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        return ctor.newInstance(broker,identity(info,RuntimeApkIdentity.SYSTEM_UI),new Binder(),null,LockInputBroker.ROLE_DIAGNOSTIC,2L);
    }
    private boolean valid(Object session)throws Exception{Method method=session.getClass().getDeclaredMethod("valid");method.setAccessible(true);return (Boolean)method.invoke(session);}
    @Test public void currentInstalledDedicatedAndLegacySystemUidBothAdmitExactMainProcess()throws Exception{
        for(int uid:new int[]{1000,10000,10225,12345,19999}){info.uid=uid;install(info);assertTrue("uid="+uid,allowed(info,RuntimeApkIdentity.SYSTEM_UI));}
    }
    @Test public void guessedUidOrMissingInstallProofCannotUseSystemFlag()throws Exception{
        var claimed=new ApplicationInfo(info);claimed.uid=12345;assertFalse(allowed(claimed,RuntimeApkIdentity.SYSTEM_UI));
        pm.removePackage(RuntimeApkIdentity.SYSTEM_UI);assertFalse(allowed(info,RuntimeApkIdentity.SYSTEM_UI));
    }
    @Test public void sharedUidOtherProcessOrOtherPackageDoesNotBorrowMainIdentity()throws Exception{
        assertFalse(allowed(info,"com.android.systemui:aux"));var plugin=new ApplicationInfo(info);plugin.packageName="miui.systemui.plugin";
        assertFalse(allowed(plugin,RuntimeApkIdentity.SYSTEM_UI));
    }
    @Test public void ordinaryOrDisabledInstalledApplicationCannotClaimSystemComponent()throws Exception{
        var ordinary=new ApplicationInfo(info);ordinary.flags=0;install(ordinary);assertFalse(allowed(info,RuntimeApkIdentity.SYSTEM_UI));
        var disabled=new ApplicationInfo(info);disabled.enabled=false;install(disabled);assertFalse(allowed(info,RuntimeApkIdentity.SYSTEM_UI));
    }
    @Test public void sourceAndSplitClaimsMustMatchCurrentInstallation()throws Exception{
        var wrong=new ApplicationInfo(info);wrong.sourceDir=info.sourceDir+".other";assertFalse(allowed(wrong,RuntimeApkIdentity.SYSTEM_UI));
        wrong=new ApplicationInfo(info);wrong.splitSourceDirs=new String[]{info.sourceDir+".split"};assertFalse(allowed(wrong,RuntimeApkIdentity.SYSTEM_UI));
    }
    @Test public void rootShellIsolatedSandboxAndOtherUserFailEvenWithInstalledSystemFlags()throws Exception{
        for(int uid:new int[]{0,1001,2000,9999,20000,90000,99000,110225}){info.uid=uid;install(info);assertFalse("uid="+uid,allowed(info,RuntimeApkIdentity.SYSTEM_UI));}
    }
    @Test public void diagnosticSessionRevalidatesCurrentInstalledUidAndSource()throws Exception{
        Object session=session();assertTrue(valid(session));var changed=new ApplicationInfo(info);changed.uid=10226;install(changed);assertFalse(valid(session));
        install(info);assertFalse(valid(session));session=session();assertTrue(valid(session));
        changed=new ApplicationInfo(info);changed.sourceDir=info.sourceDir+".replaced";install(changed);assertFalse(valid(session));
    }
    @Test public void diagnosticSessionRevalidatesAmsRecordGenerationAndUid()throws Exception{
        Object session=session();assertTrue(valid(session));record.sequence++;assertFalse(valid(session));record.sequence--;
        assertFalse(valid(session));session=session();assertTrue(valid(session));
        record.uid=10226;assertFalse(valid(session));record.uid=10225;assertFalse(valid(session));
    }
    private int discover(int role,int uid)throws Exception{
        android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
        try{
            data.writeInt(LockInputBroker.PROTOCOL);data.writeInt(role);data.writeStrongBinder(new Binder());data.writeStrongBinder(null);data.setDataPosition(0);
            Method method=broker.getClass().getDeclaredMethod("discover",Object.class,android.os.Parcel.class,android.os.Parcel.class,int.class,int.class);method.setAccessible(true);
            method.invoke(broker,new Ams(record,context),data,reply,6888,uid);reply.setDataPosition(0);reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    @Test public void actualDiagnosticDiscoveryRequiresCurrentInstallationAndKernelAmsUid()throws Exception{
        assertEquals(0,discover(LockInputBroker.ROLE_DIAGNOSTIC,10225));
        var changed=new ApplicationInfo(info);changed.uid=10226;install(changed);
        assertEquals(2,discover(LockInputBroker.ROLE_DIAGNOSTIC,10225));
        install(info);assertEquals(2,discover(LockInputBroker.ROLE_DIAGNOSTIC,10226));
    }
    public static final class Ams{public final Pids mPidsSelfLocked;public final Context mContext;Ams(Record record,Context context){mPidsSelfLocked=new Pids(record);mContext=context;}}
    public static final class Pids{final Record record;Pids(Record record){this.record=record;}public Record get(int pid){return pid==6888?record:null;}}
    public static final class Record{public int mPid=6888,uid=10225;public String processName=RuntimeApkIdentity.SYSTEM_UI;public ApplicationInfo info;long sequence=7;public long getStartSeq(){return sequence;}}
}
