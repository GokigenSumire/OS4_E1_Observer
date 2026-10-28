package com.miui.systemui.shade.header;
public final class ShadeHeaderClipController {
    public Object clipRatio=new Flow(0f);
    public static final class Flow{
        public Object value;
        public Flow(Object value){this.value=value;}
        public Object getValue(){return value;}
    }
}
