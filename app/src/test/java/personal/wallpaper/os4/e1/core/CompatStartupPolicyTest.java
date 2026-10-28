package personal.wallpaper.os4.e1.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompatStartupPolicyTest {
    private static final String CLEAN="dailyMode=DISARMED dailyPolicy=PERMANENT dailyDurationMs=0 dailyDeadline=-1 dailyNeedsRecovery=false dailyFinal=NONE recoveryStatus=NOT_VERIFIED";
    private static CompatStartupPolicy.Decision decide(String text) { return evaluate("62/1.0.0",text,"UNHOOKED","BOOTSTRAP_UNHOOKED",null,0,"boot-opaque"); }
    private static CompatStartupPolicy.Decision evaluate(String version,String text,String hooks,String bootstrap,String error,long epoch,String boot) {return CompatStartupPolicy.evaluate(version,62,"1.0.0",text,hooks,bootstrap,error,epoch,boot);}
    @Test public void cleanPermanentStarts() {assertEquals(CompatStartupPolicy.Decision.START,decide(CLEAN));}
    @Test public void alreadyRunningDoesNotRestartOrRenew() {assertEquals(CompatStartupPolicy.Decision.ALREADY_RUNNING,evaluate("62/1.0.0",CLEAN.replace("DISARMED","ENABLE"),"HOOKED","BOOTSTRAP_UNHOOKED",null,2,"boot"));}
    @Test public void timedPolicyRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN.replace("PERMANENT","TIMED")));}
    @Test public void positiveDurationRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN.replace("dailyDurationMs=0","dailyDurationMs=43200000")));}
    @Test public void fakeFarDeadlineRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN.replace("dailyDeadline=-1","dailyDeadline=9223372036854775807")));}
    @Test public void missingVersionRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate(null,CLEAN,"UNHOOKED","BOOTSTRAP_UNHOOKED",null,0,"boot"));}
    @Test public void oldVersionRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate("60/1.0.0",CLEAN,"UNHOOKED","BOOTSTRAP_UNHOOKED",null,0,"boot"));}
    @Test public void recoveryLockRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN.replace("dailyNeedsRecovery=false","dailyNeedsRecovery=true")));}
    @Test public void recoveringRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN.replace("DISARMED","RECOVERY")));}
    @Test public void unconfirmedRecoveryRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN.replace("dailyFinal=NONE","dailyFinal=FINAL_UNCONFIRMED").replace("NOT_VERIFIED","RECOVERY_UNCONFIRMED")));}
    @Test public void confirmedRecoveryCanStart() {assertEquals(CompatStartupPolicy.Decision.START,decide(CLEAN.replace("dailyFinal=NONE","dailyFinal=FINAL_CONFIRMED").replace("NOT_VERIFIED","RECOVERY_CONFIRMED")));}
    @Test public void unknownEpochRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate("62/1.0.0",CLEAN,"UNHOOKED","BOOTSTRAP_UNHOOKED",null,-1,"boot"));}
    @Test public void unknownBootRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate("62/1.0.0",CLEAN,"UNHOOKED","BOOTSTRAP_UNHOOKED",null,0,""));}
    @Test public void bootstrapFailureRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate("62/1.0.0",CLEAN,"UNHOOKED","BOOTSTRAP_UNHOOK_FAILED",null,0,"boot"));}
    @Test public void unexpectedHooksRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate("62/1.0.0",CLEAN,"HOOKED","BOOTSTRAP_UNHOOKED",null,0,"boot"));}
    @Test public void runtimeErrorRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate("62/1.0.0",CLEAN,"UNHOOKED","BOOTSTRAP_UNHOOKED","CONTROL_FAILED",0,"boot"));}
    @Test public void duplicateKeyRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN+" dailyMode=ENABLE"));}
    @Test public void malformedFieldRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN+" broken"));}
    @Test public void missingPolicyRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,decide(CLEAN.replace("dailyPolicy=PERMANENT ","")));}
    @Test public void nonPolicyCoverageTextNotRequired() {assertEquals(CompatStartupPolicy.Decision.START,decide(CLEAN+" dailyApplied=0"));}
    @Test public void runningWithRecoveryFlagIsRejected() {assertEquals(CompatStartupPolicy.Decision.REJECT,evaluate("62/1.0.0",CLEAN.replace("DISARMED","ENABLE").replace("dailyNeedsRecovery=false","dailyNeedsRecovery=true"),"HOOKED","BOOTSTRAP_UNHOOKED",null,3,"boot"));}
}
