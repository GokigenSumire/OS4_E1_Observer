package personal.wallpaper.os4.e1.lockinput;

/** 固定槽计数、每批有界；ACK之后才确认，诊断停用/换会话清旧。没有事件时间或触摸内容。 */
final class NativeDiagnosticCounters {
    static final class Row {final int code;final long total;Row(int code,long total){this.code=code;this.total=total;}}
    private final long[] totals=new long[NativeInputDetail.count()],sent=new long[NativeInputDetail.count()];
    private String token="";private boolean active;private int cursor;
    synchronized void session(String next,boolean enabled){
        if(next==null)next="";
        if(!next.equals(token)){java.util.Arrays.fill(totals,0);java.util.Arrays.fill(sent,0);cursor=0;}
        token=next;active=enabled;
    }
    synchronized void hit(int code){if(active&&NativeInputDetail.valid(code)){int i=code-NativeInputDetail.BASE;if(totals[i]<Long.MAX_VALUE)totals[i]++;}}
    synchronized Row[] pending(String expected,int limit){
        if(!active||!token.equals(expected)||limit<1||limit>16)return new Row[0];
        java.util.ArrayList<Row> rows=new java.util.ArrayList<>();
        for(int seen=0;seen<totals.length&&rows.size()<limit;seen++){
            int i=(cursor+seen)%totals.length;if(totals[i]>sent[i])rows.add(new Row(NativeInputDetail.BASE+i,totals[i]));
        }
        if(!rows.isEmpty())cursor=(rows.get(rows.size()-1).code-NativeInputDetail.BASE+1)%totals.length;
        return rows.toArray(new Row[0]);
    }
    synchronized void acknowledge(String expected,Row row){if(active&&token.equals(expected)&&row!=null&&NativeInputDetail.valid(row.code)){int i=row.code-NativeInputDetail.BASE;if(row.total>=sent[i]&&row.total<=totals[i])sent[i]=row.total;}}
}
