package personal.wallpaper.os4.e1.relay;

import android.content.SharedPreferences;

/** 只选择LOCK输入路线；默认native，不开启LOCK总开关。回退保原force/priority意愿。 */
public final class NativeInputSetting {
    static final String ENABLED="native_business_input_enabled";
    private static final DurableSwitchGate gate=new DurableSwitchGate();
    public static final class State {
        public final boolean available,enabled;public final long revision;
        State(boolean available,boolean enabled,long revision){this.available=available;this.enabled=enabled;this.revision=revision;}
    }
    private NativeInputSetting(){ }
    static synchronized State read(SharedPreferences preferences){
        try{long revision=preferences.getLong(ForceLocalSetting.REVISION,0);boolean enabled=preferences.getBoolean(ENABLED,true);
            return new State(gate.confirmed()&&revision>=0,gate.allows(enabled)&&revision>=0,revision);
        }catch(RuntimeException ignored){return new State(false,false,-1);}
    }
    static synchronized boolean write(SharedPreferences preferences,boolean enabled){
        gate.beginWrite();
        try{long previous=preferences.getLong(ForceLocalSetting.REVISION,0);if(previous<0||previous==Long.MAX_VALUE){gate.finishedWrite(false);return false;}
            boolean saved=preferences.edit().putBoolean(ENABLED,enabled).putLong(ForceLocalSetting.REVISION,previous+1).commit();gate.finishedWrite(saved);return saved;
        }catch(RuntimeException ignored){gate.finishedWrite(false);return false;}
    }
}
