package personal.wallpaper.os4.e1.lockinput;
import android.util.Log;
import de.robv.android.xposed.XposedBridge;
/** 仅固定白名单日志字段；调用方禁止传坐标、路径、异常正文或能力。 */
final class LockLog {
    private LockLog(){ }
    static void fixed(String value){
        deliver(()->XposedBridge.log(value),()->Log.i("HWC_LOCK",value));
    }
    static void deliver(Runnable framework,Runnable android){
        try{framework.run();}catch(Throwable ignored){ }
        try{android.run();}catch(Throwable ignored){ }
    }
}
