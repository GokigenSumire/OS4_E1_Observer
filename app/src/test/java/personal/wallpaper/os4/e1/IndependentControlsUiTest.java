package personal.wallpaper.os4.e1;

import android.content.Context;
import android.widget.Switch;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import personal.wallpaper.os4.e1.relay.LockRelayService;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class IndependentControlsUiTest {
    @Before public void clear(){Context app=RuntimeEnvironment.getApplication();assertTrue(CompatPreferenceStore.setEnabled(app,false));assertTrue(LockRelayService.setDesiredEnabled(app,false));}
    @Test public void missingSystemResponseNeverDisablesDesktopSwitch(){
        var activity=Robolectric.buildActivity(MainActivity.class).create();try{
            Switch home=activity.get().findViewById(android.R.id.content).findViewWithTag("home_switch");
            assertEquals("桌面",home.getText().toString());assertTrue(home.isEnabled());
            home.setChecked(true);assertTrue(CompatPreferenceStore.read(activity.get()).enabled);assertTrue(home.isEnabled());
            home.setChecked(false);assertFalse(CompatPreferenceStore.read(activity.get()).enabled);assertTrue(home.isEnabled());
        }finally{activity.destroy();}
    }
    @Test public void homeOperationsDoNotChangeLockChoiceOrStatus(){
        var activity=Robolectric.buildActivity(MainActivity.class).create();try{
            Switch home=activity.get().getWindow().getDecorView().findViewWithTag("home_switch");
            Switch lock=activity.get().getWindow().getDecorView().findViewWithTag("lock_switch");
            TextView lockState=activity.get().getWindow().getDecorView().findViewWithTag("lock_status");
            lock.setChecked(true);String before=lockState.getText().toString();
            home.setChecked(true);home.setChecked(false);
            assertTrue(lock.isChecked());assertTrue(lock.isEnabled());assertTrue(LockRelayService.isDesiredEnabled(activity.get()));assertEquals(before,lockState.getText().toString());
        }finally{activity.destroy();}
    }
    @Test public void lockOperationsDoNotChangeDesktopChoice(){
        var activity=Robolectric.buildActivity(MainActivity.class).create();try{
            Switch home=activity.get().getWindow().getDecorView().findViewWithTag("home_switch");
            Switch lock=activity.get().getWindow().getDecorView().findViewWithTag("lock_switch");
            home.setChecked(true);long revision=CompatPreferenceStore.read(activity.get()).revision;
            lock.setChecked(true);lock.setChecked(false);
            assertTrue(home.isChecked());assertTrue(home.isEnabled());assertTrue(CompatPreferenceStore.read(activity.get()).enabled);assertEquals(revision,CompatPreferenceStore.read(activity.get()).revision);
        }finally{activity.destroy();}
    }
    @Test public void allFourSavedCombinationsRemainIndependentAcrossRecreation(){
        Context app=RuntimeEnvironment.getApplication();
        for(boolean home:new boolean[]{false,true})for(boolean lock:new boolean[]{false,true}){
            assertTrue(CompatPreferenceStore.setEnabled(app,home));assertTrue(LockRelayService.setDesiredEnabled(app,lock));
            var activity=Robolectric.buildActivity(MainActivity.class).create();try{
                Switch desktop=activity.get().getWindow().getDecorView().findViewWithTag("home_switch");
                Switch screen=activity.get().getWindow().getDecorView().findViewWithTag("lock_switch");
                assertEquals(home,desktop.isChecked());assertEquals(lock,screen.isChecked());assertTrue(desktop.isEnabled());assertTrue(screen.isEnabled());
            }finally{activity.destroy();}
        }
    }
    @Test public void copyVersionKeepsRealLineBreaksAndIdentity(){
        var activity=Robolectric.buildActivity(MainActivity.class).create();try{
            android.widget.Button copy=findButton(activity.get().getWindow().getDecorView(),activity.get().getString(R.string.about_copy_version));
            assertNotNull(copy);copy.performClick();
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.get().getSystemService(Context.CLIPBOARD_SERVICE);
            String value=clipboard.getPrimaryClip().getItemAt(0).getText().toString();
            assertTrue(value.contains("\n"));assertFalse(value.contains("\\n"));assertTrue(value.contains(BuildConfig.APPLICATION_ID));
        }finally{activity.destroy();}
    }
    private static android.widget.Button findButton(android.view.View view,String label){
        if(view instanceof android.widget.Button && label.contentEquals(((android.widget.Button)view).getText()))return (android.widget.Button)view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){android.widget.Button result=findButton(group.getChildAt(i),label);if(result!=null)return result;}
        }
        return null;
    }
}
