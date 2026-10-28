package personal.wallpaper.os4.e1.relay;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Binder;
import personal.wallpaper.protocol.lock.LockProtocol;
import java.security.MessageDigest;

/** 当前签名集合精确核定；不将历史证书、debug包或shared UID成员当正式对端。 */
final class LockCallerAuth {
    private LockCallerAuth() { }
    static boolean host(Context context, int uid, int pid) {
        if (pid <= 0) return false;
        long identity = Binder.clearCallingIdentity();
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(LockProtocol.HOST_PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
            ApplicationInfo app = info.applicationInfo;
            SigningInfo signing = info.signingInfo;
            if (app == null || signing == null) return false;
            Signature[] signatures = signing.getApkContentsSigners();
            if (signatures == null) return false;
            String[] digests = new String[signatures.length];
            for (int i = 0; i < signatures.length; i++) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray());
                StringBuilder hex = new StringBuilder(64);
                for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
                digests[i] = hex.toString();
            }
            return LockProtocol.exactIdentity(LockProtocol.HOST_PACKAGE, LockProtocol.HOST_CERT,
                    uid, app.uid, pm.getPackagesForUid(uid), digests, app.enabled, signing.hasMultipleSigners());
        } catch (Exception ignored) { return false; }
        finally { Binder.restoreCallingIdentity(identity); }
    }
}
