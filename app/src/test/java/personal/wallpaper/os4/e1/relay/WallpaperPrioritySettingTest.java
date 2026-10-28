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
public final class WallpaperPrioritySettingTest {
    private SharedPreferences preferences;
    @Before public void before(){preferences=RuntimeEnvironment.getApplication().createDeviceProtectedStorageContext().getSharedPreferences("priority-setting-test",Context.MODE_PRIVATE);preferences.edit().clear().commit();assertTrue(WallpaperPrioritySetting.write(preferences,false));preferences.edit().clear().commit();assertTrue(ForceLocalSetting.write(preferences,false));preferences.edit().clear().commit();}
    @Test public void absentSettingDefaultsOff(){var state=WallpaperPrioritySetting.read(preferences);assertTrue(state.available);assertFalse(state.enabled);assertEquals(0,state.revision);}
    @Test public void independentDesiredModeDoesNotEnableLocalForceOrLock(){assertTrue(WallpaperPrioritySetting.write(preferences,true));assertTrue(WallpaperPrioritySetting.read(preferences).enabled);assertFalse(ForceLocalSetting.read(preferences).enabled);assertFalse(preferences.getBoolean("enabled",false));}
    @Test public void eitherModeChangeAdvancesSharedRevisionWithoutOverwritingOther(){assertTrue(ForceLocalSetting.write(preferences,true));assertTrue(WallpaperPrioritySetting.write(preferences,true));assertEquals(2,WallpaperPrioritySetting.read(preferences).revision);assertTrue(ForceLocalSetting.read(preferences).enabled);assertTrue(WallpaperPrioritySetting.write(preferences,false));assertEquals(3,ForceLocalSetting.read(preferences).revision);assertTrue(ForceLocalSetting.read(preferences).enabled);assertFalse(WallpaperPrioritySetting.read(preferences).enabled);}
    @Test public void failedCommitCannotAuthorizeFromInMemoryCache(){
        SharedPreferences failing=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(method.getName().equals("edit")){SharedPreferences.Editor editor=preferences.edit();final Object[] wrapper={null};wrapper[0]=Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(p,m,a)->{Object result=m.invoke(editor,a);if(m.getName().equals("commit"))return false;return result instanceof SharedPreferences.Editor?wrapper[0]:result;});return wrapper[0];}return method.invoke(preferences,args);
        });
        assertFalse(WallpaperPrioritySetting.write(failing,true));assertTrue(preferences.getBoolean(WallpaperPrioritySetting.ENABLED,false));assertFalse(WallpaperPrioritySetting.read(preferences).available);assertFalse(WallpaperPrioritySetting.read(preferences).enabled);assertTrue(WallpaperPrioritySetting.write(preferences,false));assertTrue(WallpaperPrioritySetting.read(preferences).available);
    }
    @Test public void revisionsDoNotWrapAndMalformedTypesReject(){for(long value:new long[]{-1,Long.MAX_VALUE}){preferences.edit().putLong(ForceLocalSetting.REVISION,value).commit();assertFalse(WallpaperPrioritySetting.write(preferences,true));assertFalse(WallpaperPrioritySetting.read(preferences).available);}preferences.edit().clear().commit();assertTrue(WallpaperPrioritySetting.write(preferences,false));preferences.edit().putString(WallpaperPrioritySetting.ENABLED,"true").commit();assertFalse(WallpaperPrioritySetting.read(preferences).available);}
    @Test public void repeatedEnableIsStillNewGeneration(){assertTrue(WallpaperPrioritySetting.write(preferences,true));assertTrue(WallpaperPrioritySetting.write(preferences,true));assertEquals(2,WallpaperPrioritySetting.read(preferences).revision);}
}
