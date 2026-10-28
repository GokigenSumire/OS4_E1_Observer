package personal.wallpaper.os4.e1.relay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Parcel;
import android.os.SystemClock;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.shadows.ShadowProcess;
import personal.wallpaper.os4.e1.NativeSigningFixtures;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

/** 经过真实宿主Parcel解析、状态对象及中继Binder，避免模拟最终回包掩盖票据丢失。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class PriorityInputSceneRelayTest {
    private LockRelayService service;
    private SharedPreferences preferences;
    private final String ticket=LockProtocol.sceneTicket("00000000-0000-0000-0000-000000000094","00000000-0000-0000-0000-000000000001");
    private String hostTicket,hostReason;
    private boolean hostReady;
    private int commands;
    @Before public void before() throws Exception {
        service=Robolectric.buildService(LockRelayService.class).get();
        preferences=service.createDeviceProtectedStorageContext().getSharedPreferences("lock_compat_permanent",Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        assertTrue(LockRelayService.setDesiredEnabled(service,true));
        assertTrue(LockRelayService.setNativeInputEnabled(service,true));
        assertTrue(WallpaperPrioritySetting.write(preferences,true));
        set("appliedForceRevision",LockRelayService.nativeInputState(service).revision);
        set("serverPid",4321);set("serverEpoch","server-epoch");set("serverSession",new Binder());set("hostSession","owner");
        ShadowProcess.setPid(51001);NativeSigningFixtures.host(service,32001);
        assertTrue(LockCallerAuth.host(service,32001,51001));
        hostTicket=ticket;hostReason="BUSY_EDIT";
        set("hostBinder",new Binder(){
            @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
                data.enforceInterface(LockProtocol.HOST);
                if(code==LockProtocol.STATE){assertEquals("owner",data.readString());reply.writeNoException();reply.writeInt(hostReady?1:0);reply.writeString(hostTicket);reply.writeString(hostReason);return true;}
                if(code==LockProtocol.COMMAND){commands++;reply.writeNoException();reply.writeString("ACCEPTED");return true;}
                return false;
            }
        });
        ShadowBinder.setCallingUid(1000);ShadowBinder.setCallingPid(4321);
    }
    @After public void after() throws Exception {ShadowBinder.reset();set("armed",false);set("hostSession",null);service.onDestroy();}
    private void set(String name,Object value)throws Exception{Field field=LockRelayService.class.getDeclaredField(name);field.setAccessible(true);field.set(service,value);}
    private String[] state(int expected)throws Exception{
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try{request.writeInterfaceToken(LockProtocol.RELAY);assertTrue(service.onBind(new Intent()).transact(LockProtocol.RELAY_STATE,request,reply,0));reply.readException();assertEquals(expected,reply.readInt());String[] value={reply.readString(),reply.readString()};assertEquals(0,reply.dataAvail());return value;}
        finally{request.recycle();reply.recycle();}
    }
    @Test public void busySceneSurvivesRealHostAndRelayBinderRoundTrip()throws Exception{assertArrayEquals(new String[]{ticket,"INPUT_ONLY_NATIVE_PRIORITY_BUSY_EDIT"},state(0));}
    @Test public void unknownRenderSceneSurvivesRealHostAndRelayBinderRoundTrip()throws Exception{hostReason="UNKNOWN_STATE";assertArrayEquals(new String[]{ticket,"INPUT_ONLY_NATIVE_PRIORITY_UNKNOWN_STATE"},state(0));}
    @Test public void staleImageSceneSurvivesWithoutGrantingRenderReady()throws Exception{hostReason="STALE_TICKET";assertArrayEquals(new String[]{ticket,"INPUT_ONLY_NATIVE_PRIORITY_STALE_TICKET"},state(0));}
    @Test public void absentEngineCannotBorrowPreviousScene()throws Exception{state(0);hostReason="NO_ENGINE";assertArrayEquals(new String[]{"","NO_ENGINE"},state(0));}
    @Test public void malformedSceneCannotBecomeInputAuthority()throws Exception{hostTicket="legacy-ticket";assertArrayEquals(new String[]{"","BUSY_EDIT"},state(0));}
    @Test public void disabledPriorityCannotExposeBusyScene()throws Exception{assertTrue(WallpaperPrioritySetting.write(preferences,false));set("appliedForceRevision",LockRelayService.nativeInputState(service).revision);assertArrayEquals(new String[]{"","BUSY_EDIT"},state(0));}
    @Test public void unappliedPolicyCannotExposeBusyScene()throws Exception{set("appliedForceRevision",-1L);assertArrayEquals(new String[]{"","BUSY_EDIT"},state(0));}
    @Test public void disabledCompatibilityCannotExposeBusyScene()throws Exception{assertTrue(LockRelayService.setDesiredEnabled(service,false));assertEquals("",state(0)[0]);}
    @Test public void busyInputSceneDoesNotAdmitRenderCommand()throws Exception{
        assertEquals(ticket,state(0)[0]);Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try{request.writeInterfaceToken(LockProtocol.RELAY);request.writeInt(LockProtocol.NEXT);request.writeLong(1);request.writeLong(SystemClock.elapsedRealtime());request.writeLong(1);request.writeString("actor");request.writeString(ticket);assertTrue(service.onBind(new Intent()).transact(LockProtocol.RELAY_EVENT,request,reply,0));reply.readException();assertEquals(RelayAdmission.NOT_READY,reply.readInt());assertEquals(0,commands);}
        finally{request.recycle();reply.recycle();}
    }
    @Test public void recoveredRenderUsesFreshImageTicket()throws Exception{state(0);hostReady=true;hostReason="READY";hostTicket=LockProtocol.sceneTicket(LockProtocol.sceneId(ticket),"00000000-0000-0000-0000-000000000002");assertArrayEquals(new String[]{hostTicket,"READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY"},state(1));}
}
