package personal.wallpaper.os4.e1;

import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Map;
import personal.wallpaper.os4.e1.core.ObservationBuffer;
import personal.wallpaper.os4.e1.core.HomeScope;
import personal.wallpaper.os4.e1.core.SessionEpoch;
import personal.wallpaper.os4.e1.core.ContrastController;
import personal.wallpaper.os4.e1.core.SessionPolicy;
import personal.wallpaper.os4.e1.core.DailyController;

/** 限定 HOME getter 的临时对照，不写原字段；默认基线，恢复必须另有自然调用证据。 */
final class HomeObserver {
    private final ClassLoader loader;
    private final ObservationBuffer buffer;
    private final SessionPolicy contrast;
    private final boolean dailyMode;
    private long recoveryCompleted=-1;
    private String targetStatus="NOT_VERIFIED";
    private long recoveryStarted=-1;
    private String recoveryStatus="NOT_VERIFIED";
    private int inFlight,taintedInFlight;
    private long recoveryGeneration;
    private long experimentStarted;
    static final long RECOVERY_DURATION_MS=20_000L;
    private final List<XC_MethodHook.Unhook> handles = new ArrayList<>();
    private final List<String> availability = new ArrayList<>();
    private final personal.wallpaper.os4.e1.core.HomeForwardingEvidence forwardingEvidence=new personal.wallpaper.os4.e1.core.HomeForwardingEvidence();
    private final ThreadLocal<Frame> frame = new ThreadLocal<>();
    private final ThreadLocal<Request> request = new ThreadLocal<>();
    private volatile Object service;
    private Object home, attrs, wrapper;
    private volatile int generation;
    private final SessionEpoch sessions=new SessionEpoch();
    private boolean uniqueVerified;
    private volatile String unhookStatus = "UNHOOKED";
    private static final class Frame {
        Object window; Object handle; Frame previous; long epoch; long contrastEpoch;
        String phase; Boolean target,keyguard,original,hasRequested;String entryTouch;
        boolean tainted,recoveryAfterTainted,closed,hasCompleted,hasThrew;long recoveryGeneration;
    }
    private static final class Request { String source; Request previous; boolean associated; long epoch; String arrivalTouch="UNKNOWN"; Object incoming; }
    HomeObserver(ClassLoader loader, ObservationBuffer buffer) { this(loader,buffer,false); }
    HomeObserver(ClassLoader loader, ObservationBuffer buffer,boolean dailyMode) {
        this.loader=loader;this.buffer=buffer;this.dailyMode=dailyMode;this.contrast=dailyMode?new DailyController():new ContrastController();
    }
    void setService(Object service) { this.service = service; }
    synchronized boolean resetIdentity(long now) {
        if(hasHooks()||contrast.start(now,"BASELINE")==0)return false;
        sessions.advance(); home = attrs = wrapper = null; generation = 0; uniqueVerified=false; availability.clear();forwardingEvidence.reset();
        recoveryStarted=-1;recoveryCompleted=-1;recoveryStatus="PENDING";inFlight=taintedInFlight=0;recoveryGeneration=0;experimentStarted=now;targetStatus="NOT_VERIFIED";return true;
    }
    synchronized boolean enable(long now) {boolean ok=(dailyMode||buffer.active(now))&&contrast.enable(now);if(ok)emit("PHASE_CHANGE",wrapper,Map.of());return ok;}
    synchronized void recover(long now,String reason) {
        if(List.of("ERROR","IDENTITY_CHANGED","AMBIGUOUS_HOME").contains(reason))recoveryGeneration++;
        contrast.beginRecovery(now,reason);if("RECOVERY".equals(contrast.mode())){if(recoveryStarted<0)recoveryStarted=permanentDaily()?now:Math.min(now,saturatingDeadline());recoveryStatus="RECOVERY_PENDING";}
    }
    synchronized boolean running() {return !"DISARMED".equals(contrast.mode());}
    synchronized boolean recovering() {return "RECOVERY".equals(contrast.mode());}
    synchronized boolean canStart() {return !running()&&!hasHooks()&&!contrast.needsRecovery();}
    synchronized String contrastStatus(long now) {return contrast.status(now)+" recoveryStatus="+recoveryStatus+" inFlight="+inFlight+" taintedInFlight="+taintedInFlight+" sessionStartMonoMs="+experimentStarted+" recoveryStartMonoMs="+recoveryStarted+" recoveryEndMonoMs="+recoveryCompleted+" targetWallpaper="+targetStatus+" homeForwarding="+forwardingEvidence.status()+" nativeInput=NOT_OBSERVED";}
    synchronized boolean tick(long now) {
        contrast.status(now);
        if(!dailyMode&&running()&&!buffer.active(now))recover(now,"LIMIT_ROWS");
        if("RECOVERY".equals(contrast.mode())){
            if(recoveryStarted<0)recoveryStarted=permanentDaily()?now:Math.min(now,saturatingDeadline());
            if((contrast.recoveryProved()&&inFlight==0&&taintedInFlight==0)||!recoveryWindow(now)){
                if(inFlight!=0||taintedInFlight!=0)recover(now,"ERROR");
                String end=contrast.finalizeRecovery(now,contrast.epoch());recoveryStatus="FINAL_CONFIRMED".equals(end)?"RECOVERY_CONFIRMED":"RECOVERY_UNCONFIRMED";
                recoveryCompleted=now;
                emit("RECOVERY_RESULT",wrapper,Map.of("recovery",contrast.recoveryProved()?"CONFIRMED":"UNCONFIRMED"));buffer.stop("RECOVERY_COMPLETE");detach();
            }
        }
        return !running();
    }
    private long saturatingDeadline(){long limit=contrast.durationLimitMillis();return experimentStarted>Long.MAX_VALUE-limit?Long.MAX_VALUE:experimentStarted+limit;}
    private boolean permanentDaily(){return dailyMode&&contrast instanceof DailyController&&((DailyController)contrast).isPermanent();}
    private boolean recoveryWindow(long now) {return permanentDaily()?HomeScope.permanentRecoveryWindow(now,experimentStarted,recoveryStarted):dailyMode?HomeScope.dailyRecoveryWindow(now,experimentStarted,recoveryStarted,contrast.durationLimitMillis()):HomeScope.recoveryWindow(now,experimentStarted,recoveryStarted);}
    private void close(Frame f) {if(f!=null&&f.window!=null&&!f.closed){f.closed=true;if(f.tainted)taintedInFlight--;inFlight--;}}
    synchronized void mark(String tag) {if(tag!=null&&List.of("DOUBLE","TRIPLE","TWO","LOCK","UNLOCK","SWIPE","ICON","EDITOR","PREVIEW").contains(tag))emit("USER_MARK",wrapper,Map.of("mark",tag));}
    long currentEpoch() { return sessions.current(); }
    synchronized boolean hasHooks() { return !handles.isEmpty(); }
    String hookStatus() { return unhookStatus; }
    synchronized String coverage() { return String.join("\n", availability); }

    private Object field(Object obj, String name) { return obj == null ? null : XposedHelpers.getObjectField(obj, name); }
    private String scalar(Object obj) { return obj instanceof Boolean || obj instanceof Number ? String.valueOf(obj) : "UNKNOWN"; }
    private String read(Object obj, String name) { try { return scalar(field(obj, name)); } catch (Throwable ignored) { return "UNKNOWN"; } }
    private String touch(Object layout) { return read(layout, "mWallpaperTouchEventsEnabled"); }
    private boolean eligible(Object window) {
        try {
            Object layout = field(window, "mAttrs");
            if (!"com.miui.home".equals(field(layout, "packageName")) || !Integer.valueOf(1).equals(field(layout, "type"))) return false;
            Object activity = field(window, "mActivityRecord");
            return activity != null && HomeScope.allowed(field(layout,"packageName"),field(layout,"type"),XposedHelpers.callMethod(window,"getDisplayId"),field(activity,"packageName"),XposedHelpers.callMethod(activity,"getActivityType"));
        } catch (Throwable ignored) { return false; }
    }
    private synchronized boolean bind(Object window, Object handle) {
        if (!eligible(window)) return false;
        // populate原调用已持WM锁；不在Observer锁内等待WM锁。
        Object lock=field(service,"mGlobalLock");if(lock==null||!Thread.holdsLock(lock))return false;
        Object windows=field(service,"mWindowMap");if(!(windows instanceof Map<?,?> map))return false;
        Object found=null;int checked=0;
        for(Object w:map.values()) {if(++checked>256){recover(SystemClock.elapsedRealtime(),"IDENTITY_CHANGED");return false;}if(eligible(w)){if(found!=null&&found!=w){recover(SystemClock.elapsedRealtime(),"AMBIGUOUS_HOME");return false;}found=w;}}
        if(found!=window)return false;
        if (home != null && (home != window||attrs!=field(window,"mAttrs"))) { recover(SystemClock.elapsedRealtime(),"IDENTITY_CHANGED"); return false; }
        Object actualHandle=field(window,"mInputWindowHandle");if(handle!=null&&handle!=actualHandle){recover(SystemClock.elapsedRealtime(),"IDENTITY_CHANGED");return false;}
        uniqueVerified=true;
        home = window; attrs = field(window, "mAttrs");
        wrapper = handle != null ? handle : field(window, "mInputWindowHandle");
        generation = 1; return true;
    }
    private synchronized boolean matched(Object object) { return object != null && object == wrapper; }
    /** 只调用已核原ROM纯读取方法，且当前线程须已持WM锁。未知／不匹配不覆盖。 */
    private boolean targetWallpaperAllowed(Object window){
        try{
            Object lock=field(service,"mGlobalLock");if(lock==null||!Thread.holdsLock(lock)){targetStatus="UNKNOWN";return false;}
            Object display=field(window,"mDisplayContent");Object controller=field(display,"mWallpaperController");
            if(field(display,"mCurrentFocus")!=window){targetStatus="NOT_FOCUSED_HOME";return false;}
            if(field(controller,"mWallpaperTarget")!=window){targetStatus="NOT_TARGET";return false;}
            Object token=XposedHelpers.callMethod(controller,"getTokenForTarget",window);
            Object visible=XposedHelpers.callMethod(controller,"getTopVisibleWallpaper");
            if(token==null||visible==null){targetStatus="NOT_VISIBLE";return false;}
            Object layout=field(visible,"mAttrs");Object visibleToken=field(visible,"mToken");
            int count=((Number)XposedHelpers.callMethod(token,"getChildCount")).intValue();
            if(count<1||count>16){targetStatus="UNKNOWN";return false;}
            int matches=0;for(int i=0;i<count;i++){Object child=XposedHelpers.callMethod(token,"getChildAt",i);if(child==visible)matches++;}
            boolean allowed=HomeScope.targetWallpaperAllowed(field(layout,"packageName"),field(layout,"type"),XposedHelpers.callMethod(visible,"getDisplayId"),matches==1,visibleToken==token,BuildConfig.WALLPAPER_APPLICATION_ID);
            targetStatus=allowed?"PROJECT_WALLPAPER":"OTHER_WALLPAPER";return allowed;
        }catch(Throwable ignored){targetStatus="UNKNOWN";return false;}
    }
    private Map<String,String> values(Object handle) {
        Map<String,String> data = new LinkedHashMap<>();
        data.put("touch", touch(attrs)); data.put("homeUnique", uniqueVerified?"true":"UNKNOWN");
        Object input = null;
        try { input = field(handle, "mHandle"); } catch (Throwable ignored) { }
        String config = read(input, "inputConfig"); data.put("inputConfig", config);
        try { data.put("dup", String.valueOf((Integer.parseInt(config) & 32) != 0)); }
        catch (RuntimeException ignored) { data.put("dup", "UNKNOWN"); }
        data.put("changed", read(handle, "mChanged"));
        data.put("phase",contrast.mode());
        return data;
    }
    private void emit(String point, Object handle, Map<String,String> extra) {
        if (!buffer.active(SystemClock.elapsedRealtime())) return;
        Map<String,String> data = values(handle); data.putAll(extra);
        buffer.record(SystemClock.elapsedRealtime(), point, generation, data);
        if(!dailyMode&&!buffer.active(SystemClock.elapsedRealtime())&&running())recover(SystemClock.elapsedRealtime(),"LIMIT_ROWS");
    }
    private Map<String,String> one(String key, String value) { return Map.of(key, value); }

    synchronized void install() {
        if (!handles.isEmpty()) return;
        unhookStatus = "HOOKED";
        hook("POPULATE", "com.android.server.wm.InputMonitor", "populateInputWindowHandle", "void", new String[]{"com.android.server.wm.InputWindowHandleWrapper","com.android.server.wm.WindowState"}, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Frame current = new Frame(); current.epoch=sessions.current();current.contrastEpoch=contrast.epoch();current.phase=contrast.mode();current.previous = frame.get(); frame.set(current);
                if (bind(p.args[1], p.args[0])) { current.window=p.args[1]; current.handle=p.args[0];current.entryTouch=touch(attrs);inFlight++;current.recoveryGeneration=recoveryGeneration;current.recoveryAfterTainted="RECOVERY".equals(current.phase)&&taintedInFlight==0; emit("POPULATE_BEFORE",current.handle,Map.of()); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Frame current=frame.get();
                boolean canProve=false;boolean dupFalse=false;boolean fieldIntact=false;
                try { if(current!=null && current.window!=null) {
                    emit("POPULATE_AFTER",current.handle,one("threw",String.valueOf(p.hasThrowable())));
                    // 恢复的是本次自然值：原true的系统不得误判为字段被修改或要求清DUP。
                    dupFalse=current.original!=null&&String.valueOf(current.original).equals(values(current.handle).get("dup"));
                    // OEM先判断target/keyguard，可能根本不调用getter。未观察不等于字段被改；
                    // 仍核进入/退出原字段，若观察到getter还必须与原字段相符。
                    fieldIntact=("true".equals(current.entryTouch)||"false".equals(current.entryTouch))
                            &&current.entryTouch.equals(touch(attrs))
                            &&(current.original==null||String.valueOf(current.original).equals(current.entryTouch));
                    if(!fieldIntact)contrast.noteFieldViolation(SystemClock.elapsedRealtime(),current.contrastEpoch);
                    boolean natural=current.original!=null&&current.original.equals(current.hasRequested);
                    if("RECOVERY".equals(contrast.mode())&&(p.hasThrowable()||current.hasThrew||!fieldIntact
                            ||current.original!=null&&(!dupFalse||!natural)))recover(SystemClock.elapsedRealtime(),"ERROR");
                    canProve=current.recoveryGeneration==recoveryGeneration&&current.recoveryAfterTainted&&!current.tainted&&Boolean.TRUE.equals(current.target)&&Boolean.FALSE.equals(current.keyguard)&&natural&&current.hasCompleted&&!current.hasThrew&&!p.hasThrowable();
                } }
                finally { close(current);if(current!=null&&frame.get()==current){if(current.previous==null)frame.remove();else frame.set(current.previous);} }
                long now=SystemClock.elapsedRealtime();
                if(canProve&&inFlight==0&&taintedInFlight==0&&recoveryWindow(now))contrast.observeRecovery(now,current.contrastEpoch,true,dupFalse,fieldIntact);
            }
        });
        hook("TARGET", "com.android.server.wm.WallpaperController", "isWallpaperTarget", "boolean", new String[]{"com.android.server.wm.WindowState"}, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { Frame f=frame.get(); if(f!=null&&f.window!=null&&f.window==p.args[0]) {f.target=!p.hasThrowable()&&p.getResult() instanceof Boolean?(Boolean)p.getResult():null;if(f.target==null)recover(SystemClock.elapsedRealtime(),"ERROR");emit("TARGET_RESULT",f.handle,one("target",scalar(f.target)));} }
        });
        hookPolicy(new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { Frame f=frame.get(); if(f!=null&&f.window!=null&&p.thisObject==field(service,"mPolicy")) {f.keyguard=!p.hasThrowable()&&p.getResult() instanceof Boolean?(Boolean)p.getResult():null;if(f.keyguard==null)recover(SystemClock.elapsedRealtime(),"ERROR");emit("KEYGUARD_RESULT",f.handle,one("keyguard",scalar(f.keyguard)));} }
        });
        hook("TOUCH_GETTER", "android.view.WindowManager$LayoutParams", "areWallpaperTouchEventsEnabled", "boolean", new String[]{}, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Frame f=frame.get();if(f==null||f.window==null||p.thisObject!=attrs)return;
                Object original=p.hasThrowable()?null:p.getResult();f.original=original instanceof Boolean?(Boolean)original:null;
                if(f.original==null)recover(SystemClock.elapsedRealtime(),"ERROR");
                emit("BOOL_GETTER",f.handle,Map.of("requested",scalar(original),"original",scalar(original),"effective",scalar(original)));
                long now=SystemClock.elapsedRealtime();
                boolean exact=f.epoch==sessions.current()&&f.window==home&&f.handle==wrapper&&attrs==field(f.window,"mAttrs")&&eligible(f.window);
                if(!exact){recover(now,"IDENTITY_CHANGED");return;}
                forwardingEvidence.observe(original,exact&&Boolean.TRUE.equals(f.target)&&Boolean.FALSE.equals(f.keyguard));
                // 已核同一HOME只是失焦：本次保留原值，不把正常进入应用转成恢复锁。
                // 其他壁纸、未知反射、丢WM锁仍撤权；不把未知状态解释为安全暂停。
                if(dailyMode&&"ENABLE".equals(contrast.mode())&&!targetWallpaperAllowed(f.window)){
                    if(contrast.appliedCount()>0&&!"NOT_FOCUSED_HOME".equals(targetStatus))recover(now,"IDENTITY_CHANGED");
                    return;
                }
                if((dailyMode||buffer.active(now))&&contrast.permit(now,f.contrastEpoch,uniqueVerified,exact,true,Boolean.TRUE.equals(f.target),!Boolean.FALSE.equals(f.keyguard),Boolean.FALSE.equals(original)&&!p.hasThrowable())){
                    boolean changedHere=false;
                    try{
                        changedHere=true;p.setResult(true);
                        if(!f.tainted){f.tainted=true;taintedInFlight++;}
                        if(!contrast.overrideApplied(now,f.contrastEpoch))throw new IllegalStateException("OVERRIDE_REVOKED");
                        emit("OVERRIDE",f.handle,Map.of("original","false","effective","true","applied",String.valueOf(contrast.appliedCount())));
                    }catch(Throwable ignored){
                        if(changedHere)p.setResult(original);
                        recover(SystemClock.elapsedRealtime(),"ERROR");
                    }
                }
            }
        });
        hook("HAS_WALLPAPER", "com.android.server.wm.InputWindowHandleWrapper", "setHasWallpaper", "void", new String[]{"boolean"}, pair("HAS", "requested", 0));
        hook("MASK", "com.android.server.wm.InputWindowHandleWrapper", "setInputConfigMasked", "boolean", new String[]{"int","int"}, pair("MASK", "value", 0));
        hook("PUBLISH", "com.android.server.wm.InputMonitor", "setInputWindowInfoIfNeeded", "void", new String[]{"android.view.SurfaceControl$Transaction","android.view.SurfaceControl","com.android.server.wm.InputWindowHandleWrapper"}, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if(matched(p.args[2])) emit("PUBLISH_BEFORE",p.args[2],one("submitted","JAVA_CALL")); }
            @Override protected void afterHookedMethod(MethodHookParam p) { if(matched(p.args[2])) emit("PUBLISH_AFTER",p.args[2],one("threw",String.valueOf(p.hasThrowable()))); }
        });
        hook("APPLY_SURFACE", "com.android.server.wm.InputWindowHandleWrapper", "applyChangesToSurface", "void", new String[]{"android.view.SurfaceControl$Transaction","android.view.SurfaceControl"}, pair("SURFACE_SET", null, -1));
        hook("COPY", "android.view.WindowManager$LayoutParams", "copyFrom", "int", new String[]{"android.view.WindowManager$LayoutParams"}, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if(p.thisObject==attrs && attrs!=null) {
                    Request r=request.get(); if(r!=null && r.incoming!=p.args[0])r=null; if(r!=null) r.associated=true;
                    emit("COPY_BEFORE",wrapper,Map.of("arrivalTouch",r==null?"UNKNOWN":r.arrivalTouch,"sourceTouch",touch(p.args[0]),"destTouch",touch(p.thisObject),"submitted",r==null?"OTHER":r.source));
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) { if(p.thisObject==attrs&&attrs!=null) emit("COPY_AFTER",wrapper,Map.of("destTouch",touch(p.thisObject),"copyChanges",p.hasThrowable()?"UNKNOWN":scalar(p.getResult()),"threw",String.valueOf(p.hasThrowable()))); }
        });
        hookWmsRequests("relayoutWindow", "WMS_RELAYOUT");
        hookWmsRequests("addWindow", "WMS_ADD");
        for(String required:List.of("POPULATE","TARGET","KEYGUARD","TOUCH_GETTER","HAS_WALLPAPER"))if(!availability.contains(required+"=HOOK_AVAILABLE")){forwardingEvidence.unavailable();recover(SystemClock.elapsedRealtime(),"ERROR");}
    }
    private XC_MethodHook pair(String point, String key, int index) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if(!matched(p.thisObject)) return;
                Map<String,String> v=new LinkedHashMap<>();
                if(key!=null) v.put(key,scalar(p.args[index]));
                if("MASK".equals(point)) v.put("mask",scalar(p.args[1]));
                if("HAS".equals(point)){Frame f=frame.get();if(f!=null&&f.window!=null&&f.handle==p.thisObject)f.hasRequested=(Boolean)p.args[0];}
                emit(point+"_BEFORE",p.thisObject,v);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) { if(matched(p.thisObject)) {
                if("HAS".equals(point)){Frame f=frame.get();if(f!=null&&f.window!=null&&f.handle==p.thisObject){f.hasCompleted=true;f.hasThrew=p.hasThrowable();}}
                emit(point+"_AFTER",p.thisObject,one("threw",String.valueOf(p.hasThrowable())));
            } }
        };
    }
    private Class<?> type(String name) throws ClassNotFoundException {
        return switch(name) { case "int" -> int.class; case "boolean" -> boolean.class; case "void" -> void.class; default -> Class.forName(name,false,loader); };
    }
    private void hookPolicy(XC_MethodHook callback) {
        try {
            Object policy=field(service,"mPolicy");
            Method method=policy.getClass().getMethod("isKeyguardShowing");
            if(method.getReturnType()!=boolean.class)throw new NoSuchMethodException();
            method.setAccessible(true);handles.add(XposedBridge.hookMethod(method,guard(callback)));
            availability.add("KEYGUARD=HOOK_AVAILABLE");
        }catch(Throwable ignored){availability.add("KEYGUARD=HOOK_MISSING");}
    }
    private void hook(String label,String className,String name,String result,String[] args,XC_MethodHook callback) {
        try {
            Class<?> clazz=type(className); Class<?>[] params=new Class<?>[args.length];
            for(int i=0;i<args.length;i++) params[i]=type(args[i]);
            Method method=clazz.getDeclaredMethod(name,params);
            if(method.getReturnType()!=type(result)) throw new NoSuchMethodException();
            method.setAccessible(true);
            handles.add(XposedBridge.hookMethod(method,guard(callback)));
            availability.add(label+"=HOOK_AVAILABLE");
        } catch(Throwable ignored) { availability.add(label+"=HOOK_MISSING"); }
    }
    // 精确已核重载；不hook所有WMS方法。输入不是HOME时仅内存嵌套上下文，无日志。
    private void hookWmsRequests(String method, String source) {
        String[] sig= "relayoutWindow".equals(method) ? new String[]{"com.android.server.wm.Session","android.view.IWindow","android.view.WindowManager$LayoutParams","int","int","int","int","int","int","android.view.WindowRelayoutResult","android.view.SurfaceControl"}
                : new String[]{"com.android.server.wm.Session","android.view.IWindow","android.view.WindowManager$LayoutParams","int","int","int","int","android.view.InputChannel","android.view.WindowRelayoutResult"};
        hook(source,"com.android.server.wm.WindowManagerService",method,"int",sig,new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Request r=new Request();r.epoch=sessions.current();r.previous=request.get();r.source=source;
                Object incoming=p.args[2];r.incoming=incoming;
                if(incoming!=null&&"com.miui.home".equals(field(incoming,"packageName"))&&Integer.valueOf(1).equals(field(incoming,"type")))r.arrivalTouch=touch(incoming);
                request.set(r);
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Request r=request.get();
                try { if(r!=null&&r.associated) emit(source+"_AFTER",wrapper,one("returnCode",p.hasThrowable()?"UNKNOWN":scalar(p.getResult()))); }
                finally { if(r==null||r.previous==null) request.remove();else request.set(r.previous); }
            }
        });
    }
    // 只有上面的精确getter回调可写一次result；其他回调与系统参数均只读。
    private XC_MethodHook guard(XC_MethodHook inner) throws ReflectiveOperationException {
        final long callbackEpoch = sessions.current();
        final class Ticket {boolean entered;Frame ownedFrame;Request ownedRequest;}
        final ThreadLocal<ArrayDeque<Ticket>> tickets = ThreadLocal.withInitial(ArrayDeque::new);
        Method before=null;
        try { before=inner.getClass().getDeclaredMethod("beforeHookedMethod",XC_MethodHook.MethodHookParam.class); }
        catch(NoSuchMethodException ignored) { }
        Method after=null;
        try { after=inner.getClass().getDeclaredMethod("afterHookedMethod",XC_MethodHook.MethodHookParam.class); }
        catch(NoSuchMethodException ignored) { }
        final Method beforeMethod=before; if(beforeMethod!=null) beforeMethod.setAccessible(true);
        final Method afterMethod=after; if(afterMethod!=null) afterMethod.setAccessible(true);
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                synchronized(HomeObserver.this) {
                    Frame f=frame.get();if(f!=null&&!sessions.accepts(f.epoch))frame.remove();
                    Request r=request.get();if(r!=null&&!sessions.accepts(r.epoch))request.remove();
                    boolean valid=sessions.accepts(callbackEpoch)&&running();
                    if(valid)contrast.status(SystemClock.elapsedRealtime());
                    Ticket ticket=new Ticket();ticket.entered=valid;tickets.get().push(ticket);Frame priorFrame=frame.get();Request priorRequest=request.get();
                    try { if(valid&&beforeMethod!=null) beforeMethod.invoke(inner,p); }
                    catch(Throwable ignored) { if(sessions.accepts(callbackEpoch))recover(SystemClock.elapsedRealtime(),"ERROR"); }
                    finally {if(frame.get()!=priorFrame)ticket.ownedFrame=frame.get();if(request.get()!=priorRequest)ticket.ownedRequest=request.get();}
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                synchronized(HomeObserver.this) {
                    ArrayDeque<Ticket> stack=tickets.get();Ticket ticket=stack.isEmpty()?null:stack.pop();if(stack.isEmpty())tickets.remove();
                    try {
                        if(ticket!=null&&ticket.entered&&sessions.accepts(callbackEpoch)&&running()) { if(afterMethod!=null) afterMethod.invoke(inner,p); }
                    } catch(Throwable ignored) { if(sessions.accepts(callbackEpoch))recover(SystemClock.elapsedRealtime(),"ERROR"); }
                    finally {
                        if(ticket!=null&&ticket.ownedFrame!=null&&frame.get()==ticket.ownedFrame){if(sessions.accepts(callbackEpoch))close(ticket.ownedFrame);if(ticket.ownedFrame.previous==null)frame.remove();else frame.set(ticket.ownedFrame.previous);}
                        if(ticket!=null&&ticket.ownedRequest!=null&&request.get()==ticket.ownedRequest){if(ticket.ownedRequest.previous==null)request.remove();else request.set(ticket.ownedRequest.previous);}
                    }
                }
            }
        };
    }
    void snapshot(long expectedEpoch) {
        try {
            Object wms=service; if(wms==null) return;
            Object lock=field(wms,"mGlobalLock");
            Map<String,String> captured=null;
            synchronized(lock) {
                if(!sessions.accepts(expectedEpoch)||!running())return;
                Object map=field(wms,"mWindowMap");
                if(!(map instanceof Map<?,?> windows)) return;
                Object found=null; int checked=0;
                for(Object candidate:windows.values()) {
                    if(++checked>256) { stopIfCurrent(expectedEpoch,"ERROR");return; }
                    if(eligible(candidate)) { if(found!=null&&found!=candidate) { stopIfCurrent(expectedEpoch,"AMBIGUOUS_HOME");return; } found=candidate; }
                }
                if(found!=null) {
                    synchronized(this) {
                        if(!sessions.accepts(expectedEpoch)||!running())return;
                        uniqueVerified=true;
                        if(!bind(found,null))return;
                    Object display=field(found,"mDisplayContent");
                    Object controller=field(display,"mWallpaperController");
                    Object target=field(controller,"mWallpaperTarget");
                        captured=values(wrapper);
                        captured.putAll(Map.of("target",String.valueOf(target==found),"focus",String.valueOf(field(display,"mCurrentFocus")==found),"hasSurface",read(found,"mHasSurface"),"keyguard","UNKNOWN"));
                    }
                }
            }
            synchronized(this) {
                if(sessions.accepts(expectedEpoch)&&buffer.active(SystemClock.elapsedRealtime())&&captured!=null)
                    buffer.record(SystemClock.elapsedRealtime(),"SNAPSHOT",generation,captured);
            }
        } catch(Throwable ignored) { synchronized(this) { if(sessions.accepts(expectedEpoch)) availability.add("SNAPSHOT=HOOK_MISSING"); } }
    }
    private synchronized void stopIfCurrent(long expectedEpoch,String reason) { if(sessions.accepts(expectedEpoch))recover(SystemClock.elapsedRealtime(),reason); }
    synchronized void detach() {
        if(running())return;
        List<XC_MethodHook.Unhook> failed=new ArrayList<>();
        for(XC_MethodHook.Unhook hook:handles) { try { hook.unhook(); } catch(Throwable ignored) { failed.add(hook); } }
        handles.clear();handles.addAll(failed);
        unhookStatus=failed.isEmpty()?"UNHOOKED":"UNHOOK_FAILED";
        home=attrs=wrapper=null;uniqueVerified=false;
    }
}
