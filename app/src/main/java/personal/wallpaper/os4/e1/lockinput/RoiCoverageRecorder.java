package personal.wallpaper.os4.e1.lockinput;

import java.util.TreeSet;
import java.util.ArrayList;

/** 只比较实际排除矩形，不替代hasSpace或改变允许区域。 */
final class RoiCoverageRecorder {
    private final BackgroundRegion.Box area;
    private final boolean force;
    private final TreeSet<Integer> sources=new TreeSet<>();
    private int seen;
    private boolean single,incomplete;
    RoiCoverageRecorder(BackgroundRegion.Box area,boolean force){this.area=area;this.force=force;}
    void incomplete(){incomplete=true;}
    void record(int top,int node,int why,BackgroundRegion.Box box){
        if(++seen>RoiDiagnosticDetail.MAX_EXCLUSIONS){incomplete=true;return;}
        if(box==null||area==null){incomplete=true;return;}
        if(box.right<=area.left||box.left>=area.right||box.bottom<=area.top||box.top>=area.bottom)return;
        boolean full=box.left<=area.left&&box.top<=area.top&&box.right>=area.right&&box.bottom>=area.bottom;
        single|=full;
        int code=RoiDiagnosticDetail.code(force,full,why,top,node);
        if(code==RoiDiagnosticDetail.INCOMPLETE)incomplete=true;
        sources.add(code);
    }
    /** 只能在调用方原算法已判最终ROI为空后使用；不推测未记录部分。 */
    int[] emptyDetails(){
        TreeSet<Integer> summary=new TreeSet<>();
        summary.add(force?RoiDiagnosticDetail.MODE_FORCE:RoiDiagnosticDetail.MODE_CONSERVATIVE);
        if(single)summary.add(RoiDiagnosticDetail.SINGLE_COVER);
        else if(!incomplete&&!sources.isEmpty())summary.add(RoiDiagnosticDetail.UNION_COVER);
        ArrayList<Integer> result=new ArrayList<>(summary);
        for(int code:sources)if(RoiDiagnosticDetail.full(code))result.add(code);
        for(int code:sources)if(code!=RoiDiagnosticDetail.INCOMPLETE&&!RoiDiagnosticDetail.full(code))result.add(code);
        if(incomplete||sources.isEmpty())result.add(RoiDiagnosticDetail.INCOMPLETE);
        if(result.size()>RoiDiagnosticDetail.MAX_REPORT)throw new IllegalStateException("ROI_REPORT_BOUND");
        int[] codes=new int[result.size()];int index=0;for(int code:result)codes[index++]=code;return codes;
    }
}
