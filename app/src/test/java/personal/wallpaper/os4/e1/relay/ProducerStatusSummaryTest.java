package personal.wallpaper.os4.e1.relay;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProducerStatusSummaryTest {
    @Test public void absentProducerDoesNotInventSourceLoaderOrHookFailure() {
        String absent = ProducerStatusSummary.describe(0, 0);
        assertEquals("输入进程未登记（加载或鉴别尚未证实）", absent);
        assertFalse(absent.contains("失败"));
        assertEquals(absent, ProducerStatusSummary.describe(0, 10));
        assertEquals(absent, ProducerStatusSummary.describe(0, 14));
    }
    @Test public void allKnownReasonsMapToFixedNonemptyDescription() {
        for (int i = 0; i <= 17; i++) {
            String value = ProducerStatusSummary.describe(1, i);
            assertNotNull(value); assertFalse(value.isEmpty());
            assertFalse(value.contains("content:")); assertFalse(value.contains("/storage"));
        }
    }
    @Test public void malformedPresenceOrReasonFailsToUnknown() {
        assertEquals("输入状态未知", ProducerStatusSummary.describe(-1, 1));
        assertEquals("输入状态未知", ProducerStatusSummary.describe(2, 1));
        assertEquals("输入状态未知", ProducerStatusSummary.describe(1, -1));
        assertEquals("输入状态未知", ProducerStatusSummary.describe(1, 18));
        assertEquals("输入状态未知", ProducerStatusSummary.describe(0, Integer.MAX_VALUE));
    }
    @Test public void admittedBackgroundIsNotReportedAsGestureSuccess() {
        assertEquals("背景区域已准入（未代表手势通过）", ProducerStatusSummary.describe(1, 3));
    }
    @Test public void acceptedCommandIsNotReportedAsCommittedImage() {
        assertEquals("命令已接收（未代表切图已提交）", ProducerStatusSummary.describe(1, 14));
    }
    @Test public void editEntryRequestDoesNotClaimAuthenticationSuccess() {
        assertEquals("已请求系统认证入口（未代表认证成功）", ProducerStatusSummary.describe(1, 16));
    }
    @Test public void blockersAreDistinctWithoutCredentialsOrTouchData() {
        assertNotEquals(ProducerStatusSummary.describe(1, 5), ProducerStatusSummary.describe(1, 6));
        assertNotEquals(ProducerStatusSummary.describe(1, 8), ProducerStatusSummary.describe(1, 10));
        assertEquals("系统认证期间暂停识别", ProducerStatusSummary.describe(1, 5));
        assertEquals("安全背景区域为空", ProducerStatusSummary.describe(1, 8));
    }
    @Test public void hiddenHostStillShowsRecentRoiFailureWithoutClaimingCurrentReady() {
        assertEquals("UNKNOWN_STATE；输入最近状态：安全背景区域为空",
                ProducerStatusSummary.compose(false, "UNKNOWN_STATE", true, ProducerStatusSummary.describe(1, 8)));
    }
    @Test public void busyAuthenticationStillShowsRecentInputAndDoesNotImplyRevocation() {
        assertEquals("BUSY_EDIT；输入最近状态：系统认证期间暂停识别",
                ProducerStatusSummary.compose(false, "BUSY_EDIT", true, ProducerStatusSummary.describe(1, 5)));
    }
    @Test public void readyHostLabelsInputAsRecentAndNewSessionCanStartUnknown() {
        assertEquals("主LOCK就绪；输入最近状态：当前连接尚未读取输入状态",
                ProducerStatusSummary.compose(true, "READY", true, "当前连接尚未读取输入状态"));
        assertEquals("主LOCK就绪（输入连接尚未开启）；输入最近状态：尚未读取",
                ProducerStatusSummary.compose(true, "READY", false, null));
    }
}
