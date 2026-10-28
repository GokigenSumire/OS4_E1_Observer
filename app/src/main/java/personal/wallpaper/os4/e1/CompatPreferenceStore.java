package personal.wallpaper.os4.e1;

import android.content.Context;
import android.content.SharedPreferences;

/** 用户持久意愿只存模块自己的设备保护沙箱，不让 system_server 读取或写入文件。 */
final class CompatPreferenceStore {
    static final class State {
        final boolean enabled;
        final boolean available;
        final long revision;
        final boolean writeUnconfirmed;
        State(boolean enabled, boolean available, long revision) {
            this(enabled, available, revision, false);
        }
        State(boolean enabled, boolean available, long revision, boolean writeUnconfirmed) {
            this.enabled = enabled;
            this.available = available;
            this.revision = revision;
            this.writeUnconfirmed = writeUnconfirmed;
        }
    }

    interface Storage {
        boolean enabled();
        long revision();
        boolean commit(boolean enabled, long revision);
    }

    /** SharedPreferences commit(false)也可能更新内存；新Activity不能把同进程缓存当磁盘确认。 */
    static final class Session {
        private boolean unconfirmed;
        State read(Storage storage) {
            State value=CompatPreferenceStore.read(storage);
            return unconfirmed ? new State(value.enabled, false, value.revision, true) : value;
        }
        boolean setEnabled(Storage storage, boolean enabled) {
            boolean saved=CompatPreferenceStore.setEnabled(storage, enabled);
            unconfirmed=!saved;
            return saved;
        }
        void failedWrite() { unconfirmed=true; }
        State unavailable() { return new State(false,false,-1,unconfirmed); }
    }
    private static final Session SESSION=new Session();

    private CompatPreferenceStore() { }

    private static Storage storage(Context context) {
        SharedPreferences prefs = context.createDeviceProtectedStorageContext()
                .getSharedPreferences("home_compat_permanent", Context.MODE_PRIVATE);
        return new Storage() {
            public boolean enabled() { return prefs.getBoolean("enabled", false); }
            public long revision() { return prefs.getLong("revision", 0); }
            public boolean commit(boolean enabled, long revision) {
                return prefs.edit().putBoolean("enabled", enabled).putLong("revision", revision).commit();
            }
        };
    }

    static synchronized State read(Context context) {
        try { return SESSION.read(storage(context)); }
        catch (RuntimeException ignored) { return SESSION.unavailable(); }
    }

    static synchronized boolean setEnabled(Context context, boolean enabled) {
        try { return SESSION.setEnabled(storage(context), enabled); }
        catch (RuntimeException ignored) { SESSION.failedWrite(); return false; }
    }

    static State read(Storage storage) {
        try {
            long revision = storage.revision();
            if (revision < 0) return new State(false, false, -1);
            return new State(storage.enabled(), true, revision);
        } catch (RuntimeException ignored) { return new State(false, false, -1); }
    }

    static boolean setEnabled(Storage storage, boolean enabled) {
        State old = read(storage);
        if (!old.available || old.revision == Long.MAX_VALUE) return false;
        try { return storage.commit(enabled, old.revision + 1); }
        catch (RuntimeException ignored) { return false; }
    }
}
