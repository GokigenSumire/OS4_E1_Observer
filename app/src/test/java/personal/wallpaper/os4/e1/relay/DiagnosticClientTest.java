package personal.wallpaper.os4.e1.relay;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class DiagnosticClientTest {
    private static final String TOKEN = "diagnostic-session-1";
    private static final String LINE = "row=1 stage=INPUT cause=UNKNOWN_mShowing total=1";
    private static int bytes(List<String> lines) { return lines.stream().mapToInt(line -> line.length() + 1).sum(); }
    private static DiagnosticClient.Reply reply(String token, long revision, int active, int reason, List<String> all, List<String> page) {
        return new DiagnosticClient.Reply(token, revision, active, reason, all.size(), bytes(all), page);
    }
    private static class Fake implements DiagnosticClient.Transport {
        final ArrayList<String> all = new ArrayList<>();
        final ArrayList<Integer> calls = new ArrayList<>();
        String token = TOKEN;
        long revision = 3;
        int active, reason = 2, pageSize = 8;
        @Override public DiagnosticClient.Reply call(int code, String expected, long version, int offset) throws Exception {
            calls.add(code);
            if (code == DiagnosticClient.START) { token = "diagnostic-new"; revision++; active = 1; reason = 1; all.clear(); }
            if (code == DiagnosticClient.STOP) { assertEquals(token, expected); revision++; active = 0; reason = 2; }
            if (code == DiagnosticClient.PAGE) {
                assertEquals(token, expected); assertEquals(revision, version);
                return reply(token, revision, active, reason, all,
                        all.subList(offset, Math.min(all.size(), offset + pageSize)));
            }
            return reply(token, revision, active, reason, all, List.of());
        }
    }
    @Test public void onlyFourExplicitOperationsAcceptedWithoutChannelCalls() {
        Fake fake = new Fake(); DiagnosticClient client = new DiagnosticClient();
        for (String value : new String[]{null, "", "TRACE_START", "DAILY_ON", "DAILY_OFF", "ARM", "CLEAR", "start"}) {
            DiagnosticClient.Result result = client.request(value, fake);
            assertFalse(result.success); assertEquals("INVALID_OPERATION", result.detail);
        }
        assertTrue(fake.calls.isEmpty());
    }
    @Test public void startUsesOnlyDiagnosticTransactionAndMetadata() {
        Fake fake = new Fake(); fake.all.add(LINE);
        DiagnosticClient.Result result = new DiagnosticClient().request("START", fake);
        assertTrue(result.success); assertTrue(result.active); assertFalse(result.complete);
        assertEquals(List.of(DiagnosticClient.STATE, DiagnosticClient.START), fake.calls); assertEquals(0, result.totalRows);
        assertTrue(result.lines.isEmpty());
    }
    @Test public void statusReturnsMetadataWithoutDownloadingOrChangingState() {
        Fake fake = new Fake(); fake.active = 1; fake.reason = 1; fake.all.add(LINE);
        DiagnosticClient.Result result = new DiagnosticClient().request("STATUS", fake);
        assertTrue(result.success); assertTrue(result.active); assertFalse(result.complete);
        assertEquals(1, result.totalRows); assertEquals(LINE.length() + 1, result.bytes);
        assertEquals(List.of(DiagnosticClient.STATE), fake.calls); assertTrue(result.lines.isEmpty());
    }
    @Test public void snapshotWhileActiveNeverDownloadsPageOrStopsRecording() {
        Fake fake = new Fake(); fake.active = 1; fake.reason = 1;
        DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
        assertFalse(result.success); assertEquals("STOP_FIRST", result.detail);
        assertEquals(List.of(DiagnosticClient.STATE), fake.calls); assertEquals(1, fake.active);
    }
    @Test public void startWhileAlreadyActiveDoesNotRestartClearOrPretendNewSession() {
        Fake fake = new Fake(); fake.active = 1; fake.reason = 1; fake.all.add(LINE);
        long beforeRevision = fake.revision;
        DiagnosticClient.Result result = new DiagnosticClient().request("START", fake);
        assertFalse(result.success); assertTrue(result.active); assertFalse(result.complete);
        assertEquals("ALREADY_RUNNING", result.detail); assertEquals(List.of(DiagnosticClient.STATE), fake.calls);
        assertEquals(TOKEN, fake.token); assertEquals(beforeRevision, fake.revision); assertEquals(List.of(LINE), fake.all);
        assertEquals(1, result.totalRows); assertEquals(LINE.length() + 1, result.bytes); assertTrue(result.lines.isEmpty());
    }
    @Test public void stopFreezesThenDownloadsAndVerifiesCompleteSnapshot() {
        Fake fake = new Fake(); fake.active = 1; fake.reason = 1; fake.all.add(LINE);
        DiagnosticClient.Result result = new DiagnosticClient().request("STOP", fake);
        assertTrue(result.success); assertFalse(result.active); assertTrue(result.complete);
        assertEquals(List.of(DiagnosticClient.STATE, DiagnosticClient.STOP, DiagnosticClient.PAGE, DiagnosticClient.STATE), fake.calls);
        assertEquals(List.of(LINE), result.lines); assertEquals(LINE.length() + 1, result.bytes);
    }
    @Test public void notStartedStatusAllowedButStopAndSnapshotRejected() {
        Fake fake = new Fake(); fake.token = "NONE"; fake.revision = 0; fake.reason = 0;
        DiagnosticClient client = new DiagnosticClient();
        assertTrue(client.request("STATUS", fake).success);
        assertEquals("NOT_STARTED", client.request("STOP", fake).detail);
        assertEquals("NOT_STARTED", client.request("SNAPSHOT", fake).detail);
        assertFalse(fake.calls.contains(DiagnosticClient.STOP)); assertFalse(fake.calls.contains(DiagnosticClient.PAGE));
    }
    @Test public void zeroRowStoppedSnapshotStillRechecksFinalHeader() {
        Fake fake = new Fake();
        DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
        assertTrue(result.success); assertTrue(result.complete); assertTrue(result.lines.isEmpty());
        assertEquals(List.of(DiagnosticClient.STATE, DiagnosticClient.STATE), fake.calls);
    }
    @Test public void fullTwoThousandRowsDownloadedBoundedlyWithShortPages() {
        Fake fake = new Fake(); fake.pageSize = 3;
        for (int i = 1; i <= 2000; i++) fake.all.add("row=" + i + " stage=INPUT cause=READY total=" + i);
        DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
        assertTrue(result.success); assertEquals(2000, result.lines.size()); assertEquals(bytes(fake.all), result.bytes);
        assertEquals(669, fake.calls.size());
    }
    @Test public void frozenSnapshotReuseNeedsCurrentSameHeader() {
        Fake fake = new Fake(); fake.all.add(LINE); DiagnosticClient client = new DiagnosticClient();
        assertTrue(client.request("SNAPSHOT", fake).complete); fake.calls.clear();
        assertTrue(client.request("SNAPSHOT", fake).complete);
        assertEquals(List.of(DiagnosticClient.STATE), fake.calls);
        fake.revision++; fake.calls.clear();
        assertTrue(client.request("SNAPSHOT", fake).complete); assertTrue(fake.calls.contains(DiagnosticClient.PAGE));
    }
    @Test public void newConfirmedStartClearsOnlyCachedDiagnosticSnapshot() {
        Fake fake = new Fake(); fake.all.add(LINE); DiagnosticClient client = new DiagnosticClient();
        DiagnosticClient.Result old = client.request("SNAPSHOT", fake);
        assertTrue(client.request("START", fake).active);
        assertTrue(client.request("STOP", fake).complete);
        assertEquals(List.of(LINE), old.lines); // 已返回的不可变副本不被新会话改写。
    }
    @Test public void immutableReplyAndResultDefensivelyCopyRows() {
        ArrayList<String> original = new ArrayList<>(List.of(LINE));
        DiagnosticClient.Reply reply = reply(TOKEN, 1, 0, 2, original, original);
        original.clear(); assertEquals(List.of(LINE), reply.lines);
        Fake fake = new Fake(); fake.all.add(LINE);
        DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
        fake.all.clear(); assertEquals(List.of(LINE), result.lines);
        try { result.lines.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { reply.lines.add(LINE); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void emptyOrOverfullPageCannotSucceedWithPartialData() {
        for (int size : new int[]{0, 9}) {
            Fake fake = new Fake() {
                @Override public DiagnosticClient.Reply call(int code, String token, long revision, int offset) throws Exception {
                    if (code == DiagnosticClient.PAGE) return reply(this.token, this.revision, 0, 2, all,
                            java.util.Collections.nCopies(size, LINE));
                    return super.call(code, token, revision, offset);
                }
            };
            fake.all.add(LINE);
            DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
            assertFalse(result.success); assertEquals(size == 0 ? "INVALID_PAGE_COUNT" : "INVALID_HEADER", result.detail);
            assertTrue(result.lines.isEmpty());
        }
    }
    @Test public void changedTokenRevisionOrReasonOnPageFailsClosed() {
        for (int which = 0; which < 3; which++) {
            final int mutation = which;
            Fake fake = new Fake() {
                @Override public DiagnosticClient.Reply call(int code, String token, long revision, int offset) throws Exception {
                    if (code == DiagnosticClient.PAGE)
                        return reply(mutation == 0 ? "other-session" : this.token,
                                mutation == 1 ? this.revision + 1 : this.revision, 0, mutation == 2 ? 3 : 2, all, all);
                    return super.call(code, token, revision, offset);
                }
            };
            fake.all.add(LINE);
            assertEquals("SNAPSHOT_CHANGED", new DiagnosticClient().request("SNAPSHOT", fake).detail);
        }
    }
    @Test public void finalHeaderChangeRejectsEvenAllPagesWereReadable() {
        Fake fake = new Fake() {
            int states;
            @Override public DiagnosticClient.Reply call(int code, String token, long revision, int offset) throws Exception {
                if (code == DiagnosticClient.STATE && ++states > 1) this.revision++;
                return super.call(code, token, revision, offset);
            }
        };
        fake.all.add(LINE);
        DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
        assertEquals("SNAPSHOT_CHANGED", result.detail); assertTrue(result.lines.isEmpty());
    }
    @Test public void mismatchedTotalBytesRejectedWithoutLeakingPartialRows() {
        Fake fake = new Fake() {
            @Override public DiagnosticClient.Reply call(int code, String token, long revision, int offset) throws Exception {
                DiagnosticClient.Reply source = super.call(code, token, revision, offset);
                return new DiagnosticClient.Reply(source.token, source.revision, source.active, source.reason, source.rows,
                        source.bytes + 1, source.lines);
            }
        };
        fake.all.add(LINE);
        assertEquals("SNAPSHOT_BYTES_MISMATCH", new DiagnosticClient().request("SNAPSHOT", fake).detail);
    }
    @Test public void arbitraryPathsUnicodeNewlinesAndOversizedLinesRejected() {
        for (String unsafe : new String[]{"/storage/private.jpg", "content:uri/secret", "认证正文", "line\nnew", "x".repeat(128), "x".repeat(129), "x\t=y", "key%3Asecret"}) {
            Fake fake = new Fake(); fake.all.add(unsafe);
            DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
            assertFalse(result.success); assertEquals("UNSAFE_DIAGNOSTIC_LINE", result.detail);
            assertTrue(result.lines.isEmpty());
        }
    }
    @Test public void malformedHeadersRejectUnknownFlagsAndUnboundedSizes() {
        List<DiagnosticClient.Reply> malformed = List.of(
                new DiagnosticClient.Reply(null, 1, 0, 2, 0, 0, List.of()),
                new DiagnosticClient.Reply("../secret", 1, 0, 2, 0, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, -1, 0, 2, 0, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, 0, 0, 2, 0, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 2, 2, 0, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 0, 7, 0, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 0, 2, 2001, 100, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 0, 2, 1, 131073, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 0, 2, 0, 1, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 0, 2, 1, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 1, 2, 0, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 0, 1, 0, 0, List.of()),
                new DiagnosticClient.Reply("NONE", 1, 0, 0, 0, 0, List.of()),
                new DiagnosticClient.Reply(TOKEN, 1, 0, 0, 0, 0, List.of()));
        for (DiagnosticClient.Reply source : malformed)
            assertEquals("INVALID_HEADER", new DiagnosticClient().request("STATUS", (code, token, revision, offset) -> source).detail);
    }
    @Test public void metadataMayNotSmugglePageStrings() {
        DiagnosticClient.Reply source = reply(TOKEN, 1, 0, 2, List.of(LINE), List.of(LINE));
        assertEquals("UNEXPECTED_HEADER_LINES", new DiagnosticClient().request("STATUS", (c,t,r,o) -> source).detail);
    }
    @Test public void maximumLineCountsFinalLfWithinOneHundredTwentyEightBytes() {
        Fake fake = new Fake(); fake.all.add("x".repeat(127));
        DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", fake);
        assertTrue(result.success); assertEquals(128, result.bytes);
    }
    @Test public void channelFailuresAreFixedAndDoNotExposeExceptionMessage() {
        DiagnosticClient.Result result = new DiagnosticClient().request("SNAPSHOT", (c,t,r,o) -> { throw new Exception("private/path/content"); });
        assertEquals("CHANNEL_UNAVAILABLE", result.detail); assertTrue(result.lines.isEmpty());
        assertFalse(result.detail.contains("private"));
    }
    @Test public void stopWithWrongReturnedSessionNeverReadsAnyPage() {
        Fake fake = new Fake() {
            @Override public DiagnosticClient.Reply call(int code, String token, long revision, int offset) throws Exception {
                DiagnosticClient.Reply result = super.call(code, token, revision, offset);
                return code == DiagnosticClient.STOP ? reply("replacement", result.revision, 0, 2, all, List.of()) : result;
            }
        };
        assertEquals("STOP_NOT_CONFIRMED", new DiagnosticClient().request("STOP", fake).detail);
        assertFalse(fake.calls.contains(DiagnosticClient.PAGE));
    }
    @Test public void startMustActuallyConfirmRunning() {
        DiagnosticClient.Reply stopped = reply(TOKEN, 1, 0, 2, List.of(), List.of());
        assertEquals("START_NOT_CONFIRMED", new DiagnosticClient().request("START", (c,t,r,o) -> stopped).detail);
    }
    @Test public void safeDisplayFactoryRejectsUnsafeOrInconsistentUiFixtures() {
        assertTrue(DiagnosticClient.display(true, "STOP", "FROZEN_SNAPSHOT", false, true, 2,
                1, LINE.length() + 1, List.of(LINE)).complete);
        expectInvalid(() -> DiagnosticClient.display(true, "STOP", "FROZEN_SNAPSHOT", false, true, 2, 1, 1, List.of(LINE)));
        expectInvalid(() -> DiagnosticClient.display(true, "STOP", "/path", false, true, 2, 0, 0, List.of()));
        expectInvalid(() -> DiagnosticClient.display(true, "STOP", "FROZEN_SNAPSHOT", true, true, 1, 0, 0, List.of()));
        expectInvalid(() -> DiagnosticClient.display(false, "STATUS", "FAILED", false, true, -1, 0, 0, List.of()));
        expectInvalid(() -> DiagnosticClient.display(true, "STATUS", "STOPPED", false, false, 2, 1, 1, List.of(LINE)));
        assertTrue(DiagnosticClient.display(false, "START", "ALREADY_RUNNING", true, false, 1,
                1, LINE.length() + 1, List.of()).active);
        expectInvalid(() -> DiagnosticClient.display(false, "STATUS", "ALREADY_RUNNING", true, false, 1, 0, 0, List.of()));
    }
    private static void expectInvalid(Runnable runnable) {
        try { runnable.run(); fail("expected invalid display"); } catch (IllegalArgumentException expected) { }
    }
}
