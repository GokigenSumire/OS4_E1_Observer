package personal.wallpaper.os4.e1.relay;

import personal.wallpaper.os4.e1.lockinput.NativeInputDetail;
import personal.wallpaper.protocol.lock.LockProtocol;

/** Fixed host-result slots in one user diagnostic window; contains no request IDs or content. */
final class HostOutcomeTotals {
    private final long[] totals=new long[10];
    private String token="";
    synchronized long next(String capture,int code){
        int index=code-NativeInputDetail.code("NATIVE_HOST_COMMITTED");
        if(!LockProtocol.token(capture)||index<0||index>=totals.length)return 0;
        if(!token.equals(capture)){java.util.Arrays.fill(totals,0);token=capture;}
        if(totals[index]<Long.MAX_VALUE)totals[index]++;
        return totals[index];
    }
    synchronized void stopped(){token="";java.util.Arrays.fill(totals,0);}
}
