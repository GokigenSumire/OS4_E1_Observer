package personal.wallpaper.os4.e1.lockinput;

/** 固定ROI字典；仅诊断整数，不包含坐标、类名或动作能力。 */
public final class RoiDiagnosticDetail {
    public static final int INTERACTIVE=0,FOREGROUND=1,BACKGROUND=2,LEAF=3,EMPTY_DRAW=4,
            FORCE_STATUS=5,HARD_STATUS=6,FINGERPRINT=7;
    public static final int BASE=16384,MODE_PRIORITY=BASE-7,BATCH_COMPLETE=BASE-6,MODE_FORCE=BASE-5,MODE_CONSERVATIVE=BASE-4,
            SINGLE_COVER=BASE-3,UNION_COVER=BASE-2,INCOMPLETE=BASE-1;
    static final int NODES=24,WHY_COUNT=8,MAX_EXCLUSIONS=512,MAX_REPORT=515;
    private static final int LIMIT=BASE+4*WHY_COUNT*NODES*NODES;
    private static final String[] WHY={"INTERACTIVE","FOREGROUND","BACKGROUND","LEAF","EMPTY_DRAW","FORCE_STATUS","HARD_STATUS","FINGERPRINT"};
    private RoiDiagnosticDetail(){ }
    public static int code(boolean force,boolean full,int why,int top,int node){
        if(why<0||why>=WHY_COUNT||top<0||top>=NODES||node<0||node>=NODES)return INCOMPLETE;
        return BASE+((((force?2:0)+(full?1:0))*WHY_COUNT+why)*NODES+top)*NODES+node;
    }
    public static boolean valid(int code){return code>=MODE_PRIORITY&&code<LIMIT;}
    static boolean full(int code){return code>=BASE&&code<LIMIT&&((code-BASE)/(WHY_COUNT*NODES*NODES))%2==1;}
    public static String label(int code){
        if(code==MODE_PRIORITY)return "ROI_MODE_WALLPAPER_PRIORITY_APPLIED";
        if(code==BATCH_COMPLETE)return "ROI_DETAILS_COMPLETE";
        if(code==MODE_FORCE)return "ROI_MODE_FORCE_APPLIED";
        if(code==MODE_CONSERVATIVE)return "ROI_MODE_CONSERVATIVE_APPLIED";
        if(code==SINGLE_COVER)return "ROI_SINGLE_BLOCK_COVERS";
        if(code==UNION_COVER)return "ROI_UNION_ONLY_COVERS";
        if(code==INCOMPLETE||!valid(code))return "ROI_DETAIL_INCOMPLETE";
        int value=code-BASE,node=value%NODES;value/=NODES;
        int top=value%NODES;value/=NODES;
        int why=value%WHY_COUNT,mode=value/WHY_COUNT;
        return "ROI_"+(mode>=2?"FORCE_":"CONSERVATIVE_")+(mode%2==1?"FULL_":"PART_")+WHY[why]
                +"_TOP_"+LayoutDiagnosticDetail.nodeLabel(top)+"_NODE_"+LayoutDiagnosticDetail.nodeLabel(node);
    }
}
