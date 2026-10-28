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
public final class WallpaperPriorityUiTest {
    @Before public void before(){var app=RuntimeEnvironment.getApplication();app.createDeviceProtectedStorageContext().getSharedPreferences("lock_compat_permanent",Context.MODE_PRIVATE).edit().clear().commit();assertTrue(LockRelayService.setWallpaperPriorityEnabled(app,false));assertTrue(LockRelayService.setForceLocalEnabled(app,false));}
    @Test public void openingDoesNotEnablePriorityOrLocalMode(){ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=activity.get().getWindow().getDecorView().findViewWithTag("wallpaper_priority_switch");assertNotNull(toggle);assertFalse(toggle.isChecked());assertFalse(LockRelayService.wallpaperPriorityState(activity.get()).enabled);assertFalse(LockRelayService.forceLocalState(activity.get()).enabled);}finally{activity.destroy();}}
    @Test public void inputFirstLabelDisclosesPriorityAndNormalEditorUnlock(){ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=activity.get().getWindow().getDecorView().findViewWithTag("wallpaper_priority_switch");assertTrue(toggle.getText().toString().contains("最高优先级"));toggle.setChecked(true);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();android.widget.TextView message=dialog.findViewById(android.R.id.message);assertNotNull(message);assertTrue(message.getText().toString().contains("打开图片编辑仍须系统正常解锁"));assertTrue(message.getText().toString().contains("系统动作可能同时响应"));}finally{activity.destroy();}}
    @Test public void cancellationDoesNotSaveAndConfirmationRequired(){ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).create();try{Switch toggle=activity.get().getWindow().getDecorView().findViewWithTag("wallpaper_priority_switch");toggle.setChecked(true);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertFalse(LockRelayService.wallpaperPriorityState(activity.get()).enabled);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();ShadowLooper.idleMainLooper();assertFalse(toggle.isChecked());assertFalse(LockRelayService.wallpaperPriorityState(activity.get()).enabled);}finally{activity.destroy();}}
    @Test public void confirmationPersistsSeparateIntentAndDisableRestoresOldChoice(){ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).create();try{assertTrue(LockRelayService.setForceLocalEnabled(activity.get(),true));Switch toggle=activity.get().getWindow().getDecorView().findViewWithTag("wallpaper_priority_switch");toggle.setChecked(true);ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();assertTrue(LockRelayService.wallpaperPriorityState(activity.get()).enabled);toggle.setChecked(false);assertFalse(LockRelayService.wallpaperPriorityState(activity.get()).enabled);assertTrue(LockRelayService.forceLocalState(activity.get()).enabled);}finally{activity.destroy();}}
}
