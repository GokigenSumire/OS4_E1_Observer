package personal.wallpaper.os4.e1;

import java.lang.reflect.Constructor;
import java.util.Arrays;

/** 已核WMS构造业务角色：只选唯一完整签名，不Hook所有构造或猜参数下标。 */
final class HomeBootstrapContract {
    private static final String[] PREFIX={"android.content.Context","com.android.server.input.InputManagerService","boolean","com.android.server.policy.WindowManagerPolicy","com.android.server.wm.ActivityTaskManagerService","com.android.server.wm.DisplayWindowSettingsProvider","java.util.function.Supplier","java.util.function.Supplier"};
    static boolean supports(String[] names){
        if(names==null||names.length!=9)return false;
        for(int i=0;i<PREFIX.length;i++)if(!PREFIX[i].equals(names[i]))return false;
        return "com.android.server.wm.AppCompatConfiguration".equals(names[8]);
    }
    static Constructor<?> select(Class<?> type)throws ReflectiveOperationException{
        Constructor<?> found=null;
        for(Constructor<?> constructor:type.getDeclaredConstructors())if(supports(Arrays.stream(constructor.getParameterTypes()).map(Class::getName).toArray(String[]::new))){if(found!=null)throw new NoSuchMethodException("AMBIGUOUS_WMS_CONTRACT");found=constructor;}
        if(found==null)throw new NoSuchMethodException("WMS_CONTRACT_UNAVAILABLE");found.setAccessible(true);return found;
    }
    private HomeBootstrapContract(){}
}
