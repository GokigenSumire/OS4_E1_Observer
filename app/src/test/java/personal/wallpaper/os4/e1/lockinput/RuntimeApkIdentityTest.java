package personal.wallpaper.os4.e1.lockinput;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/** 只测实际有界流hash实现，不以宿主流代替手机sourceDir/loader。 */
public final class RuntimeApkIdentityTest {
    @Test public void exactSha256MatchesKnownBytes()throws Exception{
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",RuntimeApkIdentity.sha256(new ByteArrayInputStream(new byte[]{97,98,99}),3));
    }
    @Test public void zeroByteSourceIsDeterministic()throws Exception{
        assertEquals("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",RuntimeApkIdentity.sha256(new ByteArrayInputStream(new byte[0]),0));
    }
    @Test(expected=IllegalArgumentException.class)public void truncationRejected()throws Exception{RuntimeApkIdentity.sha256(new ByteArrayInputStream(new byte[2]),3);}
    @Test(expected=IllegalArgumentException.class)public void extraContentRejected()throws Exception{RuntimeApkIdentity.sha256(new ByteArrayInputStream(new byte[4]),3);}
    @Test(expected=IOException.class)public void readFailureNotTreatedAsEmpty()throws Exception{
        RuntimeApkIdentity.sha256(new java.io.InputStream(){@Override public int read()throws IOException{throw new IOException();}},3);
    }
    @Test public void hashesAcrossBufferBoundary()throws Exception{
        byte[] input=new byte[65537];java.util.Arrays.fill(input,(byte)17);
        byte[] expected=java.security.MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder hex=new StringBuilder();for(byte b:expected)hex.append(String.format(java.util.Locale.ROOT,"%02X",b&255));
        assertEquals(hex.toString(),RuntimeApkIdentity.sha256(new ByteArrayInputStream(input),input.length));
    }
}
