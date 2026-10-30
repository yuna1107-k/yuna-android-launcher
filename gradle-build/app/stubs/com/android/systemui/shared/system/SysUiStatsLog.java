package com.android.systemui.shared.system;

/**
 * Stand-in for the SysUiStatsLog class that Soong generates with stats-log-api-gen.
 * Only the constants Launcher3 (non-Quickstep) uses are defined. Values come from
 * frameworks/proto_logging/stats/atoms.proto (LauncherUIChanged.UserType).
 */
public final class SysUiStatsLog {
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_UNKNOWN = 0;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_MAIN = 1;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_WORK = 2;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_CLONED = 3;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_PRIVATE = 4;

    private SysUiStatsLog() {}
}
