package personal.wallpaper.os4.e1.lockinput;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.app.PendingIntent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.UUID;
import personal.wallpaper.protocol.lock.LockProtocol;

/**
 * 通过已经存在的activity Binder发现的有界私有会话。
 * 发现码不保密；能力只在逐kernel caller/AMS记录认证后通过同步Parcel返回。
 * 不注册新系统服务、不改SELinux、不用广播/偏好/外部文件分发能力。
 */
public final class LockInputBroker {
    public static final int DISCOVER = LockProtocol.BOOTSTRAP_TRANSACTION;
    public static final String BOOTSTRAP = LockProtocol.BOOTSTRAP;
    public static final String SESSION = LockProtocol.SESSION;
    public static final String RELAY = LockProtocol.RELAY;
    public static final int PROTOCOL = LockProtocol.VERSION, ROLE_MODULE = LockProtocol.ROLE_MODULE, ROLE_PRODUCER = LockProtocol.ROLE_PRODUCER;
    public static final int ROLE_DIAGNOSTIC=3;
    private static final String MODULE = LockProtocol.MODULE_PACKAGE;
    private static final String MODULE_CERT = LockProtocol.MODULE_CERT;
    private static final String SYSTEM_UI = "com.android.systemui";
    private final String serverEpoch = UUID.randomUUID().toString();
    private final Handler worker;
    private Object ams;
    private Context context;
    private Session module, producer, diagnosticProducer;
    private final LockDiagnosticRing diagnostic=new LockDiagnosticRing();
    private int sourceCause=LockDiagnosticCause.code("SOURCE_NOT_REPORTED"),loaderCause=LockDiagnosticCause.code("LOADER_NOT_REPORTED"),hookCause=LockDiagnosticCause.code("HOOK_NOT_REPORTED");
    private int bootstrapCause=LockDiagnosticCause.code("PRODUCER_DIAGNOSTIC_NOT_CONTACTED");
    private RuntimeApkIdentity.Result sourceIdentity;
    private ProcessIdentity sourceProcess;
    private ProcessIdentity pendingSource;
    private boolean armed;
    private int producerReason=LockInputSummary.UNREGISTERED;
    private long generation;
    private XC_MethodHook.Unhook hook;

    private LockInputBroker() {
        HandlerThread thread = new HandlerThread("HWC-lock-broker"); thread.start();
        worker = new Handler(thread.getLooper());
    }

    public static LockInputBroker install(ClassLoader loader) {
        LockInputBroker broker = new LockInputBroker();
        try {
            Class<?> type = Class.forName("com.android.server.am.ActivityManagerService", false, loader);
            Method target = type.getDeclaredMethod("onTransact", int.class, Parcel.class, Parcel.class, int.class);
            target.setAccessible(true);
            broker.hook = XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length != 4 || !Integer.valueOf(DISCOVER).equals(param.args[0])) return;
                    if (!(param.args[1] instanceof Parcel) || !(param.args[2] instanceof Parcel)) return;
                    Parcel data = (Parcel) param.args[1], reply = (Parcel) param.args[2];
                    int position = data.dataPosition();
                    try {
                        if (data.dataSize() > 1024 || data.dataSize() < 16) return;
                        try { data.enforceInterface(BOOTSTRAP); }
                        catch (Throwable differentDescriptor) { data.setDataPosition(position); return; }
                        int callerPid = Binder.getCallingPid(), callerUid = Binder.getCallingUid();
                        if (((Integer) param.args[3] & IBinder.FLAG_ONEWAY) != 0) {
                            broker.reject(reply, "SYNC_REQUIRED"); param.setResult(true); return;
                        }
                        broker.discover(param.thisObject, data, reply, callerPid, callerUid);
                        param.setResult(true);
                    } catch (Throwable ignored) {
                        // 只有已识别本descriptor的请求才走固定拒绝，不把异常抛给AMS。
                        try { broker.reject(reply, "BOOTSTRAP_REJECTED"); param.setResult(true); }
                        catch (Throwable cannotReply) { data.setDataPosition(position); }
                    }
                }
            });
            LockLog.fixed("HWC_LOCK_BROKER_HOOK_AVAILABLE protocol=1");
        } catch (Throwable ignored) { LockLog.fixed("HWC_LOCK_BROKER_UNAVAILABLE"); }
        return broker;
    }

    private synchronized void discover(Object service, Parcel data, Parcel reply, int pid, int uid) throws Exception {
        int version = data.readInt(), role = data.readInt();
        IBinder ownerDeath = data.readStrongBinder();
        IBinder relay = data.readStrongBinder();
        if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
        if (version != PROTOCOL || (role != ROLE_MODULE && role != ROLE_PRODUCER&&role!=ROLE_DIAGNOSTIC)
                || ownerDeath == null || !ownerDeath.isBinderAlive()) { reject(reply, "REGISTRATION_INVALID"); return; }
        ams = service;
        Object ctx = field(service.getClass(), "mContext").get(service);
        if (!(ctx instanceof Context)) { reject(reply, "CONTEXT_UNAVAILABLE"); return; }
        context = (Context) ctx;
        ProcessIdentity identity = processIdentity(pid, uid);
        if (identity == null) { reject(reply, "CALLER_PROCESS_NOT_VERIFIED"); return; }
        if (role == ROLE_MODULE) {
            if (relay == null || relay != ownerDeath || !moduleIdentity(identity)) { reject(reply, "MODULE_UNAUTHORIZED"); return; }
            clearModule("MODULE_REPLACED");
            module = new Session(identity, ownerDeath, relay, ROLE_MODULE, ++generation);
            module.watch();
            accepted(reply, module);
        } else {
            if (relay != null || !installedSystemUi(identity)) { reject(reply, "PRODUCER_UNAUTHORIZED"); return; }
            if(role==ROLE_DIAGNOSTIC){
                bootstrapStatus("PRODUCER_DIAGNOSTIC_CONNECTED");
                if(diagnosticProducer!=null&&diagnosticProducer.valid()&&diagnosticProducer.identity.same(identity)){accepted(reply,diagnosticProducer);return;}
                if(diagnosticProducer!=null)diagnosticProducer.revoke();
                diagnosticProducer=new Session(identity,ownerDeath,null,ROLE_DIAGNOSTIC,++generation);diagnosticProducer.watch();accepted(reply,diagnosticProducer);return;
            }
            if (!sourceReady(identity)) {
                bootstrapStatus("PRODUCER_SOURCE_CHECK_PENDING");
                queueSourceCheck(identity);
                pending(reply, "SOURCE_IDENTITY_PENDING"); return;
            }
            if (module == null || !module.valid() || !armed) { reject(reply, "COMPAT_NOT_ARMED"); return; }
            clearProducer();
            producer = new Session(identity, ownerDeath, null, ROLE_PRODUCER, ++generation);
            producerReason=LockInputSummary.WAITING_FIRST_INPUT;
            bootstrapStatus("PRODUCER_REGISTERED");
            producer.watch();
            accepted(reply, producer);
        }
    }

    private void accepted(Parcel reply, Session session) {
        reply.writeNoException(); reply.writeInt(0); reply.writeInt(Process.myPid()); reply.writeString(serverEpoch);
        reply.writeStrongBinder(session); reply.writeLong(session.epoch); reply.writeString(session.id);
    }
    private void bootstrapStatus(String fixed){
        bootstrapCause=LockDiagnosticCause.code(fixed);
        long now=SystemClock.elapsedRealtime();LockDiagnosticRing.State current=diagnostic.state(now);
        if(current.active)diagnostic.record(current.token,now,LockDiagnosticCause.BOOTSTRAP,bootstrapCause,1);
    }
    private void reject(Parcel reply, String reason) { response(reply, 2, reason); }
    private void pending(Parcel reply, String reason) { response(reply, 1, reason); }
    private void response(Parcel reply, int status, String reason) {
        reply.writeNoException(); reply.writeInt(status); reply.writeInt(Process.myPid()); reply.writeString(serverEpoch);
        reply.writeStrongBinder(null); reply.writeLong(-1); reply.writeString(reason);
    }

    private boolean sourceReady(ProcessIdentity identity) {
        return sourceProcess != null && sourceProcess.same(identity) && sourceIdentity != null
                && sourceIdentity.sourceMatched && sourceIdentity.stillSameFile();
    }
    private void queueSourceCheck(ProcessIdentity identity) {
        if (pendingSource != null && pendingSource.same(identity)) return;
        pendingSource = identity;
        // 单个worker；运行前再次核当前登记，避免为任意UID/PID安排文件工作。
        worker.post(() -> {
            synchronized (LockInputBroker.this) {
                if (pendingSource != identity) return;
            }
            RuntimeApkIdentity.Result result = RuntimeApkIdentity.verifyInstalled(context,identity.info, null);
            synchronized (LockInputBroker.this) {
                if (pendingSource != identity) return;
                ProcessIdentity current = processIdentity(identity.pid, identity.uid);
                if (current != null && identity.same(current) && result.sourceMatched && result.stillSameFile()) {
                    sourceProcess = identity; sourceIdentity = result;
                }else{
                    bootstrapStatus("PRODUCER_SOURCE_CHECK_REJECTED");
                }
                sourceCause=LockDiagnosticCause.code(result.sourceMatched?"SOURCE_INSTALLED_COMPONENT_VERIFIED":result.reason);
                long now=SystemClock.elapsedRealtime();LockDiagnosticRing.State diag=diagnostic.state(now);
                if(diag.active)diagnostic.record(diag.token,now,LockDiagnosticCause.SOURCE,sourceCause,1);
                pendingSource = null;
                LockLog.fixed("HWC_LOCK_SOURCE " + (result.sourceMatched?"SOURCE_INSTALLED_COMPONENT_VERIFIED":result.reason) + " oemSignature=NOT_VERIFIED");
            }
        });
    }

    private boolean moduleIdentity(ProcessIdentity identity) {
        if (context == null || identity.uid < 10000 || !MODULE.equals(identity.name) || !MODULE.equals(identity.info.packageName)) return false;
        long calling = Binder.clearCallingIdentity();
        try {
            PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(identity.uid);
            if (packages == null || packages.length != 1 || !MODULE.equals(packages[0])) return false;
            PackageInfo pkg = pm.getPackageInfo(MODULE, PackageManager.GET_SIGNING_CERTIFICATES);
            if (pkg.applicationInfo == null || !pkg.applicationInfo.enabled || pkg.applicationInfo.uid != identity.uid || pkg.signingInfo == null
                    || pkg.signingInfo.hasMultipleSigners() || pkg.signingInfo.getApkContentsSigners().length != 1) return false;
            String digest = hex(MessageDigest.getInstance("SHA-256").digest(pkg.signingInfo.getApkContentsSigners()[0].toByteArray()));
            return MODULE_CERT.equals(digest);
        } catch (Throwable ignored) { return false; }
        finally { Binder.restoreCallingIdentity(calling); }
    }

    private boolean installedSystemUi(ProcessIdentity identity){
        if(identity==null||!identity.isSystemUi()||identity.info.uid!=identity.uid)return false;
        long calling=Binder.clearCallingIdentity();
        try{return RuntimeApkIdentity.matchesInstalledComponent(context,identity.info);}
        finally{Binder.restoreCallingIdentity(calling);}
    }
    private ProcessIdentity processIdentity(int pid, int uid) {
        if (ams == null || pid <= 0 || uid <= 0) return null;
        try {
            Object pidMap = field(ams.getClass(), "mPidsSelfLocked").get(ams);
            synchronized (pidMap) {
                Method get = pidMap.getClass().getDeclaredMethod("get", int.class); get.setAccessible(true);
                Object record = get.invoke(pidMap, pid);
                if (record == null) return null;
                int recordPid = ((Number) field(record.getClass(), "mPid").get(record)).intValue();
                int recordUid = ((Number) field(record.getClass(), "uid").get(record)).intValue();
                String name = (String) field(record.getClass(), "processName").get(record);
                ApplicationInfo info = (ApplicationInfo) field(record.getClass(), "info").get(record);
                Method start = record.getClass().getDeclaredMethod("getStartSeq"); start.setAccessible(true);
                long sequence = ((Number) start.invoke(record)).longValue();
                if (pid != recordPid || uid != recordUid || sequence <= 0 || info == null || info.uid != uid) return null;
                return new ProcessIdentity(pid, uid, sequence, name, record, new ApplicationInfo(info));
            }
        } catch (Throwable ignored) { return null; }
    }

    private static final class ProcessIdentity {
        final int pid, uid; final long start; final String name; final Object record; final ApplicationInfo info;
        ProcessIdentity(int pid, int uid, long start, String name, Object record, ApplicationInfo info) {
            this.pid=pid; this.uid=uid; this.start=start; this.name=name; this.record=record; this.info=info;
        }
        boolean same(ProcessIdentity other) {
            return other != null && pid == other.pid && uid == other.uid && start == other.start && record == other.record
                    && java.util.Objects.equals(name, other.name) && java.util.Objects.equals(info.packageName, other.info.packageName)
                    && java.util.Objects.equals(info.sourceDir, other.info.sourceDir)
                    && java.util.Arrays.equals(info.splitSourceDirs, other.info.splitSourceDirs);
        }
        ProducerSessionGuard.Identity guardIdentity(){return new ProducerSessionGuard.Identity(pid,uid,start,name,info.packageName,record);}
        boolean isSystemUi() { return ProducerSessionGuard.isExactSystemUi(guardIdentity(),uid); }
    }

    private final class Session extends Binder implements IBinder.DeathRecipient {
        final ProcessIdentity identity;
        final IBinder owner, relay;
        final int role; final long epoch; final String id=UUID.randomUUID().toString();
        final ProducerSessionGuard producerGuard;
        boolean dead; long highSequence;
        long diagnosticWindow=-1;int diagnosticReports;
        long pendingEditSequence=-1,pendingEditIssued=-1;
        boolean editLaunchConsumed;
        Session(ProcessIdentity identity, IBinder owner, IBinder relay, int role, long epoch) {
            this.identity=identity; this.owner=owner; this.relay=relay; this.role=role; this.epoch=epoch;
            producerGuard=role!=ROLE_MODULE?new ProducerSessionGuard(identity.guardIdentity(),identity.uid):null;
        }
        void watch() throws RemoteException { owner.linkToDeath(this, 0); }
        boolean valid() {
            if (dead || !owner.isBinderAlive()) return false;
            ProcessIdentity current = processIdentity(identity.pid, identity.uid);
            if(role==ROLE_MODULE)return identity.same(current)&&moduleIdentity(current);
            boolean valid=identity.same(current)&&producerGuard.accepts(identity.pid,identity.uid,current.guardIdentity())
                    &&installedSystemUi(current)&&(role==ROLE_DIAGNOSTIC||sourceReady(current));
            if(!valid)revoke();
            return valid;
        }
        void revoke() { dead=true;if(producerGuard!=null)producerGuard.revoke();try { owner.unlinkToDeath(this, 0); } catch(Throwable ignored) { } }
        @Override public void binderDied() {
            synchronized (LockInputBroker.this) {
                if (this == module) clearModule("MODULE_DIED");
                if (this == producer) clearProducer();
                if(this==diagnosticProducer){diagnosticProducer.revoke();diagnosticProducer=null;if(producer==null)bootstrapStatus("PRODUCER_DIAGNOSTIC_NOT_CONTACTED");}
            }
        }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            final int pid = Binder.getCallingPid(), uid = Binder.getCallingUid();
            if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(SESSION); return true; }
            if (code < 1 || code > 12) return false;
            if (reply == null || (flags & FLAG_ONEWAY) != 0) return false;
            try {
                if(data.dataSize()>LockProtocol.MAX_PARCEL_BYTES)throw new SecurityException();
                if(!LockDiagnosticRolePolicy.allows(role,code))throw new SecurityException();
                if(code>=7&&code<=10){
                    data.enforceInterface(SESSION);String token=null;long rev=-1;int offset=-1;
                    if(code==8)token=data.readString();
                    if(code==10){token=data.readString();rev=data.readLong();offset=data.readInt();}
                    if(data.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
                    LockDiagnosticRing.State result;java.util.List<String> rows;Session notify;
                    synchronized(LockInputBroker.this){
                        authorize(pid,uid,data);if(role!=ROLE_MODULE)throw new SecurityException();
                        long now=SystemClock.elapsedRealtime();LockDiagnosticRing.State current;
                        if(code==7){
                            current=diagnostic.start(now);
                            diagnostic.record(current.token,now,LockDiagnosticCause.BOOTSTRAP,LockDiagnosticCause.code("DIAG_STARTED"),1);
                            diagnostic.record(current.token,now,LockDiagnosticCause.SOURCE,sourceCause,1);
                            diagnostic.record(current.token,now,LockDiagnosticCause.LOADER,loaderCause,1);
                            diagnostic.record(current.token,now,LockDiagnosticCause.HOOK,hookCause,1);
                            diagnostic.record(current.token,now,LockDiagnosticCause.BOOTSTRAP,bootstrapCause,1);
                        }else if(code==8)diagnostic.stop(token,now);
                        rows=code==10?diagnostic.page(token,rev,offset,now):java.util.List.of();
                        result=diagnostic.state(now);notify=diagnosticProducer;
                    }
                    if(code==7||code==8)notifyDiagnostic(notify,result);
                    if(code==7){String watched=result.token;worker.postDelayed(()->{
                        LockDiagnosticRing.State ended=diagnostic.state(SystemClock.elapsedRealtime());
                        if(watched.equals(ended.token))notifyDiagnostic(diagnosticProducer,ended);
                    },LockDiagnosticRing.MAX_DURATION_MS);}
                    if(code==10)writeDiagnosticPage(reply,result,rows);else writeDiagnosticHeader(reply,result);
                    return true;
                }
                if(code==11){
                    data.enforceInterface(SESSION);String token=data.readString();int stage=data.readInt(),cause=data.readInt();long count=data.readLong();
                    if(data.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
                    synchronized(LockInputBroker.this){
                        authorize(pid,uid,data);if(role!=ROLE_PRODUCER&&role!=ROLE_DIAGNOSTIC
                                &&!(role==ROLE_MODULE&&stage==LockDiagnosticCause.INPUT&&NativeInputDetail.isRelayResult(cause)))throw new SecurityException();
                        if(NativeInputDetail.isRelayResult(cause)&&role!=ROLE_MODULE)throw new SecurityException();
                        long now=SystemClock.elapsedRealtime();
                        LockDiagnosticRing.State diag=diagnostic.state(now);
                        if(!LockDiagnosticCause.validPair(stage,cause)||count<0||!diag.token.equals(token)||stage==LockDiagnosticCause.INPUT&&!diag.active)throw new SecurityException();
                        if(diagnosticWindow<0||now-diagnosticWindow>=1000){diagnosticWindow=now;diagnosticReports=0;}
                        if(++diagnosticReports>32)throw new SecurityException();
                        if(stage==LockDiagnosticCause.SOURCE)sourceCause=cause;
                        if(stage==LockDiagnosticCause.LOADER)loaderCause=cause;
                        if(stage==LockDiagnosticCause.HOOK)hookCause=cause;
                        boolean recorded=diagnostic.record(token,now,stage,cause,count);
                        reply.writeNoException();reply.writeInt(recorded?0:2);return true;
                    }
                }
                if(code==12){
                    data.enforceInterface(SESSION);if(data.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
                    synchronized(LockInputBroker.this){authorize(pid,uid,data);if(role!=ROLE_PRODUCER&&role!=ROLE_DIAGNOSTIC)throw new SecurityException();
                        LockDiagnosticRing.State current=diagnostic.state(SystemClock.elapsedRealtime());reply.writeNoException();reply.writeInt(current.active?1:0);reply.writeString(current.token);return true;}
                }
                if(code==5){
                    data.enforceInterface(SESSION);int reason=data.readInt();if(data.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
                    synchronized(LockInputBroker.this){
                        authorize(pid,uid,data);if(role!=ROLE_PRODUCER||!LockInputSummary.valid(reason))throw new SecurityException();
                        producerReason=reason;
                    }
                    reply.writeNoException();reply.writeInt(0);return true;
                }
                if(code==6){
                    data.enforceInterface(SESSION);if(data.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
                    int registered,reason;
                    synchronized(LockInputBroker.this){
                        authorize(pid,uid,data);if(role!=ROLE_MODULE)throw new SecurityException();
                        registered=producer!=null&&producer.valid()?1:0;
                        reason=registered==1?producerReason:LockInputSummary.UNREGISTERED;
                    }
                    reply.writeNoException();reply.writeInt(registered);reply.writeInt(reason);return true;
                }
                IBinder relayForCall;
                Session moduleForCall;
                if (code == 1) {
                    data.enforceInterface(SESSION);
                    int enable=data.readInt();if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                    Session revokedProducer;
                    synchronized(LockInputBroker.this){
                        authorize(pid,uid,data);
                        if(role!=ROLE_MODULE||(enable!=0&&enable!=1))throw new SecurityException();
                        armed=enable==1;relayForCall=relay;moduleForCall=this;
                        revokedProducer=!armed?producer:null;
                        if(!armed)clearProducer();
                    }
                    // 绝不持broker monitor做跨进程IPC，允许EDIT_READY正常回流。
                    if(enable==0){notifyRevoked(revokedProducer);stopRelayNow(moduleForCall);}
                    reply.writeNoException();reply.writeInt(0);return true;
                }
                if (code == 2) {
                    data.enforceInterface(SESSION);if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                    synchronized(LockInputBroker.this){authorize(pid,uid,data);moduleForCall=module;relayForCall=armed&&module!=null&&module.valid()?module.relay:null;}
                    relayStatus(reply,relayForCall,moduleForCall);return true;
                }
                if(code==4){
                    data.enforceInterface(SESSION);
                    long actorEpoch=data.readLong();String actorSession=data.readString();long sequence=data.readLong();
                    PendingIntent intent=data.readTypedObject(PendingIntent.CREATOR);if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                    Session recipient;
                    synchronized(LockInputBroker.this){
                        authorize(pid,uid,data);long now=SystemClock.elapsedRealtime();
                        if(role!=ROLE_MODULE||!armed||producer==null||!producer.valid()||producer.epoch!=actorEpoch
                                ||!producer.id.equals(actorSession)||producer.pendingEditSequence!=sequence||producer.editLaunchConsumed
                                ||producer.pendingEditIssued<0||now<producer.pendingEditIssued||now-producer.pendingEditIssued>5000
                                ||intent==null||!intent.isImmutable()||!intent.isActivity()||!LockProtocol.HOST_PACKAGE.equals(intent.getCreatorPackage()))throw new SecurityException();
                        recipient=producer;recipient.editLaunchConsumed=true;
                    }
                    Parcel request=Parcel.obtain(),result=Parcel.obtain();
                    try{
                        request.writeInterfaceToken(LockProtocol.PRODUCER);request.writeString(serverEpoch);
                        request.writeString(actorSession);request.writeLong(actorEpoch);request.writeLong(sequence);request.writeTypedObject(intent,0);
                        if(!recipient.owner.transact(1,request,result,0))throw new RemoteException();result.readException();
                        int status=result.readInt();if (result.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");reply.writeNoException();reply.writeInt(status);return true;
                    }finally{request.recycle();result.recycle();}
                }
                data.enforceInterface(SESSION);
                int action=data.readInt();long sequence=data.readLong(),issued=data.readLong(),actorEpoch=data.readLong();
                String actorSession=data.readString(),ticket=data.readString();if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                synchronized (LockInputBroker.this) {
                    authorize(pid,uid,data);
                    if (data.dataSize() > 1024) throw new SecurityException();
                    long now=SystemClock.elapsedRealtime();
                    long ttl=action==3 ? 5000 : 1000;
                    if (role != ROLE_PRODUCER || !armed || action<1 || action>3 || sequence<=highSequence || sequence<=0
                            || sequence==Long.MAX_VALUE || issued<0 || issued>now || now-issued>ttl
                            || actorEpoch!=epoch || !id.equals(actorSession) || !LockProtocol.token(ticket)
                            || module==null || !module.valid()) throw new SecurityException();
                    highSequence=sequence;
                    if(action==LockProtocol.EDIT){pendingEditSequence=sequence;pendingEditIssued=issued;editLaunchConsumed=false;}
                    relayForCall=module.relay;moduleForCall=module;
                }
                // 当前图票据由relay/host再次原子核对，不刷新成另一图的最新票据。
                synchronized(LockInputBroker.this){if(!armed||producer!=this||module!=moduleForCall||!valid())throw new SecurityException();}
                int status=sendEvent(relayForCall,action,sequence,issued,epoch,id,ticket);
                reply.writeNoException();reply.writeInt(status);return true;
            } catch (Throwable ignored) {
                if(code>=7){reply.writeException(new SecurityException("DIAGNOSTIC_REJECTED"));return true;}
                if(code==6){reply.writeException(new SecurityException("UNAUTHORIZED_OR_NOT_READY"));return true;}
                reply.writeNoException(); reply.writeInt(code==2?0:2);
                if (code==2) { reply.writeString(""); reply.writeString("UNAUTHORIZED_OR_NOT_READY"); }
                return true;
            }
        }
        private void authorize(int pid,int uid,Parcel data){
            if(data.dataSize()>1024||pid!=identity.pid||uid!=identity.uid||!valid()
                    ||(role==ROLE_MODULE?this!=module:role==ROLE_PRODUCER?this!=producer:this!=diagnosticProducer))throw new SecurityException();
        }
    }

    private void relayStatus(Parcel out,IBinder relay,Session expectedModule) throws RemoteException {
        if (relay==null) {
            out.writeNoException(); out.writeInt(0); out.writeString(""); out.writeString("COMPAT_NOT_ARMED"); return;
        }
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(RELAY);
            if(!relay.transact(2,data,reply,0)) throw new RemoteException();
            reply.readException(); int ready=reply.readInt(); String ticket=reply.readString(),reason=reply.readString(); if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
            if ((ready!=0&&ready!=1) || ticket==null || ticket.length()>128 || reason==null || reason.length()>64) throw new RemoteException();
            synchronized(LockInputBroker.this){if(!armed||module!=expectedModule){ready=0;ticket="";reason="STALE_MODULE_SESSION";}}
            out.writeNoException(); out.writeInt(ready); out.writeString(ticket); out.writeString(reason);
        } finally { data.recycle(); reply.recycle(); }
    }
    private int sendEvent(IBinder relay,int action,long seq,long issued,long epoch,String id,String ticket) throws RemoteException {
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(RELAY); data.writeInt(action);data.writeLong(seq);data.writeLong(issued);data.writeLong(epoch);data.writeString(id);data.writeString(ticket);
            if(!relay.transact(1,data,reply,0))return 2;
            reply.readException();int status=reply.readInt();if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");return status>=0&&status<=3?status:2;
        } finally { data.recycle();reply.recycle(); }
    }
    private void stopRelay(Session previous) {
        worker.post(() -> {
            stopRelayNow(previous);
        });
    }
    private void stopRelayNow(Session previous){
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(RELAY);data.writeString(serverEpoch);data.writeString(previous.id);data.writeLong(previous.epoch);
            previous.relay.transact(LockProtocol.RELAY_STOP,data,reply,0);
        }catch(Throwable ignored){ }
        finally{data.recycle();reply.recycle();}
    }
    private void clearProducer() { if(producer!=null)producer.revoke();producer=null;producerReason=LockInputSummary.UNREGISTERED; }
    private void notifyRevoked(Session previous){
        if(previous==null)return;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockProtocol.PRODUCER);data.writeString(serverEpoch);
            data.writeString(previous.id);data.writeLong(previous.epoch);
            previous.owner.transact(LockProtocol.PRODUCER_REVOKE,data,reply,0);
        }catch(Throwable ignored){ }
        finally{data.recycle();reply.recycle();}
    }
    private void clearModule(String reason) {
        diagnostic.revoke();
        armed=false;Session oldProducer=producer;clearProducer();worker.post(()->notifyRevoked(oldProducer));
        if(module!=null){Session previous=module;module.revoke();module=null;stopRelay(previous);}
    }
    private static void writeDiagnosticHeader(Parcel reply,LockDiagnosticRing.State state){
        reply.writeNoException();reply.writeString(state.token);reply.writeLong(state.revision);reply.writeInt(state.active?1:0);reply.writeInt(state.reason);reply.writeInt(state.rows);reply.writeInt(state.bytes);
    }
    private static void writeDiagnosticPage(Parcel reply,LockDiagnosticRing.State state,java.util.List<String> rows){
        writeDiagnosticHeader(reply,state);
        // ASCII行是128bytes，但Parcel.writeString为UTF16；3行加UUID固定header最坏仍<1024。
        int count=Math.min(3,rows.size());reply.writeInt(count);for(int i=0;i<count;i++)reply.writeString(rows.get(i));
    }
    private void notifyDiagnostic(Session target,LockDiagnosticRing.State state){
        if(target==null)return;
        synchronized(LockInputBroker.this){if(target!=diagnosticProducer||!target.valid())return;}
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockProtocol.PRODUCER);data.writeString(serverEpoch);data.writeString(target.id);data.writeLong(target.epoch);
            data.writeInt(state.active?1:0);data.writeString(state.token);
            target.owner.transact(3,data,reply,0);
        }catch(Throwable ignored){ }
        finally{data.recycle();reply.recycle();}
    }
    static Field field(Class<?> type,String name) throws NoSuchFieldException {
        for(Class<?> current=type;current!=null;current=current.getSuperclass()){
            try{Field field=current.getDeclaredField(name);field.setAccessible(true);return field;}
            catch(NoSuchFieldException ignored){ }
        }
        throw new NoSuchFieldException(name);
    }
    private static String hex(byte[] input) {
        StringBuilder output=new StringBuilder();for(byte value:input)output.append(String.format(java.util.Locale.ROOT,"%02X",value&255));return output.toString();
    }
}
