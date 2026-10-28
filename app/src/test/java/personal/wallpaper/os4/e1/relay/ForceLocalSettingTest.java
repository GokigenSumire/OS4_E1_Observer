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
public final class ForceLocalSettingTest {
    private SharedPreferences preferences;
    @Before public void clean(){preferences=RuntimeEnvironment.getApplication().createDeviceProtectedStorageContext().getSharedPreferences("force-setting-test",Context.MODE_PRIVATE);preferences.edit().clear().commit();assertTrue(ForceLocalSetting.write(preferences,false));preferences.edit().clear().commit();}
    @Test public void absentSettingIsConfirmedOff(){var state=ForceLocalSetting.read(preferences);assertTrue(state.available);assertFalse(state.enabled);assertEquals(0,state.revision);}
    @Test public void enableAndDisableAreDurableAndStrictlyIncreaseRevision(){assertTrue(ForceLocalSetting.write(preferences,true));var enabled=ForceLocalSetting.read(preferences);assertTrue(enabled.available);assertTrue(enabled.enabled);assertEquals(1,enabled.revision);assertTrue(ForceLocalSetting.write(preferences,false));var disabled=ForceLocalSetting.read(preferences);assertTrue(disabled.available);assertFalse(disabled.enabled);assertEquals(2,disabled.revision);}
    @Test public void sameValueStillCreatesNewGeneration(){assertTrue(ForceLocalSetting.write(preferences,true));assertTrue(ForceLocalSetting.write(preferences,true));assertEquals(2,ForceLocalSetting.read(preferences).revision);}
    @Test public void revisionCannotWrapOrAcceptNegativeStoredValue(){for(long value:new long[]{Long.MAX_VALUE,-1}){preferences.edit().putLong(ForceLocalSetting.REVISION,value).commit();assertFalse(ForceLocalSetting.write(preferences,true));assertFalse(ForceLocalSetting.read(preferences).available);}preferences.edit().clear().commit();assertTrue(ForceLocalSetting.write(preferences,false));}
    @Test public void failedCommitMemoryReadCannotBecomeAuthorization(){
        SharedPreferences failing=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(method.getName().equals("edit")){SharedPreferences.Editor editor=preferences.edit();final Object[] wrapper={null};wrapper[0]=Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(p,m,a)->{Object result=m.invoke(editor,a);if(m.getName().equals("commit"))return false;return result instanceof SharedPreferences.Editor?wrapper[0]:result;});return wrapper[0];}
            return method.invoke(preferences,args);
        });
        assertFalse(ForceLocalSetting.write(failing,true));assertTrue(preferences.getBoolean(ForceLocalSetting.ENABLED,false));assertFalse(ForceLocalSetting.read(preferences).available);assertFalse(ForceLocalSetting.read(preferences).enabled);assertTrue(ForceLocalSetting.write(preferences,false));assertTrue(ForceLocalSetting.read(preferences).available);
    }
}
