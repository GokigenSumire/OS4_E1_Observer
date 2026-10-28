package personal.wallpaper.os4.e1.core;

/** 原临时对照和日常租约共享许可契约；不执行Android写入。 */
public interface SessionPolicy {
    long start(long now,String mode);
    boolean enable(long now);
    boolean beginRecovery(long now,String reason);
    boolean permit(long now,long ticket,boolean certified,boolean exactAttrs,boolean insidePopulate,boolean target,boolean keyguard,boolean originalFalse);
    boolean overrideApplied(long now,long ticket);
    boolean observeRecovery(long now,long ticket,boolean exactWrapper,boolean dupFalse,boolean baselineFieldIntact);
    boolean noteFieldViolation(long now,long ticket);
    String finalizeRecovery(long now,long ticket);
    String status(long now);
    long epoch();
    String mode();
    boolean needsRecovery();
    boolean recoveryProved();
    long appliedCount();
    long permitCount();
    long durationLimitMillis();
}
