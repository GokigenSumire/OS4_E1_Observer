package personal.wallpaper.os4.e1.lockinput;

import android.app.Activity;
import android.view.View;
import java.lang.reflect.Method;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** 测试通过真实ViewRoot生命周期建立可见窗口，不伪造可见性getter。 */
final class RoiTestWindow {
    static void show(ActivityController<Activity> controller)throws Exception{
        controller.visible();Method accessor=View.class.getDeclaredMethod("getViewRootImpl");accessor.setAccessible(true);
        Object viewRoot=accessor.invoke(controller.get().getWindow().getDecorView());assertNotNull(viewRoot);
        Method visible=viewRoot.getClass().getDeclaredMethod("dispatchAppVisibility",boolean.class);visible.setAccessible(true);visible.invoke(viewRoot,true);
        ShadowLooper.idleMainLooper();controller.windowFocusChanged(true);ShadowLooper.idleMainLooper();
    }
}
