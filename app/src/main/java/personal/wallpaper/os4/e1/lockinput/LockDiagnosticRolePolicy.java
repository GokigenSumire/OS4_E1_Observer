package personal.wallpaper.os4.e1.lockinput;
/** 低权诊断生产者不可因状态通道取得播放、ARM或页面读取能力。 */
public final class LockDiagnosticRolePolicy {
    private LockDiagnosticRolePolicy(){ }
    public static boolean allows(int role,int transaction){
        if(role==3)return transaction==11||transaction==12;
        if(role==1)return transaction==1||transaction==2||transaction==4||transaction==6||transaction>=7&&transaction<=11;
        if(role==2)return transaction==2||transaction==3||transaction==5||transaction==11||transaction==12;
        return false;
    }
}
