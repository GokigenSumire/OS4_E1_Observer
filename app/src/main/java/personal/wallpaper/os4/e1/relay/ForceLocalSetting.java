package personal.wallpaper.os4.e1.relay;

import android.content.SharedPreferences;

/** 私有强制模式意愿；revision避免开关来回导致旧任务借用新授权。 */
public final class ForceLocalSetting {
    static final String ENABLED="force_local_enabled", REVISION="force_local_revision";
    private static final DurableSwitchGate gate=new DurableSwitchGate();
    public static final class State {
        public final boolean available,enabled;
        public final long revision;
        State(boolean available,boolean enabled,long revision){this.available=available;this.enabled=enabled;this.revision=revision;}
    }
    private ForceLocalSetting(){ }
    static synchronized State read(SharedPreferences preferences){
        try{
            long revision=preferences.getLong(REVISION,0);
            boolean enabled=preferences.getBoolean(ENABLED,false);
            return new State(gate.confirmed()&&revision>=0,gate.allows(enabled)&&revision>=0,revision);
        }catch(RuntimeException ignored){return new State(false,false,-1);}
    }
    static synchronized boolean write(SharedPreferences preferences,boolean enabled){
        gate.beginWrite();
        try{
            long previous=preferences.getLong(REVISION,0);
            if(previous<0||previous==Long.MAX_VALUE){gate.finishedWrite(false);return false;}
            boolean saved=preferences.edit().putBoolean(ENABLED,enabled).putLong(REVISION,previous+1).commit();
            gate.finishedWrite(saved);return saved;
        }catch(RuntimeException ignored){gate.finishedWrite(false);return false;}
    }
}
