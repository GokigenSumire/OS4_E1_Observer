package com.miui.systemui.widget;

/** 对应OEM requested字段与final Super getter双态；不模拟OEM异步应用任务。 */
public abstract class PropertiesFrameLayout extends android.widget.FrameLayout {
    private float requestedScaleX=1,requestedScaleY=1,requestedRotation,requestedRotationX,requestedRotationY,requestedTranslationX,requestedTranslationY,requestedTranslationZ;
    public PropertiesFrameLayout(android.content.Context context){super(context);}
    @Override public float getScaleX(){return requestedScaleX;}
    @Override public float getScaleY(){return requestedScaleY;}
    @Override public float getRotation(){return requestedRotation;}
    @Override public float getRotationX(){return requestedRotationX;}
    @Override public float getRotationY(){return requestedRotationY;}
    @Override public float getTranslationX(){return requestedTranslationX;}
    @Override public float getTranslationY(){return requestedTranslationY;}
    @Override public float getTranslationZ(){return requestedTranslationZ;}
    // settled夹具先建立真实Android属性，避免宿主setter虚调requested getter时误作“已应用”。
    @Override public void setScaleX(float value){super.setScaleX(value);requestedScaleX=value;}
    @Override public void setScaleY(float value){super.setScaleY(value);requestedScaleY=value;}
    @Override public void setRotation(float value){super.setRotation(value);requestedRotation=value;}
    @Override public void setRotationX(float value){super.setRotationX(value);requestedRotationX=value;}
    @Override public void setRotationY(float value){super.setRotationY(value);requestedRotationY=value;}
    @Override public void setTranslationX(float value){super.setTranslationX(value);requestedTranslationX=value;}
    @Override public void setTranslationY(float value){super.setTranslationY(value);requestedTranslationY=value;}
    @Override public void setTranslationZ(float value){super.setTranslationZ(value);requestedTranslationZ=value;}
    public final float getSuperScaleX(){return super.getScaleX();}
    public final float getSuperScaleY(){return super.getScaleY();}
    public final float getSuperRotationX(){return super.getRotationX();}
    public final float getSuperRotationY(){return super.getRotationY();}
    public final float getSuperRotationZ(){return super.getRotation();}
    public final float getSuperTranslationX(){return super.getTranslationX();}
    public final float getSuperTranslationY(){return super.getTranslationY();}
    public final float getSuperTranslationZ(){return super.getTranslationZ();}
    public final void setSuperScaleX(float value){super.setScaleX(value);}
    public final void setSuperScaleY(float value){super.setScaleY(value);}
    public final void setSuperRotationX(float value){super.setRotationX(value);}
    public final void setSuperRotationY(float value){super.setRotationY(value);}
    public final void setSuperRotationZ(float value){super.setRotation(value);}
    public final void setSuperTranslationX(float value){super.setTranslationX(value);}
    public final void setSuperTranslationY(float value){super.setTranslationY(value);}
    public final void setSuperTranslationZ(float value){super.setTranslationZ(value);}
}
