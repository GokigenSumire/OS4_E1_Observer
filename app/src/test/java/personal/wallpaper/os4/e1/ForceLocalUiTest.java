package personal.wallpaper.os4.e1;

import android.app.AlertDialog;
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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import personal.wallpaper.os4.e1.relay.LockRelayService;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class ForceLocalUiTest {
    @Before public void before(){RuntimeEnvironment.getApplication().createDeviceProtectedStorageContext().getSharedPreferences("lock_compat_permanent",Context.MODE_PRIVATE).edit().clear().commit();assertTrue(LockRelayService.setForceLocalEnabled(RuntimeEnvironment.getApplication(),false));}
    @Test public void dashboardDefaultsOffAndOpeningDoesNotEnable(){ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=controller.get().findViewById(android.R.id.content).findViewWithTag("force_local_switch");assertNotNull(toggle);assertFalse(toggle.isChecked());assertFalse(LockRelayService.forceLocalState(controller.get()).enabled);}finally{controller.destroy();}}
    @Test public void enablingRequiresExplicitConfirmationAndCancellationDoesNotSave(){ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=controller.get().getWindow().getDecorView().findViewWithTag("force_local_switch");toggle.setChecked(true);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertFalse(LockRelayService.forceLocalState(controller.get()).enabled);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertFalse(toggle.isChecked());assertFalse(LockRelayService.forceLocalState(controller.get()).enabled);}finally{controller.destroy();}}
    @Test public void confirmedModePersistsAndCanBeDisabled(){ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=controller.get().getWindow().getDecorView().findViewWithTag("force_local_switch");toggle.setChecked(true);ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();assertTrue("确认回调后应已保存",LockRelayService.forceLocalState(controller.get()).enabled);assertTrue(toggle.isChecked());toggle.setChecked(false);assertFalse(LockRelayService.forceLocalState(controller.get()).enabled);assertFalse(toggle.isChecked());}finally{controller.destroy();}}
}
