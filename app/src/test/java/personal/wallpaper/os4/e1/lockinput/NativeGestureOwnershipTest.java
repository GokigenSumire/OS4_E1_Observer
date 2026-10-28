package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeGestureOwnershipTest {
    private final Object injector = new Object(), controller = new Object(), root = new Object();
    private final Object helper = new Object(), listener = new Object(), channel = new Object();

    private NativeGestureOwnership.Binding binding() {
        return new NativeGestureOwnership.Binding(injector, controller, root, helper, listener,
                channel, 7, "original-ticket", 3, 9);
    }
    private NativeGestureOwnership.Binding changed(int field) {
        return new NativeGestureOwnership.Binding(field == 0 ? new Object() : injector,
                field == 1 ? new Object() : controller, field == 2 ? new Object() : root,
                field == 3 ? new Object() : helper, field == 4 ? new Object() : listener,
                field == 5 ? new Object() : channel, field == 6 ? 8 : 7,
                field == 7 ? "next-ticket" : "original-ticket", field == 8 ? 4 : 3, field == 9 ? 10 : 9);
    }
    private NativeGestureOwnership.EventStamp stamp(long time, int action) {
        return new NativeGestureOwnership.EventStamp(100, time, action, 4,
                new int[]{4}, new float[]{50}, new float[]{90});
    }
    private NativeGestureOwnership ready() {
        NativeGestureOwnership owner = new NativeGestureOwnership(); owner.bind(binding()); return owner;
    }
    private NativeGestureOwnership.Frame admitted(NativeGestureOwnership owner, long time) {
        return owner.beginFrame(binding(), stamp(time, 0), true, true);
    }
    private NativeGestureOwnership.Frame claimed(NativeGestureOwnership owner, long time) {
        NativeGestureOwnership.Frame frame = admitted(owner,time);
        assertTrue(owner.claimGesture(frame));
        return frame;
    }

    @Test public void firstBindingCreatesPositiveGeneration() {
        NativeGestureOwnership owner = new NativeGestureOwnership();
        assertTrue(owner.bind(binding())); assertTrue(owner.currentGeneration() > 0);
    }
    @Test public void equivalentBindingPreservesGenerationAndSequence() {
        NativeGestureOwnership owner = ready(); long generation = owner.beginSequence(binding());
        assertFalse(owner.bind(binding())); assertTrue(owner.sequenceValid(binding(), generation));
    }
    @Test public void eligibleAuthorizedSynchronousFrameMaySuppress() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame frame = claimed(owner, 100);
        assertTrue(frame.shouldRecognize()); assertTrue(owner.maySuppress(listener, helper, binding()));
        assertTrue(owner.endFrame(frame)); assertFalse(owner.maySuppress(listener, helper, binding()));
    }
    @Test public void guardEligibilityDoesNotImplyArbitrationAuthorization() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame frame = owner.beginFrame(binding(), stamp(100, 0), true);
        assertTrue(frame.shouldRecognize()); assertFalse(owner.maySuppress(listener, helper, binding()));
        owner.endFrame(frame);
    }
    @Test public void ineligibleFrameCannotRecognizeOrSuppress() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame frame = owner.beginFrame(binding(), stamp(100, 0), false, true);
        assertFalse(frame.shouldRecognize()); assertFalse(owner.maySuppress(listener, helper, binding()));
        owner.endFrame(frame);
    }
    @Test public void clearSequenceAfterRecognitionKeepsCurrentClaim() {
        NativeGestureOwnership owner = ready(); long generation = owner.beginSequence(binding());
        NativeGestureOwnership.Frame frame = claimed(owner, 100); owner.clearSequence();
        assertFalse(owner.sequenceValid(binding(), generation));
        assertTrue(owner.maySuppress(listener, helper, binding())); owner.endFrame(frame);
    }
    @Test public void finallyClosesFrameEvenWhenOriginalCallbackThrows() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame frame = claimed(owner, 100);
        try { throw new IllegalStateException("ORIGINAL_CALLBACK_FAILED"); }
        catch (IllegalStateException expected) { assertTrue(frame.isActive()); }
        finally { owner.endFrame(frame); }
        assertFalse(frame.isActive()); assertFalse(owner.maySuppress(listener, helper, binding()));
    }
    @Test public void wrongListenerAndHelperCannotSuppress() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame frame = claimed(owner, 100);
        assertFalse(owner.maySuppress(new Object(), helper, binding()));
        assertFalse(owner.maySuppress(listener, new Object(), binding())); owner.endFrame(frame);
    }
    @Test public void callbackOnAnotherThreadCannotUseMainClaim() throws Exception {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame frame = claimed(owner, 100);
        boolean[] allowed = {true};
        Thread thread = new Thread(() -> allowed[0] = owner.maySuppress(listener, helper, binding()));
        thread.start(); thread.join(); assertFalse(allowed[0]); owner.endFrame(frame);
    }
    @Test public void nestedParentChildDuplicateIsNotRecognizedTwice() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame parent = claimed(owner, 100);
        NativeGestureOwnership.Frame child = admitted(owner, 100);
        assertTrue(parent.shouldRecognize()); assertTrue(child.duplicate); assertFalse(child.shouldRecognize());
        assertTrue(owner.maySuppress(listener, helper, binding()));
        owner.endFrame(child); assertTrue(owner.maySuppress(listener, helper, binding())); owner.endFrame(parent);
    }
    @Test public void repeatedInjectorCallbackAfterReturnIsStillDuplicate() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = admitted(owner, 100);
        owner.endFrame(first); NativeGestureOwnership.Frame second = admitted(owner, 100);
        assertTrue(second.duplicate); assertFalse(second.shouldRecognize()); owner.endFrame(second);
    }
    @Test public void duplicateCannotUpgradePreviouslyRejectedEventToArbitrationClaim() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame first = owner.beginFrame(binding(),stamp(100,0),false,true); owner.endFrame(first);
        NativeGestureOwnership.Frame repeated = admitted(owner,100);
        assertTrue(repeated.duplicate); assertFalse(repeated.admitted);
        assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(repeated);
    }
    @Test public void duplicateCannotAddAuthorizationAbsentOnFirstCallback() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame first = owner.beginFrame(binding(),stamp(100,0),true,false); owner.endFrame(first);
        NativeGestureOwnership.Frame repeated = admitted(owner,100);
        assertTrue(repeated.duplicate); assertTrue(repeated.admitted);
        assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(repeated);
    }
    @Test public void reorderedPointerArrayIsSameEvent() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame first = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(100, 110, 5, 8,
                        new int[]{4,8}, new float[]{50,80}, new float[]{90,100}), true, true);
        owner.endFrame(first);
        NativeGestureOwnership.Frame second = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(100, 110, 5, 8,
                        new int[]{8,4}, new float[]{80,50}, new float[]{100,90}), true, true);
        assertTrue(second.duplicate); owner.endFrame(second);
    }
    @Test public void moveArrayReorderingDoesNotInventAnActionPointer() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame first = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(100,110,2,4,new int[]{4,8},new float[]{50,80},new float[]{90,100}),true,true);
        owner.endFrame(first);
        NativeGestureOwnership.Frame second = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(100,110,2,8,new int[]{8,4},new float[]{80,50},new float[]{100,90}),true,true);
        assertTrue(second.duplicate); owner.endFrame(second);
    }
    @Test public void coordinateChangeIsNotMistakenForDuplicate() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = admitted(owner, 100);
        owner.endFrame(first);
        NativeGestureOwnership.Frame moved = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(100, 100, 0, 4,
                        new int[]{4}, new float[]{51}, new float[]{90}), true, true);
        assertFalse(moved.duplicate); owner.endFrame(moved);
    }
    @Test public void stampSnapshotsCallerArrays() {
        NativeGestureOwnership owner = ready(); int[] ids = {4}; float[] xs = {50}, ys = {90};
        NativeGestureOwnership.EventStamp saved = new NativeGestureOwnership.EventStamp(100,100,0,4,ids,xs,ys);
        ids[0] = 8; xs[0] = 1; ys[0] = 2;
        NativeGestureOwnership.Frame first = owner.beginFrame(binding(), saved, true, true); owner.endFrame(first);
        NativeGestureOwnership.Frame second = admitted(owner, 100); assertTrue(second.duplicate); owner.endFrame(second);
    }
    @Test public void differentActionPointerIdentityIsNotDuplicate() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame first = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(100,110,6,4,new int[]{4,8},new float[]{50,80},new float[]{90,100}),true,true);
        owner.endFrame(first);
        NativeGestureOwnership.Frame second = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(100,110,6,8,new int[]{4,8},new float[]{50,80},new float[]{90,100}),true,true);
        assertFalse(second.duplicate); owner.endFrame(second);
    }
    @Test public void sameTimestampDifferentDownSequenceIsNotDuplicate() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = admitted(owner, 110); owner.endFrame(first);
        NativeGestureOwnership.Frame second = owner.beginFrame(binding(),
                new NativeGestureOwnership.EventStamp(109,110,0,4,new int[]{4},new float[]{50},new float[]{90}),true,true);
        assertFalse(second.duplicate); owner.endFrame(second);
    }
    @Test public void differentActionIsNotDuplicate() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = admitted(owner,100); owner.endFrame(first);
        NativeGestureOwnership.Frame second = owner.beginFrame(binding(),stamp(100,1),true,true);
        assertFalse(second.duplicate); owner.endFrame(second);
    }
    @Test public void allBindingFieldsCancelSequenceFramesAndDelayedGeneration() {
        for (int field = 0; field < 10; field++) {
            NativeGestureOwnership owner = ready(); long generation = owner.beginSequence(binding());
            NativeGestureOwnership.Frame frame = claimed(owner,100); NativeGestureOwnership.Binding next = changed(field);
            assertTrue("field=" + field,owner.bind(next));
            assertFalse(frame.isActive()); assertFalse(owner.sequenceValid(binding(),generation));
            assertFalse(owner.bindingValid(next,generation)); assertFalse(owner.maySuppress(listener,helper,binding()));
        }
    }
    @Test public void changedBindingDoesNotInheritDuplicateHistory() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = admitted(owner,100); owner.endFrame(first);
        NativeGestureOwnership.Binding next = changed(7); owner.bind(next);
        NativeGestureOwnership.Frame frame = owner.beginFrame(next,stamp(100,0),true,true);
        assertFalse(frame.duplicate); owner.endFrame(frame);
    }
    @Test public void currentBindingMismatchCannotUsePreviouslyAllowedFrame() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame frame = claimed(owner,100);
        assertFalse(owner.maySuppress(listener,helper,changed(7))); owner.endFrame(frame);
    }
    @Test public void revokeOnDisconnectInvalidatesSequenceAndPendingFrame() {
        NativeGestureOwnership owner = ready(); long generation = owner.beginSequence(binding());
        NativeGestureOwnership.Frame frame = claimed(owner,100); owner.revoke();
        assertFalse(frame.isActive()); assertFalse(owner.sequenceValid(binding(),generation));
        assertFalse(owner.bindingValid(binding(),generation)); assertFalse(owner.maySuppress(listener,helper,binding()));
    }
    @Test public void rebindAfterRevokeRequiresFreshSequence() {
        NativeGestureOwnership owner = ready(); long previous = owner.beginSequence(binding()); owner.revoke(); owner.bind(binding());
        assertFalse(owner.sequenceValid(binding(),previous)); long next = owner.beginSequence(binding());
        assertNotEquals(previous,next); assertTrue(owner.sequenceValid(binding(),next));
    }
    @Test public void nestedIneligibleFrameCannotBorrowOuterClaim() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame parent = claimed(owner,100);
        NativeGestureOwnership.Frame child = owner.beginFrame(binding(),stamp(110,2),false,true);
        assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(child);
        assertTrue(owner.maySuppress(listener,helper,binding())); owner.endFrame(parent);
    }
    @Test public void eligibleFirstTapAloneDoesNotOwnAnOldNativeHelperDoubleTap() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame firstTap = admitted(owner,100);
        assertTrue(firstTap.admitted); assertTrue(firstTap.shouldRecognize());
        assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(firstTap);
    }
    @Test public void unclaimedFirstOccurrenceCannotBeUpgradedByDuplicateCallback() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = admitted(owner,100); owner.endFrame(first);
        NativeGestureOwnership.Frame duplicate = admitted(owner,100);
        assertTrue(duplicate.duplicate); assertFalse(owner.claimGesture(duplicate));
        assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(duplicate);
    }
    @Test public void claimedFirstOccurrenceLetsDuplicateKeepArbitrationWithoutRecognition() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = claimed(owner,100); owner.endFrame(first);
        NativeGestureOwnership.Frame duplicate = admitted(owner,100);
        assertTrue(duplicate.duplicate); assertFalse(duplicate.shouldRecognize()); assertTrue(owner.claimGesture(duplicate));
        assertTrue(owner.maySuppress(listener,helper,binding())); owner.endFrame(duplicate);
        assertFalse(owner.maySuppress(listener,helper,binding()));
    }
    @Test public void rejectedFirstRegionCannotGainClaimThroughDuplicate() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame protectedTap = owner.beginFrame(binding(),stamp(100,0),false,true);
        assertFalse(owner.claimGesture(protectedTap)); owner.endFrame(protectedTap);
        NativeGestureOwnership.Frame duplicate = admitted(owner,100);
        assertFalse(owner.claimGesture(duplicate)); assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(duplicate);
    }
    @Test public void explicitUserAuthorizationIsStillRequiredToClaimGesture() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame frame = owner.beginFrame(binding(),stamp(100,0),true,false);
        assertFalse(owner.claimGesture(frame)); assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(frame);
    }
    @Test public void onlyCurrentSynchronousTopMayClaim() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = admitted(owner,100);
        NativeGestureOwnership.Frame nested = admitted(owner,110);
        assertFalse(owner.claimGesture(first)); assertTrue(owner.claimGesture(nested));
        owner.endFrame(nested); assertTrue(owner.claimGesture(first)); owner.endFrame(first);
        assertFalse(owner.claimGesture(first));
    }
    @Test public void oldNativeTapOutsideRegionDoesNotTransferClaimToNextSingleInsideTap() {
        NativeGestureOwnership owner = ready();
        NativeGestureOwnership.Frame outside = owner.beginFrame(binding(),stamp(100,0),false,true); owner.endFrame(outside);
        NativeGestureOwnership.Frame inside = admitted(owner,200);
        assertFalse(inside.duplicate); assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(inside);
    }
    @Test public void previouslyClaimedEventLosesClaimOnRevokeAndRebind() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame first = claimed(owner,100);
        owner.revoke(); owner.bind(binding()); assertFalse(owner.claimGesture(first));
        NativeGestureOwnership.Frame sameStamp = admitted(owner,100);
        assertFalse(sameStamp.duplicate); assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(sameStamp);
    }
    @Test public void foreignOrNullFrameCannotClaimCurrentCall() {
        NativeGestureOwnership owner = ready(), other = ready();
        NativeGestureOwnership.Frame own = admitted(owner,100), foreign = admitted(other,100);
        assertFalse(owner.claimGesture(null)); assertFalse(owner.claimGesture(foreign));
        assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(own); other.endFrame(foreign);
    }
    @Test public void differentThreadCannotClaimSynchronousFrame() throws Exception {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame frame = admitted(owner,100);
        boolean[] accepted = {true}; Thread thread = new Thread(() -> accepted[0] = owner.claimGesture(frame));
        thread.start(); thread.join(); assertFalse(accepted[0]);
        assertFalse(owner.maySuppress(listener,helper,binding())); owner.endFrame(frame);
    }
    @Test public void mismatchedFrameEndRevokesWholeStack() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame parent = claimed(owner,100);
        NativeGestureOwnership.Frame child = claimed(owner,110);
        assertFalse(owner.endFrame(parent)); assertFalse(parent.isActive()); assertFalse(child.isActive());
        assertFalse(owner.maySuppress(listener,helper,binding()));
    }
    @Test public void foreignFrameCannotCloseOrBorrowOwnership() {
        NativeGestureOwnership first = ready(), second = ready();
        NativeGestureOwnership.Frame one = claimed(first,100), two = claimed(second,100);
        assertFalse(first.endFrame(two)); assertFalse(one.isActive()); assertTrue(two.isActive()); second.endFrame(two);
    }
    @Test public void depthOverflowRevokesAllClaimsWithoutThrowingToHost() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame[] stack = new NativeGestureOwnership.Frame[NativeGestureOwnership.MAX_DEPTH];
        for (int i = 0; i < stack.length; i++) { stack[i] = claimed(owner,100 + i); assertFalse(stack[i].rejected); }
        NativeGestureOwnership.Frame overflow = admitted(owner,120);
        assertTrue(overflow.rejected); assertFalse(overflow.isActive()); assertFalse(owner.endFrame(overflow));
        for (NativeGestureOwnership.Frame frame : stack) assertFalse(frame.isActive());
        assertFalse(owner.maySuppress(listener,helper,binding()));
    }
    @Test public void beginWithoutBoundInstanceFailsClosed() {
        NativeGestureOwnership owner = new NativeGestureOwnership(); NativeGestureOwnership.Frame frame = admitted(owner,100);
        assertTrue(frame.rejected); assertFalse(frame.shouldRecognize()); assertFalse(owner.maySuppress(listener,helper,binding()));
    }
    @Test public void mismatchedBeginRevokesExistingClaim() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame frame = claimed(owner,100);
        NativeGestureOwnership.Frame rejected = owner.beginFrame(changed(0),stamp(110,2),true,true);
        assertTrue(rejected.rejected); assertFalse(frame.isActive()); assertFalse(owner.maySuppress(listener,helper,binding()));
    }
    @Test public void recentStampMemoryIsBounded() {
        NativeGestureOwnership owner = ready();
        for (int i = 0; i <= NativeGestureOwnership.MAX_RECENT_EVENTS; i++) {
            NativeGestureOwnership.Frame frame = admitted(owner,100 + i); owner.endFrame(frame);
        }
        NativeGestureOwnership.Frame expired = admitted(owner,100); assertFalse(expired.duplicate); owner.endFrame(expired);
    }
    @Test public void activeParentStampCannotBeEvictedByManyChildCalls() {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame parent = admitted(owner,100);
        for (int i = 1; i <= NativeGestureOwnership.MAX_RECENT_EVENTS + 1; i++) {
            NativeGestureOwnership.Frame child = admitted(owner,100 + i); owner.endFrame(child);
        }
        NativeGestureOwnership.Frame repeated = admitted(owner,100);
        assertTrue(repeated.duplicate); assertFalse(repeated.shouldRecognize());
        owner.endFrame(repeated); owner.endFrame(parent);
    }
    @Test public void nestedFrameOnOtherThreadRevokesCurrentOwner() throws Exception {
        NativeGestureOwnership owner = ready(); NativeGestureOwnership.Frame parent = claimed(owner,100);
        boolean[] rejected = {false};
        Thread thread = new Thread(() -> rejected[0] = admitted(owner,110).rejected);
        thread.start(); thread.join(); assertTrue(rejected[0]); assertFalse(parent.isActive());
        assertFalse(owner.maySuppress(listener,helper,binding()));
    }
    @Test public void recognizerEmitsOnceForDuplicateNaturalCallbacks() {
        NativeGestureOwnership owner = ready(); LockGestureRecognizer recognizer = new LockGestureRecognizer(300,250,10);
        for (long time : new long[]{100,120,200,220,300,320}) {
            int action = time % 100 == 0 ? LockGestureRecognizer.DOWN : LockGestureRecognizer.UP;
            NativeGestureOwnership.EventStamp stamp = new NativeGestureOwnership.EventStamp(time-time%100,time,action,4,
                    new int[]{4},new float[]{50},new float[]{90});
            NativeGestureOwnership.Frame frame = owner.beginFrame(binding(),stamp,true,true);
            int result = frame.shouldRecognize() ? recognizer.onEvent(action,time,1,new int[]{4},new float[]{50},new float[]{90},0) : 0;
            NativeGestureOwnership.Frame duplicate = owner.beginFrame(binding(),stamp,true,true);
            assertFalse(duplicate.shouldRecognize()); owner.endFrame(duplicate);
            assertEquals(time == 320 ? LockGestureRecognizer.PREVIOUS : LockGestureRecognizer.NONE,result);
            owner.endFrame(frame);
        }
        assertEquals(LockGestureRecognizer.NONE,recognizer.onTime(1000));
    }
    @Test(expected=IllegalArgumentException.class) public void bindingRejectsNullInstance() {
        new NativeGestureOwnership.Binding(null,controller,root,helper,listener,channel,7,"ticket",3,9);
    }
    @Test(expected=IllegalArgumentException.class) public void bindingRejectsInvalidTicket() {
        new NativeGestureOwnership.Binding(injector,controller,root,helper,listener,channel,7,"not/a/token",3,9);
    }
    @Test(expected=IllegalArgumentException.class) public void bindingRejectsMissingActorEpoch() {
        new NativeGestureOwnership.Binding(injector,controller,root,helper,listener,channel,0,"ticket",3,9);
    }
    @Test(expected=IllegalArgumentException.class) public void bindingRejectsNegativeRevision() {
        new NativeGestureOwnership.Binding(injector,controller,root,helper,listener,channel,7,"ticket",-1,9);
    }
    @Test(expected=IllegalArgumentException.class) public void stampRejectsDuplicatePointerIds() {
        new NativeGestureOwnership.EventStamp(100,110,5,4,new int[]{4,4},new float[]{1,2},new float[]{3,4});
    }
    @Test(expected=IllegalArgumentException.class) public void stampRejectsActionIndexMisusedAsPointerId() {
        new NativeGestureOwnership.EventStamp(100,110,5,1,new int[]{4,8},new float[]{1,2},new float[]{3,4});
    }
    @Test(expected=IllegalArgumentException.class) public void pointerActionRequiresStablePointerId() {
        new NativeGestureOwnership.EventStamp(100,110,5,-1,new int[]{4,8},new float[]{1,2},new float[]{3,4});
    }
    @Test(expected=IllegalArgumentException.class) public void stampRejectsNonFiniteCoordinates() {
        new NativeGestureOwnership.EventStamp(100,110,2,-1,new int[]{4},new float[]{Float.NaN},new float[]{3});
    }
    @Test(expected=IllegalArgumentException.class) public void stampRejectsBackwardsTime() {
        new NativeGestureOwnership.EventStamp(100,99,0,4,new int[]{4},new float[]{1},new float[]{3});
    }
}
