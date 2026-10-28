package personal.wallpaper.os4.e1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import personal.wallpaper.os4.e1.core.HomeScope;
import personal.wallpaper.os4.e1.relay.LockRelayService;
import personal.wallpaper.os4.e1.relay.ForceLocalSetting;
import personal.wallpaper.os4.e1.relay.WallpaperPrioritySetting;

/** 日常兼容设置；已保存意愿与系统当前生效独立显示，状态查询不改变开关。 */
public final class MainActivity extends Activity {
    private static final int SAVE_OK = 0, SAVE_ENABLE_FAILED = 1, SAVE_DISABLE_FAILED = 2;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Button> buttons = new ArrayList<>();
    private LinearLayout stateCard;
    private TextView stateTitle, stateBody, sample, savedSetting, saveWarning, notice;
    private TextView wallpaperIdentity, wallpaperAvailability;
    private Switch enabledSwitch;
    private Switch lockEnabledSwitch;
    private TextView lockStatus;
    private Switch forceLocalSwitch;
    private TextView forceLocalStatus;
    private boolean renderingForce;
    private Switch prioritySwitch;
    private TextView priorityStatus;
    private Switch nativeSwitch;
    private TextView nativeStatus;
    private boolean renderingNative;
    private boolean renderingPriority;
    private Button applySetting, stopCurrent;
    private Bundle lastResponse;
    private DashboardState current;
    private CompatPreferenceStore.State preference;
    private long controlEpoch = -1;
    private String controlBootSession;
    private String responseIssue;
    private String sampledAt;
    private int saveFailure;
    private boolean busy, confirmationVisible, renderingSwitch, destroyed;
    /** Only renders the local fixed summary; never polls Binder or changes either switch. */
    private final Runnable lockStatusRefresh = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (lockStatus != null) lockStatus.setText("锁屏兼容状态：" + LockRelayService.status(MainActivity.this));
            main.postDelayed(this, 1000);
        }
    };
    private interface Reply { void accept(Bundle result); }

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_HomeCompat);
        super.onCreate(state);
        if (state != null) saveFailure = state.getInt("preferenceSaveFailure", SAVE_OK);
        createDashboard();
        render();
    }

    private void createDashboard() {
        LinearLayout screen = column();
        screen.setBackgroundColor(color(R.color.dashboard_background));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(16), dp(20), dp(28));
        scroll.addView(box);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconSize.setMarginEnd(dp(14));
        brand.addView(icon, iconSize);
        LinearLayout brandText = column();
        text(brandText, getApplicationInfo().loadLabel(getPackageManager()).toString(), 24, true, R.color.dashboard_text);
        text(brandText, getString(R.string.dashboard_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 13, false, R.color.dashboard_secondary);
        brand.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(brand);
        TextView kicker = text(box, getString(R.string.dashboard_kicker), 14, false, R.color.dashboard_secondary);
        margin(kicker, 8, 20);

        stateCard = card(box);
        text(stateCard, getString(R.string.dashboard_status_label), 12, true, R.color.dashboard_warning_text);
        stateTitle = text(stateCard, getString(R.string.dashboard_unchecked_title), 25, true, R.color.dashboard_warning_text);
        margin(stateTitle, 8, 8);
        stateTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        stateBody = text(stateCard, getString(R.string.dashboard_unchecked_body), 14, false, R.color.dashboard_warning_text);
        sample = text(stateCard, "", 11, false, R.color.dashboard_warning_text);
        margin(sample, 12, 0);

        LinearLayout setting = card(box);
        enabledSwitch = new Switch(this);
        enabledSwitch.setText(R.string.dashboard_setting_title);
        enabledSwitch.setTextSize(19);
        enabledSwitch.setTextColor(color(R.color.dashboard_text));
        enabledSwitch.setTextOn(getString(R.string.dashboard_on));
        enabledSwitch.setTextOff(getString(R.string.dashboard_off));
        enabledSwitch.setShowText(true);
        enabledSwitch.setMinHeight(dp(56));
        enabledSwitch.setPadding(0, dp(8), 0, dp(8));
        setting.addView(enabledSwitch, new LinearLayout.LayoutParams(-1, -2));
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (renderingSwitch) return;
            render();
            requestChange(checked);
        });
        savedSetting = text(setting, "", 14, true, R.color.dashboard_secondary);
        margin(savedSetting, 8, 8);
        text(setting, getString(R.string.dashboard_setting_policy), 13, false, R.color.dashboard_secondary);
        saveWarning = text(setting, "", 13, true, R.color.dashboard_warning_text);
        saveWarning.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        margin(saveWarning, 10, 0);
        applySetting = button(setting, R.string.dashboard_apply_setting, () -> requestChange(true));
        stopCurrent = button(setting, R.string.dashboard_stop_current, () -> requestChange(false));
        button(setting, R.string.dashboard_refresh, this::refreshStatus);

        LinearLayout lockSetting = card(box);
        text(lockSetting, "锁屏手势兼容", 19, true, R.color.dashboard_text);
        lockEnabledSwitch = new Switch(this);
        lockEnabledSwitch.setText("启用：开／关");
        lockEnabledSwitch.setTextColor(color(R.color.dashboard_text));
        lockEnabledSwitch.setMinHeight(dp(56));
        lockEnabledSwitch.setChecked(LockRelayService.isDesiredEnabled(this));
        lockSetting.addView(lockEnabledSwitch, new LinearLayout.LayoutParams(-1, -2));
        lockStatus = text(lockSetting, "", 14, false, R.color.dashboard_secondary);
        text(lockSetting, "仅本项目独立锁屏；双击下一张、三击上一张、两指双击经系统安全解锁后编辑。需要系统框架与系统界面作用域；开关保存后持续开启，重启重新核定身份。", 13, false, R.color.dashboard_secondary);
        lockEnabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!LockRelayService.setDesiredEnabled(this, checked)) {
                lockStatus.setText("开关未确认保存，当前锁屏兼容暂停，请重试。");
                return;
            }
            lockStatus.setText(checked ? "已保存开启，等待独立锁屏连接与输入准入。" : "已保存关闭，正在撤销锁屏会话。");
        });
        nativeSwitch=new Switch(this);nativeSwitch.setTag("native_input_switch");nativeSwitch.setText("原生锁屏业务输入（推荐）");
        nativeSwitch.setTextColor(color(R.color.dashboard_text));nativeSwitch.setMinHeight(dp(56));lockSetting.addView(nativeSwitch,new LinearLayout.LayoutParams(-1,-2));
        nativeStatus=text(lockSetting,"",14,false,R.color.dashboard_secondary);
        text(lockSetting,"只在本项目LOCK、有效会话下识别三手势，双击／三击优先时本次原生双击息屏不执行。下方开启原生锁屏手势优先可取消普通区域避让；两指编辑仍需系统安全解锁。",13,false,R.color.dashboard_secondary);
        renderNative();
        nativeSwitch.setOnCheckedChangeListener((button,checked)->{if(renderingNative)return;boolean saved=LockRelayService.setNativeInputEnabled(this,checked);renderNative();if(!saved)nativeStatus.setText("输入路线未确认保存，当前锁屏兼容暂停，请重试。");});
        forceLocalSwitch=new Switch(this);
        forceLocalSwitch.setTag("force_local_switch");
        forceLocalSwitch.setText("强制局部兼容（顶部20%禁区）");
        forceLocalSwitch.setTextColor(color(R.color.dashboard_text));
        forceLocalSwitch.setMinHeight(dp(56));
        lockSetting.addView(forceLocalSwitch,new LinearLayout.LayoutParams(-1,-2));
        forceLocalStatus=text(lockSetting,"",14,false,R.color.dashboard_warning_text);
        text(lockSetting,"仅精确状态栏跳过内部布局检查；顶部20%不能切图。可能与越界控件冲突，密码／指纹、通知和系统安全解锁保护不取消。关闭可回到保守模式。",13,false,R.color.dashboard_secondary);
        renderForceLocal();
        forceLocalSwitch.setOnCheckedChangeListener((button,checked)->{
            if(renderingForce)return;
            renderForceLocal();
            if(checked){
                new AlertDialog.Builder(this).setTitle("开启强制局部兼容？")
                        .setMessage("将跳过精确状态栏内部布局检查，并禁止屏幕顶部20%触发壁纸手势。仍可能与状态栏越界控件冲突；系统认证和其它交互保护保留。此设置会保存，关闭即可恢复保守模式。")
                        .setNegativeButton("取消",null)
                        .setPositiveButton("确认开启",(dialog,which)->saveForceLocal(true)).show();
            }else saveForceLocal(false);
        });
        prioritySwitch=new Switch(this);prioritySwitch.setTag("wallpaper_priority_switch");
        prioritySwitch.setText("锁屏手势最高优先级");prioritySwitch.setTextColor(color(R.color.dashboard_text));prioritySwitch.setMinHeight(dp(56));
        lockSetting.addView(prioritySwitch,new LinearLayout.LayoutParams(-1,-2));priorityStatus=text(lockSetting,"",14,false,R.color.dashboard_warning_text);
        text(lockSetting,"上方原生锁屏业务输入开启时，三手势不受通知、音乐、面板、认证控件、主题、顶部或边缘限制；图片忙碌时先识别再顺序处理，系统动作可能同时响应。沿用已保存的壁纸优先开关；关闭恢复原策略。打开图片编辑仍须系统正常解锁。",13,false,R.color.dashboard_secondary);
        renderPriority();
        renderNative();
        prioritySwitch.setOnCheckedChangeListener((button,checked)->{
            if(renderingPriority)return;renderPriority();
            if(checked)new AlertDialog.Builder(this).setTitle("开启锁屏手势最高优先级？")
                    .setMessage("当前锁屏内不按通知、音乐、面板、认证控件或主题状态阻断三手势，顶部和边缘也可识别；图片处理忙碌时顺序等待，系统动作可能同时响应。打开图片编辑仍须系统正常解锁。设置持久保存，关闭恢复原策略，原强制局部开关不变；旧输入路线仍保守。")
                    .setNegativeButton("取消",null).setPositiveButton("接受风险并开启",(dialog,which)->savePriority(true)).show();
            else savePriority(false);
        });

        LinearLayout advanced = card(box);
        text(advanced, "高级", 16, true, R.color.dashboard_text);
        button(advanced, R.string.dashboard_debug_logs, () ->
                startActivity(new Intent(this, LockDebugActivity.class)));

        LinearLayout companion = card(box);
        text(companion, getString(R.string.dashboard_wallpaper_title), 16, true, R.color.dashboard_text);
        wallpaperIdentity = text(companion, getString(R.string.dashboard_wallpaper_status_unknown), 15, true, R.color.dashboard_text);
        margin(wallpaperIdentity, 10, 4);
        wallpaperAvailability = text(companion, "", 12, false, R.color.dashboard_secondary);
        button(companion, R.string.dashboard_open_wallpaper, this::openWallpaperApp);
        TextView hint = text(companion, getString(R.string.dashboard_wallpaper_optional), 12, false, R.color.dashboard_secondary);
        margin(hint, 10, 0);

        LinearLayout protection = card(box);
        text(protection, getString(R.string.dashboard_protection_title), 16, true, R.color.dashboard_text);
        TextView scope = text(protection, getString(R.string.dashboard_protection_body), 13, false, R.color.dashboard_secondary);
        margin(scope, 10, 8);
        text(protection, getString(R.string.dashboard_recovery_hint), 12, false, R.color.dashboard_secondary);
        notice = text(box, "", 13, false, R.color.dashboard_warning_text);
        notice.setVisibility(View.GONE);
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        setContentView(screen);
    }

    /** 恢复前台只重新读取状态，不保存意愿，也不自动开启。 */
    private void refreshStatus() {
        if (busy || confirmationVisible || destroyed) return;
        refreshWallpaperApp();
        renderForceLocal();
        renderPriority();
        busy = true;
        render();
        command("STATUS", null, result -> {
            display(result);
            busy = false;
            render();
        });
    }
    private void renderForceLocal(){
        if(forceLocalSwitch==null)return;
        ForceLocalSetting.State force=LockRelayService.forceLocalState(this);
        renderingForce=true;forceLocalSwitch.setChecked(force.available&&force.enabled);renderingForce=false;
        forceLocalStatus.setText(!force.available?"强制设置未确认，当前兼容暂停，请重试保存。":force.enabled?"已保存：强制模式开；顶部20%禁用手势，实际生效等待新会话。":"已保存：保守模式；强制局部兼容关闭。");
    }
    private void saveForceLocal(boolean enabled){
        boolean saved=LockRelayService.setForceLocalEnabled(this,enabled);
        renderForceLocal();
        if(!saved)forceLocalStatus.setText("强制设置未确认保存，当前兼容暂停，请重试。不会把内存回读当作已保存。");
    }
    private void renderPriority(){
        if(prioritySwitch==null)return;
        WallpaperPrioritySetting.State priority=LockRelayService.wallpaperPriorityState(this);
        renderingPriority=true;prioritySwitch.setChecked(priority.available&&priority.enabled);renderingPriority=false;
        priorityStatus.setText(!priority.available?"设置未确认，当前锁屏兼容暂停，请重试。":priority.enabled?"已保存：锁屏手势最高优先级开，先识别、顺序处理；系统动作可能同时响应。":"已保存：手势优先关闭，采用原局部／保守策略。");
    }
    private void savePriority(boolean enabled){
        boolean saved=LockRelayService.setWallpaperPriorityEnabled(this,enabled);renderPriority();
        if(!saved)priorityStatus.setText("设置未确认保存，当前兼容暂停，不把内存回读当已保存。请重试。");
    }
    private void renderNative(){
        if(nativeSwitch==null)return;personal.wallpaper.os4.e1.relay.NativeInputSetting.State nativeInput=LockRelayService.nativeInputState(this);
        renderingNative=true;nativeSwitch.setChecked(nativeInput.available&&nativeInput.enabled);renderingNative=false;
        nativeStatus.setText(!nativeInput.available?"路线保存未确认，兼容暂停。":nativeInput.enabled?"原生业务路线：等待实际入口／安全准入；下方两项仅旧路线回退使用。":"原生路线已关闭，恢复原局部／保守策略；原生息屏不接管。");
    }

    private void requestChange(boolean enable) {
        if (busy || confirmationVisible || destroyed) return;
        if (!enable) {
            confirmChange(false, controlEpoch, controlBootSession);
            return;
        }
        busy = true;
        render();
        command("STATUS", null, result -> {
            display(result);
            busy = false;
            render();
            if (result == null || current == null || !current.canEnable || !validControlTicket()
                    || !preference.available) return;
            confirmChange(true, controlEpoch, controlBootSession);
        });
    }

    private void confirmChange(boolean enable, long epoch, String bootSession) {
        if (busy || confirmationVisible || destroyed) return;
        confirmationVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(enable ? R.string.dashboard_enable_title : R.string.dashboard_disable_title)
                .setMessage(enable ? R.string.dashboard_enable_confirmation : R.string.dashboard_disable_confirmation)
                .setNegativeButton(R.string.dashboard_cancel, null)
                .setPositiveButton(R.string.dashboard_confirm, (d, which) -> {
                    confirmationVisible = false;
                    if (enable && (epoch != controlEpoch || !Objects.equals(bootSession, controlBootSession))) {
                        showNotice(R.string.dashboard_session_changed);
                        return;
                    }
                    persistAndApply(enable);
                }).create();
        dialog.setOnDismissListener(d -> confirmationVisible = false);
        dialog.show();
    }

    private void persistAndApply(boolean enable) {
        if (busy || destroyed) return;
        boolean committed = CompatPreferenceStore.setEnabled(this, enable);
        // 捕获同一保存后的意愿与revision，绝不在异步下发时借用较新的关闭revision去开启。
        CompatPreferenceStore.State saved = CompatPreferenceStore.read(this);
        DashboardState.Mutation mutation = DashboardState.afterPersistence(enable, committed,
                saved.available, saved.enabled, saved.revision);
        saveFailure = committed ? SAVE_OK : enable ? SAVE_ENABLE_FAILED : SAVE_DISABLE_FAILED;
        if (mutation == DashboardState.Mutation.REJECT) {
            saveFailure = SAVE_ENABLE_FAILED;
            render();
            return;
        }
        busy = true;
        render();
        long revision = DashboardState.commandRevision(enable, committed, saved.available, saved.revision);
        command(mutation == DashboardState.Mutation.ENABLE ? "DAILY_ON" : "DAILY_OFF", saved, revision, result -> {
            display(result);
            busy = false;
            render();
        });
    }

    private boolean validControlTicket() {
        return controlEpoch >= 0 && controlBootSession != null && !controlBootSession.isEmpty();
    }

    private void command(String cmd, CompatPreferenceStore.State saved, Reply reply) {
        command(cmd, saved, saved == null ? -1 : saved.revision, reply);
    }

    private void command(String cmd, CompatPreferenceStore.State saved, long desiredRevision, Reply reply) {
        if ("DAILY_ON".equals(cmd) && (saved == null || !saved.available || !saved.enabled
                || saved.revision < 0 || desiredRevision != saved.revision)) {
            responseIssue = "SETTING_UNCONFIRMED";
            reply.accept(null);
            return;
        }
        Intent intent = new Intent(ControlServer.ACTION).setPackage("android");
        intent.putExtra("cmd", cmd);
        if ("DAILY_ON".equals(cmd) || "DAILY_OFF".equals(cmd)) {
            intent.putExtra("epoch", controlEpoch);
            intent.putExtra("bootSession", controlBootSession);
            intent.putExtra("desiredRevision", desiredRevision);
        }
        final boolean[] finished = {false};
        Runnable timeout = () -> {
            if (!finished[0] && !destroyed) {
                finished[0] = true;
                responseIssue = "NO_CONTROL_RESPONSE";
                controlEpoch = -1;
                controlBootSession = null;
                reply.accept(null);
            }
        };
        main.postDelayed(timeout, 8000);
        try {
            sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent received) {
                    if (finished[0] || destroyed) return;
                    finished[0] = true;
                    main.removeCallbacks(timeout);
                    Bundle result = getResultExtras(false);
                    if (result != null && result.containsKey("module")
                            && !HomeScope.responseAllowed(result.getString("module"),
                                    BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                        responseIssue = "MODULE_VERSION_MISMATCH";
                        controlEpoch = -1;
                        controlBootSession = null;
                        reply.accept(null);
                    } else if (result != null && result.containsKey("module")) {
                        controlEpoch = result.getLong("epoch", -1);
                        controlBootSession = result.getString("bootSession");
                        responseIssue = null;
                        reply.accept(result);
                    } else {
                        responseIssue = "NO_CONTROL_RESPONSE";
                        controlEpoch = -1;
                        controlBootSession = null;
                        reply.accept(null);
                    }
                }
            }, main, 0, null, null);
        } catch (RuntimeException ignored) {
            finished[0] = true;
            main.removeCallbacks(timeout);
            responseIssue = "NO_CONTROL_RESPONSE";
            controlEpoch = -1;
            controlBootSession = null;
            reply.accept(null);
        }
    }

    private void display(Bundle result) {
        lastResponse = result == null ? null : new Bundle(result);
        sampledAt = result == null ? null : DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        render();
    }

    private void render() {
        preference = CompatPreferenceStore.read(this);
        current = lastResponse == null ? null : DashboardState.from(lastResponse.getString("contrast"),
                lastResponse.getString("hooks"), lastResponse.getString("bootstrap"),
                lastResponse.getString("coverage"), lastResponse.getString("error"),
                preference.available, preference.enabled);
        renderingSwitch = true;
        enabledSwitch.setChecked(preference.available && preference.enabled);
        renderingSwitch = false;
        boolean mayTurnOff = preference.available && preference.enabled;
        boolean mayTurnOn = preference.available && current != null && current.canEnable && validControlTicket();
        enabledSwitch.setEnabled(!busy && (mayTurnOff || mayTurnOn));
        enabledSwitch.setAlpha(enabledSwitch.isEnabled() ? 1f : .5f);
        savedSetting.setText(preference.writeUnconfirmed ? R.string.dashboard_setting_unconfirmed
                : saveFailure != SAVE_OK ? R.string.dashboard_setting_unsaved
                : !preference.available ? R.string.dashboard_setting_unknown
                : preference.enabled ? R.string.dashboard_setting_on : R.string.dashboard_setting_off);
        enabledSwitch.setStateDescription(savedSetting.getText());
        saveWarning.setVisibility(saveFailure != SAVE_OK || !preference.available ? View.VISIBLE : View.GONE);
        saveWarning.setText(preference.writeUnconfirmed ? R.string.dashboard_save_unconfirmed
                : saveFailure == SAVE_DISABLE_FAILED ? R.string.dashboard_save_off_failed
                : saveFailure == SAVE_ENABLE_FAILED ? R.string.dashboard_save_on_failed
                : R.string.dashboard_setting_read_failed);
        applySetting.setVisibility(current != null && current.needsApply ? View.VISIBLE : View.GONE);
        stopCurrent.setVisibility(current == null || !preference.available || saveFailure == SAVE_DISABLE_FAILED
                || current.needsStop || current.kind == DashboardState.Kind.UNKNOWN
                || current.kind == DashboardState.Kind.LOCKED || current.kind == DashboardState.Kind.HOOK_FAILURE
                || current.kind == DashboardState.Kind.CONTROL_ERROR ? View.VISIBLE : View.GONE);
        for (Button button : buttons) {
            button.setEnabled(!busy);
            button.setAlpha(busy ? .45f : 1f);
        }
        int title = R.string.dashboard_unchecked_title, body = R.string.dashboard_unchecked_body;
        if (current == null) {
            if ("MODULE_VERSION_MISMATCH".equals(responseIssue)) {
                title = R.string.dashboard_mismatch_title;
                body = R.string.dashboard_mismatch_body;
            } else if (responseIssue != null) {
                title = R.string.dashboard_unknown_title;
                body = R.string.dashboard_unknown_body;
            }
        } else {
            switch (current.kind) {
                case READY -> {
                    title = preference.available && preference.enabled ? R.string.dashboard_pending_title : R.string.dashboard_ready_title;
                    body = preference.available && preference.enabled ? R.string.dashboard_pending_body : R.string.dashboard_ready_body;
                }
                case ACTIVE -> {
                    title = R.string.dashboard_active_title;
                    body = preference.available && !preference.enabled ? R.string.dashboard_still_active_body : R.string.dashboard_active_body;
                }
                case WAITING_TARGET -> { title = R.string.dashboard_waiting_title; body = R.string.dashboard_waiting_body; }
                case RECOVERING -> { title = R.string.dashboard_recovering_title; body = R.string.dashboard_recovering_body; }
                case LOCKED -> { title = R.string.dashboard_locked_title; body = R.string.dashboard_locked_body; }
                case HOOK_FAILURE -> { title = R.string.dashboard_hook_failure_title; body = R.string.dashboard_hook_failure_body; }
                case CONTROL_ERROR -> { title = R.string.dashboard_control_error_title; body = R.string.dashboard_control_error_body; }
                default -> { title = R.string.dashboard_invalid_title; body = R.string.dashboard_invalid_body; }
            }
        }
        stateTitle.setText(title);
        stateBody.setText(body);
        boolean positive = current != null && current.positive && saveFailure == SAVE_OK;
        int textColor = color(positive ? R.color.dashboard_positive_text : R.color.dashboard_warning_text);
        stateTitle.setTextColor(textColor);
        stateBody.setTextColor(textColor);
        sample.setTextColor(textColor);
        ((TextView) stateCard.getChildAt(0)).setTextColor(textColor);
        stateCard.setBackground(shape(color(positive ? R.color.dashboard_positive_surface : R.color.dashboard_warning_surface), 0, 24));
        sample.setText(sampledAt == null ? getString(R.string.dashboard_sample_unknown)
                : getString(R.string.dashboard_sample_hint, sampledAt));
    }

    @SuppressWarnings("deprecation")
    private CompanionAppState refreshWallpaperApp() {
        CompanionAppState installed;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(BuildConfig.WALLPAPER_APPLICATION_ID, 0);
            Intent launch = getPackageManager().getLaunchIntentForPackage(BuildConfig.WALLPAPER_APPLICATION_ID);
            String launcherPackage = launch == null || launch.getComponent() == null
                    ? null : launch.getComponent().getPackageName();
            installed = CompanionAppState.installed(BuildConfig.WALLPAPER_APPLICATION_ID, info.packageName,
                    info.versionName, info.getLongVersionCode(),
                    info.applicationInfo == null || info.applicationInfo.enabled, launcherPackage);
        } catch (PackageManager.NameNotFoundException ignored) {
            installed = CompanionAppState.notInstalled();
        } catch (SecurityException ignored) {
            installed = CompanionAppState.unavailable();
        }
        switch (installed.availability) {
            case NOT_INSTALLED -> {
                wallpaperIdentity.setText(R.string.dashboard_wallpaper_missing);
                wallpaperAvailability.setText(R.string.dashboard_wallpaper_unavailable);
            }
            case UNAVAILABLE -> {
                wallpaperIdentity.setText(R.string.dashboard_wallpaper_status_unknown);
                wallpaperAvailability.setText(R.string.dashboard_wallpaper_query_unavailable);
            }
            case INSTALLED -> {
                String name = installed.versionName == null ? getString(R.string.dashboard_version_unknown) : installed.versionName;
                String code = installed.versionCode < 0 ? getString(R.string.dashboard_version_unknown) : Long.toString(installed.versionCode);
                wallpaperIdentity.setText(getString(R.string.dashboard_wallpaper_installed, name, code));
                wallpaperAvailability.setText(installed.canOpen ? R.string.dashboard_wallpaper_ready : R.string.dashboard_wallpaper_no_launcher);
            }
        }
        return installed;
    }

    private void openWallpaperApp() {
        CompanionAppState installed = refreshWallpaperApp();
        if (!installed.canOpen) {
            showNotice(installed.availability == CompanionAppState.Availability.NOT_INSTALLED
                    ? R.string.dashboard_wallpaper_unavailable
                    : installed.availability == CompanionAppState.Availability.UNAVAILABLE
                    ? R.string.dashboard_wallpaper_query_unavailable : R.string.dashboard_wallpaper_no_launcher);
            return;
        }
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(BuildConfig.WALLPAPER_APPLICATION_ID);
            if (launch == null || launch.getComponent() == null
                    || !BuildConfig.WALLPAPER_APPLICATION_ID.equals(launch.getComponent().getPackageName())) {
                refreshWallpaperApp();
                showNotice(R.string.dashboard_wallpaper_no_launcher);
                return;
            }
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(BuildConfig.WALLPAPER_APPLICATION_ID).setComponent(launch.getComponent()));
        } catch (ActivityNotFoundException ignored) {
            refreshWallpaperApp();
            showNotice(R.string.dashboard_wallpaper_no_launcher);
        } catch (SecurityException ignored) {
            refreshWallpaperApp();
            showNotice(R.string.dashboard_wallpaper_query_unavailable);
        }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = column();
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(shape(color(R.color.dashboard_surface), color(R.color.dashboard_outline), 24));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        parent.addView(card, params);
        return card;
    }
    private TextView text(LinearLayout parent, String value, float size, boolean bold, int textColor) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(textColor));
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private Button button(LinearLayout parent, int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        button.setTextColor(color(R.color.dashboard_accent));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.dashboard_ripple)),
                shape(color(R.color.dashboard_surface), color(R.color.dashboard_outline), 16), null));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(button, params);
        buttons.add(button);
        return button;
    }
    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private void margin(View view, int top, int bottom) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        view.setLayoutParams(params);
    }
    private void showNotice(int message) { notice.setText(message); notice.setVisibility(View.VISIBLE); }
    private int color(int resource) { return getColor(resource); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onResume() {
        super.onResume();
        if (wallpaperIdentity != null) refreshStatus();
        main.removeCallbacks(lockStatusRefresh);
        lockStatusRefresh.run();
    }
    @Override protected void onPause() {
        main.removeCallbacks(lockStatusRefresh);
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("preferenceSaveFailure", saveFailure);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
