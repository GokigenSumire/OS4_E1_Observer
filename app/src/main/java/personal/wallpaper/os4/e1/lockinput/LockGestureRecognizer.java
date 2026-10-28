package personal.wallpaper.os4.e1.lockinput;

/** 不依赖Android的完整三手势识别；坐标只留当前短手势内存，不对外发布。 */
public final class LockGestureRecognizer {
    public static final int NONE=0,NEXT=1,PREVIOUS=2,EDIT=3;
    public static final int DOWN=0,UP=1,MOVE=2,CANCEL=3,POINTER_DOWN=5,POINTER_UP=6;
    private final long interval,maxPress;
    private final float slopSquared;
    private boolean touching;
    private int maximumPointers,taps,tapPointers;
    private long downAt,lastUp=-1,deadline=-1;
    private int firstId=-1,secondId=-1;
    private float firstX,firstY,secondX,secondY;
    private float tapX,tapY;
    private boolean pointerUpSeen;
    private int remainingPointer=-1;

    public LockGestureRecognizer(long interval,long maxPress,float slop) {
        if(interval<100||interval>1000||maxPress<50||maxPress>1000||!Float.isFinite(slop)||slop<=0)throw new IllegalArgumentException();
        this.interval=interval;this.maxPress=maxPress;this.slopSquared=slop*slop;
    }
    public void reset(){touching=false;maximumPointers=0;taps=0;tapPointers=0;lastUp=-1;deadline=-1;firstId=-1;secondId=-1;pointerUpSeen=false;remainingPointer=-1;}
    public long deadline(){return deadline;}
    public boolean hasCandidate(){return touching||taps>0;}
    public boolean pendingDoubleCandidate(){return !touching&&taps==2&&tapPointers==1&&deadline>=0;}
    public int onTime(long now){
        if(now<0){reset();return NONE;}
        if(!touching&&deadline>=0&&now>=deadline){int result=taps==2&&tapPointers==1?NEXT:NONE;reset();return result;}
        return NONE;
    }
    public int onEvent(int action,long time,int count,int[] ids,float[] xs,float[] ys){
        return onEvent(action,time,count,ids,xs,ys,count-1);
    }
    public int onEvent(int action,long time,int count,int[] ids,float[] xs,float[] ys,int actionIndex){
        if(time<0||time>Long.MAX_VALUE-1000||count<1||count>2||ids==null||xs==null||ys==null||ids.length!=count||xs.length!=count||ys.length!=count){reset();return NONE;}
        for(int i=0;i<count;i++)if(ids[i]<0||!Float.isFinite(xs[i])||!Float.isFinite(ys[i])){reset();return NONE;}
        if(count==2&&ids[0]==ids[1]){reset();return NONE;}
        if(action==CANCEL){reset();return NONE;}
        int timed=onTime(time);
        if(action==DOWN){
            if(touching||count!=1){reset();return timed;}
            if(lastUp>=0&&(time<lastUp||time-lastUp>interval)){reset();}
            if(taps>0){float dx=xs[0]-tapX,dy=ys[0]-tapY;if(dx*dx+dy*dy>slopSquared*16)reset();}
            touching=true;maximumPointers=1;downAt=time;firstId=ids[0];firstX=xs[0];firstY=ys[0];secondId=-1;pointerUpSeen=false;remainingPointer=firstId;
            return timed;
        }
        if(!touching||time<downAt||time-downAt>maxPress){reset();return timed;}
        if(action==POINTER_DOWN){
            if(count!=2||maximumPointers!=1){reset();return timed;}
            int first=ids[0]==firstId?0:ids[1]==firstId?1:-1;
            if(first<0||moved(xs[first],ys[first],firstX,firstY)){reset();return timed;}
            int second=1-first;secondId=ids[second];secondX=xs[second];secondY=ys[second];maximumPointers=2;
        }else if(action!=MOVE&&action!=UP&&action!=POINTER_UP){reset();return timed;}
        if(action==MOVE&&count!=(maximumPointers==2&&!pointerUpSeen?2:1)){reset();return timed;}
        if(action==POINTER_UP){
            if(maximumPointers!=2||pointerUpSeen||count!=2||actionIndex<0||actionIndex>=count){reset();return timed;}
            remainingPointer=ids[1-actionIndex];pointerUpSeen=true;
        }
        for(int i=0;i<count;i++){
            if(ids[i]==firstId){if(moved(xs[i],ys[i],firstX,firstY)){reset();return timed;}}
            else if(ids[i]==secondId){if(moved(xs[i],ys[i],secondX,secondY)){reset();return timed;}}
            else{reset();return timed;}
        }
        if(action==UP){
            if(count!=1||ids[0]!=remainingPointer||(maximumPointers==2&&!pointerUpSeen)){reset();return timed;}
            touching=false;
            if(taps>0&&tapPointers!=maximumPointers){taps=0;deadline=-1;}
            tapPointers=maximumPointers;taps++;lastUp=time;
            tapX=firstX;tapY=firstY;
            if(tapPointers==2&&taps==2){reset();return EDIT;}
            if(tapPointers==1&&taps==3){reset();return PREVIOUS;}
            deadline=time+interval;
        }
        return timed;
    }
    private boolean moved(float x,float y,float startX,float startY){float dx=x-startX,dy=y-startY;return dx*dx+dy*dy>slopSquared;}
}
