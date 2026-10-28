package personal.wallpaper.os4.e1.core;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ObservationBufferTest {
    private static final String SESSION = "e1-m0abc-42";

    @Test public void declaredProductionLimitsAreExact() {
        assertEquals(200_000L, ObservationBuffer.MAX_DURATION_MS);
        assertEquals(6_000, ObservationBuffer.MAX_ROWS);
        assertEquals(1_572_864, ObservationBuffer.MAX_BYTES);
        assertEquals(1_024, ObservationBuffer.MAX_LINE_BYTES);
    }

    @Test public void initialStateHasNoFabricatedObservations() {
        ObservationBuffer buffer = new ObservationBuffer();
        assertFalse(buffer.active(10));
        assertEquals(0, buffer.size());
        assertEquals("active=false session=NONE reason=NOT_STARTED rows=0 bytes=0"
                + " dropped=0 rejected=0 elapsedMs=0", buffer.status(10));
        assertTrue(buffer.page(0, 10).isEmpty());
    }

    @Test public void normalRecordIsStableAndSorted() {
        ObservationBuffer buffer = started();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("touch", "UNKNOWN");
        values.put("dup", "false");
        values.put("target", "true");
        assertTrue(buffer.record(1_023, "SNAPSHOT", 7, values));
        String row = buffer.page(0, 1).get(0);
        assertEquals("seq=1 elapsedMs=23 session=" + SESSION
                + " point=SNAPSHOT generation=7 dup=false target=true touch=UNKNOWN", row);
        assertEquals(row.getBytes(StandardCharsets.UTF_8).length + 1,
                number(buffer.status(1_023), "bytes"));
    }

    @Test public void orderOfCallerMapDoesNotChangeEncoding() {
        ObservationBuffer first = started();
        ObservationBuffer second = started();
        Map<String, String> a = new LinkedHashMap<>();
        a.put("touch", "true"); a.put("dup", "0x20");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("dup", "0x20"); b.put("touch", "true");
        assertTrue(first.record(1_001, "SNAPSHOT", 0, a));
        assertTrue(second.record(1_001, "SNAPSHOT", 0, b));
        assertEquals(first.page(0, 1), second.page(0, 1));
    }

    @Test public void nullableObservationRemainsUnknown() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(1_000, "BOOL_GETTER", -1, scalar("touch", null)));
        assertTrue(buffer.page(0, 1).get(0).endsWith(" touch=UNKNOWN"));
        assertFalse(buffer.page(0, 1).get(0).contains("touch=false"));
    }

    @Test public void absentObservationIsNotAddedAsFalse() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertFalse(buffer.page(0, 1).get(0).contains("touch="));
    }

    @Test public void acceptsOnlyListedPointNames() {
        String[] points = {"SNAPSHOT", "POPULATE_BEFORE", "POPULATE_AFTER",
                "TARGET_RESULT", "KEYGUARD_RESULT", "BOOL_GETTER", "HAS_BEFORE",
                "HAS_AFTER", "MASK_BEFORE", "MASK_AFTER", "PUBLISH_BEFORE",
                "PUBLISH_AFTER", "SURFACE_SET_BEFORE", "SURFACE_SET_AFTER",
                "WMS_RELAYOUT_AFTER", "WMS_ADD_AFTER", "COPY_BEFORE", "COPY_AFTER",
                "PHASE_CHANGE", "OVERRIDE", "RECOVERY_RESULT", "USER_MARK"};
        ObservationBuffer buffer = started();
        for (String point : points) {
            assertTrue(point, buffer.record(1_000, point, 0, Collections.emptyMap()));
        }
        assertEquals(points.length, buffer.size());
        assertFalse(buffer.record(1_000, "SOME_NEW_POINT", 0, Collections.emptyMap()));
        assertTrue(buffer.active(1_000));
    }

    @Test public void acceptsEveryListedKey() {
        String[] keys = {"touch", "target", "keyguard", "dup", "inputConfig",
                "changed", "requested", "mask", "value", "sourceTouch", "destTouch", "arrivalTouch",
                "returnCode", "threw", "qualified", "showWallpaper", "hasSurface",
                "visible", "focus", "copyChanges", "submitted", "homeUnique",
                "phase", "original", "effective", "applied", "recovery", "mark"};
        ObservationBuffer buffer = started();
        for (String key : keys) {
            assertTrue(key, buffer.record(1_000, "SNAPSHOT", 0, scalar(key, "UNKNOWN")));
        }
        assertEquals(keys.length, buffer.size());
    }

    @Test public void arrivalTouchIsExplicitlyAllowedButPrivateVariantsAreRejected() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(1_000, "COPY_BEFORE", 0, scalar("arrivalTouch", "UNKNOWN")));
        assertTrue(buffer.record(1_000, "COPY_BEFORE", 0, scalar("arrivalTouch", "true")));
        assertTrue(buffer.page(0, 1).get(0).endsWith("arrivalTouch=UNKNOWN"));
        assertFalse(buffer.record(1_000, "COPY_BEFORE", 0, scalar("arrival_touch", "UNKNOWN")));
        assertFalse(buffer.record(1_000, "COPY_BEFORE", 0,
                scalar("arrivalTouch", "PRIVATE_PHOTO_TITLE")));
        assertEquals(2, buffer.size());
        assertEquals(2, number(buffer.status(1_000), "rejected"));
    }

    @Test public void acceptsSignedLongAndHexAndFixedEnumValues() {
        String[] values = {"true", "false", "UNKNOWN", "0", "-1", "42",
                "9223372036854775807", "-9223372036854775808", "0x0", "0xABCdef",
                "0xFFFFFFFFFFFFFFFF", "WMS_RELAYOUT", "WMS_ADD", "OTHER", "POPULATE",
                "SNAPSHOT", "NOT_OBSERVED", "JAVA_CALL", "JAVA_COMPLETED",
                "BASELINE", "ENABLE", "RECOVERY", "DISARMED", "CONFIRMED", "UNCONFIRMED",
                "PENDING", "DOUBLE", "TRIPLE", "TWO", "LOCK", "UNLOCK", "SWIPE", "ICON",
                "EDITOR", "PREVIEW"};
        ObservationBuffer buffer = started();
        for (String value : values) {
            assertTrue(value, buffer.record(1_000, "MASK_BEFORE", 0, scalar("value", value)));
        }
        assertEquals(values.length, buffer.size());
    }

    @Test public void rejectsOutOfRangeAndNonScalarValues() {
        String[] values = {"", " ", "True", "FALSE", "null", "1.0", "NaN", "+1",
                "-", "0x", "0xGG", "0X20", "0x10000000000000000",
                "9223372036854775808", "-9223372036854775809", "A_NEW_ENUM",
                "1\nmask=0", "true false", "true\tfalse", "true\u0000", "１２３"};
        ObservationBuffer buffer = started();
        for (String value : values) {
            assertFalse(value, buffer.record(1_000, "SNAPSHOT", 0, scalar("touch", value)));
        }
        assertEquals(0, buffer.size());
        assertEquals(values.length, number(buffer.status(1_000), "rejected"));
        assertEquals(values.length, number(buffer.status(1_000), "dropped"));
    }

    @Test public void rejectsPathsTokensTitlesAndThrowableMessagesEntirely() {
        String[] privateValues = {"/storage/emulated/0/private.jpg", "C:\\private\\photo.jpg",
                "content://private/photo", "secret-token-42", "PRIVATE_PHOTO_TITLE",
                "java.lang.IllegalStateException: PRIVATE MESSAGE", "我的私人相册"};
        String[] privateKeys = {"path", "token", "title", "message", "throwable",
                "uri", "object", "stackTrace", "seq", "session", "elapsedMs"};
        ObservationBuffer buffer = started();
        for (String value : privateValues) {
            assertFalse(buffer.record(1_000, "SNAPSHOT", 0, scalar("value", value)));
        }
        for (String key : privateKeys) {
            Map<String, String> values = scalar(key, "true");
            values.put("touch", "UNKNOWN");
            assertFalse(buffer.record(1_000, "SNAPSHOT", 0, values));
        }
        assertEquals(0, buffer.size());
        String status = buffer.status(1_000);
        for (String value : privateValues) assertFalse(status.contains(value));
        assertTrue(buffer.active(1_000));
    }

    @Test public void rejectsPrivateOrNullPointWithoutEchoingIt() {
        ObservationBuffer buffer = started();
        assertFalse(buffer.record(1_000, "/storage/private.jpg", 0, Collections.emptyMap()));
        assertFalse(buffer.record(1_000, null, 0, Collections.emptyMap()));
        assertEquals(0, buffer.size());
        assertFalse(buffer.status(1_000).contains("private"));
    }

    @Test public void nullMapAndNullKeyAreRejected() {
        ObservationBuffer buffer = started();
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, null));
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, scalar(null, "true")));
        assertEquals(2, number(buffer.status(1_000), "rejected"));
        assertEquals(0, buffer.size());
    }

    @Test public void invalidPayloadDoesNotAdvanceSequence() {
        ObservationBuffer buffer = started();
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, scalar("title", "UNKNOWN")));
        assertTrue(buffer.record(1_001, "SNAPSHOT", 0, Collections.emptyMap()));
        assertTrue(buffer.page(0, 1).get(0).startsWith("seq=1 "));
    }

    @Test public void oversizedMapIsRejectedBeforeIteration() {
        ObservationBuffer buffer = started();
        Map<String, String> oversized = new AbstractMap<String, String>() {
            @Override public int size() { return Integer.MAX_VALUE; }
            @Override public Set<Entry<String, String>> entrySet() {
                throw new AssertionError("ITERATION_MUST_NOT_HAPPEN");
            }
        };
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, oversized));
        assertEquals(0, buffer.size());
    }

    @Test public void mapRuntimeFailureCannotEscapeOrLeakMessage() {
        ObservationBuffer buffer = started();
        Map<String, String> failing = new AbstractMap<String, String>() {
            @Override public Set<Entry<String, String>> entrySet() {
                throw new IllegalStateException("PRIVATE PHOTO TITLE");
            }
        };
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, failing));
        assertEquals(1, number(buffer.status(1_000), "rejected"));
        assertFalse(buffer.status(1_000).contains("PRIVATE"));
        assertTrue(buffer.active(1_000));
    }

    @Test public void callerMutationDoesNotAlterRecordedRow() {
        ObservationBuffer buffer = started();
        Map<String, String> values = scalar("touch", "UNKNOWN");
        assertTrue(buffer.record(1_000, "SNAPSHOT", 0, values));
        values.put("touch", "false");
        values.put("title", "SECRET");
        assertTrue(buffer.page(0, 1).get(0).endsWith("touch=UNKNOWN"));
    }

    @Test public void rejectsUnsafeSessionIdentifiersWithoutSavingThem() {
        String[] sessions = {null, "", "private photo", "../path", "content://uri",
                "title=secret", "id\nsecret", "id\tsecret", "中文", "id\u0000",
                repeated('a', 65)};
        for (String session : sessions) {
            ObservationBuffer buffer = new ObservationBuffer();
            assertFalse(buffer.start(0, session));
            assertFalse(buffer.active(0));
            assertTrue(buffer.status(0).contains("session=NONE reason=INVALID_SESSION"));
        }
    }

    @Test public void acceptsSafeSessionAtMaximumLength() {
        ObservationBuffer buffer = new ObservationBuffer();
        String session = repeated('a', 62) + "_-";
        assertTrue(buffer.start(0, session));
        assertTrue(buffer.status(0).contains("session=" + session + " "));
    }

    @Test public void negativeStartClockIsRejected() {
        ObservationBuffer buffer = new ObservationBuffer();
        assertFalse(buffer.start(-1, SESSION));
        assertTrue(buffer.status(0).contains("reason=ERROR"));
        assertEquals(0, buffer.size());
    }

    @Test public void repeatedStartWhileActiveKeepsOriginalCapture() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(1_001, "SNAPSHOT", 0, Collections.emptyMap()));
        assertFalse(buffer.start(1_002, "e1-other"));
        assertEquals(1, buffer.size());
        assertTrue(buffer.status(1_002).contains("session=" + SESSION + " reason=RUNNING"));
    }

    @Test public void invalidRestartStopsButDoesNotErasePriorEvidence() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(1_001, "SNAPSHOT", 0, scalar("touch", "UNKNOWN")));
        assertFalse(buffer.start(1_002, "/PRIVATE/PATH"));
        assertFalse(buffer.active(1_002));
        assertEquals(1, buffer.size());
        assertTrue(buffer.status(1_002).contains("reason=INVALID_SESSION"));
        assertFalse(buffer.status(1_002).contains("PRIVATE"));
    }

    @Test public void safeRestartResetsDataAndCounters() {
        ObservationBuffer buffer = started();
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, scalar("title", "UNKNOWN")));
        assertTrue(buffer.record(1_001, "SNAPSHOT", 0, Collections.emptyMap()));
        buffer.stop("USER_STOP");
        assertFalse(buffer.record(1_001, "SNAPSHOT", 0, Collections.emptyMap()));
        assertTrue(buffer.start(2_000, "e1-second"));
        assertEquals("active=true session=e1-second reason=RUNNING rows=0 bytes=0"
                + " dropped=0 rejected=0 elapsedMs=0", buffer.status(2_000));
        assertTrue(buffer.record(2_001, "SNAPSHOT", 0, Collections.emptyMap()));
        assertTrue(buffer.page(0, 1).get(0).startsWith("seq=1 elapsedMs=1 "));
    }

    @Test public void userStopFreezesReasonAndElapsed() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(1_023, "SNAPSHOT", 0, Collections.emptyMap()));
        buffer.stop("USER_STOP");
        buffer.stop("ERROR");
        assertFalse(buffer.active(Long.MAX_VALUE));
        assertTrue(buffer.status(Long.MAX_VALUE).endsWith("elapsedMs=23"));
        assertTrue(buffer.status(Long.MAX_VALUE).contains("reason=USER_STOP"));
    }

    @Test public void stopBeforeStartKeepsInitialState() {
        ObservationBuffer buffer = new ObservationBuffer();
        buffer.stop("USER_STOP");
        assertTrue(buffer.status(0).contains("reason=NOT_STARTED"));
    }

    @Test public void acceptsAllFixedStopReasons() {
        String[] reasons = {"USER_STOP", "TIMEOUT", "LIMIT_ROWS", "LIMIT_BYTES",
                "LIMIT_LINE", "ERROR", "AMBIGUOUS_HOME", "UNHOOK_REQUESTED", "INVALID_SESSION"};
        for (String reason : reasons) {
            ObservationBuffer buffer = started();
            buffer.stop(reason);
            assertFalse(buffer.active(1_000));
            assertTrue(buffer.status(1_000).contains("reason=" + reason + " "));
        }
    }

    @Test public void arbitraryStopReasonBecomesFixedErrorWithoutLeak() {
        for (String reason : new String[]{null, "OTHER_REASON", "PRIVATE PHOTO TITLE",
                "RuntimeException: private/path", "USER_STOP\nSECRET"}) {
            ObservationBuffer buffer = started();
            buffer.stop(reason);
            assertTrue(buffer.status(1_000).contains("reason=ERROR"));
            assertFalse(buffer.status(1_000).contains("PRIVATE"));
            assertFalse(buffer.status(1_000).contains("SECRET"));
        }
    }

    @Test public void backwardClockNeverDecreasesElapsed() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(1_050, "SNAPSHOT", 0, Collections.emptyMap()));
        assertTrue(buffer.record(1_001, "SNAPSHOT", 0, Collections.emptyMap()));
        assertTrue(buffer.record(-1, "SNAPSHOT", 0, Collections.emptyMap()));
        for (String row : buffer.page(0, 3)) assertTrue(row.contains("elapsedMs=50 "));
        assertEquals(50, number(buffer.status(0), "elapsedMs"));
    }

    @Test public void exactTimeoutStopsAndDropsIncomingRecord() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(200_999, "SNAPSHOT", 0, Collections.emptyMap()));
        assertFalse(buffer.record(201_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertFalse(buffer.active(201_000));
        assertEquals(1, buffer.size());
        assertTrue(buffer.status(201_000).contains("reason=TIMEOUT"));
        assertEquals(1, number(buffer.status(201_000), "dropped"));
        assertEquals(0, number(buffer.status(201_000), "rejected"));
        assertEquals(200_000, number(buffer.status(201_000), "elapsedMs"));
    }

    @Test public void statusAndActiveIndependentlyEnforceTimeout() {
        ObservationBuffer byStatus = started();
        assertTrue(byStatus.status(201_000).startsWith("active=false "));
        assertTrue(byStatus.status(201_000).contains("reason=TIMEOUT"));
        ObservationBuffer byActive = started();
        assertFalse(byActive.active(201_000));
        assertTrue(byActive.status(201_000).contains("reason=TIMEOUT"));
    }

    @Test public void largeClockCannotOverflowElapsed() {
        ObservationBuffer buffer = new ObservationBuffer();
        assertTrue(buffer.start(0, SESSION));
        assertFalse(buffer.active(Long.MAX_VALUE));
        assertEquals(200_000, number(buffer.status(Long.MAX_VALUE), "elapsedMs"));
        ObservationBuffer nearMaximum = new ObservationBuffer();
        assertTrue(nearMaximum.start(Long.MAX_VALUE - 10, SESSION));
        assertTrue(nearMaximum.record(Long.MAX_VALUE, "SNAPSHOT", 0, Collections.emptyMap()));
        assertEquals(10, number(nearMaximum.status(Long.MAX_VALUE), "elapsedMs"));
    }

    @Test public void activeStartAfterTimeoutCanCreateNewSession() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.start(201_000, "e1-next"));
        assertTrue(buffer.active(201_000));
        assertTrue(buffer.status(201_000).contains("session=e1-next reason=RUNNING"));
    }

    @Test public void productionRowLimitStopsAtExactlyMaximum() {
        ObservationBuffer buffer = started();
        for (int i = 0; i < ObservationBuffer.MAX_ROWS; i++) {
            assertTrue(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        }
        assertEquals(ObservationBuffer.MAX_ROWS, buffer.size());
        assertFalse(buffer.active(1_000));
        assertTrue(buffer.status(1_000).contains("reason=LIMIT_ROWS"));
        assertEquals(0, number(buffer.status(1_000), "dropped"));
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertEquals(1, number(buffer.status(1_000), "dropped"));
    }

    @Test public void productionByteLimitStopsBeforeExceedingCapacity() {
        ObservationBuffer buffer = started();
        Map<String, String> values = allLargeValues();
        int attempts = 0;
        while (buffer.active(1_000)) {
            buffer.record(1_000, "WMS_RELAYOUT_AFTER", 0, values);
            assertTrue(++attempts <= ObservationBuffer.MAX_ROWS);
        }
        assertTrue(buffer.status(1_000).contains("reason=LIMIT_BYTES"));
        assertTrue(number(buffer.status(1_000), "bytes") <= ObservationBuffer.MAX_BYTES);
        assertEquals(1, number(buffer.status(1_000), "dropped"));
        long actualBytes = 0;
        for (String row : buffer.page(0, Integer.MAX_VALUE)) {
            int length = row.getBytes(StandardCharsets.UTF_8).length + 1;
            assertTrue(length <= ObservationBuffer.MAX_LINE_BYTES);
            actualBytes += length;
        }
        assertEquals(actualBytes, number(buffer.status(1_000), "bytes"));
    }

    @Test public void exactByteLimitAcceptsLastLineThenStops() {
        ObservationBuffer sample = started();
        assertTrue(sample.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        int exact = (int) number(sample.status(1_000), "bytes");
        ObservationBuffer buffer = new ObservationBuffer(5, exact, 1_024, 60_000);
        assertTrue(buffer.start(1_000, SESSION));
        assertTrue(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertTrue(buffer.status(1_000).contains("reason=LIMIT_BYTES"));
        assertEquals(exact, number(buffer.status(1_000), "bytes"));
        assertEquals(0, number(buffer.status(1_000), "dropped"));
    }

    @Test public void overByteLimitRejectsWholeLineWithoutPartialData() {
        ObservationBuffer buffer = new ObservationBuffer(5, 20, 1_024, 60_000);
        assertTrue(buffer.start(1_000, SESSION));
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertEquals(0, buffer.size());
        assertTrue(buffer.status(1_000).contains("reason=LIMIT_BYTES"));
        assertEquals(0, number(buffer.status(1_000), "bytes"));
        assertEquals(1, number(buffer.status(1_000), "dropped"));
    }

    @Test public void lineLimitRejectsWholeLineAndStops() {
        ObservationBuffer buffer = new ObservationBuffer(5, 500, 20, 60_000);
        assertTrue(buffer.start(1_000, SESSION));
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertEquals(0, buffer.size());
        assertTrue(buffer.status(1_000).contains("reason=LIMIT_LINE"));
        assertEquals(1, number(buffer.status(1_000), "dropped"));
        assertEquals(0, number(buffer.status(1_000), "rejected"));
    }

    @Test public void exactLineLimitIncludesNewlineByte() {
        ObservationBuffer sample = started();
        sample.record(1_000, "SNAPSHOT", 0, Collections.emptyMap());
        int exact = (int) number(sample.status(1_000), "bytes");
        ObservationBuffer buffer = new ObservationBuffer(5, 500, exact, 60_000);
        assertTrue(buffer.start(1_000, SESSION));
        assertTrue(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertFalse(buffer.active(1_000));
        assertTrue(buffer.status(1_000).contains("reason=LIMIT_LINE"));
        assertEquals(0, number(buffer.status(1_000), "dropped"));
        ObservationBuffer oneLess = new ObservationBuffer(5, 500, exact - 1, 60_000);
        assertTrue(oneLess.start(1_000, SESSION));
        assertFalse(oneLess.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertTrue(oneLess.status(1_000).contains("reason=LIMIT_LINE"));
    }

    @Test public void cannotRaiseAnyProductionLimit() {
        int[][] invalid = {{0, 1, 1}, {6_001, 1, 1}, {1, 0, 1},
                {1, 1_572_865, 1}, {1, 1, 0}, {1, 1, 1_025}};
        for (int[] limits : invalid) {
            try {
                new ObservationBuffer(limits[0], limits[1], limits[2], 60_000);
                fail("INVALID_LIMIT_ACCEPTED");
            } catch (IllegalArgumentException expected) {
                assertEquals("INVALID_LIMIT", expected.getMessage());
            }
        }
        for (long duration : new long[]{0, 200_001}) {
            try {
                new ObservationBuffer(1, 1, 1, duration);
                fail("INVALID_DURATION_ACCEPTED");
            } catch (IllegalArgumentException expected) {
                assertEquals("INVALID_LIMIT", expected.getMessage());
            }
        }
    }

    @Test public void pagingHandlesAllBoundsWithoutOverflow() {
        ObservationBuffer buffer = started();
        for (int i = 0; i < 5; i++) buffer.record(1_000, "SNAPSHOT", i, Collections.emptyMap());
        assertEquals(2, buffer.page(1, 2).size());
        assertTrue(buffer.page(1, 2).get(0).startsWith("seq=2 "));
        assertEquals(4, buffer.page(1, Integer.MAX_VALUE).size());
        assertTrue(buffer.page(-1, 2).isEmpty());
        assertTrue(buffer.page(0, 0).isEmpty());
        assertTrue(buffer.page(0, -1).isEmpty());
        assertTrue(buffer.page(5, 1).isEmpty());
        assertTrue(buffer.page(Integer.MAX_VALUE, Integer.MAX_VALUE).isEmpty());
        assertTrue(buffer.page(Integer.MIN_VALUE, Integer.MAX_VALUE).isEmpty());
    }

    @Test public void pageIsImmutableAndIndependentOfLaterReset() {
        ObservationBuffer buffer = started();
        buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap());
        List<String> page = buffer.page(0, 10);
        try {
            page.clear();
            fail("MUTABLE_PAGE");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, buffer.size());
        }
        buffer.stop("USER_STOP");
        assertTrue(buffer.start(2_000, "e1-next"));
        assertEquals(0, buffer.size());
        assertEquals(1, page.size());
        assertTrue(page.get(0).contains("session=" + SESSION));
    }

    @Test public void inactiveRecordsOnlyIncreaseDroppedCount() {
        ObservationBuffer buffer = new ObservationBuffer();
        assertFalse(buffer.record(0, "SNAPSHOT", 0, Collections.emptyMap()));
        assertEquals(1, number(buffer.status(0), "dropped"));
        assertEquals(0, number(buffer.status(0), "rejected"));
        assertEquals(0, buffer.size());
        assertTrue(buffer.status(0).contains("reason=NOT_STARTED"));
    }

    @Test public void concurrentWritersHaveUniqueOrderedSequenceAndExactCounts() throws Exception {
        ObservationBuffer buffer = started();
        runConcurrent(8, worker -> {
            for (int i = 0; i < 100; i++) {
                assertTrue(buffer.record(1_000 + i, "SNAPSHOT", worker, scalar("value", "1")));
                buffer.status(1_000 + i);
                buffer.page(0, 2);
            }
        });
        assertEquals(800, buffer.size());
        assertEquals(0, number(buffer.status(1_099), "dropped"));
        long elapsed = -1;
        List<String> rows = buffer.page(0, Integer.MAX_VALUE);
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(i + 1, number(rows.get(i), "seq"));
            long next = number(rows.get(i), "elapsedMs");
            assertTrue(next >= elapsed);
            elapsed = next;
        }
    }

    @Test public void concurrentWritersCannotExceedRowsOrMiscountDrops() throws Exception {
        ObservationBuffer buffer = started();
        runConcurrent(8, worker -> {
            for (int i = 0; i < 900; i++) {
                buffer.record(1_000, "SNAPSHOT", worker, Collections.emptyMap());
            }
        });
        assertEquals(6_000, buffer.size());
        assertEquals(1_200, number(buffer.status(1_000), "dropped"));
        assertTrue(buffer.status(1_000).contains("reason=LIMIT_ROWS"));
    }

    @Test public void concurrentInvalidAndValidRecordsKeepExactAccounting() throws Exception {
        ObservationBuffer buffer = started();
        runConcurrent(8, worker -> {
            for (int i = 0; i < 50; i++) {
                assertTrue(buffer.record(1_000, "SNAPSHOT", worker, scalar("touch", "UNKNOWN")));
                assertFalse(buffer.record(1_000, "SNAPSHOT", worker, scalar("title", "UNKNOWN")));
            }
        });
        assertEquals(400, buffer.size());
        assertEquals(400, number(buffer.status(1_000), "dropped"));
        assertEquals(400, number(buffer.status(1_000), "rejected"));
    }

    @Test public void concurrentStopNeverAllowsPostStopWrites() throws Exception {
        ObservationBuffer buffer = started();
        runConcurrent(6, worker -> {
            for (int i = 0; i < 30; i++) buffer.record(1_000, "SNAPSHOT", worker, Collections.emptyMap());
            buffer.stop("UNHOOK_REQUESTED");
            assertFalse(buffer.record(1_000, "SNAPSHOT", worker, Collections.emptyMap()));
        });
        int sizeAfter = buffer.size();
        assertFalse(buffer.active(1_000));
        assertFalse(buffer.record(1_000, "SNAPSHOT", 0, Collections.emptyMap()));
        assertEquals(sizeAfter, buffer.size());
        assertTrue(buffer.status(1_000).contains("reason=UNHOOK_REQUESTED"));
    }

    @Test public void phaseTransitionsKeepOneSessionSequenceAndPriorEvidence() {
        ObservationBuffer buffer = started();
        String[] phases = {"BASELINE", "ENABLE", "RECOVERY", "DISARMED"};
        for (int i = 0; i < phases.length; i++) {
            assertTrue(buffer.record(1_000 + i, "PHASE_CHANGE", 1, scalar("phase", phases[i])));
        }
        assertEquals(4, buffer.size());
        List<String> rows = buffer.page(0, 10);
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(i + 1, number(rows.get(i), "seq"));
            assertTrue(rows.get(i).contains("session=" + SESSION + " "));
            assertTrue(rows.get(i).endsWith("phase=" + phases[i]));
        }
    }

    @Test public void restorationGraceIsRecordedAfterOverrideHardDeadline() {
        ObservationBuffer buffer = started();
        assertTrue(buffer.record(180_999, "OVERRIDE", 1, scalar("effective", "true")));
        assertTrue(buffer.record(181_000, "PHASE_CHANGE", 1, scalar("phase", "RECOVERY")));
        assertTrue(buffer.record(200_999, "RECOVERY_RESULT", 1, scalar("recovery", "CONFIRMED")));
        assertTrue(buffer.active(200_999));
        assertFalse(buffer.record(201_000, "RECOVERY_RESULT", 1, scalar("recovery", "CONFIRMED")));
        assertEquals(3, buffer.size());
        assertTrue(buffer.status(201_000).contains("reason=TIMEOUT"));
    }

    @Test public void overrideRowRetainsOriginalAndEffectiveValuesSeparately() {
        ObservationBuffer buffer = started();
        Map<String, String> values = scalar("phase", "ENABLE");
        values.put("original", "false");
        values.put("effective", "true");
        values.put("applied", "1");
        assertTrue(buffer.record(1_001, "OVERRIDE", 1, values));
        String row = buffer.page(0, 1).get(0);
        assertTrue(row.endsWith("applied=1 effective=true original=false phase=ENABLE"));
        assertFalse(row.contains("touch="));
    }

    @Test public void allManualActionMarkersAreFixedSafeEnums() {
        ObservationBuffer buffer = started();
        String[] marks = {"DOUBLE", "TRIPLE", "TWO", "LOCK", "UNLOCK", "SWIPE", "ICON",
                "EDITOR", "PREVIEW"};
        for (String mark : marks) {
            assertTrue(buffer.record(1_000, "USER_MARK", 1, scalar("mark", mark)));
        }
        assertEquals(marks.length, buffer.size());
        for (String unsafe : new String[]{"DOUBLE private_title", "TWO\nsecret", "SCREENSHOT",
                "/private/path", "content://uri", "我的照片"}) {
            assertFalse(buffer.record(1_000, "USER_MARK", 1, scalar("mark", unsafe)));
        }
        assertEquals(marks.length, buffer.size());
        assertEquals(6, number(buffer.status(1_000), "rejected"));
    }

    @Test public void restorationStatesDoNotAutomaticallyBecomeConfirmed() {
        ObservationBuffer buffer = started();
        for (String recovery : new String[]{"PENDING", "UNCONFIRMED", "CONFIRMED"}) {
            assertTrue(buffer.record(1_000, "RECOVERY_RESULT", 1, scalar("recovery", recovery)));
        }
        assertTrue(buffer.page(0, 3).get(0).endsWith("recovery=PENDING"));
        assertTrue(buffer.page(0, 3).get(1).endsWith("recovery=UNCONFIRMED"));
        assertTrue(buffer.page(0, 3).get(2).endsWith("recovery=CONFIRMED"));
    }

    @Test public void allTwentyEightAllowedFieldsFitOneLineAtMaximumScalarLength() {
        ObservationBuffer buffer = started();
        Map<String, String> fields = allLargeValues();
        assertEquals(28, fields.size());
        assertTrue(buffer.record(1_000, "RECOVERY_RESULT", Integer.MAX_VALUE, fields));
        String row = buffer.page(0, 1).get(0);
        assertTrue(row.getBytes(StandardCharsets.UTF_8).length + 1 <= 1_024);
        assertEquals(28, row.split(" ").length - 5);
        assertEquals(0, number(buffer.status(1_000), "rejected"));
    }

    private static ObservationBuffer started() {
        ObservationBuffer buffer = new ObservationBuffer();
        assertTrue(buffer.start(1_000, SESSION));
        return buffer;
    }

    private static Map<String, String> scalar(String key, String value) {
        Map<String, String> values = new HashMap<>();
        values.put(key, value);
        return values;
    }

    private static Map<String, String> allLargeValues() {
        String[] keys = {"touch", "target", "keyguard", "dup", "inputConfig",
                "changed", "requested", "mask", "value", "sourceTouch", "destTouch", "arrivalTouch",
                "returnCode", "threw", "qualified", "showWallpaper", "hasSurface",
                "visible", "focus", "copyChanges", "submitted", "homeUnique",
                "phase", "original", "effective", "applied", "recovery", "mark"};
        Map<String, String> values = new HashMap<>();
        for (String key : keys) values.put(key, "-9223372036854775808");
        return values;
    }

    private static String repeated(char c, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int i = 0; i < count; i++) value.append(c);
        return value.toString();
    }

    private static long number(String row, String key) {
        for (String field : row.split(" ")) {
            if (field.startsWith(key + "=")) return Long.parseLong(field.substring(key.length() + 1));
        }
        throw new AssertionError("MISSING_SAFE_FIELD:" + key);
    }

    private interface ConcurrentWork { void run(int worker); }

    private static void runConcurrent(int workers, ConcurrentWork work) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        Future<?>[] futures = new Future<?>[workers];
        try {
            for (int i = 0; i < workers; i++) {
                final int worker = i;
                futures[i] = executor.submit(() -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("INTERRUPTED");
                    }
                    work.run(worker);
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
