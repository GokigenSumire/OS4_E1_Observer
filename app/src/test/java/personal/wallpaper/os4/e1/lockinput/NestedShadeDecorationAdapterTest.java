package personal.wallpaper.os4.e1.lockinput;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.statusbar.notification.stack.ui.view.SharedNotificationContainer;
import com.miui.systemui.shade.ShadeBackgroundView;
import com.miui.systemui.shade.header.ShadeHeaderClipController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 真实Android嵌套布局与生产foreground递归；同名小fixture不执行OEM或声明手机因果。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class NestedShadeDecorationAdapterTest {
    private static final int CONTAINER=0x7f051101,SHADE=0x7f051102;
    private Activity activity;
    private Context context;
    private NotificationShadeWindowView root;
    private SharedNotificationContainer container;
    private ShadeBackgroundView shade;
    @Before public void setUp(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();Resources original=activity.getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){
                if("id".equals(type)&&"com.android.systemui".equals(pkg)){
                    if("shared_notification_container".equals(name))return CONTAINER;
                    if("shade_background".equals(name))return SHADE;
                }
                return super.getIdentifier(name,type,pkg);
            }
        };
        context=new ContextWrapper(activity){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);activity.setContentView(root);
        container=new SharedNotificationContainer(context);container.setId(CONTAINER);
        shade=new ShadeBackgroundView(context);shade.setId(SHADE);
        root.addView(container,new FrameLayout.LayoutParams(-1,-1));container.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        container.injector.shadeBackground=shade;layout();
    }
    private void layout(){root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);container.layout(0,0,1000,2000);shade.layout(0,0,1000,2000);}
    private boolean ignored(){return IdleDecoration.mayIgnore(root,shade);}
    @Test public void injectedIdleNestedBackgroundIsNotARealNotification(){assertTrue(ignored());}
    @Test public void idleKnownDrawHelperUsesOnlySuperDraw(){container.injector.helper=new ShadeHeaderClipController();assertTrue(ignored());}
    @Test public void drawingClipHelperMustStillBeExcluded(){ShadeHeaderClipController helper=new ShadeHeaderClipController();helper.clipRatio=new ShadeHeaderClipController.Flow(.1f);container.injector.helper=helper;assertFalse(ignored());}
    @Test public void unknownHelperCannotBeTrusted(){container.injector.helper=new Object();assertFalse(ignored());}
    @Test public void unknownRatioTypeCannotBeTrusted(){ShadeHeaderClipController helper=new ShadeHeaderClipController();helper.clipRatio=new ShadeHeaderClipController.Flow("0");container.injector.helper=helper;assertFalse(ignored());}
    @Test public void nanRatioCannotBeTrusted(){ShadeHeaderClipController helper=new ShadeHeaderClipController();helper.clipRatio=new ShadeHeaderClipController.Flow(Float.NaN);container.injector.helper=helper;assertFalse(ignored());}
    @Test public void injectorMustOwnExactBackgroundObject(){container.injector.shadeBackground=new ShadeBackgroundView(context);assertFalse(ignored());}
    @Test public void injectorMustOwnExactContainerObject(){container.injector.container=new SharedNotificationContainer(context);assertFalse(ignored());}
    @Test public void exactContainerResourceIdentityRequired(){container.setId(CONTAINER+100);assertFalse(ignored());}
    @Test public void ordinaryNestedFrameWithSameIdNotTrusted(){container.removeView(shade);FrameLayout ordinary=new FrameLayout(context);ordinary.setId(CONTAINER);root.removeView(container);root.addView(ordinary);ordinary.addView(shade);layout();assertFalse(ignored());}
    @Test public void childInteractionStillBlocks(){shade.setClickable(true);assertFalse(ignored());}
    @Test public void parentExtraBackgroundStillBlocks(){container.setBackground(new ColorDrawable(Color.RED));layout();assertFalse(ignored());}
    @Test public void parentInteractionStillBlocks(){container.setLongClickable(true);assertFalse(ignored());}
    @Test public void extraChildForegroundStillBlocks(){shade.setForeground(new ColorDrawable(Color.RED));layout();assertFalse(ignored());}
    @Test public void globalDuplicateShadeIdCannotStealObjectIdentity(){ShadeBackgroundView another=new ShadeBackgroundView(context);another.setId(SHADE);root.addView(another,0);layout();assertTrue(ignored());}
    @Test public void productionForegroundSkipsOnlyDecorationAndKeepsRealNotificationRect()throws Exception{
        View notification=new View(context);container.addView(notification,new FrameLayout.LayoutParams(200,100));layout();notification.layout(20,30,220,130);
        ArrayList<Rect> excluded=new ArrayList<>();
        Method walk=LockSurfaceSnapshot.class.getDeclaredMethod("foreground",View.class,View.class,View.class,java.util.List.class,int[].class);walk.setAccessible(true);
        assertTrue(root.isShown());assertTrue(shade.isShown());
        assertTrue((Boolean)walk.invoke(null,root,container,root,excluded,new int[]{0}));
        assertEquals(1,excluded.size());Rect rect=new Rect();assertTrue(notification.getGlobalVisibleRect(rect));assertEquals(rect,excluded.get(0));
        assertFalse(excluded.contains(new Rect(0,0,1000,2000)));
    }
}
