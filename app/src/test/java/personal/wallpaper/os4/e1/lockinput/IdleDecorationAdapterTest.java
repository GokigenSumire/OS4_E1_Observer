package personal.wallpaper.os4.e1.lockinput;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.animation.AlphaAnimation;
import com.android.systemui.shade.NotificationShadeWindowView;
import com.android.systemui.scrim.ScrimView;
import com.android.systemui.biometrics.AuthRippleView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 真Android View/Drawable/Animator适配，不是只测纯枚举；OEM构造和布局仍需目标机。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
public final class IdleDecorationAdapterTest {
    private static final int FRONT=0x7f041101,RIPPLE=0x7f041102,OTHER=0x7f041103;
    private Context context;
    private NotificationShadeWindowView root;
    @Before public void setUp(){
        Context base=RuntimeEnvironment.getApplication();Resources original=base.getResources();
        Resources resources=new Resources(original.getAssets(),original.getDisplayMetrics(),original.getConfiguration()){
            @Override public int getIdentifier(String name,String type,String pkg){
                if("id".equals(type)&&"com.android.systemui".equals(pkg)){
                    if("scrim_in_front".equals(name))return FRONT;if("auth_ripple".equals(name))return RIPPLE;
                }
                return super.getIdentifier(name,type,pkg);
            }
        };
        context=new ContextWrapper(base){@Override public Resources getResources(){return resources;}};
        root=new NotificationShadeWindowView(context);
    }
    private <T extends View>T add(T child,int id){root.addView(child);child.setId(id);layout();return child;}
    private void layout(){root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,2000);for(int i=0;i<root.getChildCount();i++)root.getChildAt(i).layout(0,0,1000,2000);}
    private ScrimView idle(){ScrimView view=add(new ScrimView(context),FRONT);view.setVisualAlpha(0f);return view;}
    @Test public void transparentFrontScrimWithViewAlphaOneIsProvedIdle(){ScrimView view=idle();assertEquals(View.VISIBLE,view.getVisibility());assertEquals(1f,view.getAlpha(),0f);assertEquals(0,view.mDrawable.getAlpha());assertTrue(IdleDecoration.mayIgnore(root,view));}
    @Test public void nonzeroScrimDrawableStillBlocks(){ScrimView view=idle();view.mDrawable.setAlpha(255);assertEquals(255,view.mDrawable.getAlpha());assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void nonzeroScrimVisualAlphaStillBlocks(){ScrimView view=idle();view.mViewAlpha=.1f;assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void nanVisualAlphaCannotBeTrusted(){ScrimView view=idle();view.mViewAlpha=Float.NaN;assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void unknownDrawableCannotBeTrusted(){ScrimView view=idle();view.mDrawable=null;assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void sameClassWrongIdDoesNotGainExemption(){ScrimView view=idle();view.setId(OTHER);assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void sameIdUnknownClassDoesNotGainExemption(){View view=add(new View(context),FRONT);view.setAlpha(0f);assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void unrelatedNestedScrimDoesNotGainExemption(){ScrimView view=idle();root.removeView(view);android.widget.FrameLayout nested=new android.widget.FrameLayout(context);root.addView(nested);nested.addView(view);layout();assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void clickOwnershipBlocksEvenWhenNotDrawn(){ScrimView view=idle();view.setClickable(true);assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void longClickOwnershipBlocksEvenWhenNotDrawn(){ScrimView view=idle();view.setLongClickable(true);assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void listenerBlocksEvenIfClickableManuallyCleared(){ScrimView view=idle();view.setOnClickListener(v->{});view.setClickable(false);assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void extraBackgroundBlocks(){ScrimView view=idle();view.setBackground(new ColorDrawable(Color.RED));layout();assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void extraForegroundBlocks(){ScrimView view=idle();view.setForeground(new ColorDrawable(Color.RED));layout();assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void viewAnimationBlocks(){ScrimView view=idle();view.setAnimation(new AlphaAnimation(0,1));assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void layoutPendingBlocks(){ScrimView view=idle();view.requestLayout();assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void idleRippleFlagsAndNoAnimatorsCanBeProved(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);view.setVisibility(View.VISIBLE);layout();assertTrue(IdleDecoration.mayIgnore(root,view));}
    @Test public void activeDwellBlocks(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);view.drawDwell=true;assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void activeRippleBlocks(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);view.drawRipple=true;assertFalse(IdleDecoration.mayIgnore(root,view));}
    @Test public void runningRippleAnimatorBlocks(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);animator.setDuration(5000);animator.start();view.unlockedRippleAnimator=animator;assertFalse(IdleDecoration.mayIgnore(root,view));animator.cancel();}
    @Test public void stoppedKnownAnimatorDoesNotInventDrawing(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);view.fadeDwellAnimator=ValueAnimator.ofFloat(0f,1f);assertTrue(IdleDecoration.mayIgnore(root,view));}
    @Test public void runningDwellPulseAnimatorBlocks(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);ValueAnimator animator=ValueAnimator.ofFloat(0,1);animator.setDuration(5000);animator.start();view.dwellPulseOutAnimator=animator;assertFalse(IdleDecoration.mayIgnore(root,view));animator.cancel();}
    @Test public void runningFadeDwellAnimatorBlocks(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);ValueAnimator animator=ValueAnimator.ofFloat(0,1);animator.setDuration(5000);animator.start();view.fadeDwellAnimator=animator;assertFalse(IdleDecoration.mayIgnore(root,view));animator.cancel();}
    @Test public void runningRetractDwellAnimatorBlocks(){AuthRippleView view=add(new AuthRippleView(context),RIPPLE);ValueAnimator animator=ValueAnimator.ofFloat(0,1);animator.setDuration(5000);animator.start();view.retractDwellAnimator=animator;assertFalse(IdleDecoration.mayIgnore(root,view));animator.cancel();}
}
