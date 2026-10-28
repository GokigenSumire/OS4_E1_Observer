package personal.wallpaper.os4.e1.lockinput;

/** 固定布局诊断字典；整数仅描述拒绝，不授予区域或动作能力。 */
public final class LayoutDiagnosticDetail {
    public static final int UNKNOWN=0,NODE_LIMIT=1,CHILD_LIMIT=2,LAYOUT_REQ=3,
            ANIM_NEW=4,ANIM_ACTIVE=5,ANIM_ENDED=6,ANIM_ENDED_FILL=7;
    public static final int OTHER=0,KEYGUARD=1,SHARED=2,FACEBOX=3,FACE=4,FRONT=5,
            MSGBOX=6,MESSAGE=7,BOUNCER=8,PANEL=9,CONTROL=10,HEADER=11,HDR_CLIP=12,
            HDR_FRONT=13,FTR_CLIP=14,AOD=15,RIPPLE=16,NSSL=17,SHADE=18,
            BRIGHTNESS=19,LEFT=20,STATUS=21,LOWLIGHT=22,LIGHT=23;
    public static final int BASE=4096;
    private static final String[] WHY={"UNKNOWN","NODE_LIMIT","CHILD_LIMIT","LAYOUT_REQ",
            "ANIM_NEW","ANIM_ACTIVE","ANIM_ENDED","ANIM_ENDED_FILL"};
    private static final String[] NODE={"OTHER","KEYGUARD","SHARED","FACEBOX","FACE","FRONT",
            "MSGBOX","MESSAGE","BOUNCER","PANEL","CONTROL","HEADER","HDR_CLIP","HDR_FRONT",
            "FTR_CLIP","AOD","RIPPLE","NSSL","SHADE","BRIGHTNESS","LEFT","STATUS","LOWLIGHT","LIGHT"};
    private static final int LIMIT=BASE+WHY.length*NODE.length*NODE.length;
    private LayoutDiagnosticDetail(){ }
    public static int code(int why,int top,int node){
        if(why<0||why>=WHY.length||top<0||top>=NODE.length||node<0||node>=NODE.length)return 0;
        return BASE+(why*NODE.length+top)*NODE.length+node;
    }
    public static boolean valid(int code){return code>=BASE&&code<LIMIT;}
    public static int why(int code){return valid(code)?(code-BASE)/(NODE.length*NODE.length):-1;}
    public static int top(int code){return valid(code)?((code-BASE)/NODE.length)%NODE.length:-1;}
    public static int node(int code){return valid(code)?(code-BASE)%NODE.length:-1;}
    static String nodeLabel(int node){return node>=0&&node<NODE.length?NODE[node]:NODE[OTHER];}
    public static String label(int code){
        if(!valid(code))return "LAYOUT_UNKNOWN_TOP_OTHER_NODE_OTHER";
        return "LAYOUT_"+WHY[why(code)]+"_TOP_"+NODE[top(code)]+"_NODE_"+NODE[node(code)];
    }
}
