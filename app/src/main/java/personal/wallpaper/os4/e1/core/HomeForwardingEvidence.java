package personal.wallpaper.os4.e1.core;

/** 仅记录已限定自然getter的条件，不把条件true或零事件推成原生触摸已到达。 */
public final class HomeForwardingEvidence {
    private String state="INSUFFICIENT_EVIDENCE";
    public synchronized void reset(){state="INSUFFICIENT_EVIDENCE";}
    public synchronized void unavailable(){state="INTERFACE_UNAVAILABLE";}
    public synchronized void observe(Object original,boolean eligible){
        state=!eligible||!(original instanceof Boolean)?"INSUFFICIENT_EVIDENCE":Boolean.TRUE.equals(original)?"NATIVE_FORWARDING_ALLOWED":"FORWARDING_MISSING";
    }
    public synchronized String status(){return state;}
}
