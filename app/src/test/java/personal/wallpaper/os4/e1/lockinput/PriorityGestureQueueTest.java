package personal.wallpaper.os4.e1.lockinput;

import org.junit.Test;
import personal.wallpaper.protocol.lock.LockProtocol;
import static org.junit.Assert.*;

public final class PriorityGestureQueueTest {
    private final Object channel=new Object(),root=new Object();
    private PriorityGestureQueue.Owner owner(){return new PriorityGestureQueue.Owner(channel,root,1,"scene");}
    private PriorityGestureQueue queue(){var q=new PriorityGestureQueue();q.bind(owner());return q;}
    @Test public void recognizedActionsAreFifo(){var q=queue();for(int a:new int[]{1,2,3})assertTrue(q.offer(owner(),a,0));for(int a:new int[]{1,2,3}){var item=q.peek();assertEquals(a,item.action);assertTrue(q.acknowledge(item));}assertEquals(0,q.size());}
    @Test public void busyDoesNotRemoveOrResignHead(){var q=queue();q.offer(owner(),LockProtocol.NEXT,0);var first=q.peek();assertSame(first,q.peek());assertEquals(0,first.created);assertEquals(1,q.size());}
    @Test public void capacityIsExactlyThirtyTwo(){var q=queue();for(int i=0;i<32;i++)assertTrue(q.offer(owner(),1,0));assertFalse(q.offer(owner(),1,0));assertEquals(32,q.size());}
    @Test public void tenSecondBoundaryIsInclusive(){var q=queue();q.offer(owner(),1,0);assertEquals(0,q.expire(10000));assertEquals(1,q.expire(10001));assertNull(q.peek());}
    @Test public void clockRewindExpiresInsteadOfExtending(){var q=queue();q.offer(owner(),1,20);assertEquals(1,q.expire(19));}
    @Test public void sameSceneRetainsQueue(){var q=queue();q.offer(owner(),1,0);assertEquals(0,q.bind(owner()));assertEquals(1,q.size());}
    @Test public void newSceneCannotReceiveOldActions(){var q=queue();q.offer(owner(),1,0);assertEquals(1,q.bind(new PriorityGestureQueue.Owner(channel,root,1,"new-scene")));assertNull(q.peek());}
    @Test public void newRootCannotReceiveOldActions(){var q=queue();q.offer(owner(),1,0);assertEquals(1,q.bind(new PriorityGestureQueue.Owner(channel,new Object(),1,"scene")));}
    @Test public void newActorCannotReceiveOldActions(){var q=queue();q.offer(owner(),1,0);assertEquals(1,q.bind(new PriorityGestureQueue.Owner(channel,root,2,"scene")));}
    @Test public void oldCallbackCannotRemoveNewHead(){var q=queue();q.offer(owner(),1,0);var old=q.peek();q.clear();q.bind(owner());q.offer(owner(),2,1);assertFalse(q.acknowledge(old));assertEquals(2,q.peek().action);}
    @Test public void wrongOwnerCannotEnqueue(){var q=queue();assertFalse(q.offer(new PriorityGestureQueue.Owner(new Object(),root,1,"scene"),1,0));}
    @Test public void invalidActionAndTimeCannotEnqueue(){var q=queue();assertFalse(q.offer(owner(),0,0));assertFalse(q.offer(owner(),1,-1));}
    @Test public void expiredHeadDoesNotRemoveLaterValidInput(){var q=queue();q.offer(owner(),1,0);q.offer(owner(),2,9000);assertEquals(1,q.expire(11000));assertEquals(2,q.peek().action);}
    @Test public void oldChannelRevocationCannotCancelNewOwner(){var q=queue();q.offer(owner(),1,0);Object next=new Object();q.bind(new PriorityGestureQueue.Owner(next,root,2,"scene"));q.offer(new PriorityGestureQueue.Owner(next,root,2,"scene"),2,1);assertEquals(0,q.clearFor(channel));assertEquals(1,q.size());}
}
