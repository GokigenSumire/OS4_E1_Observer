package personal.wallpaper.os4.e1.lockinput;

import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** 先实际加入排除区，后尽力旁录；分类异常不得改变原列表。 */
final class RoiExclusionTrace extends ArrayList<Rect> {
    interface Classifier {int kind(View node)throws Exception;}
    private final View root;
    private final Classifier classifier;
    private final RoiCoverageRecorder recorder;
    private final IdentityHashMap<View,Integer> kinds=new IdentityHashMap<>();
    private View top,node;
    private int why=RoiDiagnosticDetail.LEAF;
    private RoiExclusionTrace(View root,Rect area,boolean force,Classifier classifier){
        this.root=root;this.classifier=classifier;
        recorder=new RoiCoverageRecorder(new BackgroundRegion.Box(area.left,area.top,area.right,area.bottom),force);
    }
    static ArrayList<Rect> create(boolean enabled,View root,Rect area,boolean force,Classifier classifier){
        if(enabled)try{return new RoiExclusionTrace(root,area,force,classifier);}catch(Throwable ignored){ }
        return new ArrayList<>();
    }
    static void context(List<Rect> output,View top,View node){
        if(output instanceof RoiExclusionTrace){RoiExclusionTrace trace=(RoiExclusionTrace)output;trace.top=top;trace.node=node;trace.why=RoiDiagnosticDetail.LEAF;}
    }
    static void reason(List<Rect> output,int why){if(output instanceof RoiExclusionTrace)((RoiExclusionTrace)output).why=why;}
    static void container(List<Rect> output,View view){
        if(!(output instanceof RoiExclusionTrace))return;
        RoiExclusionTrace trace=(RoiExclusionTrace)output;
        try{trace.why=view.isClickable()||view.isLongClickable()||view.hasOnClickListeners()?RoiDiagnosticDetail.INTERACTIVE:
                view.getForeground()!=null?RoiDiagnosticDetail.FOREGROUND:RoiDiagnosticDetail.BACKGROUND;}
        catch(Throwable ignored){trace.recorder.incomplete();}
    }
    static void fingerprint(List<Rect> output){
        if(output instanceof RoiExclusionTrace){RoiExclusionTrace trace=(RoiExclusionTrace)output;trace.top=null;trace.node=null;trace.why=RoiDiagnosticDetail.FINGERPRINT;}
    }
    private int kind(View view)throws Exception{
        if(view==null)return LayoutDiagnosticDetail.OTHER;
        Integer cached=kinds.get(view);if(cached!=null)return cached;
        int result=classifier.kind(view);
        if(result<0||result>=RoiDiagnosticDetail.NODES){recorder.incomplete();result=LayoutDiagnosticDetail.OTHER;}
        if(kinds.size()<RoiDiagnosticDetail.MAX_EXCLUSIONS)kinds.put(view,result);
        return result;
    }
    @Override public boolean add(Rect value){
        boolean added=super.add(value);
        if(added)try{
            int topKind=top!=null&&top.getParent()==root?kind(top):LayoutDiagnosticDetail.OTHER;
            recorder.record(topKind,kind(node),why,new BackgroundRegion.Box(value.left,value.top,value.right,value.bottom));
        }catch(Throwable ignored){recorder.incomplete();}
        return added;
    }
    static int[] emptyDetails(List<Rect> output,boolean force){
        try{if(output instanceof RoiExclusionTrace)return ((RoiExclusionTrace)output).recorder.emptyDetails();}catch(Throwable ignored){ }
        return new int[]{force?RoiDiagnosticDetail.MODE_FORCE:RoiDiagnosticDetail.MODE_CONSERVATIVE,RoiDiagnosticDetail.INCOMPLETE};
    }
}
