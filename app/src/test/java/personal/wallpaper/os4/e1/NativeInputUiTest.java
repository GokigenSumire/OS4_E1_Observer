package personal.wallpaper.os4.e1;

import android.content.Context;
import android.widget.Switch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import personal.wallpaper.os4.e1.relay.LockRelayService;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class NativeInputUiTest {
    @Before public void before(){Context app=RuntimeEnvironment.getApplication();app.createDeviceProtectedStorageContext().getSharedPreferences("lock_compat_permanent",Context.MODE_PRIVATE).edit().clear().commit();assertTrue(LockRelayService.setNativeInputEnabled(app,true));}
    @Test public void openingShowsNativeDefaultWithoutEnablingLock(){ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=activity.get().getWindow().getDecorView().findViewWithTag("native_input_switch");assertNotNull(toggle);assertTrue(toggle.isChecked());assertFalse(LockRelayService.isDesiredEnabled(activity.get()));}finally{activity.destroy();}}
    @Test public void routeTogglePersistsAndKeepsLegacyIntent(){Context app=RuntimeEnvironment.getApplication();assertTrue(LockRelayService.setForceLocalEnabled(app,true));ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=activity.get().getWindow().getDecorView().findViewWithTag("native_input_switch");toggle.setChecked(false);assertFalse(LockRelayService.nativeInputState(activity.get()).enabled);assertTrue(LockRelayService.forceLocalState(activity.get()).enabled);assertFalse(LockRelayService.isDesiredEnabled(activity.get()));}finally{activity.destroy();}}
    @Test public void recreationReadsPersistedFallbackRatherThanSilentlyReenabling(){Context app=RuntimeEnvironment.getApplication();assertTrue(LockRelayService.setNativeInputEnabled(app,false));ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=activity.get().getWindow().getDecorView().findViewWithTag("native_input_switch");assertNotNull(toggle);assertFalse(toggle.isChecked());}finally{activity.destroy();}}
}
