package personal.wallpaper.os4.e1.lockinput;

import android.content.pm.ApplicationInfo;
import android.system.Os;
import android.system.StructStat;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 系统来源验证与接口兼容分层；摘要仅追溯，不是ROM放行名单。 */
public final class RuntimeApkIdentity {
    public static final String SYSTEM_UI = "com.android.systemui";
    private static final long MAX_SOURCE_BYTES=256L*1024*1024,MAX_TOTAL_BYTES=512L*1024*1024;
    private RuntimeApkIdentity() { }

    public static final class Result {
        public final boolean sourceMatched;
        public final boolean loaderMatched;
        public final String reason;
        /** 仅进程内使用，不导出路径。 */
        final String canonicalSource;
        final long device, inode, size, modified;
        final String[] sources;
        final StructStat[] sourceStats;
        public final String buildDigest;
        Result(boolean source, boolean loader, String reason, String path, StructStat stat) {
            this(source,loader,reason,path,stat,path==null?new String[0]:new String[]{path},stat==null?new StructStat[0]:new StructStat[]{stat},"");
        }
        private Result(boolean source,boolean loader,String reason,String path,StructStat stat,String[] sources,StructStat[] stats,String digest){
            this.sourceMatched = source; this.loaderMatched = loader; this.reason = reason;
            canonicalSource = path;
            device = stat == null ? -1 : stat.st_dev; inode = stat == null ? -1 : stat.st_ino;
            size = stat == null ? -1 : stat.st_size; modified = stat == null ? -1 : stat.st_mtime;
            this.sources=sources.clone();this.sourceStats=stats.clone();buildDigest=digest;
        }
        public boolean stillSameFile() {
            if (!sourceMatched || canonicalSource == null) return false;
            try {
                if(sources.length==0||sources.length!=sourceStats.length)return false;
                for(int i=0;i<sources.length;i++)if(!sameStat(sourceStats[i],Os.stat(sources[i])))return false;
                return true;
            } catch (Throwable ignored) { return false; }
        }
    }

    /** Broker核安装管理器当前组件，不信任调用方自报路径。 */
    public static Result verifyInstalled(android.content.Context context,ApplicationInfo info,Class<?> declaringClass){
        try{
            ApplicationInfo installed=installedComponent(context,info);
            if(installed==null)return fail("PACKAGE_OR_SPLITS_NOT_PINNED");
            return verify(installed,declaringClass);
        }catch(Throwable ignored){return fail("SOURCE_VERIFICATION_UNAVAILABLE");}
    }
    /** 轻量当前安装证明，不读APK文件；诊断及每次生产会话有效性都必须先过此关。 */
    static boolean matchesInstalledComponent(android.content.Context context,ApplicationInfo info){
        try{return installedComponent(context,info)!=null;}catch(Throwable ignored){return false;}
    }
    private static ApplicationInfo installedComponent(android.content.Context context,ApplicationInfo info)throws Exception{
        if(context==null||!trustedSystemComponent(info))return null;
        ApplicationInfo installed=context.getPackageManager().getApplicationInfo(SYSTEM_UI,0);
        if(!trustedSystemComponent(installed)||installed.uid!=info.uid
                ||!new File(installed.sourceDir).getCanonicalPath().equals(new File(info.sourceDir).getCanonicalPath())
                ||!java.util.Arrays.equals(installed.splitSourceDirs,info.splitSourceDirs))return null;
        return installed;
    }
    static boolean trustedSystemComponent(ApplicationInfo info){
        return info!=null&&SYSTEM_UI.equals(info.packageName)&&ProducerSessionGuard.supportedUid(info.uid)&&info.enabled
                &&(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0&&info.sourceDir!=null;
    }
    /** 框架载入元数据的后台来源初筛；不是安装身份证明，Broker另核PM及完整能力契约。 */
    public static Result verify(ApplicationInfo info, Class<?> declaringClass) {
        String source = null;
        StructStat before = null;
        try {
            if (!trustedSystemComponent(info))
                return fail("PACKAGE_OR_SPLITS_NOT_PINNED");
            String[] sources=sourcePaths(info);StructStat[] stats=new StructStat[sources.length];long total=0;
            StringBuilder digest=new StringBuilder();
            for(int index=0;index<sources.length;index++){
            String path=sources[index];StructStat stat=Os.stat(path);if(index==0){source=path;before=stat;}
            if(!android.system.OsConstants.S_ISREG(stat.st_mode)||stat.st_size<=0||stat.st_size>MAX_SOURCE_BYTES||(total+=stat.st_size)>MAX_TOTAL_BYTES)return fail("SOURCE_SIZE_MISMATCH");
            try(FileInputStream stream=new FileInputStream(path)){digest.append(sha256(stream,stat.st_size));}
            int dexCount=0,memberCount=0;
            try (ZipFile zip = new ZipFile(path)) {
                var entries = zip.entries();
                Set<String> names = new HashSet<>();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if(++memberCount>32768||!names.add(entry.getName()))return fail("DUPLICATE_APK_MEMBER");
                    if(entry.getName().matches("classes[0-9]*\\.dex")){dexCount++;if(dexCount>64||entry.getSize()<1||entry.getSize()>128L*1024*1024)return fail("DEX_MEMBER_OUT_OF_BOUNDS");}
                }
            }
            if(index==0&&dexCount==0)return fail("DEX_SET_CHANGED");
            stats[index]=Os.stat(path);if(!sameStat(stat,stats[index]))return fail("SOURCE_CHANGED_WHILE_READING");
            }
            for(int i=0;i<sources.length;i++)if(!sameStat(stats[i],Os.stat(sources[i])))return fail("SOURCE_CHANGED_WHILE_READING");
            boolean loader=declaringClass!=null&&loaderMatches(declaringClass.getClassLoader(),sources);
            return new Result(true,loader,loader?"DECLARING_LOADER_SOURCE_MATCHED":"DECLARING_LOADER_NOT_MATCHED",source,stats[0],sources,stats,digest.toString());
        } catch (Throwable ignored) {
            return new Result(false, false, "SOURCE_VERIFICATION_UNAVAILABLE", source, before);
        }
    }

    static String[] sourcePaths(ApplicationInfo info)throws Exception{
        java.util.ArrayList<String> paths=new java.util.ArrayList<>();paths.add(new File(info.sourceDir).getCanonicalPath());
        if(info.splitSourceDirs!=null)for(String split:info.splitSourceDirs){if(split==null||paths.size()>=16)throw new SecurityException();String path=new File(split).getCanonicalPath();if(paths.contains(path))throw new SecurityException();paths.add(path);}
        return paths.toArray(new String[0]);
    }
    /** 只接受当前系统组件的base/split，不接受猜测插件或in-memory来源。 */
    static boolean loaderMatches(ClassLoader loader, String expected) throws Exception {
        return loaderMatches(loader,new String[]{expected});
    }
    static boolean loaderMatches(ClassLoader loader,String[] expected)throws Exception{
        if (loader == null) return false;
        Class<?> base = Class.forName("dalvik.system.BaseDexClassLoader", false, loader);
        if (!base.isInstance(loader)) return false;
        Object pathList = field(base, "pathList").get(loader);
        Object elements = field(pathList.getClass(), "dexElements").get(pathList);
        if (elements == null || Array.getLength(elements) == 0 || Array.getLength(elements) > 16) return false;
        boolean matched = false;
        for (int i = 0; i < Array.getLength(elements); i++) {
            Object element = Array.get(elements, i);
            Object dexFile = field(element.getClass(), "dexFile").get(element);
            if (dexFile == null) return false;
            Method getName = dexFile.getClass().getMethod("getName");
            Object name = getName.invoke(dexFile);
            if (!(name instanceof String) || !java.util.Arrays.asList(expected).contains(new File((String) name).getCanonicalPath())) return false;
            matched = true;
        }
        return matched;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    static boolean sameStat(StructStat a, StructStat b) {
        return a.st_dev == b.st_dev && a.st_ino == b.st_ino && a.st_size == b.st_size && a.st_mtime == b.st_mtime;
    }
    private static Result fail(String reason) { return new Result(false, false, reason, null, null); }
    static String sha256(java.io.InputStream input, long exactBytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = new byte[32768]; long total = 0; int count;
        while ((count = input.read(bytes)) != -1) {
            total += count; if (total > exactBytes) throw new IllegalArgumentException("SOURCE_EXCEEDED_BUDGET");
            digest.update(bytes, 0, count);
        }
        if (total != exactBytes) throw new IllegalArgumentException("SOURCE_TRUNCATED");
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        return result.toString();
    }
}
