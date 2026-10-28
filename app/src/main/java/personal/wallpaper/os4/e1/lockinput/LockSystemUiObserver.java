package personal.wallpaper.os4.e1.lockinput;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import personal.wallpaper.protocol.lock.LockProtocol;

/** 精确SystemUI主进程自然根输入；从不改MotionEvent、原返回值或系统策略。 */
public final class LockSystemUiObserver {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler worker;
    private Class<?> rootClass;
    private ClassLoader sourceLoader;
    private final ApplicationInfo appInfo;
    private RuntimeApkIdentity.Result identity;
    private XC_MethodHook.Unhook hook;
    private volatile Channel channel;
    private Channel diagnosticChannel;
    private volatile DiagnosticState diagnosticState;
    private int sourceDiagnostic=LockDiagnosticCause.code("SOURCE_NOT_REPORTED"),loaderDiagnostic=LockDiagnosticCause.code("LOADER_NOT_REPORTED"),hookDiagnostic=LockDiagnosticCause.code("HOOK_NOT_REPORTED");
    private int bootstrapDiagnostic=LockDiagnosticCause.code("PRODUCER_INITIALIZATION_QUEUED");
    private boolean diagnosticAttempted;
    private int lastDiagnosticCause=-1;
    private long diagnosticCount;
    private boolean diagnosticReportQueued;
    private final RoiDiagnosticBatch roiBatch=new RoiDiagnosticBatch();
    private RoiReport roiReport;
    private String diagnosticPhaseToken="";
    private final int[] diagnosticPhaseCodes={-1,-1,-1,-1};
    private volatile HostState state;
    private final MonotonicObservation<HostState> stateObservations=new MonotonicObservation<>();
    private final Object statePublicationLock=new Object();
    private final ThreadLocal<Boolean> nativeStateRetryable=ThreadLocal.withInitial(()->false);
    private volatile Context context;
    private View attachedRoot;
    private LockGestureRecognizer gestures;
    private String gestureTicket;
    private long gestureEpoch;
    private int layoutKey;
    private LockSurfaceSnapshot previousLayout;
    private long sequence;
    private volatile long pendingEditSequence=-1,pendingEditIssued=-1;
    private volatile Channel pendingEditChannel;
    private volatile View pendingEditRoot;
    private final Map<String,Long> counters = new LinkedHashMap<>();
    private long lastSummary;
    private boolean polling;
    private boolean pollQueued;
    private volatile int summaryReason=LockInputSummary.WAITING_FIRST_INPUT;
    private long lastSummaryReport=-1;
    private NativeSystemUiHooks nativeHooks;
    private volatile boolean capabilityUnsupported;
    private final NativeGestureOwnership nativeOwner=new NativeGestureOwnership();
    private final NativeDiagnosticCounters nativeDiagnostics=new NativeDiagnosticCounters();
    private final java.util.LinkedHashSet<String> nativeHookReports=new java.util.LinkedHashSet<>();
    private NativeBusinessSnapshot nativeLayout;
    private NativeInvocation nativeInvocation;
    private float[] nativeLastXs,nativeLastYs;
    private long nativeSequenceGeneration=-1,nativeAuthorityGeneration;
    private boolean nativeObservedEntry;
    private final PriorityGestureQueue priorityQueue=new PriorityGestureQueue();
    private boolean priorityDrainScheduled,priorityDrainInFlight;
    private final ThreadLocal<String> nativeNotReadyReason=new ThreadLocal<>();
    private volatile boolean nativeSelected=true;
    private String nativeDiagnosticToken="";
    private static final class NativeInvocation {
        final NativeGestureOwnership.Frame frame;final NativeGestureOwnership.Binding binding;
        final NativeBusinessSnapshot snapshot;final int barState;final boolean expanding,qs,dozing;
        final int[] ids;final float[] xs,ys;final NativeInvocation previous;
        NativeInvocation(NativeGestureOwnership.Frame frame,NativeGestureOwnership.Binding binding,NativeBusinessSnapshot snapshot,int state,boolean expanding,boolean qs,boolean dozing,int[] ids,float[] xs,float[] ys,NativeInvocation previous){
            this.frame=frame;this.binding=binding;this.snapshot=snapshot;this.barState=state;this.expanding=expanding;this.qs=qs;this.dozing=dozing;this.ids=ids;this.xs=xs;this.ys=ys;this.previous=previous;
        }
    }
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        @Override public void onViewAttachedToWindow(View view){ }
        @Override public void onViewDetachedFromWindow(View view){
            if(attachedRoot!=view)return;
            revokeNative();resetGesture();clearEdit();view.removeOnAttachStateChangeListener(this);attachedRoot=null;previousLayout=null;
            Channel live=channel;if(live!=null)invalidate(live);
        }
    };

    private LockSystemUiObserver(Class<?> rootClass,ApplicationInfo info){
        this.rootClass=rootClass;this.appInfo=new ApplicationInfo(info);
        sourceLoader=rootClass==null?null:rootClass.getClassLoader();
        HandlerThread thread=new HandlerThread("HWC-lock-producer");thread.start();worker=new Handler(thread.getLooper());
    }
    public static void install(ClassLoader loader,ApplicationInfo info){
        try{
            if(info==null||!RuntimeApkIdentity.SYSTEM_UI.equals(info.packageName)||info.uid!=Process.myUid())return;
            LockSystemUiObserver observer=new LockSystemUiObserver(null,info);
            observer.sourceLoader=loader;
            observer.startInitialization(observer::verifyAndHook);
        }catch(Throwable ignored){LockLog.fixed("HWC_LOCK_PRODUCER_UNAVAILABLE");}
    }
    /** 先建立仅诊断的鉴别联系；检查异常不能杀死worker并永久吞掉失败原因。 */
    void startInitialization(Runnable initialize){
        capabilityUnsupported=true;
        worker.post(this::pollDiagnostic);
        worker.post(()->initializeSafely(initialize));
    }
    void initializeSafely(Runnable initialize){
        bootstrapDiagnostic=LockDiagnosticCause.code("PRODUCER_INITIALIZATION_STARTED");
        try{
            initialize.run();
            if(bootstrapDiagnostic==LockDiagnosticCause.code("PRODUCER_INITIALIZATION_STARTED"))
                bootstrapDiagnostic=LockDiagnosticCause.code("PRODUCER_INITIALIZATION_FINISHED");
        }catch(Throwable ignored){
            capabilityUnsupported=true;identity=null;
            bootstrapDiagnostic=LockDiagnosticCause.code("PRODUCER_INITIALIZATION_FAILED");
            hookDiagnostic=LockDiagnosticCause.code("HOOK_UNAVAILABLE");
            Channel old=channel;if(old!=null)invalidate(old);
            nativeOwner.revoke();main.post(()->{revokeNative();resetGesture();clearEdit();});
            LockLog.fixed("HWC_LOCK_PRODUCER_INITIALIZATION_FAILED");
        }finally{worker.post(this::pollDiagnosticOnce);}
    }
    private void verifyAndHook(){
        try{rootClass=Class.forName("com.android.systemui.shade.NotificationShadeWindowView",false,sourceLoader);}catch(Throwable unavailable){rootClass=null;}
        sourceDiagnostic=LockDiagnosticCause.code("SOURCE_VERIFY_STARTED");
        Class<?> declaring=rootClass;
        try{declaring=Class.forName("com.android.keyguard.injector.KeyguardPanelViewInjector",false,sourceLoader);}catch(Throwable ignored){ }
        identity=RuntimeApkIdentity.verify(appInfo,declaring);
        sourceDiagnostic=LockDiagnosticCause.code(identity.sourceMatched?"SOURCE_INSTALLED_COMPONENT_VERIFIED":identity.reason);
        loaderDiagnostic=LockDiagnosticCause.code(identity.loaderMatched?"DECLARING_LOADER_SOURCE_MATCHED":"DECLARING_LOADER_NOT_MATCHED");
        if(rootClass==null)hookDiagnostic=LockDiagnosticCause.code("ROOT_CLASS_UNAVAILABLE");
        LockLog.fixed("HWC_LOCK_RUNTIME source="+(identity.sourceMatched?"SOURCE_INSTALLED_COMPONENT_VERIFIED":"NOT_MATCHED")
                +" loader="+(identity.loaderMatched?"DECLARING_LOADER_SOURCE_MATCHED":"NOT_MATCHED")+" oemSignature=NOT_VERIFIED reason="+identity.reason);
        if(!identity.sourceMatched||!identity.loaderMatched){bootstrapDiagnostic=LockDiagnosticCause.code("PRODUCER_SOURCE_REJECTED");worker.post(this::pollDiagnosticOnce);return;}
        nativeHooks=NativeSystemUiHooks.install(sourceLoader,identity,new NativeSystemUiHooks.Callback(){
            @Override public void observed(String source){nativeObserved(source);}
            @Override public Object begin(Object injector,MotionEvent event,int state,boolean expanding,boolean qs,boolean dozing){return beginNative(injector,event,state,expanding,qs,dozing);}
            @Override public void end(Object frame){endNative(frame);}
            @Override public boolean suppress(Object listener){return suppressNativeSleep(listener);}
            @Override public boolean internalCancelAllowed(Object value,Object window,View root){return nativeInternalCancelAllowed(value,window,root);}
            @Override public boolean nativeRouteSelected(){return nativeSelected;}
            @Override public void bindingRejected(String reason){nativeRejected(reason);}
            @Override public void lifecycle(Object controller){revokeNative();nativeHit("NATIVE_INSTANCE_DETACHED");}
        });
        capabilityUnsupported=!nativeHooks.actionHooksReady();
        worker.post(this::poll);
        if(rootClass==null){hookDiagnostic=LockDiagnosticCause.code("ROOT_CLASS_UNAVAILABLE");return;}
        try{
            if(!RuntimeApkIdentity.loaderMatches(rootClass.getClassLoader(),identity.sources))throw new SecurityException();
            Method dispatch=rootClass.getDeclaredMethod("dispatchTouchEvent",MotionEvent.class);dispatch.setAccessible(true);
            hook=XposedBridge.hookMethod(dispatch,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    // 不设置任何param字段；原分发、异常、boolean结果均原样保留。
                    if(!(param.thisObject instanceof View)||param.args==null||param.args.length!=1||!(param.args[0] instanceof MotionEvent))return;
                    try{observe((View)param.thisObject,(MotionEvent)param.args[0]);}catch(Throwable ignored){resetGesture();count("OBSERVER_REJECTED");}
                }
            });
            LockLog.fixed("HWC_LOCK_PRODUCER_HOOK_AVAILABLE entry=NATURAL_ROOT_DISPATCH");
            hookDiagnostic=LockDiagnosticCause.code("HOOK_AVAILABLE");
        }catch(Throwable ignored){hookDiagnostic=LockDiagnosticCause.code("HOOK_UNAVAILABLE");LockLog.fixed("HWC_LOCK_PRODUCER_HOOK_UNAVAILABLE");}
    }
    private void observe(View root,MotionEvent event){
        if(capabilityUnsupported){resetGesture();nativeHit("NATIVE_CAPABILITY_SIGNATURE_REJECTED");return;}
        if(nativeSelected){nativeHit("NATIVE_LEGACY_OBSERVE_ONLY");return;}
        if(Looper.myLooper()!=Looper.getMainLooper()){count("ROOT_THREAD_UNKNOWN");return;}
        context=root.getContext().getApplicationContext();
        Channel live=channel;HostState current=state;
        if(live==null||current==null||!current.ready||current.channel!=live||SystemClock.elapsedRealtime()-current.at>2500){
            resetGesture();count("HOST_NOT_READY",LockDiagnosticCause.host(live!=null,current!=null,
                    current!=null&&current.ready,current!=null&&current.channel==live,
                    current!=null&&SystemClock.elapsedRealtime()-current.at>2500));requestPoll();return;
        }
        int action=event.getActionMasked();
        // 没有候选的普通MOVE/UP不遍历前景树，不采集坐标。
        if(gestureTicket==null&&action!=MotionEvent.ACTION_DOWN)return;
        if(!identity.stillSameFile()){resetGesture();state=null;channel=null;count("SOURCE_CHANGED");return;}
        DiagnosticState diagnostic=diagnosticState;
        LockSurfaceSnapshot snapshot=LockSurfaceSnapshot.read(root,diagnostic!=null&&diagnostic.active,current.forceLocal,current.wallpaperPriority);
        if(!snapshot.allowed){resetGesture();count(snapshot.reason,snapshot.layoutDetailCode);reportRoiDetails(snapshot,diagnostic);return;}
        if(snapshot.wallpaperPriority)reportRoiDetails(snapshot,diagnostic);
        // 至此已先拒所有已核认证状态；之后才能读取坐标用于纯本地ROI/移动判定。
        int count=event.getPointerCount();
        if(action==MotionEvent.ACTION_CANCEL||count<1||count>2){resetGesture();count("POINTER_OR_CANCEL_REJECTED");return;}
        if(attachedRoot!=root){
            if(attachedRoot!=null){attachedRoot.removeOnAttachStateChangeListener(attachment);attachedRoot=null;previousLayout=null;resetGesture();clearEdit();invalidate(live);return;}
            attachedRoot=root;root.addOnAttachStateChangeListener(attachment);
        }
        if(!snapshot.sameLayout(previousLayout)){resetGesture();layoutKey=snapshot.layoutKey;previousLayout=snapshot;}
        if(gestures==null){ViewConfiguration config=ViewConfiguration.get(root.getContext());gestures=new LockGestureRecognizer(ViewConfiguration.getDoubleTapTimeout(),ViewConfiguration.getTapTimeout()+200,config.getScaledTouchSlop());}
        if(action==MotionEvent.ACTION_DOWN){
            // 第一个DOWN冻结本域原图票据，定时/显式换图不得把旧候选重定向到新图。
            if(gestureTicket==null){gestureTicket=current.ticket;gestureEpoch=live.actorEpoch;}
            else if(!gestureTicket.equals(current.ticket)||gestureEpoch!=live.actorEpoch){resetGesture();return;}
        }
        if(gestureTicket==null||gestureEpoch!=live.actorEpoch||!gestureTicket.equals(current.ticket)){resetGesture();count("STALE_GESTURE_TICKET");return;}
        int[] ids=new int[count];float[] xs=new float[count],ys=new float[count];int[] location=new int[2];root.getLocationOnScreen(location);
        for(int i=0;i<count;i++){
            ids[i]=event.getPointerId(i);xs[i]=event.getX(i)+location[0];ys[i]=event.getY(i)+location[1];
            if(!snapshot.contains(xs[i],ys[i])){resetGesture();count("OUTSIDE_POSITIVE_ROI");return;}
        }
        int recognized=gestures.onEvent(action,event.getEventTime(),count,ids,xs,ys,event.getActionIndex());
        if(recognized!=LockGestureRecognizer.NONE){send(recognized,root,live);return;}
        main.removeCallbacks(decide);
        if(gestures.deadline()>=0)main.postAtTime(decide,gestures.deadline());
        count("ALLOW_METADATA_OBSERVATION");
    }
    private final Runnable decide=()->{
        View root=attachedRoot;Channel live=channel;HostState current=state;
        if(nativeSelected){resetGesture();return;}
        if(gestures==null||root==null||live==null||current==null||current.channel!=live||!current.ready
                ||gestureTicket==null||!gestureTicket.equals(current.ticket)||SystemClock.elapsedRealtime()-current.at>2500
                ||!identity.stillSameFile()) {resetGesture();return;}
        DiagnosticState diagnostic=diagnosticState;
        LockSurfaceSnapshot snapshot=LockSurfaceSnapshot.read(root,diagnostic!=null&&diagnostic.active,current.forceLocal,current.wallpaperPriority);
        if(!snapshot.allowed){resetGesture();count(snapshot.reason,snapshot.layoutDetailCode);reportRoiDetails(snapshot,diagnostic);return;}
        if(!snapshot.sameLayout(previousLayout)){resetGesture();return;}
        int recognized=gestures.onTime(SystemClock.uptimeMillis());
        if(recognized!=0)send(recognized,root,live);else if(gestures.deadline()<0)resetGesture();
    };
    private void send(int action,View root,Channel live){
        if(sequence==Long.MAX_VALUE){resetGesture();channel=null;state=null;return;}
        final long seq=++sequence,issued=SystemClock.elapsedRealtime();final String ticket=gestureTicket;
        if(action==LockProtocol.EDIT){pendingEditSequence=seq;pendingEditIssued=issued;pendingEditChannel=live;pendingEditRoot=root;}
        resetGesture();
        worker.post(()->{
            if(channel!=live)return;
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(LockInputBroker.SESSION);data.writeInt(action);data.writeLong(seq);data.writeLong(issued);
                data.writeLong(live.actorEpoch);data.writeString(live.actorSession);data.writeString(ticket);
                if(!live.session.transact(3,data,reply,0))throw new IllegalStateException();
                reply.readException();int result=reply.readInt();if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");count(result==0?"COMMAND_ACCEPTED":"COMMAND_REJECTED");
                if(result!=0&&action==LockProtocol.EDIT)main.post(()->{if(pendingEditSequence==seq)clearEdit();});
            }catch(Throwable ignored){invalidate(live);count("COMMAND_CHANNEL_UNAVAILABLE");}
            finally{data.recycle();reply.recycle();}
            requestPoll();
        });
    }
    private void resetGesture(){main.removeCallbacks(decide);main.removeCallbacks(nativeDecide);if(gestures!=null)gestures.reset();gestureTicket=null;nativeOwner.clearSequence();nativeSequenceGeneration=-1;nativeLastXs=null;nativeLastYs=null;}
    private void revokeNative(){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            nativeOwner.revoke();clearEdit();long revokedGeneration=nativeOwner.currentGeneration();
            main.post(()->{if(nativeOwner.currentGeneration()==revokedGeneration)revokeNative();});return;
        }
        resetGesture();nativeOwner.revoke();nativeLayout=null;nativeInvocation=null;clearEdit();if(nativeAuthorityGeneration<Long.MAX_VALUE)nativeAuthorityGeneration++;
    }
    private void nativeObserved(String source){
        synchronized(this){if((source.startsWith("NATIVE_HOOK_")||source.startsWith("NATIVE_CAPABILITY_"))&&nativeHookReports.size()<20)nativeHookReports.add(source);}
        if("NATIVE_ENTRY_ROOT".equals(source))nativeObservedEntry=true;
        nativeHit(source);
    }
    private void nativeHit(String fixed){nativeDiagnostics.hit(NativeInputDetail.code(fixed));}
    private void nativeRejected(String fixed){
        HostState current=state;long now=SystemClock.elapsedRealtime();
        boolean ordinary="NATIVE_SEQUENCE_CANCELLED".equals(fixed)||"NATIVE_MOVEMENT_OR_HOLD_CANCELLED".equals(fixed)||"NATIVE_POINTER_REJECTED".equals(fixed);
        boolean preserve=ordinary&&current!=null&&current.inputReady&&current.wallpaperPriority&&current.channel==channel&&pendingEditChannel==channel
                &&pendingEditSequence>0&&pendingEditIssued>=0&&now>=pendingEditIssued&&now-pendingEditIssued<=5000&&pendingEditRoot==attachedRoot
                &&pendingEditRoot!=null&&pendingEditRoot.isAttachedToWindow()&&identity!=null&&identity.stillSameFile();
        if(preserve){resetGesture();nativeOwner.revoke();nativeLayout=null;nativeInvocation=null;if(nativeAuthorityGeneration<Long.MAX_VALUE)nativeAuthorityGeneration++;nativeHit("NATIVE_EDIT_PENDING_BUSY_PRESERVED");}
        else revokeNative();
        nativeHit(fixed);summaryReason=LockInputSummary.fromReason(fixed);
    }
    /** 当前server/relay/host逐次只读核验，不能用UI开关或旧根ROI作为native输入授权。 */
    private HostState nativeState(Channel expected){
        nativeStateRetryable.set(false);
        nativeNotReadyReason.remove();
        if(expected==null||channel!=expected){nativeHit("NATIVE_STATE_CHANNEL_ABSENT");return null;}
        if(identity==null||!identity.stillSameFile()){nativeHit("NATIVE_STATE_SOURCE_CHANGED");return null;}
        final long request; synchronized(statePublicationLock){request=stateObservations.beginRequest(Looper.myLooper()==Looper.getMainLooper());}
        if(request<0){nativeStateRetryable.set(true);nativeHit("NATIVE_STATE_FOREGROUND_BUSY");return null;}
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockInputBroker.SESSION);
            if(!expected.session.transact(2,data,reply,0)||reply.dataSize()>LockProtocol.MAX_PARCEL_BYTES){nativeHit("NATIVE_STATE_TRANSACT_FAILED");return null;}
            reply.readException();int ready=reply.readInt();String ticket=reply.readString(),reason=reply.readString();
            if(reply.dataAvail()!=0){nativeHit("NATIVE_STATE_TRAILING_DATA");return null;}
            if(ready==0&&LockProtocol.priorityInputOnly(reason)&&LockProtocol.sceneId(ticket)!=null){
                HostState previous=state;
                HostState fresh=new HostState(expected,false,ticket,SystemClock.elapsedRealtime(),previous!=null&&previous.channel==expected&&previous.forceLocal,true,true,reason);
                if(!publishHostState(request,expected,fresh,false)){nativeHit("NATIVE_STATE_RESPONSE_STALE");return null;}
                nativeHit("NATIVE_PRIORITY_INPUT_ONLY");return fresh;
            }
            if(ready!=1){nativeNotReadyReason.set(reason);nativeHit("NATIVE_STATE_NOT_READY");nativeHit(switch(reason==null?"":reason){case "BUSY_EDIT"->"NATIVE_STATE_BUSY_EDIT";case "NO_ENGINE"->"NATIVE_STATE_NO_ENGINE";case "STALE_TICKET"->"NATIVE_STATE_STALE_IMAGE";default->"NATIVE_STATE_UNKNOWN_RENDER";});return null;}
            if(!LockProtocol.token(ticket)){nativeHit("NATIVE_STATE_TICKET_INVALID");return null;}
            if(!WallpaperPriorityMode.nativeInput(reason)){nativeHit("NATIVE_STATE_MARKER_INVALID");return null;}
            boolean priority=WallpaperPriorityMode.enabled(reason),forceLocal=WallpaperPriorityMode.forceLocal(reason);
            HostState fresh=new HostState(expected,true,ticket,SystemClock.elapsedRealtime(),forceLocal,priority,true,reason);
            if(!publishHostState(request,expected,fresh,false)){nativeStateRetryable.set(channel==expected);nativeHit("NATIVE_STATE_RESPONSE_STALE");return null;}return fresh;
        }catch(Throwable ignored){nativeHit("NATIVE_STATE_EXCEPTION");return null;}finally{stateObservations.finishRequest(request);data.recycle();reply.recycle();}
    }
    /** 同步先撤旧owner；排队资源清理只可作用于其观察/代次，不能撤掉后来准入的新序列。 */
    private boolean publishHostState(long request,Channel expected,HostState fresh,boolean editHold){
        synchronized(statePublicationLock){
            if(channel!=expected||!stateObservations.publish(request,fresh))return false;
            HostState previous=state;state=fresh;if(fresh.inputReady)nativeSelected=fresh.nativeInput;
            boolean samePriorityScene=previous!=null&&previous.channel==fresh.channel&&previous.wallpaperPriority&&fresh.wallpaperPriority
                    &&LockProtocol.sceneId(previous.ticket)!=null&&LockProtocol.sceneId(previous.ticket).equals(LockProtocol.sceneId(fresh.ticket));
            boolean changed=previous!=null&&(previous.channel!=fresh.channel||previous.nativeInput!=fresh.nativeInput
                    ||!samePriorityScene&&previous.forceLocal!=fresh.forceLocal||previous.wallpaperPriority!=fresh.wallpaperPriority
                    ||previous.wallpaperPriority&&fresh.wallpaperPriority&&!java.util.Objects.equals(LockProtocol.sceneId(previous.ticket),LockProtocol.sceneId(fresh.ticket))
                    ||!editHold&&(!samePriorityScene&&fresh.ready&&!previous.ticket.equals(fresh.ticket)||!fresh.inputReady));
            if(changed){
                int dropped=priorityQueue.clearFor(expected);for(int i=0;i<dropped;i++)nativeHit("NATIVE_PRIORITY_QUEUE_SCENE_REVOKED");
                if(!editHold)clearEdit();
                nativeOwner.revoke();long revokedGeneration=nativeOwner.currentGeneration();
                main.post(()->{if(state==fresh&&nativeOwner.currentGeneration()==revokedGeneration)revokeNative();});
            }
            return true;
        }
    }
    private NativeGestureOwnership.Binding nativeBinding(NativeBusinessSnapshot snapshot,Channel live,HostState current){
        NativeBusinessSnapshot.Binding b=snapshot.binding;
        return new NativeGestureOwnership.Binding(b.injector,b.controller,b.root,b.helper,b.listener,live,live.actorEpoch,recognitionTicket(live,current),current.nativeInput?1:0,nativeAuthorityGeneration);
    }
    private String recognitionTicket(Channel live,HostState current){String scene=current.wallpaperPriority?LockProtocol.sceneId(current.ticket):null;return scene!=null?scene:current.ticket;}
    /** 优先识别只读最近已核场景；同步Root每个触摸不再阻塞等待跨进程渲染状态。 */
    private HostState inputState(Channel live){
        HostState cached=state;long now=SystemClock.elapsedRealtime();
        if(live!=null&&channel==live&&cached!=null&&cached.channel==live&&cached.inputReady&&cached.nativeInput&&cached.wallpaperPriority
                &&LockProtocol.sceneId(cached.ticket)!=null&&now>=cached.at&&now-cached.at<=2500&&identity!=null&&identity.stillSameFile()){
            nativeHit("NATIVE_PRIORITY_CACHE_HIT");return cached;
        }
        return nativeState(live);
    }
    /** MotionEvent只在这个同步调用中读取，返回的frame只持副本标量和绑定，不持事件。 */
    private Object beginNative(Object injector,MotionEvent event,int barState,boolean expanding,boolean qs,boolean dozing){
        int rawAction=event.getActionMasked();nativeHit(NativeInputDetail.eventName(rawAction));
        if(Looper.myLooper()!=Looper.getMainLooper()){nativeHit("NATIVE_THREAD_REJECTED");return null;}
        HostState cached=state;
        if(nativeHooks==null||!nativeHooks.actionHooksReady()){nativeRejected("NATIVE_HOOK_SLEEP_UNAVAILABLE");return null;}
        if(cached==null||!cached.nativeInput){nativeHit("NATIVE_ROUTE_LEGACY");requestPoll();return null;}
        Channel live=channel;HostState current=inputState(live);
        if(current==null){
            if("BUSY_EDIT".equals(nativeNotReadyReason.get())&&pendingEditChannel==live&&pendingEditSequence>0&&pendingEditIssued>=0
                    &&SystemClock.elapsedRealtime()>=pendingEditIssued&&SystemClock.elapsedRealtime()-pendingEditIssued<=5000&&channel==live){
                resetGesture();nativeOwner.revoke();nativeLayout=null;nativeInvocation=null;nativeHit("NATIVE_EDIT_PENDING_BUSY_PRESERVED");return null;
            }
            revokeNative();nativeRejected("NATIVE_SESSION_INVALID");requestPoll();return null;
        }
        nativeHit("NATIVE_SESSION_READY");
        NativeBusinessSnapshot snapshot=NativeBusinessSnapshot.read(injector,barState,expanding,qs,dozing,current.wallpaperPriority);
        if(!snapshot.allowed){revokeNative();nativeRejected(snapshot.reason);if(snapshot.diagnosticScope!=null)nativeHit(snapshot.diagnosticScope);if(snapshot.diagnosticCause!=null)nativeHit(snapshot.diagnosticCause);return null;}
        nativeHit("NATIVE_INSTANCE_BOUND");nativeHit("NATIVE_ALLOWED");
        context=snapshot.root.getContext().getApplicationContext();
        if(attachedRoot!=snapshot.root){
            if(attachedRoot!=null)attachedRoot.removeOnAttachStateChangeListener(attachment);
            revokeNative();attachedRoot=snapshot.root;attachedRoot.addOnAttachStateChangeListener(attachment);nativeHit("NATIVE_INSTANCE_REPLACED");
        }
        if(nativeLayout!=null&&!snapshot.sameLayout(nativeLayout)){revokeNative();nativeHit("NATIVE_LOCAL_LAYOUT_CHANGED");}
        NativeGestureOwnership.Binding binding=nativeBinding(snapshot,live,current);
        if(nativeOwner.bind(binding)){resetGesture();nativeHit("NATIVE_INSTANCE_BOUND");}
        nativeLayout=snapshot;
        int action=event.getActionMasked(),count=event.getPointerCount();
        if(action==MotionEvent.ACTION_CANCEL||count<1||count>2){nativeRejected(action==MotionEvent.ACTION_CANCEL?"NATIVE_SEQUENCE_CANCELLED":"NATIVE_POINTER_REJECTED");return null;}
        // 认证及局部边界的未知已先拒，至此才读取触点；全部副本仅在本进程短手势内存中使用。
        int[] ids=new int[count];float[] xs=new float[count],ys=new float[count];
        for(int i=0;i<count;i++){
            ids[i]=event.getPointerId(i);xs[i]=event.getRawX(i);ys[i]=event.getRawY(i);
            NativeBusinessSnapshot.PointResult point=snapshot.point(xs[i],ys[i]);
            if(!point.allowed){nativeRejected(point.reason);return null;}
        }
        int index=event.getActionIndex();int actionId=(action==MotionEvent.ACTION_MOVE)?-1:index>=0&&index<count?ids[index]:-1;
        NativeGestureOwnership.EventStamp stamp=new NativeGestureOwnership.EventStamp(event.getDownTime(),event.getEventTime(),action,actionId,ids,xs,ys);
        NativeGestureOwnership.Frame frame=nativeOwner.beginFrame(binding,stamp,true,true);
        NativeInvocation invocation=new NativeInvocation(frame,binding,snapshot,barState,expanding,qs,dozing,ids,xs,ys,nativeInvocation);nativeInvocation=invocation;
        if(frame.rejected){nativeRejected("NATIVE_SEQUENCE_CANCELLED");return invocation;}
        if(frame.duplicate){nativeHit("NATIVE_DUPLICATE_EVENT");return invocation;}
        nativeLastXs=xs.clone();nativeLastYs=ys.clone();
        if(gestures==null){ViewConfiguration config=ViewConfiguration.get(snapshot.root.getContext());gestures=new LockGestureRecognizer(ViewConfiguration.getDoubleTapTimeout(),ViewConfiguration.getTapTimeout()+200,config.getScaledTouchSlop());}
        if(action==MotionEvent.ACTION_DOWN&&gestureTicket==null){gestureTicket=recognitionTicket(live,current);gestureEpoch=live.actorEpoch;nativeSequenceGeneration=nativeOwner.beginSequence(binding);nativeHit("NATIVE_SEQUENCE_STARTED");}
        if(gestureTicket==null||!gestureTicket.equals(recognitionTicket(live,current))||gestureEpoch!=live.actorEpoch||!nativeOwner.sequenceValid(binding,nativeSequenceGeneration)){
            nativeRejected("NATIVE_SEQUENCE_CANCELLED");nativeOwner.revoke();return invocation;
        }
        // 先结清尚未来得及运行的旧双击，再把本次DOWN交给新序列。
        // 若onEvent先启动新序列，nativeSend中的reset会连带清掉这个真实首按。
        int overdue=action==MotionEvent.ACTION_DOWN?gestures.onTime(event.getEventTime()):LockGestureRecognizer.NONE;
        if(overdue!=LockGestureRecognizer.NONE){
            nativeOwner.claimGesture(frame);nativeSend(overdue,invocation,live);
            if(!beginAfterExpiredDouble(invocation,live))return invocation;
        }
        int recognized=gestures.onEvent(action,event.getEventTime(),count,ids,xs,ys,index);
        if(recognized!=LockGestureRecognizer.NONE||gestures.pendingDoubleCandidate())nativeOwner.claimGesture(frame);
        if(recognized!=LockGestureRecognizer.NONE){nativeSend(recognized,invocation,live);return invocation;}
        if(!gestures.hasCandidate()){nativeRejected("NATIVE_MOVEMENT_OR_HOLD_CANCELLED");nativeOwner.revoke();return invocation;}
        main.removeCallbacks(nativeDecide);
        if(gestures.deadline()>=0)main.postAtTime(nativeDecide,gestures.deadline());
        nativeHit("NATIVE_SEQUENCE_WAITING");summaryReason=LockInputSummary.READY_BACKGROUND;
        return invocation;
    }
    /** 只继续当前同步DOWN；不恢复旧事件，也不能跨通道、场景、模式或owner撤权。 */
    private boolean beginAfterExpiredDouble(NativeInvocation invocation,Channel live){
        HostState current=state;
        if(channel!=live||current==null||current.channel!=live||!current.inputReady||!current.nativeInput
                ||!recognitionTicket(live,current).equals(invocation.binding.ticket)
                ||!nativeOwner.bindingValid(invocation.binding,invocation.frame.generation))return false;
        gestureTicket=recognitionTicket(live,current);gestureEpoch=live.actorEpoch;
        nativeSequenceGeneration=nativeOwner.beginSequence(invocation.binding);
        nativeLastXs=invocation.xs.clone();nativeLastYs=invocation.ys.clone();
        nativeHit("NATIVE_SEQUENCE_STARTED");nativeHit("NATIVE_EXPIRED_DOUBLE_NEXT_DOWN_PRESERVED");
        return nativeOwner.sequenceValid(invocation.binding,nativeSequenceGeneration);
    }
    private void endNative(Object value){
        if(!(value instanceof NativeInvocation))return;NativeInvocation invocation=(NativeInvocation)value;
        try{if(!nativeOwner.endFrame(invocation.frame)&&!invocation.frame.rejected&&nativeOwner.currentGeneration()==invocation.frame.generation)revokeNative();}
        finally{if(nativeInvocation==invocation)nativeInvocation=invocation.previous;nativeHit("NATIVE_CALLBACK_FRAME_CLEARED");}
    }
    /** 只辨认精确OEM同栈取消来源，不认可未知CANCEL、不授予手势/原生息屏接管。 */
    private boolean nativeInternalCancelAllowed(Object value,Object window,View root){
        if(Looper.myLooper()!=Looper.getMainLooper()||!(value instanceof NativeInvocation))return false;
        NativeInvocation invocation=(NativeInvocation)value;
        if(nativeInvocation!=invocation||invocation.frame==null||!invocation.frame.isActive()||!invocation.frame.admitted
                ||!nativeOwner.bindingMatches(invocation.binding)||invocation.snapshot.binding.shadeRoot!=root)return false;
        NativeRootBinding.Result fresh=NativeRootBinding.read(root);
        return fresh.allowed&&fresh.windowController==window&&fresh.injector==invocation.binding.injector
                &&fresh.panelController==invocation.snapshot.binding.notificationController;
    }
    private boolean suppressNativeSleep(Object listener){
        if(Looper.myLooper()!=Looper.getMainLooper()){nativeHit("NATIVE_THREAD_REJECTED");nativeHit("NATIVE_SLEEP_PASSTHROUGH");return false;}
        NativeInvocation invocation=nativeInvocation;
        if(invocation==null){nativeHit("NATIVE_SLEEP_PASSTHROUGH");return false;}
        Channel live=channel;HostState current=inputState(live);
        if(current==null||!recognitionTicket(live,current).equals(invocation.binding.ticket)||nativeHooks==null||!nativeHooks.actionHooksReady()){
            revokeNative();nativeHit("NATIVE_SLEEP_PASSTHROUGH");return false;
        }
        NativeBusinessSnapshot snapshot=NativeBusinessSnapshot.read(invocation.binding.injector,invocation.barState,invocation.expanding,invocation.qs,invocation.dozing,current.wallpaperPriority);
        if(!snapshot.allowed||!snapshot.sameLayout(invocation.snapshot)){nativeRejected(snapshot.allowed?"NATIVE_LOCAL_LAYOUT_CHANGED":snapshot.reason);nativeHit("NATIVE_SLEEP_PASSTHROUGH");return false;}
        for(int i=0;i<invocation.xs.length;i++)if(!snapshot.point(invocation.xs[i],invocation.ys[i]).allowed){revokeNative();nativeHit("NATIVE_SLEEP_PASSTHROUGH");return false;}
        boolean suppress=nativeOwner.maySuppress(listener,snapshot.binding.helper,nativeBinding(snapshot,live,current));
        nativeHit(suppress?"NATIVE_SLEEP_SUPPRESSED":"NATIVE_SLEEP_PASSTHROUGH");return suppress;
    }
    private final Runnable nativeDecide=()->{
        NativeBusinessSnapshot before=nativeLayout;Channel live=channel;HostState current=inputState(live);
        if(before==null||current==null||gestureTicket==null||!gestureTicket.equals(recognitionTicket(live,current))||gestures==null){nativeRejected("NATIVE_SESSION_INVALID");return;}
        NativeBusinessSnapshot fresh=NativeBusinessSnapshot.read(before.binding.injector,1,false,false,false,current.wallpaperPriority);
        if(!fresh.allowed||!fresh.sameLayout(before)){nativeRejected(fresh.allowed?"NATIVE_LOCAL_LAYOUT_CHANGED":fresh.reason);return;}
        if(nativeLastXs==null||nativeLastYs==null){nativeRejected("NATIVE_SEQUENCE_CANCELLED");return;}
        for(int i=0;i<nativeLastXs.length;i++){NativeBusinessSnapshot.PointResult point=fresh.point(nativeLastXs[i],nativeLastYs[i]);if(!point.allowed){nativeRejected(point.reason);return;}}
        NativeGestureOwnership.Binding binding=nativeBinding(fresh,live,current);
        if(!nativeOwner.sequenceValid(binding,nativeSequenceGeneration)){nativeRejected("NATIVE_SEQUENCE_CANCELLED");return;}
        int recognized=gestures.onTime(SystemClock.uptimeMillis());
        if(recognized!=LockGestureRecognizer.NONE)nativeSend(recognized,new NativeInvocation(null,binding,fresh,1,false,false,false,new int[0],nativeLastXs.clone(),nativeLastYs.clone(),null),live);
        else if(gestures.deadline()<0)resetGesture();
    };
    private void nativeSend(int action,NativeInvocation invocation,Channel live){
        nativeHit(action==1?"NATIVE_RECOGNIZED_NEXT":action==2?"NATIVE_RECOGNIZED_PREVIOUS":"NATIVE_RECOGNIZED_EDIT");
        if(invocation.snapshot.wallpaperPriority){enqueuePriority(action,invocation,live);return;}
        if(sequence==Long.MAX_VALUE){revokeNative();return;}
        final long seq=++sequence,issued=SystemClock.elapsedRealtime(),generation=nativeOwner.currentGeneration();
        final String ticket=gestureTicket;final NativeGestureOwnership.Binding binding=invocation.binding;
        if(action==LockProtocol.EDIT){pendingEditSequence=seq;pendingEditIssued=issued;pendingEditChannel=live;pendingEditRoot=invocation.snapshot.root;}
        resetGesture(); // 本次同步仲裁frame保留至endNative，而不是随识别票据一起清除。
        main.post(()->{
            NativeBusinessSnapshot check=NativeBusinessSnapshot.read(binding.injector,invocation.barState,invocation.expanding,invocation.qs,invocation.dozing,invocation.snapshot.wallpaperPriority);
            if(!check.allowed||!check.sameLayout(invocation.snapshot)||!nativeOwner.bindingValid(binding,generation)){nativeRejected(check.allowed?"NATIVE_BINDING_CHANGED":check.reason);return;}
            for(int i=0;i<invocation.xs.length;i++){NativeBusinessSnapshot.PointResult point=check.point(invocation.xs[i],invocation.ys[i]);if(!point.allowed){nativeRejected(point.reason);return;}}
            worker.post(()->sendNativeCommandAttempt(action,seq,issued,generation,ticket,binding,live,invocation,0));
        });
    }
    /** 只延后前台状态查询的暂时竞争；原票据／issued／TTL不变，最多3次20ms，不重复发送。 */
    private void sendNativeCommandAttempt(int action,long seq,long issued,long generation,String ticket,NativeGestureOwnership.Binding binding,Channel live,NativeInvocation approval,int attempt){
            if(channel!=live||!nativeOwner.bindingValid(binding,generation)||!LockProtocol.timely(action,issued,SystemClock.elapsedRealtime())){nativeHit("NATIVE_COMMAND_REJECTED");return;}
            HostState current=nativeState(live);
            if(current==null){
                if(Boolean.TRUE.equals(nativeStateRetryable.get())&&channel==live&&nativeOwner.bindingValid(binding,generation)
                        &&LockProtocol.timely(action,issued,SystemClock.elapsedRealtime())){
                    if(attempt<3){nativeHit("NATIVE_COMMAND_DEFERRED");main.postDelayed(()->{
                        if(channel!=live||!nativeOwner.bindingValid(binding,generation)||!LockProtocol.timely(action,issued,SystemClock.elapsedRealtime())){nativeHit("NATIVE_COMMAND_REJECTED");return;}
                        NativeBusinessSnapshot fresh=NativeBusinessSnapshot.read(binding.injector,approval.barState,approval.expanding,approval.qs,approval.dozing,approval.snapshot.wallpaperPriority);
                        if(!fresh.allowed||!fresh.sameLayout(approval.snapshot)){nativeRejected(fresh.allowed?"NATIVE_LOCAL_LAYOUT_CHANGED":fresh.reason);nativeHit("NATIVE_COMMAND_REJECTED");return;}
                        for(int i=0;i<approval.xs.length;i++)if(!fresh.point(approval.xs[i],approval.ys[i]).allowed){nativeRejected("NATIVE_LOCAL_LAYOUT_CHANGED");nativeHit("NATIVE_COMMAND_REJECTED");return;}
                        worker.post(()->sendNativeCommandAttempt(action,seq,issued,generation,ticket,binding,live,approval,attempt+1));
                    },20);return;}
                    nativeHit("NATIVE_COMMAND_RETRY_EXHAUSTED");
                }
                nativeHit("NATIVE_COMMAND_REJECTED");return;
            }
            if(!ticket.equals(current.ticket)||!nativeOwner.bindingValid(binding,generation)){nativeHit("NATIVE_COMMAND_REJECTED");return;}
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(LockInputBroker.SESSION);data.writeInt(action);data.writeLong(seq);data.writeLong(issued);data.writeLong(live.actorEpoch);data.writeString(live.actorSession);data.writeString(ticket);
                nativeHit("NATIVE_COMMAND_SENT");
                if(!live.session.transact(3,data,reply,0))throw new IllegalStateException();
                reply.readException();int result=reply.readInt();if(reply.dataAvail()!=0)throw new IllegalArgumentException();
                nativeHit(result==0?"NATIVE_COMMAND_ACCEPTED":"NATIVE_COMMAND_REJECTED");summaryReason=result==0?LockInputSummary.COMMAND_ACCEPTED:LockInputSummary.COMMAND_REJECTED;
                if(result!=0&&action==LockProtocol.EDIT)main.post(()->{if(pendingEditSequence==seq)clearEdit();});
            }catch(Throwable ignored){invalidate(live);nativeHit("NATIVE_COMMAND_CHANNEL_LOST");}finally{data.recycle();reply.recycle();}requestPoll();
    }
    private void enqueuePriority(int action,NativeInvocation invocation,Channel live){
        HostState current=state;String scene=current==null?null:LockProtocol.sceneId(current.ticket);
        if(current==null||current.channel!=live||channel!=live||!current.inputReady||!current.wallpaperPriority||scene==null||!scene.equals(invocation.binding.ticket)){
            resetGesture();nativeHit("NATIVE_PRIORITY_SCENE_UNAVAILABLE");return;
        }
        PriorityGestureQueue.Owner owner=new PriorityGestureQueue.Owner(live,invocation.snapshot.root,live.actorEpoch,scene,invocation.snapshot.binding.shadeRoot);
        int changed=priorityQueue.bind(owner);if(changed>0)nativeHit("NATIVE_PRIORITY_QUEUE_SCENE_REVOKED");
        int expired=priorityQueue.expire(SystemClock.elapsedRealtime());for(int i=0;i<expired;i++)nativeHit("NATIVE_PRIORITY_QUEUE_EXPIRED");
        boolean offered=priorityQueue.offer(owner,action,SystemClock.elapsedRealtime());
        resetGesture();nativeHit(offered?"NATIVE_PRIORITY_QUEUE_ACCEPTED":"NATIVE_PRIORITY_QUEUE_FULL");
        if(offered)schedulePriorityDrain(0);
    }
    private void schedulePriorityDrain(long delay){
        if(priorityDrainScheduled||priorityDrainInFlight||priorityQueue.peek()==null)return;
        priorityDrainScheduled=true;main.postDelayed(this::drainPriority,delay);
    }
    /** 已识别意图等待当前场景可提交；不把MotionEvent或旧图片请求留到以后重放。 */
    private void drainPriority(){
        priorityDrainScheduled=false;
        int expired=priorityQueue.expire(SystemClock.elapsedRealtime());for(int i=0;i<expired;i++)nativeHit("NATIVE_PRIORITY_QUEUE_EXPIRED");
        PriorityGestureQueue.Item item=priorityQueue.peek();if(item==null||priorityDrainInFlight)return;
        if(!priorityContext(item,state)){priorityQueue.clear();nativeHit("NATIVE_PRIORITY_QUEUE_SCENE_REVOKED");return;}
        priorityDrainInFlight=true;Channel live=(Channel)item.owner.channel;
        worker.postAtFrontOfQueue(()->{HostState fresh=nativeState(live);boolean retryable=Boolean.TRUE.equals(nativeStateRetryable.get());main.post(()->priorityReadComplete(item,fresh,retryable));});
    }
    private boolean priorityContext(PriorityGestureQueue.Item item,HostState current){
        if(item==null||current==null||!current.inputReady||!current.nativeInput||!current.wallpaperPriority||channel!=item.owner.channel
                ||current.channel!=item.owner.channel||((Channel)item.owner.channel).actorEpoch!=item.owner.actorEpoch
                ||!item.owner.scene.equals(LockProtocol.sceneId(current.ticket))||identity==null||!identity.stillSameFile())return false;
        NativeRootBinding.Result root=NativeRootBinding.read(item.owner.window);
        if(!root.allowed)return false;
        NativeBusinessSnapshot checked=NativeBusinessSnapshot.read(root.injector,root.barState,root.expanding,root.qsExpanded,root.dozing,true);
        return checked.allowed&&checked.root==item.owner.root;
    }
    private void priorityReadComplete(PriorityGestureQueue.Item item,HostState fresh,boolean retryable){
        priorityDrainInFlight=false;
        int expired=priorityQueue.expire(SystemClock.elapsedRealtime());for(int i=0;i<expired;i++)nativeHit("NATIVE_PRIORITY_QUEUE_EXPIRED");
        if(priorityQueue.peek()!=item){schedulePriorityDrain(0);return;}
        if(fresh==null&&retryable&&channel==item.owner.channel){nativeHit("NATIVE_PRIORITY_QUEUE_HOST_BUSY");schedulePriorityDrain(50);return;}
        if(!priorityContext(item,fresh)){priorityQueue.clear();nativeHit("NATIVE_PRIORITY_QUEUE_SCENE_REVOKED");return;}
        if(!fresh.ready){nativeHit("NATIVE_PRIORITY_QUEUE_HOST_BUSY");schedulePriorityDrain(50);return;}
        if(sequence==Long.MAX_VALUE){priorityQueue.clear();nativeHit("NATIVE_PRIORITY_QUEUE_REJECTED");return;}
        final long seq=++sequence,issued=SystemClock.elapsedRealtime();Channel live=(Channel)item.owner.channel;
        if(item.action==LockProtocol.EDIT){pendingEditSequence=seq;pendingEditIssued=issued;pendingEditChannel=live;pendingEditRoot=(View)item.owner.root;}
        priorityDrainInFlight=true;
        worker.postAtFrontOfQueue(()->{
            int result=2;
            if(channel==live&&priorityQueue.peek()==item&&LockProtocol.timely(item.action,issued,SystemClock.elapsedRealtime())){
                Parcel data=Parcel.obtain(),reply=Parcel.obtain();
                try{
                    data.writeInterfaceToken(LockInputBroker.SESSION);data.writeInt(item.action);data.writeLong(seq);data.writeLong(issued);
                    data.writeLong(live.actorEpoch);data.writeString(live.actorSession);data.writeString(fresh.ticket);
                    nativeHit("NATIVE_COMMAND_SENT");
                    if(!live.session.transact(3,data,reply,0))throw new IllegalStateException();reply.readException();result=reply.readInt();
                    if(reply.dataAvail()!=0||result<0||result>3)result=2;
                    nativeHit(result==0?"NATIVE_COMMAND_ACCEPTED":"NATIVE_COMMAND_REJECTED");
                }catch(Throwable ignored){invalidate(live);nativeHit("NATIVE_COMMAND_CHANNEL_LOST");}
                finally{data.recycle();reply.recycle();}
            }
            final int outcome=result;
            main.post(()->{
                priorityDrainInFlight=false;
                if(priorityQueue.peek()!=item){schedulePriorityDrain(0);return;}
                if(outcome==0){priorityQueue.acknowledge(item);nativeHit("NATIVE_PRIORITY_QUEUE_DISPATCHED");}
                else if(outcome==2){priorityQueue.acknowledge(item);if(item.action==LockProtocol.EDIT&&pendingEditSequence==seq)clearEdit();nativeHit("NATIVE_PRIORITY_QUEUE_REJECTED");}
                else{if(item.action==LockProtocol.EDIT&&pendingEditSequence==seq)clearEdit();nativeHit("NATIVE_PRIORITY_QUEUE_HOST_BUSY");}
                schedulePriorityDrain(50);requestPoll();
            });
        });
    }
    private void clearEdit(){pendingEditSequence=-1;pendingEditIssued=-1;pendingEditChannel=null;pendingEditRoot=null;}
    private void count(String reason){count(reason,0);}
    private synchronized void count(String reason,int layoutDetailCode){
        int nextReason=LockInputSummary.fromReason(reason);
        if(nextReason!=LockInputSummary.HOST_NOT_READY||summaryReason==LockInputSummary.WAITING_FIRST_INPUT||summaryReason==LockInputSummary.HOST_NOT_READY)summaryReason=nextReason;
        DiagnosticState capture=diagnosticState;
        if(capture!=null&&capture.active){
            counters.put(reason,counters.getOrDefault(reason,0L)+1);
            int cause=LockDiagnosticCause.input(reason,layoutDetailCode);diagnosticCount++;
            if(cause!=lastDiagnosticCause&&!diagnosticReportQueued){
                diagnosticReportQueued=true;lastDiagnosticCause=cause;long total=diagnosticCount;
                worker.post(()->{
                    synchronized(LockSystemUiObserver.this){diagnosticReportQueued=false;}
                    reportDiagnostic(capture,LockDiagnosticCause.INPUT,cause,total);
                });
            }
        }else {counters.clear();return;}
        long now=SystemClock.elapsedRealtime();if(now-lastSummary<10000)return;lastSummary=now;
        // 固定原因/累计总数，无认证阶段逐事件时间、pointer序列、坐标、View文字或路径。
        StringBuilder text=new StringBuilder("HWC_LOCK_SUMMARY");for(var entry:counters.entrySet())text.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        LockLog.fixed(text.toString());
    }
    private synchronized void requestPoll(){
        if(pollQueued)return;pollQueued=true;
        worker.post(()->{synchronized(LockSystemUiObserver.this){pollQueued=false;}pollOnce();});
    }
    private void poll(){pollOnce();worker.postDelayed(this::poll,channel==null?5000:1000);}
    private void pollOnce(){
        if(polling||identity==null||!identity.sourceMatched||!identity.loaderMatched)return;
        polling=true;long request=-1;Channel requestedChannel=null;
        try{
            Channel live=channel;if(live==null)live=connect(LockInputBroker.ROLE_PRODUCER);if(live==null)return;
            synchronized(statePublicationLock){request=stateObservations.beginRequest();}if(request<0)return;requestedChannel=live;
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(LockInputBroker.SESSION);
                if(!live.session.transact(2,data,reply,0))throw new IllegalStateException();
                reply.readException();int ready=reply.readInt();String ticket=reply.readString(),reason=reply.readString();if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                if((ready!=0&&ready!=1)||ticket==null||ticket.length()>128||reason==null||reason.length()>64)throw new IllegalStateException();
                if("UNAUTHORIZED_OR_NOT_READY".equals(reason)){
                    if(stateObservations.current(request)&&channel==live)invalidate(live);else nativeHit("NATIVE_STATE_RESPONSE_STALE");return;
                }
                boolean inputOnly=ready==0&&LockProtocol.priorityInputOnly(reason)&&LockProtocol.sceneId(ticket)!=null;
                HostState previous=state;
                boolean forceLocal=inputOnly?previous!=null&&previous.channel==live&&previous.forceLocal:ForceLocalRegion.enabled(reason);
                boolean priority=inputOnly||WallpaperPriorityMode.enabled(reason);
                boolean nativeInput=inputOnly||(ready==1?WallpaperPriorityMode.nativeInput(reason):nativeSelected);
                HostState fresh=new HostState(live,ready==1&&LockProtocol.token(ticket),ticket,SystemClock.elapsedRealtime(),forceLocal,priority,nativeInput,reason);
                boolean editHold=!fresh.ready&&("BUSY_EDIT".equals(reason)||"INPUT_ONLY_NATIVE_PRIORITY_BUSY_EDIT".equals(reason))&&pendingEditChannel==live&&pendingEditSequence>0;
                if(editHold&&!inputOnly&&previous!=null&&previous.channel==live)fresh=new HostState(live,false,previous.ticket,SystemClock.elapsedRealtime(),previous.forceLocal,previous.wallpaperPriority,previous.nativeInput,reason);
                if(!publishHostState(request,live,fresh,editHold))return;
                reportSummary(live);
            }finally{data.recycle();reply.recycle();}
        }catch(Throwable ignored){
            if(request>0&&stateObservations.current(request)&&requestedChannel==channel){invalidate(requestedChannel);count("BOOTSTRAP_OR_STATUS_UNAVAILABLE");}
            else nativeHit("NATIVE_STATE_RESPONSE_STALE");
        }
        finally{polling=false;}
    }
    private void reportSummary(Channel live){
        long now=SystemClock.elapsedRealtime();if(lastSummaryReport>=0&&now-lastSummaryReport<1000)return;
        lastSummaryReport=now;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockInputBroker.SESSION);data.writeInt(summaryReason);
            if(!live.session.transact(5,data,reply,0))return;
            reply.readException();reply.readInt();if(reply.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
        }catch(Throwable ignored){/* 摘要失败不撤合法编辑票据，也不放宽准入。 */}
        finally{data.recycle();reply.recycle();}
    }
    private Channel connect(int role)throws Exception{
        Class<?> manager=Class.forName("android.app.ActivityManager");Method get=manager.getDeclaredMethod("getService");get.setAccessible(true);
        Object service=get.invoke(null);Method asBinder=Class.forName("android.os.IInterface").getMethod("asBinder");
        IBinder activity=(IBinder)asBinder.invoke(service);
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockInputBroker.BOOTSTRAP);data.writeInt(1);data.writeInt(role);data.writeStrongBinder(callback);data.writeStrongBinder(null);
            if(!activity.transact(LockInputBroker.DISCOVER,data,reply,0))return null;
            reply.readException();int status=reply.readInt(),ssPid=reply.readInt();String epoch=reply.readString();IBinder session=reply.readStrongBinder();
            long actorEpoch=reply.readLong();String actorSession=reply.readString();if (reply.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
            if(status!=0||ssPid<=0||!LockProtocol.token(epoch)||session==null||actorEpoch<=0||!LockProtocol.token(actorSession)){if(role==LockInputBroker.ROLE_PRODUCER)count(status==1?"SOURCE_IDENTITY_PENDING":"REGISTRATION_NOT_READY");return null;}
            Channel fresh=new Channel(session,ssPid,epoch,actorEpoch,actorSession);
            if(role==LockInputBroker.ROLE_PRODUCER){channel=fresh;summaryReason=LockInputSummary.WAITING_FIRST_INPUT;lastSummaryReport=-1;session.linkToDeath(()->invalidate(fresh),0);}
            else session.linkToDeath(()->{if(diagnosticChannel==fresh){diagnosticChannel=null;diagnosticState=null;}},0);
            return fresh;
        }finally{data.recycle();reply.recycle();}
    }
    private void pollDiagnostic(){pollDiagnosticOnce();worker.postDelayed(this::pollDiagnostic,1000);}
    private void pollDiagnosticOnce(){
        diagnosticAttempted=true;
        try{
            if(diagnosticChannel==null)diagnosticChannel=connect(LockInputBroker.ROLE_DIAGNOSTIC);
            Channel live=diagnosticChannel;if(live==null)return;
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(LockInputBroker.SESSION);
                if(!live.session.transact(12,data,reply,0))return;
                reply.readException();int active=reply.readInt();String token=reply.readString();if(reply.dataAvail()!=0||(active!=0&&active!=1)||!LockProtocol.token(token))throw new IllegalStateException();
                DiagnosticState previous=diagnosticState;
                DiagnosticState next=new DiagnosticState(live,active==1,token);diagnosticState=next;
                nativeDiagnostics.session(token,next.active);
                if(!nativeDiagnosticToken.equals(token)){nativeDiagnosticToken=token;nativeObservedEntry=false;synchronized(this){for(String fixed:nativeHookReports)nativeHit(fixed);}}
                if(next.active&&!nativeObservedEntry)nativeHit("NATIVE_ENTRY_NOT_HIT");
                flushNativeDiagnostics(next);
                if(previous==null||!previous.token.equals(token))synchronized(LockSystemUiObserver.this){diagnosticCount=0;lastDiagnosticCause=-1;}
                if(!diagnosticPhaseToken.equals(token)){diagnosticPhaseToken=token;java.util.Arrays.fill(diagnosticPhaseCodes,-1);}
                int[] phases={sourceDiagnostic,loaderDiagnostic,hookDiagnostic,bootstrapDiagnostic};
                for(int i=0;i<phases.length;i++)if(phases[i]!=diagnosticPhaseCodes[i]){
                    reportDiagnostic(next,i+1,phases[i],1);diagnosticPhaseCodes[i]=phases[i];
                }
            }finally{data.recycle();reply.recycle();}
        }catch(Throwable ignored){diagnosticChannel=null;diagnosticState=null;}
    }
    private void reportDiagnostic(DiagnosticState state,int stage,int cause,long total){
        if(diagnosticChannel!=state.channel)return;
        // 身份阶段允许仅刷新broker的3个固定末状态；INPUT仅在用户诊断窗中发送。
        if(stage==LockDiagnosticCause.INPUT&&!state.active)return;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockInputBroker.SESSION);data.writeString(state.token);data.writeInt(stage);data.writeInt(cause);data.writeLong(total);
            if(state.channel.session.transact(11,data,reply,0)){reply.readException();reply.readInt();}
        }catch(Throwable ignored){ }
        finally{data.recycle();reply.recycle();}
    }
    private void flushNativeDiagnostics(DiagnosticState captured){
        if(!currentDiagnostic(captured))return;
        for(NativeDiagnosticCounters.Row row:nativeDiagnostics.pending(captured.token,16)){
            if(!currentDiagnostic(captured))break;
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(LockInputBroker.SESSION);data.writeString(captured.token);data.writeInt(LockDiagnosticCause.INPUT);data.writeInt(row.code);data.writeLong(row.total);
                if(!captured.channel.session.transact(11,data,reply,0))break;reply.readException();
                if(reply.readInt()!=0||reply.dataAvail()!=0)break;nativeDiagnostics.acknowledge(captured.token,row);
            }catch(Throwable ignored){break;}finally{data.recycle();reply.recycle();}
        }
    }
    private boolean currentDiagnostic(DiagnosticState captured){
        DiagnosticState current=diagnosticState;
        return captured!=null&&captured.active&&current!=null&&current.active
                &&current.channel==captured.channel&&current.token.equals(captured.token);
    }
    private boolean reportRoiCode(DiagnosticState captured,int code){
        if(!currentDiagnostic(captured)||diagnosticChannel!=captured.channel||!RoiDiagnosticDetail.valid(code))return false;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(LockInputBroker.SESSION);data.writeString(captured.token);data.writeInt(LockDiagnosticCause.INPUT);data.writeInt(code);data.writeLong(1);
            if(!captured.channel.session.transact(11,data,reply,0))return false;
            reply.readException();int result=reply.readInt();return result==0&&reply.dataAvail()==0;
        }catch(Throwable ignored){return false;}
        finally{data.recycle();reply.recycle();}
    }
    private void reportRoiDetails(LockSurfaceSnapshot snapshot,DiagnosticState captured){
        if(!currentDiagnostic(captured))return;
        synchronized(this){
            if(roiReport!=null){
                if(roiReport.captured.channel==captured.channel&&roiReport.captured.token.equals(captured.token))return;
                worker.removeCallbacks(roiReport);roiReport=null;roiBatch.clear();
            }
            int[] codes=roiBatch.take(captured.token,snapshot.roiDetails());if(codes==null)return;
            RoiReport task=new RoiReport(captured,codes);roiReport=task;
            if(!worker.post(task)){roiReport=null;roiBatch.clear();}
        }
    }
    /** 每1200ms至多24条，为原32/s准入留余量；最坏516行，后台有界分批。 */
    private final class RoiReport implements Runnable {
        final DiagnosticState captured;final int[] codes;int offset,retries;
        RoiReport(DiagnosticState captured,int[] codes){this.captured=captured;this.codes=codes;}
        @Override public void run(){
            boolean again=false;
            try{
                for(int sent=0;sent<24&&offset<=codes.length;sent++){
                    if(!currentDiagnostic(captured))break;
                    int code=offset==codes.length?RoiDiagnosticDetail.BATCH_COMPLETE:codes[offset];
                    if(!reportRoiCode(captured,code)){retries++;break;}
                    offset++;retries=0;
                }
                if(offset<=codes.length&&retries<3&&currentDiagnostic(captured))again=worker.postDelayed(this,1200);
            }finally{
                if(!again)synchronized(LockSystemUiObserver.this){if(roiReport==this){roiReport=null;if(offset<=codes.length)roiBatch.clear();}}
            }
        }
    }
    private void invalidate(Channel old){synchronized(statePublicationLock){if(channel==old){channel=null;state=null;stateObservations.invalidate();nativeOwner.revoke();long generation=nativeOwner.currentGeneration();main.post(()->{if(channel==null&&nativeOwner.currentGeneration()==generation){revokeNative();clearEdit();}});}}}
    private final Binder callback=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
            int uid=Binder.getCallingUid(),pid=Binder.getCallingPid();Channel live=channel;
            if((code!=1&&code!=2&&code!=3)||reply==null||data.dataSize()>1024||(flags&IBinder.FLAG_ONEWAY)!=0)return false;
            try{
                data.enforceInterface(LockProtocol.PRODUCER);
                if(code==3){
                    String epoch=data.readString(),actor=data.readString();long actorEpoch=data.readLong();int active=data.readInt();String token=data.readString();
                    if(data.dataAvail()!=0)throw new IllegalArgumentException("TRAILING_DATA");
                    Channel diag=diagnosticChannel;
                    if(uid!=1000||diag==null||pid!=diag.serverPid||!diag.serverEpoch.equals(epoch)||!diag.actorSession.equals(actor)||diag.actorEpoch!=actorEpoch||(active!=0&&active!=1)||!LockProtocol.token(token))throw new SecurityException();
                    synchronized(LockSystemUiObserver.this){
                        DiagnosticState previous=diagnosticState;diagnosticState=new DiagnosticState(diag,active==1,token);
                        nativeDiagnostics.session(token,active==1);
                        if(previous==null||!previous.token.equals(token)){diagnosticCount=0;lastDiagnosticCause=-1;counters.clear();}
                    }
                    reply.writeNoException();reply.writeInt(0);return true;
                }
                if(code==2){
                    String epoch=data.readString(),actor=data.readString();long actorEpoch=data.readLong();if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                    if(uid!=1000||live==null||pid!=live.serverPid||!live.serverEpoch.equals(epoch)||!live.actorSession.equals(actor)||live.actorEpoch!=actorEpoch)throw new SecurityException();
                    // 在Binder线程立即撤volatile准入；主线程排队延迟不能继续采集坐标。
                    invalidate(live);reply.writeNoException();reply.writeInt(0);return true;
                }
                String ssEpoch=data.readString(),actorSession=data.readString();long actorEpoch=data.readLong(),seq=data.readLong();
                PendingIntent intent=data.readTypedObject(PendingIntent.CREATOR);if (data.dataAvail() != 0) throw new IllegalArgumentException("TRAILING_DATA");
                if(uid!=1000||live==null||pid!=live.serverPid||!live.serverEpoch.equals(ssEpoch)||!live.actorSession.equals(actorSession)
                        ||live.actorEpoch!=actorEpoch||seq<=0||intent==null)throw new SecurityException();
                main.post(()->launchEdit(live,seq,intent));reply.writeNoException();reply.writeInt(0);return true;
            }catch(Throwable ignored){reply.writeNoException();reply.writeInt(2);return true;}
        }
    };
    private void launchEdit(Channel live,long seq,PendingIntent intent){
        nativeHit("NATIVE_EDIT_TICKET_READY");
        long now=SystemClock.elapsedRealtime();Context app=context;
        if(app==null||channel!=live||pendingEditChannel!=live||seq!=pendingEditSequence||pendingEditIssued<0
                ||now<pendingEditIssued||now-pendingEditIssued>5000||pendingEditRoot==null
                ||pendingEditRoot!=attachedRoot||!pendingEditRoot.isAttachedToWindow()||!identity.stillSameFile()){nativeHit("NATIVE_AUTH_ENTRY_REJECTED_STATE");return;}
        clearEdit();
        try{
            if(!intent.isActivity()||!intent.isImmutable()||!LockProtocol.HOST_PACKAGE.equals(intent.getCreatorPackage())){nativeHit("NATIVE_AUTH_ENTRY_REJECTED_CAPABILITY");return;}
            PackageManager pm=app.getPackageManager();PackageInfo pkg=pm.getPackageInfo(LockProtocol.HOST_PACKAGE,PackageManager.GET_SIGNING_CERTIFICATES);
            String[] packages=pm.getPackagesForUid(intent.getCreatorUid());
            if(pkg.applicationInfo==null||!pkg.applicationInfo.enabled||intent.getCreatorUid()<10000||pkg.applicationInfo.uid!=intent.getCreatorUid()||packages==null||packages.length!=1
                    ||!LockProtocol.HOST_PACKAGE.equals(packages[0])||pkg.signingInfo==null||pkg.signingInfo.hasMultipleSigners()
                    ||pkg.signingInfo.getApkContentsSigners().length!=1){nativeHit("NATIVE_AUTH_ENTRY_REJECTED_IDENTITY");return;}
            byte[] sha=MessageDigest.getInstance("SHA-256").digest(pkg.signingInfo.getApkContentsSigners()[0].toByteArray());
            StringBuilder hex=new StringBuilder();for(byte value:sha)hex.append(String.format(java.util.Locale.ROOT,"%02X",value&255));
            if(!LockProtocol.HOST_CERT.equals(hex.toString())){nativeHit("NATIVE_AUTH_ENTRY_REJECTED_IDENTITY");return;}
            ActivityOptions options=ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 34)
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            intent.send(app,0,null,null,null,null,options.toBundle());count("EDIT_AUTH_ENTRY_SENT");nativeHit("NATIVE_AUTH_ENTRY_SENT");
        }catch(Throwable ignored){count("EDIT_AUTH_ENTRY_UNAVAILABLE");nativeHit("NATIVE_AUTH_ENTRY_UNAVAILABLE");}
    }
    private static final class Channel{
        final IBinder session;final int serverPid;final String serverEpoch,actorSession;final long actorEpoch;
        Channel(IBinder session,int pid,String serverEpoch,long epoch,String actorSession){this.session=session;this.serverPid=pid;this.serverEpoch=serverEpoch;this.actorEpoch=epoch;this.actorSession=actorSession;}
    }
    private static final class HostState{
        final Channel channel;final boolean ready,forceLocal,wallpaperPriority,nativeInput,inputReady;final String ticket,reason;final long at;
        HostState(Channel channel,boolean ready,String ticket,long at,boolean forceLocal){this(channel,ready,ticket,at,forceLocal,false);}
        HostState(Channel channel,boolean ready,String ticket,long at,boolean forceLocal,boolean priority){this(channel,ready,ticket,at,forceLocal,priority,false);}
        HostState(Channel channel,boolean ready,String ticket,long at,boolean forceLocal,boolean priority,boolean nativeInput){this(channel,ready,ticket,at,forceLocal,priority,nativeInput,ready?"READY":"NOT_READY");}
        HostState(Channel channel,boolean ready,String ticket,long at,boolean forceLocal,boolean priority,boolean nativeInput,String reason){this.channel=channel;this.ready=ready;this.ticket=ticket;this.at=at;this.forceLocal=forceLocal;this.wallpaperPriority=priority;this.nativeInput=nativeInput;this.reason=reason;this.inputReady=ready||nativeInput&&priority&&LockProtocol.priorityInputOnly(reason)&&LockProtocol.sceneId(ticket)!=null;}
    }
    private static final class DiagnosticState{
        final Channel channel;final boolean active;final String token;
        DiagnosticState(Channel channel,boolean active,String token){this.channel=channel;this.active=active;this.token=token;}
    }
}
