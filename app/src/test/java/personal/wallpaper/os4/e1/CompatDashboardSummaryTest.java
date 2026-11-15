package personal.wallpaper.os4.e1;
import org.junit.Test;
import static org.junit.Assert.*;
public class CompatDashboardSummaryTest {
    @Test public void lockOnlyDoesNotDependOnHomeWaiting() {
        var s=CompatDashboardSummary.from(true,false,true,"等待桌面", "桌面详情", "主LOCK就绪；输入尚未读取");
        assertEquals("锁屏兼容",s.title); assertFalse(s.body.contains("等待桌面"));
        assertTrue(s.body.contains("输入尚未读取")); assertFalse(s.body.contains("已生效"));
    }
    @Test public void bothShowIndependentStates() {
        var s=CompatDashboardSummary.from(true,true,true,"等待桌面", "详情", "HOST_UNAVAILABLE");
        assertTrue(s.body.contains("等待桌面")); assertTrue(s.body.contains("HOST_UNAVAILABLE"));
    }
    @Test public void homeOnlyDoesNotShowDisabledLockErrors() {
        var s=CompatDashboardSummary.from(true,true,false,"已开启", "详情", "OLD_ERROR");
        assertEquals("桌面兼容",s.title); assertFalse(s.body.contains("OLD_ERROR"));
    }
    @Test public void savedOffCannotHideRecoveryFailure() {
        var s=CompatDashboardSummary.from(true,false,false,"恢复未确认", "请停止", "OFF");
        assertTrue(s.body.contains("恢复未确认")); assertTrue(s.body.contains("请停止"));
    }
    @Test public void unavailablePreferencesRemainUnknownAndLockStillVisible() {
        var s=CompatDashboardSummary.from(false,false,true,"已关闭", "详情", "SAVE_UNCONFIRMED");
        assertTrue(s.title.contains("待确认")); assertTrue(s.body.contains("SAVE_UNCONFIRMED"));
    }
    @Test public void missingLockStatusNeverClaimsReady() {
        assertTrue(CompatDashboardSummary.from(true,false,true,"已关闭","详情",null).body.contains("尚未读取"));
    }
}
