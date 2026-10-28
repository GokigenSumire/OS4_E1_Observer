package personal.wallpaper.os4.e1.lockinput;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;
import static org.junit.Assert.*;

/** 真文件／ZIP／stat和模拟安装管理器；假DEX不执行，不冒OEM代码或加载器已验证。 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34,manifest=Config.NONE)
public final class InstalledSystemUiSourceTest {
    @Test public void installedDedicatedSystemUiUidIsNotMistakenForAnUntrustedApplication()throws Exception{
        var installed=source((byte)3);installed.uid=10225;
        PackageInfo pkg=new PackageInfo();pkg.packageName=installed.packageName;pkg.applicationInfo=installed;
        ((ShadowPackageManager)Shadow.extract(RuntimeEnvironment.getApplication().getPackageManager())).installPackage(pkg);
        var result=RuntimeApkIdentity.verifyInstalled(RuntimeEnvironment.getApplication(),installed,null);
        assertTrue(result.reason,result.sourceMatched);assertTrue(result.stillSameFile());assertFalse(result.loaderMatched);
        var wrong=new ApplicationInfo(installed);wrong.uid=1000;
        assertFalse(RuntimeApkIdentity.verifyInstalled(RuntimeEnvironment.getApplication(),wrong,null).sourceMatched);
    }
    private ApplicationInfo source(byte value)throws Exception{
        File file=File.createTempFile("system-source-", ".apk",RuntimeEnvironment.getApplication().getCacheDir());
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){zip.putNextEntry(new ZipEntry("classes.dex"));zip.write(new byte[]{value,2,3});zip.closeEntry();}
        ApplicationInfo info=new ApplicationInfo();info.packageName=RuntimeApkIdentity.SYSTEM_UI;info.uid=1000;info.enabled=true;info.flags=ApplicationInfo.FLAG_SYSTEM;info.sourceDir=file.getCanonicalPath();return info;
    }
    @Test public void twoDifferentBuildDigestsAreNotAnIdentityAllowlist()throws Exception{
        var a=RuntimeApkIdentity.verify(source((byte)1),null);var b=RuntimeApkIdentity.verify(source((byte)9),null);
        assertTrue(a.reason,a.sourceMatched);assertTrue(b.reason,b.sourceMatched);assertNotEquals(a.buildDigest,b.buildDigest);assertTrue(a.stillSameFile());assertTrue(b.stillSameFile());assertFalse(a.loaderMatched);assertFalse(b.loaderMatched);
    }
    @Test public void ordinaryApplicationCannotClaimSystemUiByName()throws Exception{var info=source((byte)1);info.flags=0;assertFalse(RuntimeApkIdentity.verify(info,null).sourceMatched);}
    @Test public void systemFlagWithoutSystemUidDoesNotGrantIdentity()throws Exception{var info=source((byte)1);info.uid=12345;assertFalse(RuntimeApkIdentity.verifyInstalled(RuntimeEnvironment.getApplication(),info,null).sourceMatched);}
    @Test public void disabledSystemComponentRejected()throws Exception{var info=source((byte)1);info.enabled=false;assertFalse(RuntimeApkIdentity.verify(info,null).sourceMatched);}
    @Test public void installationPathMismatchCannotBorrowSystemFlags()throws Exception{var installed=source((byte)1);PackageInfo pkg=new PackageInfo();pkg.packageName=installed.packageName;pkg.applicationInfo=installed;((ShadowPackageManager)Shadow.extract(RuntimeEnvironment.getApplication().getPackageManager())).installPackage(pkg);var fake=source((byte)1);assertFalse(RuntimeApkIdentity.verifyInstalled(RuntimeEnvironment.getApplication(),fake,null).sourceMatched);assertTrue(RuntimeApkIdentity.verifyInstalled(RuntimeEnvironment.getApplication(),installed,null).sourceMatched);}
    @Test public void sourceModificationRevokesPriorObservation()throws Exception{var info=source((byte)1);var result=RuntimeApkIdentity.verify(info,null);assertTrue(result.sourceMatched);try(FileOutputStream stream=new FileOutputStream(info.sourceDir,true)){stream.write(7);}assertFalse(result.stillSameFile());}
    @Test public void duplicateOrUnboundedSplitSetRejected()throws Exception{var info=source((byte)1);info.splitSourceDirs=new String[]{info.sourceDir};assertFalse(RuntimeApkIdentity.verify(info,null).sourceMatched);info.splitSourceDirs=new String[17];assertFalse(RuntimeApkIdentity.verify(info,null).sourceMatched);}
    @Test public void splitMutationAlsoRevokesBaseObservation()throws Exception{var info=source((byte)1);var split=source((byte)2);info.splitSourceDirs=new String[]{split.sourceDir};var result=RuntimeApkIdentity.verify(info,null);assertTrue(result.sourceMatched);try(FileOutputStream stream=new FileOutputStream(split.sourceDir,true)){stream.write(6);}assertFalse(result.stillSameFile());}
    @Test public void unrecognizedLoaderNeverGetsSourceAuthority()throws Exception{var info=source((byte)1);assertFalse(RuntimeApkIdentity.loaderMatches(getClass().getClassLoader(),new String[]{info.sourceDir}));}
}
