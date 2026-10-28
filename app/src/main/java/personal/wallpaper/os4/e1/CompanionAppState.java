package personal.wallpaper.os4.e1;

/** 配套主应用的安装展示模型；不读取系统状态、不持有控制许可。 */
final class CompanionAppState {
    enum Availability { NOT_INSTALLED, INSTALLED, UNAVAILABLE }
    final Availability availability;
    final String versionName;
    final long versionCode;
    final boolean canOpen;

    private CompanionAppState(Availability availability, String versionName, long versionCode, boolean canOpen) {
        this.availability = availability;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.canOpen = canOpen;
    }

    static CompanionAppState notInstalled() {
        return new CompanionAppState(Availability.NOT_INSTALLED, null, -1, false);
    }

    static CompanionAppState unavailable() {
        return new CompanionAppState(Availability.UNAVAILABLE, null, -1, false);
    }

    static CompanionAppState installed(String expectedPackage, String actualPackage, String versionName,
                                        long versionCode, boolean enabled, String launcherPackage) {
        if (!("personal.wallpaper.os3".equals(expectedPackage)
                || "personal.wallpaper.os3.debug".equals(expectedPackage))
                || !expectedPackage.equals(actualPackage)) return unavailable();
        String actualVersion = versionName == null || versionName.trim().isEmpty() ? null : versionName;
        return new CompanionAppState(Availability.INSTALLED, actualVersion,
                versionCode >= 0 ? versionCode : -1, enabled && expectedPackage.equals(launcherPackage));
    }
}
