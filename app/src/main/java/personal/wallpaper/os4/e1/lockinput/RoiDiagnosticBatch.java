package personal.wallpaper.os4.e1.lockinput;

import java.util.Arrays;

/** 有界会话去重，不携坐标或View；只有成功排队的批次调用take。 */
final class RoiDiagnosticBatch {
    private String token;
    private int[] previous;
    synchronized int[] take(String token,int[] codes){
        if(token==null||codes==null||codes.length==0||codes.length>RoiDiagnosticDetail.MAX_REPORT)return null;
        for(int code:codes)if(!RoiDiagnosticDetail.valid(code)||code==RoiDiagnosticDetail.BATCH_COMPLETE)return null;
        if(token.equals(this.token)&&Arrays.equals(previous,codes))return null;
        this.token=token;previous=codes.clone();return codes.clone();
    }
    synchronized void clear(){token=null;previous=null;}
}
