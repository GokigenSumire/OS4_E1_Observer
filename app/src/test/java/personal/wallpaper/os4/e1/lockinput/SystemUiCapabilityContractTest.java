package personal.wallpaper.os4.e1.lockinput;

import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** 按OEM精确方法及字段外壳选族；不把宿主选择当手机或安全区域验证。 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,manifest=Config.NONE)
public final class SystemUiCapabilityContractTest {
    private final ClassLoader loader=com.android.keyguard.injector.KeyguardPanelViewInjector.class.getClassLoader();
    @Test public void completeKnownFamilySelectedWithoutApkHashOrDeviceModel(){assertTrue(SystemUiCapabilityContract.inspect(loader,type->true).supported);}
    @Test public void everyRequiredMethodIsNecessary(){for(String[] spec:SystemUiCapabilityContract.METHODS){SystemUiCapabilityContract.Result result=SystemUiCapabilityContract.inspect(name->{if(name.equals(spec[0]))throw new ClassNotFoundException();return Class.forName(name,false,loader);},loader);assertFalse(spec[0]+"."+spec[1],result.supported);}}
    @Test public void sameMethodNameWrongReturnTypeDoesNotBecomeCompatible(){assertEquals("NATIVE_CAPABILITY_SIGNATURE_REJECTED",SystemUiCapabilityContract.inspect(name->name.equals("com.android.systemui.shade.NotificationShadeWindowView")?WrongRoot.class:Class.forName(name,false,loader),loader).reason);}
    @Test public void staticCallbackCannotBorrowInstanceOwnership(){assertFalse(SystemUiCapabilityContract.inspect(name->name.equals("com.android.systemui.shade.NotificationShadeWindowView")?StaticRoot.class:Class.forName(name,false,loader),loader).supported);}
    @Test public void primitiveFieldInsteadOfReferenceRejected(){assertEquals("NATIVE_CAPABILITY_FIELD_REJECTED",SystemUiCapabilityContract.inspect(name->name.equals("com.android.systemui.shade.NotificationShadeWindowViewController$1")?WrongOwner.class:Class.forName(name,false,loader),loader).reason);}
    @Test public void missingBusinessStateRejected(){assertEquals("NATIVE_CAPABILITY_FIELD_REJECTED",SystemUiCapabilityContract.inspect(name->name.equals("com.android.systemui.shade.NotificationShadeWindowViewController")?MissingWindow.class:Class.forName(name,false,loader),loader).reason);}
    @Test public void sourceVerifierRejectionNeverInstallsActionHooks(){NativeSystemUiHooksTest.Recorder callback=new NativeSystemUiHooksTest.Recorder();NativeSystemUiHooks hooks=NativeSystemUiHooks.installWithContract(loader,type->false,callback);assertFalse(hooks.actionHooksReady());assertEquals(0,callback.begin);assertTrue(callback.reports.contains("NATIVE_CAPABILITY_SOURCE_REJECTED"));}
    @Test public void completeContractInstallsOnlyExistingBusinessRoute()throws Exception{NativeSystemUiHooksTest.Recorder callback=new NativeSystemUiHooksTest.Recorder();NativeSystemUiHooks hooks=NativeSystemUiHooks.installWithContract(loader,type->true,callback);assertTrue(hooks.actionHooksReady());assertTrue(callback.reports.contains("NATIVE_HOOK_CONTRACT_V1_READY"));java.lang.reflect.Field field=NativeSystemUiHooks.class.getDeclaredField("handles");field.setAccessible(true);for(Object item:(java.util.List<?>)field.get(hooks))((de.robv.android.xposed.XC_MethodHook.Unhook)item).unhook();}
    @Test public void newSourceStageCannotBeForgedAsInput(){int code=LockDiagnosticCause.code("SOURCE_INSTALLED_COMPONENT_VERIFIED");assertTrue(LockDiagnosticCause.validPair(LockDiagnosticCause.SOURCE,code));assertFalse(LockDiagnosticCause.validPair(LockDiagnosticCause.INPUT,code));}
    public static final class WrongRoot{public int dispatchTouchEvent(MotionEvent event){return 0;}}
    public static final class StaticRoot{public static boolean dispatchTouchEvent(MotionEvent event){return false;}}
    public static final class WrongOwner{public int this$0;}
    public static final class MissingWindow{public void cancelCurrentTouch$1(){}}
}
