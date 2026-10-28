package personal.wallpaper.os4.e1.relay;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class NativeInputSettingTest {
    private SharedPreferences preferences;
    @Before public void before(){Context app=RuntimeEnvironment.getApplication();app.createDeviceProtectedStorageContext().getSharedPreferences("lock_compat_permanent",Context.MODE_PRIVATE).edit().clear().commit();preferences=app.createDeviceProtectedStorageContext().getSharedPreferences("native-setting-test",Context.MODE_PRIVATE);preferences.edit().clear().commit();assertTrue(NativeInputSetting.write(preferences,true));preferences.edit().clear().commit();}
    @Test public void missingRouteDefaultsNativeWithoutEnablingLock(){var state=NativeInputSetting.read(preferences);assertTrue(state.available);assertTrue(state.enabled);assertEquals(0,state.revision);assertFalse(preferences.getBoolean("enabled",false));assertFalse(LockRelayService.isDesiredEnabled(RuntimeEnvironment.getApplication()));}
    @Test public void changingRouteAdvancesSharedRevisionButPreservesLegacyPreferences(){preferences.edit().putBoolean(ForceLocalSetting.ENABLED,true).putBoolean(WallpaperPrioritySetting.ENABLED,true).putBoolean("enabled",false).commit();assertTrue(NativeInputSetting.write(preferences,false));assertEquals(1,NativeInputSetting.read(preferences).revision);assertFalse(NativeInputSetting.read(preferences).enabled);assertTrue(preferences.getBoolean(ForceLocalSetting.ENABLED,false));assertTrue(preferences.getBoolean(WallpaperPrioritySetting.ENABLED,false));assertFalse(preferences.getBoolean("enabled",true));}
    @Test public void repeatedEnableStillCreatesNewPolicyGeneration(){assertTrue(NativeInputSetting.write(preferences,true));assertTrue(NativeInputSetting.write(preferences,true));assertEquals(2,NativeInputSetting.read(preferences).revision);}
    @Test public void failedCommitCannotAuthorizeItsInMemoryValue(){SharedPreferences failing=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{if(method.getName().equals("edit")){SharedPreferences.Editor editor=preferences.edit();final Object[] wrapper={null};wrapper[0]=Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(p,m,a)->{Object result=m.invoke(editor,a);if(m.getName().equals("commit"))return false;return result instanceof SharedPreferences.Editor?wrapper[0]:result;});return wrapper[0];}return method.invoke(preferences,args);});assertFalse(NativeInputSetting.write(failing,true));assertFalse(NativeInputSetting.read(preferences).available);assertFalse(NativeInputSetting.read(preferences).enabled);assertTrue(NativeInputSetting.write(preferences,false));assertTrue(NativeInputSetting.read(preferences).available);}
    @Test public void negativeOrOverflowRevisionFailsClosedWithoutWrapping(){for(long revision:new long[]{-1,Long.MAX_VALUE}){preferences.edit().putLong(ForceLocalSetting.REVISION,revision).commit();assertFalse(NativeInputSetting.write(preferences,true));assertFalse(NativeInputSetting.read(preferences).available);assertEquals(revision,preferences.getLong(ForceLocalSetting.REVISION,0));}}
    @Test public void corruptBooleanOrRevisionNeverDefaultsToEnabled(){preferences.edit().putString(NativeInputSetting.ENABLED,"true").commit();assertFalse(NativeInputSetting.read(preferences).available);preferences.edit().clear().putString(ForceLocalSetting.REVISION,"1").commit();assertFalse(NativeInputSetting.read(preferences).available);}
    @Test public void preferenceExceptionCannotAuthorizeRoute(){SharedPreferences throwing=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{throw new IllegalStateException("UNAVAILABLE");});assertFalse(NativeInputSetting.write(throwing,true));assertFalse(NativeInputSetting.read(throwing).available);}
}
