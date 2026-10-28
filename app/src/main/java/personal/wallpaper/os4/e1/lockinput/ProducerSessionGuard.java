package personal.wallpaper.os4.e1.lockinput;

/** Binder之外的纯结构／代次判定；任何UID均不单独构成安装身份，Broker另核当前系统组件。 */
public final class ProducerSessionGuard {
    public static final String SYSTEM_UI = "com.android.systemui";
    public static final class Identity {
        public final int pid, uid;
        public final long startSequence;
        public final String processName, packageName;
        public final Object record;
        public Identity(int pid, int uid, long startSequence, String processName, String packageName, Object record) {
            this.pid=pid; this.uid=uid; this.startSequence=startSequence;
            this.processName=processName; this.packageName=packageName; this.record=record;
        }
    }
    private final Identity admitted;
    private final int componentUid;
    private boolean alive = true;
    public ProducerSessionGuard(Identity identity) {
        this(identity,1000);
    }
    public ProducerSessionGuard(Identity identity,int installedComponentUid) {
        if (!isExactSystemUi(identity,installedComponentUid)) throw new IllegalArgumentException("SYSTEMUI_PROCESS_REQUIRED");
        admitted = identity;
        componentUid=installedComponentUid;
    }
    public static boolean isExactSystemUi(Identity value) {
        return isExactSystemUi(value,1000);
    }
    public static boolean isExactSystemUi(Identity value,int installedComponentUid) {
        return value != null && value.pid > 0 && supportedUid(value.uid)&&value.uid==installedComponentUid && value.startSequence > 0
                && value.record != null && SYSTEM_UI.equals(value.processName) && SYSTEM_UI.equals(value.packageName);
    }
    /** 当前只支持user0的系统共享或普通安装UID；拒root/shell/隔离/沙箱及其他用户。 */
    static boolean supportedUid(int uid){return uid==1000||uid>=10000&&uid<=19999;}
    public synchronized boolean accepts(int kernelPid, int kernelUid, Identity current) {
        return alive && isExactSystemUi(current,componentUid) && kernelPid == admitted.pid && kernelUid == admitted.uid
                && current.pid == admitted.pid && current.uid == admitted.uid
                && current.startSequence == admitted.startSequence && current.record == admitted.record;
    }
    public synchronized void revoke() { alive=false; }
}
