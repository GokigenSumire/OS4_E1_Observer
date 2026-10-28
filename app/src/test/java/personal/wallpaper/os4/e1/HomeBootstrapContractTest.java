package personal.wallpaper.os4.e1;
import org.junit.Test;
import static org.junit.Assert.*;
public final class HomeBootstrapContractTest {
    private String[] signature(){return new String[]{"android.content.Context","com.android.server.input.InputManagerService","boolean","com.android.server.policy.WindowManagerPolicy","com.android.server.wm.ActivityTaskManagerService","com.android.server.wm.DisplayWindowSettingsProvider","java.util.function.Supplier","java.util.function.Supplier","com.android.server.wm.AppCompatConfiguration"};}
    @Test public void knownCurrentConfigurationAccepted(){assertTrue(HomeBootstrapContract.supports(signature()));}
    @Test public void unverifiedOlderTailIsNotInventedAsSupported(){var names=signature();names[8]="com.android.server.wm.LetterboxConfiguration";assertFalse(HomeBootstrapContract.supports(names));}
    @Test public void eachMandatoryRoleTypeRequired(){for(int i=0;i<9;i++){var names=signature();names[i]="java.lang.Object";assertFalse(HomeBootstrapContract.supports(names));}}
    @Test public void missingAndExtraArgumentsAreNotGuessed(){assertFalse(HomeBootstrapContract.supports(null));assertFalse(HomeBootstrapContract.supports(new String[8]));assertFalse(HomeBootstrapContract.supports(new String[10]));}
    @Test(expected=NoSuchMethodException.class)public void unknownConstructorNotHooked()throws Exception{HomeBootstrapContract.select(Object.class);}
}
