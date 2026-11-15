package personal.wallpaper.os4.e1;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CompatTargetPresentationTest {
    @Test public void desktopShowsActualAndSavedStateSeparately(){
        var off=new CompatPreferenceStore.State(false,true,2);
        var on=new CompatPreferenceStore.State(true,true,3);
        assertEquals("关闭待确认 · 当前仍生效",CompatTargetPresentation.desktop(DesktopToggleControllerTest.active(false),off,false).text);
        assertFalse(CompatTargetPresentation.desktop(DesktopToggleControllerTest.active(false),off,false).positive);
        assertTrue(CompatTargetPresentation.desktop(DesktopToggleControllerTest.active(true),on,false).positive);
        assertEquals("已保存开启 · 等待应用",CompatTargetPresentation.desktop(DesktopToggleControllerTest.ready(true),on,false).text);
    }
    @Test public void pendingOrUnknownSettingNeverClaimsEffective(){
        assertFalse(CompatTargetPresentation.desktop(DesktopToggleControllerTest.active(true),new CompatPreferenceStore.State(true,true,1),true).positive);
        assertFalse(CompatTargetPresentation.desktop(DesktopToggleControllerTest.active(true),new CompatPreferenceStore.State(true,false,1),false).positive);
    }
    @Test public void recoveryDoesNotSaySwitchIsLocked(){
        String text=CompatTargetPresentation.desktop(DesktopToggleControllerTest.recovering(),new CompatPreferenceStore.State(true,true,1),false).text;
        assertTrue(text.contains("可修改"));assertFalse(text.contains("锁定"));
    }
    @Test public void lockConnectionAloneIsNotProofOfActiveGestures(){
        var state=CompatTargetPresentation.lock(true,"主LOCK就绪；输入最近状态：已连接");
        assertTrue(state.text.contains("就绪"));assertFalse(state.positive);assertFalse(state.text.contains("LOCK"));
    }
    @Test public void lockSaveFailureSurvivesOffDisplay(){assertEquals("锁屏设置未保存，请重试",CompatTargetPresentation.lock(false,"SAVE_UNCONFIRMED").text);}
    @Test public void unknownAndMissingLockHostHaveOwnShortState(){
        assertTrue(CompatTargetPresentation.lock(true,"HOST_UNAVAILABLE").text.contains("锁屏连接"));
        assertTrue(CompatTargetPresentation.lock(false,"HOST_UNAVAILABLE").positive);
    }
}
