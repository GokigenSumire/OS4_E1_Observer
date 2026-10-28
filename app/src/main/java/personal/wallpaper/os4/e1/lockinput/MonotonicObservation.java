package personal.wallpaper.os4.e1.lockinput;

/** 后发请求具有唯一发布权；请求进行中不以较早状态授予新动作，失联也使用相同代次撤权。 */
public final class MonotonicObservation<T> {
    private long issued,published;
    private T value;
    private boolean exhausted;
    private long foregroundRequest=-1;
    public synchronized long beginRequest(){return beginRequest(false);}
    public synchronized long beginRequest(boolean foreground){
        if(!foreground&&foregroundRequest>0)return -1;
        if(exhausted||issued==Long.MAX_VALUE){exhausted=true;value=null;return -1;}
        long request=++issued;if(foreground)foregroundRequest=request;return request;
    }
    public synchronized void finishRequest(long request){if(foregroundRequest==request)foregroundRequest=-1;}
    public synchronized boolean publish(long request,T next){if(exhausted||request<=0||request!=issued)return false;published=request;value=next;return true;}
    public synchronized T snapshot(){return !exhausted&&published==issued?value:null;}
    public synchronized boolean current(long request){return !exhausted&&request>0&&request==issued;}
    public synchronized void invalidate(){foregroundRequest=-1;beginRequest();value=null;}
}
