package personal.wallpaper.os4.e1.relay;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import personal.wallpaper.os4.e1.BuildConfig;
import personal.wallpaper.protocol.lock.LockProtocol;
import personal.wallpaper.os4.e1.lockinput.NativeInputDetail;
import personal.wallpaper.os4.e1.lockinput.MonotonicObservation;

/** 正式模块自己的中继进程；两侧都核真实Binder调用身份，不在广播或偏好中传能力。 */
public final class LockRelayService extends Service {
    private final HostOutcomeTotals hostOutcomeTotals=new HostOutcomeTotals();
    private static final String PREFS = "lock_compat_permanent", ENABLED = "enabled";
    private static volatile String liveStatus = "NOT_CONNECTED";
    private static final DurableSwitchGate switchGate = new DurableSwitchGate();
    private static volatile LockRelayService diagnosticService;

    /** 仅本应用进程的直接调用，不是Binder、广播或可导出能力。回调只在主线程。 */
    public interface DiagCallback { void onResult(DiagSnapshot result); }
    public interface DiagRequest { void cancel(); }
    private static final class PendingDiagRequest implements DiagRequest {
        private final AtomicReference<DiagCallback> callback;
        private PendingDiagRequest(DiagCallback callback) { this.callback = new AtomicReference<>(callback); }
        /** 页面离开可取消回调；不停止诊断，也不改变任一永久开关。 */
        @Override public void cancel() { callback.set(null); }
        private void deliver(DiagSnapshot result) {
            DiagCallback live = callback.getAndSet(null);
            if (live != null) live.onResult(result);
        }
    }
    /** UI可持有的不可变脱敏结果；故意不提供内部session token、Binder或actor身份。 */
    public static final class DiagSnapshot {
        public final boolean success, active, complete;
        public final String operation, state, detail;
        public final int reasonCode, totalRows, bytes;
        public final List<String> lines;
        private DiagSnapshot(DiagnosticClient.Result result) {
            success = result.success; active = result.active; complete = result.complete;
            operation = result.operation; detail = result.detail;
            state = active ? "RUNNING" : complete ? "STOPPED_SNAPSHOT" : detail;
            reasonCode = result.reasonCode; totalRows = result.totalRows; bytes = result.bytes;
            lines = Collections.unmodifiableList(new ArrayList<>(result.lines));
        }
        public static DiagSnapshot create(boolean success, String operation, String detail, boolean active,
                boolean complete, int reasonCode, int totalRows, int bytes, List<String> lines) {
            return new DiagSnapshot(DiagnosticClient.display(success, operation, detail, active, complete,
                    reasonCode, totalRows, bytes, lines));
        }
        public String toText() {
            StringBuilder text = new StringBuilder("Hyper Wallpaper 兼容诊断\n");
            text.append("module=").append(BuildConfig.VERSION_CODE).append('/').append(BuildConfig.VERSION_NAME).append('\n');
            text.append("operation=").append(operation).append(" success=").append(success)
                    .append(" state=").append(state).append(" active=").append(active)
                    .append(" complete=").append(complete).append('\n');
            text.append("reasonCode=").append(reasonCode).append(" totalRows=").append(totalRows)
                    .append(" bytes=").append(bytes).append(" detail=").append(detail).append('\n');
            for (String line : lines) text.append(line).append('\n');
            return text.toString();
        }
    }
    public static DiagRequest requestDiag(Context context, String operation, DiagCallback callback) {
        PendingDiagRequest request = new PendingDiagRequest(callback);
        if (!localDiagnosticCaller(context)) {
            postDiagnostic(request, DiagnosticClient.Result.failure(operation, "LOCAL_CALLER_REQUIRED"));
            return request;
        }
        if (!DiagnosticClient.validOperation(operation)) {
            postDiagnostic(request, DiagnosticClient.Result.failure(operation, "INVALID_OPERATION"));
            return request;
        }
        LockRelayService service = diagnosticService;
        if (service == null || service.destroyed) {
            postDiagnostic(request, DiagnosticClient.Result.failure(operation, "SERVICE_UNAVAILABLE"));
            return request;
        }
        service.submitDiagnostic(operation, request);
        return request;
    }
    private static boolean localDiagnosticCaller(Context context) {
        try {
            return context != null && Binder.getCallingUid() == Process.myUid()
                    && Binder.getCallingPid() == Process.myPid()
                    && context.getApplicationInfo().uid == Process.myUid()
                    && BuildConfig.APPLICATION_ID.equals(context.getPackageName());
        } catch (RuntimeException ignored) { return false; }
    }
    private static void postDiagnostic(PendingDiagRequest request, DiagnosticClient.Result result) {
        DiagSnapshot snapshot = new DiagSnapshot(result);
        new Handler(Looper.getMainLooper()).post(() -> request.deliver(snapshot));
    }
    private static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public static synchronized boolean isDesiredEnabled(Context context) {
        try { return switchGate.allows(preferences(context).getBoolean(ENABLED, false)); } catch (RuntimeException ignored) { return false; }
    }
    public static synchronized boolean setDesiredEnabled(Context context, boolean enabled) {
        switchGate.beginWrite();
        try {
            boolean committed = preferences(context).edit().putBoolean(ENABLED, enabled).commit();
            switchGate.finishedWrite(committed);
            return committed;
        } catch (RuntimeException ignored) { switchGate.finishedWrite(false); return false; }
    }
    public static ForceLocalSetting.State forceLocalState(Context context) {
        try { return ForceLocalSetting.read(preferences(context)); }
        catch (RuntimeException ignored) { return new ForceLocalSetting.State(false,false,-1); }
    }
    public static boolean setForceLocalEnabled(Context context, boolean enabled) {
        boolean saved;
        try { saved=ForceLocalSetting.write(preferences(context),enabled); }
        catch (RuntimeException ignored) { saved=false; }
        LockRelayService service=diagnosticService;
        if(service!=null)service.invalidateForcePolicy();
        return saved;
    }
    public static WallpaperPrioritySetting.State wallpaperPriorityState(Context context){
        try{return WallpaperPrioritySetting.read(preferences(context));}catch(RuntimeException ignored){return new WallpaperPrioritySetting.State(false,false,-1);}
    }
    public static boolean setWallpaperPriorityEnabled(Context context,boolean enabled){
        boolean saved;try{saved=WallpaperPrioritySetting.write(preferences(context),enabled);}catch(RuntimeException ignored){saved=false;}
        LockRelayService service=diagnosticService;if(service!=null)service.invalidateForcePolicy();return saved;
    }
    public static NativeInputSetting.State nativeInputState(Context context){
        try{return NativeInputSetting.read(preferences(context));}catch(RuntimeException ignored){return new NativeInputSetting.State(false,false,-1);}
    }
    public static boolean setNativeInputEnabled(Context context,boolean enabled){
        boolean saved;try{saved=NativeInputSetting.write(preferences(context),enabled);}catch(RuntimeException ignored){saved=false;}
        LockRelayService service=diagnosticService;if(service!=null)service.invalidateForcePolicy();return saved;
    }
    public static String status(Context context) {
        return !switchGate.confirmed() ? "SAVE_UNCONFIRMED" : isDesiredEnabled(context) ? liveStatus : "OFF";
    }

    private final Object stateLock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "LOCK-relay"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    // 诊断分页不占用播放/认证worker；最多一个请求，忙时拒绝，不堆积页面或回调。
    private final ThreadPoolExecutor diagnosticWorker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), r -> { Thread t = new Thread(r, "LOCK-diagnostic"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean diagnosticBusy = new AtomicBoolean();
    private final DiagnosticClient diagnosticClient = new DiagnosticClient();
    private final RelayAdmission admission = new RelayAdmission();
    private volatile boolean destroyed, hostReady;
    private volatile String hostTicket = "", hostReason = "NO_ENGINE", hostSession;
    private static final class HostObservation {
        final boolean ready;final String ticket,reason;
        HostObservation(boolean ready,String ticket,String reason){
            this.ready=ready;
            // 未就绪图片仍可属于当前已鉴别LOCK场景；保留输入身份不授予渲染或命令READY。
            boolean inputScene=LockProtocol.sceneId(ticket)!=null&&LockProtocol.inputOnlyPriorityReason(reason)!=null;
            this.ticket=ready||inputScene?ticket:"";
            this.reason=reason;
        }
    }
    private volatile HostObservation hostObservation=new HostObservation(false,"","NO_ENGINE");
    private final MonotonicObservation<HostObservation> hostObservations=new MonotonicObservation<>();
    private final Object hostObservationLock=new Object();
    private final Object nativeHostIOLock=new Object();
    private volatile IBinder hostBinder, serverSession;
    private volatile int serverPid = -1, hostPid = -1;
    private volatile String serverEpoch;
    private volatile long moduleActorEpoch = -1;
    private volatile String moduleActorSession;
    private volatile boolean armed;
    private volatile long appliedForceRevision=-1;
    private volatile String producerSummary = "输入状态尚未读取";
    private long commandSequence;
    private String editDiagnosticSession="",editDiagnosticToken="";
    private long editDiagnosticCursor;
    private boolean editDiagnosticUnavailable;
    private final Object editDiagnosticLock=new Object();
    private Pending pending;
    private boolean hostBound;
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (p, key) -> {
        if (ENABLED.equals(key)) {
            if (!isDesiredEnabled(this)) { invalidateHostObservation("COMPAT_NOT_ARMED"); admission.clear(); }
            execute(this::synchronizeState);
        }
        if(ForceLocalSetting.REVISION.equals(key))invalidateForcePolicy();
    };
    private void invalidateForcePolicy() {
        invalidateHostObservation("POLICY_CHANGED");
        synchronized(stateLock){pending=null;admission.clear();}
        execute(()->{disarm();closeHostSession();appliedForceRevision=-1;synchronizeState();});
    }
    private static final class Pending {
        final String id, actorSession;
        final long actorEpoch, gestureSeq, deadline, forceRevision;
        Pending(String id, String actorSession, long actorEpoch, long gestureSeq, long deadline,long forceRevision) {
            this.id = id; this.actorSession = actorSession; this.actorEpoch = actorEpoch;
            this.gestureSeq = gestureSeq; this.deadline = deadline;
            this.forceRevision=forceRevision;
        }
    }

    private boolean execute(Runnable task) {
        if (destroyed) return false;
        try { worker.execute(() -> { if (!destroyed) task.run(); }); return true; }
        catch (RuntimeException ignored) { liveStatus = "BUSY"; return false; }
    }
    private void submitDiagnostic(String operation, PendingDiagRequest request) {
        if (!diagnosticBusy.compareAndSet(false, true)) {
            postDiagnostic(request, DiagnosticClient.Result.failure(operation, "BUSY"));
            return;
        }
        try {
            diagnosticWorker.execute(() -> {
                DiagnosticClient.Result result;
                try {
                    IBinder existing = serverSession;
                    if ((existing == null || !existing.isBinderAlive()) && !destroyed && diagnosticService == this) connectServer();
                    IBinder server = serverSession;
                    if (destroyed || diagnosticService != this || server == null || !server.isBinderAlive())
                        result = DiagnosticClient.Result.failure(operation, "CHANNEL_UNAVAILABLE");
                    else result = requestDiagnosticWithEditBaseline(operation,server);
                    if (destroyed || diagnosticService != this)
                        result = DiagnosticClient.Result.failure(operation, "SERVICE_UNAVAILABLE");
                } catch (Exception ignored) {
                    result = DiagnosticClient.Result.failure(operation, "CHANNEL_UNAVAILABLE");
                } finally { diagnosticBusy.set(false); }
                postDiagnostic(request, result);
            });
        } catch (RuntimeException ignored) {
            diagnosticBusy.set(false);
            postDiagnostic(request, DiagnosticClient.Result.failure(operation, "SERVICE_UNAVAILABLE"));
        }
    }
    private DiagnosticClient.Reply diagnosticTransaction(IBinder expected, int code, String token,
            long revision, int offset) throws Exception {
        if (destroyed || serverSession != expected || !expected.isBinderAlive()) throw new IllegalStateException();
        if (code < DiagnosticClient.START || code > DiagnosticClient.PAGE) throw new IllegalArgumentException();
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.SESSION);
            if (code == DiagnosticClient.STOP) data.writeString(token);
            else if (code == DiagnosticClient.PAGE) { data.writeString(token); data.writeLong(revision); data.writeInt(offset); }
            if (data.dataSize() > LockProtocol.MAX_PARCEL_BYTES || !expected.transact(code, data, reply, 0)
                    || reply.dataSize() > LockProtocol.MAX_PARCEL_BYTES) throw new IllegalStateException();
            reply.readException();
            String sessionToken = reply.readString(); long resultRevision = reply.readLong();
            int active = reply.readInt(), reason = reply.readInt(), rows = reply.readInt(), bytes = reply.readInt();
            ArrayList<String> lines = new ArrayList<>();
            if (code == DiagnosticClient.PAGE) {
                int count = reply.readInt();
                if (count < 0 || count > DiagnosticClient.PAGE_ROWS) throw new IllegalArgumentException();
                for (int i = 0; i < count; i++) lines.add(reply.readString());
            }
            if (reply.dataAvail() != 0 || destroyed || serverSession != expected) throw new IllegalArgumentException();
            return new DiagnosticClient.Reply(sessionToken, resultRevision, active, reason, rows, bytes, lines);
        } finally { data.recycle(); reply.recycle(); }
    }
    /** 先采主结果游标，再开启诊断，最后才向页面返回RUNNING，防首次轮询吞早期事件。 */
    private DiagnosticClient.Result requestDiagnosticWithEditBaseline(String operation,IBinder server)throws Exception{
        synchronized(editDiagnosticLock){
            boolean preparing=false,primed=false;long cursor=0;String hostOwner=hostSession;
            if("START".equals(operation)){
                DiagnosticClient.Reply before=diagnosticTransaction(server,DiagnosticClient.STATE,null,0,0);
                preparing=before.active!=1;
                if(preparing)try{cursor=editResultBaseline(hostBinder,hostOwner);primed=true;}catch(Throwable ignored){ }
            }
            DiagnosticClient.Result result=diagnosticClient.request(operation,(code,token,revision,offset)->diagnosticTransaction(server,code,token,revision,offset));
            if(preparing&&result.success&&result.active){
                DiagnosticClient.Reply started=diagnosticTransaction(server,DiagnosticClient.STATE,null,0,0);
                editDiagnosticSession=hostOwner==null?"":hostOwner;editDiagnosticToken=started.token;editDiagnosticCursor=cursor;editDiagnosticUnavailable=false;
                if(!primed||!java.util.Objects.equals(hostOwner,hostSession)){
                    recordEditDiagnostic(server,started.token,NativeInputDetail.code("NATIVE_EDIT_BASELINE_UNAVAILABLE"),1);editDiagnosticSession="";
                }
            }
            return result;
        }
    }
    private long editResultBaseline(IBinder host,String session)throws Exception{
        if(host==null||session==null||!trustedHostInstalled())throw new SecurityException();
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockProtocol.HOST);data.writeString(session);data.writeLong(0);
            if(!host.transact(LockProtocol.EDIT_DIAGNOSTICS,data,reply,0)||reply.dataSize()>LockProtocol.MAX_PARCEL_BYTES)throw new IllegalStateException();
            reply.readException();int schema=reply.readInt();long latest=reply.readLong();int truncated=reply.readInt(),count=reply.readInt();
            if(schema!=1||latest<0||truncated<0||truncated>1||count<0||count>32)throw new IllegalArgumentException();
            long prior=0;for(int i=0;i<count;i++){long seq=reply.readLong();int cause=reply.readInt();if(seq<=prior||seq>latest||NativeInputDetail.editCause(cause)==0)throw new IllegalArgumentException();prior=seq;}
            if(reply.dataAvail()!=0||hostBinder!=host||!session.equals(hostSession))throw new SecurityException();return latest;
        }finally{data.recycle();reply.recycle();}
    }
    private boolean serverCaller(int uid, int pid) {
        return uid == Process.SYSTEM_UID && pid > 0 && pid == serverPid && serverSession != null
                && serverSession.isBinderAlive() && LockProtocol.token(serverEpoch);
    }
    private boolean trustedHostInstalled() {
        try {
            int uid = getPackageManager().getApplicationInfo(LockProtocol.HOST_PACKAGE, 0).uid;
            return LockCallerAuth.host(this, uid, Process.myPid());
        } catch (Exception ignored) { return false; }
    }

    private final Binder relayBinder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            int uid = Binder.getCallingUid(), pid = Binder.getCallingPid();
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(LockProtocol.RELAY); return true; }
            if (code < LockProtocol.RELAY_EVENT || code > LockProtocol.RELAY_STOP) return false;
            if (reply == null || (flags & IBinder.FLAG_ONEWAY) != 0 || data.dataSize() > LockProtocol.MAX_PARCEL_BYTES) return false;
            try {
                data.enforceInterface(LockProtocol.RELAY);
                if (!serverCaller(uid, pid)) throw new SecurityException("UNAUTHORIZED");
                if (code == LockProtocol.RELAY_EVENT) {
                    int action = data.readInt(); long seq = data.readLong(), issued = data.readLong(), actorEpoch = data.readLong();
                    String actorSession = data.readString(), ticket = data.readString(); if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                    int result;
                    synchronized(nativeHostIOLock){
                    ForceLocalSetting.State force=forceLocalState(LockRelayService.this);
                    WallpaperPrioritySetting.State priority=wallpaperPriorityState(LockRelayService.this);
                    NativeInputSetting.State nativeInput=nativeInputState(LockRelayService.this);
                    if(nativeInput.enabled)refreshNativeHostStateLocked();
                    HostObservation hostState=hostObservation;
                    synchronized (stateLock) {
                        result = admission.admit(isDesiredEnabled(LockRelayService.this), nativeInput.available && nativeInput.revision==force.revision && hostState.ready && priority.available && priority.revision==force.revision && force.available && force.revision==appliedForceRevision && ticket != null && ticket.equals(hostState.ticket),
                                action, seq, issued, actorEpoch, actorSession, ticket, SystemClock.elapsedRealtime());
                        if (result == RelayAdmission.ACCEPTED) {
                            Pending request = new Pending(UUID.randomUUID().toString(), actorSession, actorEpoch, seq,
                                    SystemClock.elapsedRealtime() + 15000,force.revision);
                            pending = request;
                            if (!execute(() -> command(request, action, ticket, issued))) {
                                pending = null; admission.complete(); result = RelayAdmission.BUSY;
                            }
                        }
                    }
                    }
                    reply.writeNoException(); reply.writeInt(result);
                } else if (code == LockProtocol.RELAY_STATE) {
                    if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                    synchronized(nativeHostIOLock){
                    ForceLocalSetting.State force=forceLocalState(LockRelayService.this);
                    WallpaperPrioritySetting.State priority=wallpaperPriorityState(LockRelayService.this);
                    NativeInputSetting.State nativeInput=nativeInputState(LockRelayService.this);
                    if(nativeInput.enabled)refreshNativeHostState();
                    HostObservation hostState=hostObservation;
                    boolean ready = !destroyed && nativeInput.available && nativeInput.revision==force.revision && isDesiredEnabled(LockRelayService.this) && hostState.ready && priority.available && priority.revision==force.revision && force.available && force.revision==appliedForceRevision;
                    String readyReason=readyMarker(nativeInput.enabled,force.enabled,priority.enabled);
                    boolean modeCurrent=!destroyed&&nativeInput.available&&nativeInput.revision==force.revision
                            &&isDesiredEnabled(LockRelayService.this)&&priority.available&&priority.revision==force.revision
                            &&force.available&&force.revision==appliedForceRevision;
                    boolean inputOnly=LockProtocol.priorityInputOnlyAllowed(nativeInput.enabled,priority.enabled,modeCurrent,ready,hostState.ticket,hostState.reason);
                    reply.writeNoException(); reply.writeInt(ready ? 1 : 0); reply.writeString(ready||inputOnly ? hostState.ticket : "");
                    reply.writeString(ready ? readyReason : inputOnly ? LockProtocol.inputOnlyPriorityReason(hostState.reason) : hostState.reason);
                    }
                } else {
                    String epoch = data.readString(), actor = data.readString(); long actorEpoch = data.readLong();
                    if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                    if (!java.util.Objects.equals(serverEpoch, epoch) || !java.util.Objects.equals(moduleActorSession, actor)
                            || actorEpoch != moduleActorEpoch) throw new SecurityException("STALE_REGISTRATION");
                    invalidateHostObservation("COMPAT_NOT_ARMED"); armed = false; admission.clear();
                    synchronized (stateLock) { pending = null; }
                    execute(() -> { closeHostSession(); liveStatus = "PAUSED"; });
                    reply.writeNoException(); reply.writeInt(0);
                }
            } catch (SecurityException ignored) { reply.writeException(new SecurityException("UNAUTHORIZED_OR_STALE")); }
            catch (RuntimeException ignored) { reply.writeException(new IllegalArgumentException("BAD_REQUEST")); }
            return true;
        }
    };

    private final Binder hostCallback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            int uid = Binder.getCallingUid(), pid = Binder.getCallingPid();
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(LockProtocol.CALLBACK); return true; }
            if (code != LockProtocol.CALLBACK_RESULT || reply == null || (flags & IBinder.FLAG_ONEWAY) != 0
                    || data.dataSize() > LockProtocol.MAX_PARCEL_BYTES) return false;
            try {
                data.enforceInterface(LockProtocol.CALLBACK);
                if (!LockCallerAuth.host(LockRelayService.this, uid, pid)) throw new SecurityException("UNAUTHORIZED");
                String session = data.readString(), id = data.readString(), status = data.readString();
                PendingIntent launch = data.readTypedObject(PendingIntent.CREATOR); if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                Pending request;
                synchronized (stateLock) {
                    if (hostSession == null || !hostSession.equals(session) || hostPid != -1 && hostPid != pid)
                        throw new SecurityException("STALE_SESSION");
                    hostPid = pid; request = pending;
                    if (request == null || !request.id.equals(id)) throw new SecurityException("STALE_REQUEST");
                    pending = null; admission.complete();
                }
                if (!LockProtocol.token(status)) { if (launch != null) launch.cancel(); throw new IllegalArgumentException("BAD_STATUS"); }
                if(!LockProtocol.EDIT_READY.equals(status))reportHostOutcome(NativeInputDetail.commandResult(status));
                execute(() -> {
                    if (launch != null && LockProtocol.EDIT_READY.equals(status) && isDesiredEnabled(LockRelayService.this)
                            && SystemClock.elapsedRealtime() <= request.deadline) forwardEdit(request, launch);
                    else if (launch != null) launch.cancel();
                    synchronizeState();
                });
                reply.writeNoException();
            } catch (SecurityException ignored) { reply.writeException(new SecurityException("UNAUTHORIZED_OR_STALE")); }
            catch (RuntimeException ignored) { reply.writeException(new IllegalArgumentException("BAD_REQUEST")); }
            return true;
        }
    };

    private final ServiceConnection hostConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            hostBinder = service;
            try { service.linkToDeath(() -> {
                if (hostBinder != service) return;
                invalidateHostObservation("HOST_UNAVAILABLE"); hostBinder = null; hostSession = null; admission.clear(); execute(LockRelayService.this::disarm);
            }, 0); }
            catch (Exception ignored) { hostBinder = null; }
            execute(LockRelayService.this::synchronizeState);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            invalidateHostObservation("HOST_UNAVAILABLE"); hostBinder = null; hostSession = null; admission.clear(); execute(LockRelayService.this::disarm);
        }
    };

    @Override public void onCreate() {
        super.onCreate(); prefs = preferences(this); prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
        diagnosticService = this;
        Intent host = new Intent().setComponent(new ComponentName(LockProtocol.HOST_PACKAGE,
                "dev.abdus.apps.shimmer.p10.input.LockCommandHostService"));
        try { hostBound = bindService(host, hostConnection, Context.BIND_AUTO_CREATE); }
        catch (RuntimeException ignored) { liveStatus = "HOST_UNAVAILABLE"; }
        main.post(tick);
    }
    static String readyMarker(boolean nativeInput,boolean forceLocal,boolean wallpaperPriority){
        if(nativeInput&&forceLocal&&wallpaperPriority)return "READY_NATIVE_INPUT+READY_FORCE_LOCAL+READY_WALLPAPER_PRIORITY";
        if(nativeInput&&wallpaperPriority)return "READY_NATIVE_INPUT+READY_WALLPAPER_PRIORITY";
        if(nativeInput&&forceLocal)return "READY_NATIVE_INPUT+READY_FORCE_LOCAL";
        if(nativeInput)return "READY_NATIVE_INPUT";
        if(wallpaperPriority)return "READY_WALLPAPER_PRIORITY";
        if(forceLocal)return "READY_FORCE_LOCAL";
        return "READY";
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            execute(LockRelayService.this::synchronizeState); main.postDelayed(this, 1000);
        }
    };

    private void openHost() throws Exception {
        synchronized(nativeHostIOLock){openHostLocked();}
    }
    private void openHostLocked() throws Exception {
        IBinder host = hostBinder;
        if (host == null || !host.isBinderAlive()) return;
        if (!trustedHostInstalled()) throw new SecurityException("HOST_UNTRUSTED");
        long request=beginHostObservation();
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.HOST); data.writeInt(LockProtocol.VERSION); data.writeStrongBinder(hostCallback);
            if (!host.transact(LockProtocol.HELLO, data, reply, 0)) throw new IllegalStateException("HOST_UNAVAILABLE");
            reply.readException(); String session = reply.readString();
            if (!LockProtocol.token(session)) throw new SecurityException("BAD_SESSION");
            if (destroyed || hostBinder != host) throw new SecurityException("HOST_CHANGED");
            HostObservation observation=readHostState(reply);if(reply.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
            synchronized(hostObservationLock){if(!hostObservations.current(request)||hostBinder!=host)throw new SecurityException("HOST_CHANGED");hostSession = session;hostPid = -1;commandSequence = 0;publishHostObservation(request,host,session,observation);}
        } finally { reply.recycle(); data.recycle(); }
    }
    private HostObservation readHostState(Parcel reply) {
        int ready=reply.readInt();String ticket=reply.readString(),reason=reply.readString();
        if((ready!=0&&ready!=1)||ticket==null||ticket.length()>128||!LockProtocol.token(reason)||ready==1&&!LockProtocol.token(ticket))throw new IllegalArgumentException("BAD_HOST_STATE");
        return new HostObservation(ready==1,ticket,reason);
    }
    private long beginHostObservation(){return beginHostObservation(false);}
    private long beginHostObservation(boolean foreground){synchronized(hostObservationLock){long request=hostObservations.beginRequest(foreground);if(request>0)hostObservation=new HostObservation(false,"","STATE_PENDING");return request;}}
    private void invalidateHostObservation(String reason){synchronized(hostObservationLock){hostObservations.invalidate();hostObservation=new HostObservation(false,"",reason);hostReady=false;hostTicket="";hostReason=reason;}}
    private boolean publishHostObservation(long request,IBinder host,String session,HostObservation observation){synchronized(hostObservationLock){
        if(hostBinder!=host||!java.util.Objects.equals(hostSession,session)||!hostObservations.publish(request,observation))return false;
        hostObservation=observation;hostReady=observation.ready;hostTicket=observation.ticket;hostReason=observation.reason;return true;
    }}
    private void refreshHost() throws Exception {
        synchronized(nativeHostIOLock){refreshHostLocked();}
    }
    private void refreshHostLocked() throws Exception {
        IBinder host = hostBinder;
        String session=hostSession;
        if (host == null || session == null) { invalidateHostObservation("HOST_UNAVAILABLE"); return; }
        if (!trustedHostInstalled()) throw new SecurityException("HOST_UNTRUSTED");
        long request=beginHostObservation();
        if(request<0)return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.HOST); data.writeString(session);
            if (!host.transact(LockProtocol.STATE, data, reply, 0)) throw new IllegalStateException("HOST_UNAVAILABLE");
            reply.readException();HostObservation observation=readHostState(reply);if(reply.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");publishHostObservation(request,host,session,observation);
        } finally { reply.recycle(); data.recycle(); }
    }
    /** 原生同步仲裁再读当前Host状态；不创建会话、不持stateLock跨IPC，不用旧ready冒当前目标。 */
    private void refreshNativeHostState(){
        synchronized(nativeHostIOLock){refreshNativeHostStateLocked();}
    }
    private void refreshNativeHostStateLocked(){
        IBinder host=hostBinder;String session=hostSession;
        if(host==null||session==null||!isDesiredEnabled(this)){invalidateHostObservation("HOST_UNAVAILABLE");return;}
        long request=beginHostObservation(true);
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            if(!trustedHostInstalled())throw new SecurityException();
            data.writeInterfaceToken(LockProtocol.HOST);data.writeString(session);
            if(!host.transact(LockProtocol.STATE,data,reply,0)||reply.dataSize()>LockProtocol.MAX_PARCEL_BYTES)throw new IllegalStateException();
            reply.readException();HostObservation observation=readHostState(reply);
            if(reply.dataAvail()!=0||hostBinder!=host||!session.equals(hostSession)||destroyed||!isDesiredEnabled(this))throw new SecurityException();
            publishHostObservation(request,host,session,observation);
        }catch(Throwable ignored){synchronized(hostObservationLock){if(hostObservations.current(request))invalidateHostObservation("HOST_UNAVAILABLE");}}
        finally{hostObservations.finishRequest(request);data.recycle();reply.recycle();}
    }

    /** 只取系统已有AMS Binder；失败不向广播、外部文件或新ServiceManager名降级。 */
    private IBinder activityManagerBinder() throws Exception {
        try {
            Class<?> bypass = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass");
            bypass.getMethod("addHiddenApiExemptions", String[].class).invoke(null,
                    (Object) new String[]{"Landroid/app/ActivityManager;"});
        } catch (ClassNotFoundException ignored) { /* 框架已有豁免时仍可尝试；否则明确失败。 */ }
        Class<?> manager = Class.forName("android.app.ActivityManager");
        Method getter = manager.getDeclaredMethod("getService"); getter.setAccessible(true);
        Object service = getter.invoke(null);
        if (!(service instanceof IInterface)) throw new IllegalStateException("AMS_UNAVAILABLE");
        return ((IInterface) service).asBinder();
    }
    private synchronized void connectServer() throws Exception {
        // 诊断只复用或建立MODULE通道，不调用arm/宿主/播放；并发正常worker不能互换当前session。
        if (serverSession != null && serverSession.isBinderAlive()) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.BOOTSTRAP); data.writeInt(LockProtocol.VERSION);
            data.writeInt(LockProtocol.ROLE_MODULE); data.writeStrongBinder(relayBinder); data.writeStrongBinder(relayBinder);
            if (!activityManagerBinder().transact(LockProtocol.BOOTSTRAP_TRANSACTION, data, reply, 0))
                throw new IllegalStateException("BOOTSTRAP_UNAVAILABLE");
            reply.readException(); int status = reply.readInt(), pid = reply.readInt(); String epoch = reply.readString();
            IBinder session = reply.readStrongBinder(); long actorEpoch = reply.readLong(); String actorSession = reply.readString();
            if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
            if (status != 0 || pid <= 0 || !LockProtocol.token(epoch) || session == null || !session.isBinderAlive()
                    || actorEpoch <= 0 || !LockProtocol.token(actorSession)) throw new SecurityException("BOOTSTRAP_REJECTED");
            serverPid = pid; serverEpoch = epoch; serverSession = session; moduleActorEpoch = actorEpoch;
            moduleActorSession = actorSession; armed = false;
            producerSummary = "当前连接尚未读取输入状态";
            session.linkToDeath(() -> {
                if (serverSession != session) return;
                serverSession = null; serverPid = -1; serverEpoch = null; moduleActorEpoch = -1; moduleActorSession = null;
                armed = false; invalidateHostObservation("SERVER_UNAVAILABLE"); admission.clear();
                producerSummary = "输入连接已失效";
                execute(this::closeHostSession);
            }, 0);
        } finally { reply.recycle(); data.recycle(); }
    }
    private void arm(boolean enabled) throws Exception {
        IBinder server = serverSession;
        if (server == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.SESSION); data.writeInt(enabled ? 1 : 0);
            if (!server.transact(LockProtocol.SESSION_ARM, data, reply, 0)) throw new IllegalStateException("ARM_REJECTED");
            reply.readException(); if (reply.readInt() != 0) throw new SecurityException("ARM_REJECTED");
            if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA"); armed = enabled;
        } finally { reply.recycle(); data.recycle(); }
    }
    private void disarm() { try { if (armed) arm(false); } catch (Exception ignored) { armed = false; } }

    /** 日常粗粒度状态只读；状态不可用不撤销已合法建立的认证hold。 */
    private void refreshProducerSummary() {
        IBinder server = serverSession;
        if (server == null) { producerSummary = "输入状态暂不可用"; return; }
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.SESSION);
            if (!server.transact(6, data, reply, 0)) throw new IllegalStateException("STATUS_UNAVAILABLE");
            reply.readException(); int present = reply.readInt(), reason = reply.readInt();
            if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
            if (serverSession == server) producerSummary = ProducerStatusSummary.describe(present, reason);
        } catch (Exception ignored) {
            if (serverSession == server) producerSummary = "输入状态暂不可用";
        } finally { reply.recycle(); data.recycle(); }
    }

    private void synchronizeState() {
        if (destroyed) return;
        try {
            if (!isDesiredEnabled(this)) { disarm(); closeHostSession(); liveStatus = "OFF"; return; }
            ForceLocalSetting.State force=forceLocalState(this);
            WallpaperPrioritySetting.State priority=wallpaperPriorityState(this);
            NativeInputSetting.State nativeInput=nativeInputState(this);
            if(!nativeInput.available||nativeInput.revision!=force.revision){invalidateHostObservation("NATIVE_SETTING_UNCONFIRMED");disarm();closeHostSession();liveStatus="NATIVE_SETTING_UNCONFIRMED";return;}
            if(!priority.available||priority.revision!=force.revision||!force.available){invalidateHostObservation("FORCE_SETTING_UNCONFIRMED");disarm();closeHostSession();liveStatus="FORCE_SETTING_UNCONFIRMED";return;}
            if(appliedForceRevision!=force.revision){disarm();closeHostSession();appliedForceRevision=force.revision;}
            if (hostBinder == null) { invalidateHostObservation("HOST_UNAVAILABLE"); liveStatus = "HOST_UNAVAILABLE"; disarm(); return; }
            if (hostSession == null) openHost(); else refreshHost();
            if (serverSession == null) connectServer();
            // 短暂BUSY_EDIT／正常认证会让ready=false，但不能撤掉已合法建立的编辑hold。
            // 永久开关控制会话；逐事件准入继续由hostReady／原图ticket控制。
            boolean shouldArm = isDesiredEnabled(this) && hostSession != null;
            if (armed != shouldArm) arm(shouldArm);
            refreshProducerSummary();
            pollEditDiagnostics();
            HostObservation observed=hostObservation;
            liveStatus = !isDesiredEnabled(this) ? "OFF" : ProducerStatusSummary.compose(observed.ready, observed.reason, armed, producerSummary);
            Pending request;
            synchronized (stateLock) {
                request = pending;
                if (request != null && SystemClock.elapsedRealtime() > request.deadline) { pending = null; admission.complete(); }
                else request = null;
            }
            if (request != null) closeHostSession();
        } catch (Exception ignored) {
            invalidateHostObservation("HOST_UNAVAILABLE"); liveStatus = serverSession == null ? "BOOTSTRAP_UNAVAILABLE" : "HOST_UNAVAILABLE";
            disarm();
            serverSession = null; serverPid = -1; serverEpoch = null; moduleActorEpoch = -1; moduleActorSession = null;
            producerSummary = "输入连接已失效";
            closeHostSession();
        }
    }
    private void command(Pending request, int action, String ticket, long issued) {
        if (destroyed || !isDesiredEnabled(this) || hostBinder == null || hostSession == null) { completeRejected(request); return; }
        ForceLocalSetting.State force=forceLocalState(this);
        WallpaperPrioritySetting.State priority=wallpaperPriorityState(this);
        NativeInputSetting.State nativeInput=nativeInputState(this);
        if(!nativeInput.available||nativeInput.revision!=request.forceRevision){completeRejected(request);return;}
        if(!priority.available||priority.revision!=request.forceRevision){completeRejected(request);return;}
        if(!force.available||force.revision!=request.forceRevision||appliedForceRevision!=request.forceRevision){completeRejected(request);return;}
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            if (!trustedHostInstalled()) throw new SecurityException("HOST_UNTRUSTED");
            if (++commandSequence == Long.MAX_VALUE) throw new IllegalStateException("SEQUENCE_EXHAUSTED");
            data.writeInterfaceToken(LockProtocol.HOST); data.writeString(hostSession); data.writeString(ticket);
            data.writeString(request.id); data.writeLong(commandSequence); data.writeLong(issued); data.writeInt(action);
            if (!hostBinder.transact(LockProtocol.COMMAND, data, reply, 0)) throw new IllegalStateException("HOST_UNAVAILABLE");
            reply.readException(); String status = reply.readString(); if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
            if (!"ACCEPTED".equals(status)) { reportHostOutcome(NativeInputDetail.commandResult(status)); completeRejected(request); }
        } catch (Exception ignored) { completeRejected(request); }
        finally { reply.recycle(); data.recycle(); }
    }
    /** 只在主动短诊断中拉取当前owner的固定编辑结果，不把旧认证历史冒本轮。 */
    private void pollEditDiagnostics(){synchronized(editDiagnosticLock){pollEditDiagnosticsLocked();}}
    private void pollEditDiagnosticsLocked(){
        IBinder server=serverSession,host=hostBinder;String session=hostSession;
        if(server==null||host==null||session==null)return;
        try{
            DiagnosticClient.Reply diagnostic=diagnosticTransaction(server,DiagnosticClient.STATE,null,0,0);
            if(diagnostic.active!=1){editDiagnosticToken="";return;}
            boolean baseline=!session.equals(editDiagnosticSession)||!diagnostic.token.equals(editDiagnosticToken);
            if(baseline){editDiagnosticSession=session;editDiagnosticToken=diagnostic.token;editDiagnosticCursor=0;editDiagnosticUnavailable=false;}
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(LockProtocol.HOST);data.writeString(session);data.writeLong(editDiagnosticCursor);
                if(!host.transact(LockProtocol.EDIT_DIAGNOSTICS,data,reply,0)||reply.dataSize()>LockProtocol.MAX_PARCEL_BYTES)throw new IllegalStateException();
                reply.readException();int schema=reply.readInt();long latest=reply.readLong();int truncated=reply.readInt(),count=reply.readInt();
                if(schema!=1||latest<editDiagnosticCursor||truncated<0||truncated>1||count<0||count>32)throw new IllegalArgumentException();
                long[] sequences=new long[count];int[] codes=new int[count];long prior=editDiagnosticCursor;
                for(int i=0;i<count;i++){sequences[i]=reply.readLong();codes[i]=NativeInputDetail.editCause(reply.readInt());if(sequences[i]<=prior||sequences[i]>latest||codes[i]==0)throw new IllegalArgumentException();prior=sequences[i];}
                if(reply.dataAvail()!=0||hostBinder!=host||!session.equals(hostSession)||serverSession!=server)throw new SecurityException();
                if(baseline){recordEditDiagnostic(server,diagnostic.token,NativeInputDetail.code("NATIVE_EDIT_BASELINE_UNAVAILABLE"),1);editDiagnosticCursor=latest;return;}
                if(truncated==1&&!recordEditDiagnostic(server,diagnostic.token,NativeInputDetail.code("NATIVE_EDIT_RESULTS_TRUNCATED"),1))return;
                for(int i=0;i<count;i++){
                    if(!recordEditDiagnostic(server,diagnostic.token,codes[i],sequences[i]))return;
                    editDiagnosticCursor=sequences[i];
                }
                if(count==0)editDiagnosticCursor=latest;
            }finally{data.recycle();reply.recycle();}
        }catch(Throwable ignored){
            if(!editDiagnosticUnavailable&&!editDiagnosticToken.isEmpty()){editDiagnosticUnavailable=recordEditDiagnostic(server,editDiagnosticToken,NativeInputDetail.code("NATIVE_EDIT_RESULTS_UNAVAILABLE"),1);}
        }
    }
    private boolean recordEditDiagnostic(IBinder expected,String token,int code,long count){
        if(expected==null||serverSession!=expected||!NativeInputDetail.isRelayResult(code))return false;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockProtocol.SESSION);data.writeString(token);data.writeInt(0);data.writeInt(code);data.writeLong(count);
            if(!expected.transact(11,data,reply,0)||reply.dataSize()>LockProtocol.MAX_PARCEL_BYTES)return false;
            reply.readException();return reply.readInt()==0&&reply.dataAvail()==0;
        }catch(Throwable ignored){return false;}finally{data.recycle();reply.recycle();}
    }
    private void reportHostOutcome(int outcome){
        IBinder server=serverSession;if(server==null||!NativeInputDetail.isRelayResult(outcome))return;
        try{DiagnosticClient.Reply capture=diagnosticTransaction(server,DiagnosticClient.STATE,null,0,0);
            if(capture.active==1){
                long count=hostOutcomeTotals.next(capture.token,outcome);
                if(count>0)recordEditDiagnostic(server,capture.token,outcome,count);
            }else hostOutcomeTotals.stopped();
        }catch(Throwable ignored){/* 诊断失败不改变业务结果，不补发旧命令。 */}
    }
    private void completeRejected(Pending request) {
        synchronized (stateLock) { if (pending == request) { pending = null; admission.complete(); } }
    }
    private void forwardEdit(Pending request, PendingIntent launch) {
        IBinder server = serverSession;
        if (server == null || !launch.isActivity() || !launch.isImmutable() || !LockProtocol.HOST_PACKAGE.equals(launch.getCreatorPackage())) {
            launch.cancel(); return;
        }
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.SESSION); data.writeLong(request.actorEpoch); data.writeString(request.actorSession);
            data.writeLong(request.gestureSeq); data.writeTypedObject(launch, 0);
            if (!server.transact(LockProtocol.SESSION_LAUNCH_EDIT, data, reply, 0)) throw new IllegalStateException("EDIT_REJECTED");
            reply.readException(); if (reply.dataAvail() >= 4 && reply.readInt() != 0) launch.cancel();
        } catch (Exception ignored) { launch.cancel(); }
        finally { reply.recycle(); data.recycle(); }
    }
    private void closeHostSession() {
        IBinder host = hostBinder; String session = hostSession;
        hostSession = null; hostPid = -1; invalidateHostObservation("HOST_CLOSED");
        synchronized (stateLock) { pending = null; admission.clear(); }
        if (host == null || session == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(LockProtocol.HOST); data.writeString(session);
            if (host.transact(LockProtocol.CLOSE, data, reply, 0)) reply.readException();
        } catch (Exception ignored) { }
        finally { reply.recycle(); data.recycle(); }
    }
    @Override public IBinder onBind(Intent intent) { return relayBinder; }
    @Override public void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null);
        if (diagnosticService == this) diagnosticService = null;
        diagnosticWorker.shutdownNow();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        invalidateHostObservation("SERVICE_DESTROYED"); admission.clear();
        // 不在Main等待任何远程Binder；即使远端阻塞，新的命令已在本进程拒绝。
        try { worker.execute(() -> { disarm(); closeHostSession(); }); } catch (RuntimeException ignored) { }
        worker.shutdown();
        if (hostBound) try { unbindService(hostConnection); } catch (RuntimeException ignored) { }
        liveStatus = "NOT_CONNECTED"; super.onDestroy();
    }
}
