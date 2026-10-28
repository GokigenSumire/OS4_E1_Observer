package personal.wallpaper.os4.e1.core;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SessionEpochTest {
    @Test public void initialEpochDoesNotAuthorizeAnyCallback() {
        SessionEpoch epochs = new SessionEpoch();
        assertEquals(0, epochs.current());
        assertFalse(epochs.accepts(0));
        assertFalse(epochs.accepts(1));
        assertFalse(epochs.accepts(-1));
    }

    @Test public void advanceIsStrictAndOnlyCurrentTicketIsAccepted() {
        SessionEpoch epochs = new SessionEpoch();
        for (long expected = 1; expected <= 100; expected++) {
            long ticket = epochs.advance();
            assertEquals(expected, ticket);
            assertEquals(ticket, epochs.current());
            assertTrue(epochs.accepts(ticket));
            assertFalse(epochs.accepts(ticket - 1));
            assertFalse(epochs.accepts(ticket + 1));
        }
    }

    @Test public void oldCallbackCannotBelongToRestartedSession() {
        SessionEpoch epochs = new SessionEpoch();
        long beforeTicket = epochs.advance();
        assertTrue(epochs.accepts(beforeTicket));
        long restarted = epochs.advance();
        assertFalse(epochs.accepts(beforeTicket));
        assertTrue(epochs.accepts(restarted));
    }

    @Test public void oldTimerCannotAuthorizeNewSessionDetach() {
        SessionEpoch epochs = new SessionEpoch();
        long timerTicket = epochs.advance();
        long stopTicket = epochs.advance();
        long nextStartTicket = epochs.advance();
        assertFalse(epochs.accepts(timerTicket));
        assertFalse(epochs.accepts(stopTicket));
        assertTrue(epochs.accepts(nextStartTicket));
    }

    @Test public void intermediateAndFutureTicketsNeverMatch() {
        SessionEpoch epochs = new SessionEpoch();
        long first = epochs.advance();
        epochs.advance();
        long current = epochs.advance();
        assertFalse(epochs.accepts(first));
        assertFalse(epochs.accepts(first + 1));
        assertFalse(epochs.accepts(current + 1));
        assertFalse(epochs.accepts(Long.MIN_VALUE));
        assertFalse(epochs.accepts(Long.MAX_VALUE));
    }

    @Test public void currentAndAcceptsDoNotAdvanceTicket() {
        SessionEpoch epochs = new SessionEpoch();
        long ticket = epochs.advance();
        for (int i = 0; i < 100; i++) {
            assertEquals(ticket, epochs.current());
            assertTrue(epochs.accepts(ticket));
            assertFalse(epochs.accepts(ticket + 1));
        }
        assertEquals(ticket + 1, epochs.advance());
    }

    @Test public void maximumTicketIsAcceptedButNeverWraps() {
        SessionEpoch epochs = new SessionEpoch(Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, epochs.advance());
        assertTrue(epochs.accepts(Long.MAX_VALUE));
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                epochs.advance();
                fail("EPOCH_WRAPPED");
            } catch (IllegalStateException expected) {
                assertEquals("EPOCH_EXHAUSTED", expected.getMessage());
            }
            assertEquals(Long.MAX_VALUE, epochs.current());
            assertTrue(epochs.accepts(Long.MAX_VALUE));
            assertFalse(epochs.accepts(Long.MIN_VALUE));
            assertFalse(epochs.accepts(0));
        }
    }

    @Test public void negativeInitialEpochIsRejected() {
        try {
            new SessionEpoch(-1);
            fail("NEGATIVE_EPOCH_ACCEPTED");
        } catch (IllegalArgumentException expected) {
            assertEquals("INVALID_EPOCH", expected.getMessage());
        }
    }

    @Test public void concurrentAdvancesAreUniqueAndGapFree() throws Exception {
        SessionEpoch epochs = new SessionEpoch();
        ConcurrentLinkedQueue<Long> tickets = new ConcurrentLinkedQueue<>();
        runConcurrent(8, worker -> {
            for (int i = 0; i < 1_000; i++) {
                long ticket = epochs.advance();
                tickets.add(ticket);
                assertTrue(epochs.current() >= ticket);
            }
        });
        assertEquals(8_000, epochs.current());
        assertEquals(8_000, tickets.size());
        Set<Long> unique = new HashSet<>(tickets);
        assertEquals(8_000, unique.size());
        for (long ticket = 1; ticket <= 8_000; ticket++) assertTrue(unique.contains(ticket));
        assertTrue(epochs.accepts(8_000));
        assertFalse(epochs.accepts(7_999));
    }

    @Test public void delayedOldCallbackChecksAfterConcurrentRestart() throws Exception {
        SessionEpoch epochs = new SessionEpoch();
        long oldTicket = epochs.advance();
        CountDownLatch oldInvocationStarted = new CountDownLatch(1);
        CountDownLatch newSessionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> oldAfter = executor.submit(() -> {
                oldInvocationStarted.countDown();
                assertTrue(newSessionStarted.await(10, TimeUnit.SECONDS));
                return epochs.accepts(oldTicket);
            });
            assertTrue(oldInvocationStarted.await(10, TimeUnit.SECONDS));
            long newTicket = epochs.advance();
            newSessionStarted.countDown();
            assertFalse(oldAfter.get(10, TimeUnit.SECONDS));
            assertTrue(epochs.accepts(newTicket));
        } finally {
            newSessionStarted.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
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
