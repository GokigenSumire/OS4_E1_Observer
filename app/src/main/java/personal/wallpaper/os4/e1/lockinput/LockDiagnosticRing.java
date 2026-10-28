package personal.wallpaper.os4.e1.lockinput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 独立LOCK固定标量内存记录；无文件、坐标、原始触摸、真实精细时间或系统副作用。 */
public final class LockDiagnosticRing {
    public static final long MAX_DURATION_MS=200_000L;
    public static final int MAX_ROWS=2000,MAX_BYTES=131072,MAX_LINE_BYTES=128,PAGE_ROWS=8;
    public static final int NOT_STARTED=0,RUNNING=1,USER_STOP=2,TIMEOUT=3,LIMIT_ROWS=4,LIMIT_BYTES=5,REVOKED=6;
    public static final class State {
        public final String token;public final long revision;public final boolean active;public final int reason,rows,bytes;
        State(String token,long revision,boolean active,int reason,int rows,int bytes){this.token=token;this.revision=revision;this.active=active;this.reason=reason;this.rows=rows;this.bytes=bytes;}
    }
    private final int rowLimit,byteLimit;private final long duration;
    private final ArrayList<String> rows=new ArrayList<>();
    private String token="NONE";private long revision;private long start;private boolean active;private int reason=NOT_STARTED,bytes;
    public LockDiagnosticRing(){this(MAX_ROWS,MAX_BYTES,MAX_DURATION_MS);}
    LockDiagnosticRing(int maxRows,int maxBytes,long maxDuration){if(maxRows<1||maxRows>MAX_ROWS||maxBytes<1||maxBytes>MAX_BYTES||maxDuration<1||maxDuration>MAX_DURATION_MS)throw new IllegalArgumentException();rowLimit=maxRows;byteLimit=maxBytes;duration=maxDuration;}
    public synchronized State start(long now){expire(now);if(active)return state(now);rows.clear();bytes=0;token=UUID.randomUUID().toString();revision=1;start=now;active=now>=0;reason=active?RUNNING:REVOKED;return state(now);}
    public synchronized State state(long now){expire(now);return new State(token,revision,active,reason,rows.size(),bytes);}
    public synchronized State stop(String expected,long now){expire(now);if(!token.equals(expected))throw new IllegalArgumentException("STALE_DIAG_SESSION");finish(USER_STOP);return state(now);}
    public synchronized void revoke(){finish(REVOKED);}
    public synchronized boolean record(String expected,long now,int stage,int cause,long count){
        expire(now);if(!active||!token.equals(expected)||!LockDiagnosticCause.validPair(stage,cause)||count<0)return false;
        String line="row="+(rows.size()+1)+" stage="+LockDiagnosticCause.stage(stage)+" cause="+LockDiagnosticCause.label(cause)+" total="+count;
        if(line.length()+1>MAX_LINE_BYTES)return false;
        if(rows.size()>=rowLimit){finish(LIMIT_ROWS);return false;}
        if(bytes+line.length()+1>byteLimit){finish(LIMIT_BYTES);return false;}
        rows.add(line);bytes+=line.length()+1;revision++;
        if(rows.size()>=rowLimit)finish(LIMIT_ROWS);else if(bytes>=byteLimit)finish(LIMIT_BYTES);
        return true;
    }
    public synchronized List<String> page(String expected,long expectedRevision,int offset,long now){
        expire(now);if(active||!token.equals(expected)||revision!=expectedRevision||offset<0||offset>rows.size())throw new IllegalArgumentException("DIAG_SNAPSHOT_CHANGED");
        return List.copyOf(rows.subList(offset,Math.min(rows.size(),offset+PAGE_ROWS)));
    }
    private void expire(long now){if(active&&(now<start||now-start>=duration))finish(now<start?REVOKED:TIMEOUT);}
    private void finish(int terminal){if(active){active=false;reason=terminal;revision++;}}
}
