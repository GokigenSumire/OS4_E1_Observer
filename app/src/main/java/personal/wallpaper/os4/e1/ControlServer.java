package personal.wallpaper.os4.e1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import personal.wallpaper.os4.e1.core.ObservationBuffer;
import personal.wallpaper.os4.e1.core.HomeScope;
import personal.wallpaper.os4.e1.core.PersistentCommandGuard;
import personal.wallpaper.os4.e1.core.PersistentControlEpoch;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 签名权限保护的显式包广播；系统进程只保留小型内存，不写文件。 */
final class ControlServer {
    static final String ACTION = BuildConfig.APPLICATION_ID+".CONTROL";
    static final String PERMISSION = BuildConfig.APPLICATION_ID+".CONTROL";
    private final Handler handler;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "E1-timeout"); t.setDaemon(true); return t;
    });
    private final ScheduledExecutorService snapshotWorker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "E1-snapshot"); t.setDaemon(true); return t;
    });
    private final ObservationBuffer buffer = new ObservationBuffer();
    private final HomeObserver observer;
    private Set<XC_MethodHook.Unhook> bootstrapHooks;
    private boolean registered;
    private String bootstrapState="BOOTSTRAP_PENDING";
    private final PersistentControlEpoch controlEpoch = new PersistentControlEpoch();
    private final PersistentCommandGuard persistentCommands = new PersistentCommandGuard();
    private long traceEpoch;
    private long traceStartMono=-1,traceStartWall=-1;
    private final String bootSession=java.util.UUID.randomUUID().toString();
    ControlServer(ClassLoader loader) {
        HandlerThread thread = new HandlerThread("E1-control"); thread.start();
        handler = new Handler(thread.getLooper());
        observer = new HomeObserver(loader, buffer,true);
    }
    synchronized void setBootstrapHooks(Set<XC_MethodHook.Unhook> hooks) { bootstrapHooks = hooks;if(registered)handler.post(this::detachBootstrap); }
    private synchronized void detachBootstrap() {
        java.util.HashSet<XC_MethodHook.Unhook> failed=new java.util.HashSet<>();
        if(bootstrapHooks!=null)for(XC_MethodHook.Unhook hook:bootstrapHooks) {try{hook.unhook();}catch(Throwable ignored){failed.add(hook);}}
        bootstrapHooks=failed;bootstrapState=failed.isEmpty()?"BOOTSTRAP_UNHOOKED":"BOOTSTRAP_UNHOOK_FAILED";
    }
    private void watch(long started) {
        synchronized(this) {
            if(controlEpoch.current()!=started)return;
            if(observer.tick(SystemClock.elapsedRealtime()))return;
            watchdog.schedule(()->watch(started),observer.recovering()?250:5000,TimeUnit.MILLISECONDS);
        }
    }
    void bind(Context context, Object wms) {
        handler.post(() -> {
            if (registered) return;
            try {
                observer.setService(wms);
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context ctx, Intent intent) {
                        if (!ACTION.equals(intent.getAction())) return;
                        Bundle result = new Bundle();
                        try {
                            String cmd = intent.getStringExtra("cmd");
                            if ("DAILY_ON".equals(cmd)) {
                                synchronized (ControlServer.this) {
                                    if (bootSession.equals(intent.getStringExtra("bootSession"))&&controlEpoch.acceptsOn(intent.getLongExtra("epoch",-1))&&observer.canStart()
                                            &&persistentCommands.acceptOn(intent.getLongExtra("desiredRevision",-1))) {
                                        long now=SystemClock.elapsedRealtime();
                                        if (observer.resetIdentity(now)) {
                                            long started = controlEpoch.advance();
                                            watchdog.schedule(() -> watch(started),250,TimeUnit.MILLISECONDS);
                                            observer.install();
                                            if(!observer.enable(SystemClock.elapsedRealtime()))observer.recover(SystemClock.elapsedRealtime(),"ERROR");
                                            long observationEpoch = observer.currentEpoch();
                                            snapshotWorker.execute(() -> observer.snapshot(observationEpoch));
                                        }
                                    }else result.putString("error","START_REJECTED_RECOVERY_OR_HOOKS");
                                }
                            } else if("TRACE_START".equals(cmd)) {
                                synchronized(ControlServer.this){
                                    if(!bootSession.equals(intent.getStringExtra("bootSession"))||!HomeScope.dailyCommandAllowed(intent.getLongExtra("epoch",-1),controlEpoch.current())||buffer.active(SystemClock.elapsedRealtime()))result.putString("error","TRACE_START_REJECTED");
                                    else{long now=SystemClock.elapsedRealtime();if(buffer.start(now,intent.getStringExtra("session"))){traceEpoch++;traceStartMono=now;traceStartWall=System.currentTimeMillis();}}
                                }
                            } else if("TRACE_STOP".equals(cmd)) {
                                synchronized(ControlServer.this){buffer.stop("USER_STOP");}
                            } else if("MARK".equals(cmd)) {
                                synchronized(ControlServer.this){if(HomeScope.commandAllowed(intent.getLongExtra("epoch",-1),controlEpoch.current()))observer.mark(intent.getStringExtra("mark"));else result.putString("error","STALE_COMMAND");}
                            } else if ("DAILY_OFF".equals(cmd) || "UNHOOK".equals(cmd)) {
                                synchronized (ControlServer.this) {
                                    boolean unhook="UNHOOK".equals(cmd);
                                    boolean emergency=unhook||intent.getLongExtra("desiredRevision",-1)<0;
                                    if(emergency||persistentCommands.acceptOff(intent.getLongExtra("desiredRevision",-1))){
                                        if(emergency)persistentCommands.emergencyStop();
                                        long stoppedEpoch=controlEpoch.revoke();
                                        observer.recover(SystemClock.elapsedRealtime(),unhook?"UNHOOK_REQUESTED":"USER_STOP");
                                        observer.tick(SystemClock.elapsedRealtime());
                                        observer.detach();
                                        detachBootstrap();
                                        watchdog.schedule(()->watch(stoppedEpoch),250,TimeUnit.MILLISECONDS);
                                    }else result.putString("error","STALE_DESIRED_REVISION");
                                }
                            } else if (!"STATUS".equals(cmd) && !"TRACE_PAGE".equals(cmd)) {
                                result.putString("error", "COMMAND_REJECTED");
                            }
                            synchronized (ControlServer.this) {
                                observer.tick(SystemClock.elapsedRealtime());
                                if (!observer.running()) observer.detach();
                            }
                            result.putString("status", buffer.status(SystemClock.elapsedRealtime()));
                            result.putString("hooks", observer.hookStatus());
                            result.putString("coverage", observer.coverage());
                            result.putInt("rows", buffer.size());
                            result.putBoolean("running",observer.running());
                            result.putString("contrast",observer.contrastStatus(SystemClock.elapsedRealtime()));
                            result.putLong("traceEpoch",traceEpoch);
                            result.putString("bootSession",bootSession);
                            result.putString("clock","bootSession="+bootSession+" traceStartElapsedRealtimeMs="+traceStartMono+" traceStartWallMs="+traceStartWall+" sampledElapsedRealtimeMs="+SystemClock.elapsedRealtime()+" sampledWallMs="+System.currentTimeMillis());
                            if ("TRACE_PAGE".equals(cmd)) {
                                int offset = intent.getIntExtra("offset", 0);
                                if(!bootSession.equals(intent.getStringExtra("bootSession"))||!HomeScope.exportAllowed(intent.getLongExtra("traceEpoch",-1),traceEpoch,intent.getIntExtra("rows",-1),buffer.size(),buffer.active(SystemClock.elapsedRealtime())))
                                    result.putString("error","SESSION_CHANGED");
                                else result.putStringArrayList("lines", new ArrayList<>(buffer.page(offset, 32)));
                            }
                            result.putString("module", BuildConfig.VERSION_CODE+"/"+BuildConfig.VERSION_NAME);
                            result.putLong("epoch",controlEpoch.current());
                            result.putLong("desiredRevision",persistentCommands.revision());
                            result.putString("bootstrap",bootstrapState);
                        } catch (Throwable ignored) {
                            observer.recover(SystemClock.elapsedRealtime(),"ERROR");long failedEpoch=controlEpoch.current();watchdog.schedule(()->watch(failedEpoch),250,TimeUnit.MILLISECONDS);result.putString("error", "CONTROL_FAILED");
                        }
                        setResultCode(0); setResultExtras(result);
                    }
                }, new IntentFilter(ACTION), PERMISSION, handler, Context.RECEIVER_EXPORTED);
                registered = true;
                // 只移除本模块的一次性构造入口；其他模块回调不受影响。
                detachBootstrap();
            } catch (Throwable ignored) { buffer.stop("ERROR"); }
        });
    }
}
