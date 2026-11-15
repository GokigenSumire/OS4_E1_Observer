package personal.wallpaper.os4.e1;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DesktopToggleControllerTest {
    static final class Store implements DesktopToggleController.Store {
        boolean enabled, available=true, fail;
        long revision; int writes;
        public CompatPreferenceStore.State read(){return new CompatPreferenceStore.State(enabled,available,revision,!available);}
        public boolean save(boolean value){writes++;enabled=value;revision++;available=!fail;return !fail;}
    }
    static final class Request {
        final String command; final long revision; final DesktopToggleController.Callback callback;
        Request(String command,long revision,DesktopToggleController.Callback callback){this.command=command;this.revision=revision;this.callback=callback;}
    }
    static final class Backend implements DesktopToggleController.Backend {
        final ArrayList<Request> calls=new ArrayList<>(); boolean throwsError;
        public void status(DesktopToggleController.Callback cb){if(throwsError)throw new IllegalStateException();calls.add(new Request("STATUS",-1,cb));}
        public void enable(CompatPreferenceStore.State saved,DesktopToggleController.Ticket ticket,DesktopToggleController.Callback cb){
            assertTrue(saved.available&&saved.enabled&&ticket.valid());calls.add(new Request("ON",saved.revision,cb));
        }
        public void stop(long revision,DesktopToggleController.Callback cb){calls.add(new Request("OFF",revision,cb));}
    }
    private final Store store=new Store(); private final Backend backend=new Backend(); private int updates;
    private final DesktopToggleController controller=new DesktopToggleController(store,backend,()->updates++);
    static DashboardState ready(boolean desired){return DashboardState.from("dailyPolicy=PERMANENT dailyMode=DISARMED dailyNeedsRecovery=false dailyFinal=FINAL_CONFIRMED","UNHOOKED","BOOTSTRAP_UNHOOKED","OK","NONE",true,desired);}
    static DashboardState active(boolean desired){return DashboardState.from("dailyPolicy=PERMANENT dailyMode=ENABLE targetWallpaper=PROJECT_WALLPAPER","HOOKED","BOOTSTRAP_UNHOOKED","OK","NONE",true,desired);}
    static DashboardState recovering(){return DashboardState.from("dailyPolicy=PERMANENT dailyMode=DISARMED dailyNeedsRecovery=true dailyFinal=FINAL_UNCONFIRMED","UNHOOKED","BOOTSTRAP_UNHOOKED","OK","NONE",true,true);}
    private void reply(int index,DashboardState state){backend.calls.get(index).callback.accept(new DesktopToggleController.Response(state,new DesktopToggleController.Ticket(2,"boot"),null));}
    @Test public void onSavesBeforeAnyRemoteReply(){controller.change(true);assertTrue(store.enabled);assertEquals(1,store.writes);assertTrue(controller.pending);assertEquals("STATUS",backend.calls.get(0).command);}
    @Test public void recoveryCanSaveOnWithoutBypassingBackendAdmission(){controller.change(true);reply(0,recovering());assertTrue(store.enabled);assertFalse(controller.pending);assertEquals(1,backend.calls.size());assertTrue(controller.message.contains("尚未生效"));}
    @Test public void readyResponseEnablesWithTheSameSavedRevision(){controller.change(true);reply(0,ready(true));assertEquals("ON",backend.calls.get(1).command);assertEquals(1,backend.calls.get(1).revision);reply(1,active(true));assertFalse(controller.pending);assertEquals(DashboardState.Kind.ACTIVE,controller.actual.kind);}
    @Test public void offDuringStatusSupersedesOldOnBeforeItCanBeSent(){controller.change(true);controller.change(false);assertFalse(store.enabled);assertEquals("OFF",backend.calls.get(1).command);reply(0,ready(true));assertEquals(2,backend.calls.size());reply(1,ready(false));assertFalse(controller.pending);}
    @Test public void offDuringEnableDoesNotAcceptLateActiveReply(){controller.change(true);reply(0,ready(true));controller.change(false);reply(2,ready(false));reply(1,active(true));assertFalse(store.enabled);assertEquals(DashboardState.Kind.READY,controller.actual.kind);assertFalse(controller.pending);}
    @Test public void onAfterOffIgnoresLateOffReply(){controller.change(false);controller.change(true);reply(0,ready(false));assertTrue(controller.pending);reply(1,ready(true));assertEquals("ON",backend.calls.get(2).command);assertEquals(store.revision,backend.calls.get(2).revision);}
    @Test public void oldTimeoutCannotOverwriteNewlyConfirmedStop(){controller.change(true);controller.change(false);reply(1,ready(false));backend.calls.get(0).callback.accept(null);assertEquals(DashboardState.Kind.READY,controller.actual.kind);assertEquals("",controller.message);}
    @Test public void externalRevisionChangeRejectsUnexecutedEnable(){controller.change(true);store.save(false);reply(0,ready(true));assertEquals(1,backend.calls.size());assertFalse(controller.pending);}
    @Test public void failedSaveCannotEnableEvenIfMemoryLooksOn(){store.fail=true;controller.change(true);assertTrue(store.enabled);assertTrue(controller.savingFailed);assertFalse(controller.pending);assertTrue(backend.calls.isEmpty());}
    @Test public void failedOffSaveStillSendsEmergencyStop(){store.fail=true;controller.change(false);assertEquals("OFF",backend.calls.get(0).command);assertEquals(-1,backend.calls.get(0).revision);reply(0,ready(false));assertTrue(controller.savingFailed);assertTrue(controller.message.contains("未保存"));}
    @Test public void retryFailedOffSavesOffAgainWithoutTurningItOn(){store.fail=true;controller.change(false);reply(0,ready(false));store.fail=false;controller.retry();assertFalse(store.enabled);assertFalse(controller.savingFailed);assertEquals(2,store.writes);assertEquals("OFF",backend.calls.get(1).command);assertEquals(store.revision,backend.calls.get(1).revision);}
    @Test public void invalidControlTicketCannotEnable(){controller.change(true);backend.calls.get(0).callback.accept(new DesktopToggleController.Response(ready(true),new DesktopToggleController.Ticket(-1,null),null));assertEquals(1,backend.calls.size());assertFalse(controller.pending);}
    @Test public void oldModuleIsAnAdvisoryAndStillAllowsOff(){controller.change(true);backend.calls.get(0).callback.accept(new DesktopToggleController.Response(null,null,"MODULE_VERSION_MISMATCH"));assertTrue(store.enabled);assertTrue(controller.message.contains("重载"));controller.change(false);assertEquals("OFF",backend.calls.get(1).command);assertFalse(store.enabled);}
    @Test public void refreshIsReadOnly(){controller.refresh();reply(0,ready(false));assertEquals(0,store.writes);assertFalse(store.enabled);assertEquals(1,backend.calls.size());}
    @Test public void retryUsesSavedRevisionWithoutAnotherWrite(){controller.change(true);reply(0,recovering());controller.retry();reply(1,ready(true));assertEquals(1,store.writes);assertEquals(1,backend.calls.get(2).revision);}
    @Test public void repeatedRetryWhilePendingRemainsAdvisory(){controller.change(true);controller.retry();assertEquals(1,backend.calls.size());assertTrue(controller.pending);assertTrue(controller.message.contains("可修改"));controller.change(false);assertFalse(store.enabled);}
    @Test public void transportExceptionDoesNotBlockFutureChoice(){backend.throwsError=true;controller.change(true);assertFalse(controller.pending);backend.throwsError=false;controller.change(false);assertEquals("OFF",backend.calls.get(0).command);}
    @Test public void closingDiscardsCallbacksAndFurtherActions(){controller.change(true);int before=updates;controller.close();reply(0,ready(true));controller.change(false);assertEquals(before,updates);assertEquals(1,store.writes);assertEquals(1,backend.calls.size());}
}
