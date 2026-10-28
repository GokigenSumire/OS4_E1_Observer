package personal.wallpaper.os4.e1.relay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class WallpaperPriorityRelayTest {
    private LockRelayService service;private SharedPreferences preferences;private Binder relay;
    @Before public void before()throws Exception{
        service=Robolectric.buildService(LockRelayService.class).get();preferences=service.createDeviceProtectedStorageContext().getSharedPreferences("lock_compat_permanent",Context.MODE_PRIVATE);preferences.edit().clear().commit();assertTrue(ForceLocalSetting.write(preferences,false));assertTrue(WallpaperPrioritySetting.write(preferences,false));assertTrue(NativeInputSetting.write(preferences,false));preferences.edit().clear().putBoolean(NativeInputSetting.ENABLED,false).commit();
        assertTrue(LockRelayService.setDesiredEnabled(service,true));set("serverPid",4321);set("serverEpoch",UUID.randomUUID().toString());set("serverSession",new Binder());set("hostReady",true);set("hostTicket",UUID.randomUUID().toString());set("hostReason","NO_ENGINE");set("appliedForceRevision",0L);relay=(Binder)get("relayBinder");ShadowBinder.setCallingUid(1000);ShadowBinder.setCallingPid(4321);
        Class<?> observed=Class.forName(LockRelayService.class.getName()+"$HostObservation");var observation=observed.getDeclaredConstructor(boolean.class,String.class,String.class);observation.setAccessible(true);set("hostObservation",observation.newInstance(true,get("hostTicket"),"NO_ENGINE"));
    }
    @After public void after()throws Exception{ShadowBinder.reset();set("armed",false);set("hostSession",null);service.onDestroy();}
    private void set(String name,Object value)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);field.set(service,value);}
    private Object get(String name)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);return field.get(service);}
    private String status(int expected)throws Exception{Parcel request=Parcel.obtain(),reply=Parcel.obtain();try{request.writeInterfaceToken(LockProtocol.RELAY);assertTrue(relay.transact(LockProtocol.RELAY_STATE,request,reply,0));reply.readException();assertEquals(expected,reply.readInt());String ticket=reply.readString(),reason=reply.readString();assertEquals(0,reply.dataAvail());if(expected==1)assertTrue(LockProtocol.token(ticket));else assertEquals("",ticket);return reason;}finally{request.recycle();reply.recycle();}}
    @Test public void absentPriorityPreservesOriginalReadyMarker()throws Exception{assertEquals("READY",status(1));}
    @Test public void priorityMarkerRequiresAppliedCurrentRevision()throws Exception{assertTrue(WallpaperPrioritySetting.write(preferences,true));assertEquals("NO_ENGINE",status(0));set("appliedForceRevision",1L);assertEquals("READY_WALLPAPER_PRIORITY",status(1));}
    @Test public void nativeMarkerCarriesPriorityWithoutChangingReadyBit(){assertEquals("READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY",LockRelayService.readyMarker(true,false,true));}
    @Test public void nativeMarkerCarriesForceAndPriorityAsSeparateFixedCapabilities(){assertEquals("READY_NATIVE_INPUT+READY_FORCE_LOCAL+READY_WALLPAPER_PRIORITY",LockRelayService.readyMarker(true,true,true));}
    @Test public void disablePriorityRestoresSavedLocalModeWithNewRevision()throws Exception{assertTrue(ForceLocalSetting.write(preferences,true));assertTrue(WallpaperPrioritySetting.write(preferences,true));set("appliedForceRevision",2L);assertEquals("READY_WALLPAPER_PRIORITY",status(1));assertTrue(WallpaperPrioritySetting.write(preferences,false));assertEquals("NO_ENGINE",status(0));set("appliedForceRevision",3L);assertEquals("READY_FORCE_LOCAL",status(1));}
    @Test public void priorityNeverEnablesDisabledLock()throws Exception{assertTrue(WallpaperPrioritySetting.write(preferences,true));set("appliedForceRevision",1L);assertTrue(LockRelayService.setDesiredEnabled(service,false));assertEquals("NO_ENGINE",status(0));}
    @Test public void untrustedCallerCannotReadMode()throws Exception{ShadowBinder.setCallingUid(20000);try{status(0);fail();}catch(SecurityException expected){}}
    @Test public void queuedCommandCannotBorrowNewPriorityRevision()throws Exception{Class<?> pendingClass=Class.forName(LockRelayService.class.getName()+"$Pending");var constructor=pendingClass.getDeclaredConstructors()[0];constructor.setAccessible(true);Object pending=constructor.newInstance(UUID.randomUUID().toString(),UUID.randomUUID().toString(),1L,1L,Long.MAX_VALUE,0L);set("hostSession",UUID.randomUUID().toString());set("hostBinder",new Binder(){@Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){throw new AssertionError("stale must not call host");}});assertTrue(WallpaperPrioritySetting.write(preferences,true));Method command=LockRelayService.class.getDeclaredMethod("command",pendingClass,int.class,String.class,long.class);command.setAccessible(true);command.invoke(service,pending,LockProtocol.NEXT,UUID.randomUUID().toString(),0L);}
}
