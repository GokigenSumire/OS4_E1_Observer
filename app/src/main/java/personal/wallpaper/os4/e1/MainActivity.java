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
import java.util.ArrayList;
import personal.wallpaper.os4.e1.core.HomeScope;
import personal.wallpaper.os4.e1.relay.LockRelayService;

/** 桌面和锁屏分别保存、分别呈现；主开关可一直操作，实际准入仍由各自服务确认。 */
public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final java.util.concurrent.ExecutorService updateExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private DesktopToggleController home;
    private Switch enabledSwitch, lockEnabledSwitch, nativeSwitch, forceLocalSwitch, prioritySwitch;
    private TextView homeState, homeNotice, lockStatus, nativeStatus, forceLocalStatus, priorityStatus;
    private TextView wallpaperIdentity, wallpaperAvailability, notice, updateStatus;
    private Button checkUpdates, homeRetry;
    private personal.wallpaper.update.ReleaseUpdateChecker.Result updateResult;
    private boolean renderingHome, renderingLock, renderingNative, renderingForce, renderingPriority, destroyed;
    private final Runnable lockStatusRefresh = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            renderLock();
            main.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_HomeCompat);
        super.onCreate(state);
        home = new DesktopToggleController(new DesktopToggleController.Store() {
            public CompatPreferenceStore.State read() { return CompatPreferenceStore.read(MainActivity.this); }
            public boolean save(boolean enabled) { return CompatPreferenceStore.setEnabled(MainActivity.this, enabled); }
        }, new DesktopToggleController.Backend() {
            public void status(DesktopToggleController.Callback cb) { sendHome("STATUS", null, -1, cb); }
            public void enable(CompatPreferenceStore.State saved, DesktopToggleController.Ticket ticket, DesktopToggleController.Callback cb) {
                // 控制器已校验最新代次／保存revision；传递同一保存结果，不读取另一次选择的revision。
                sendHome("DAILY_ON", ticket, saved.revision, cb);
            }
            public void stop(long revision, DesktopToggleController.Callback cb) { sendHome("DAILY_OFF", null, revision, cb); }
        }, this::renderDesktop);
        createDashboard();
        renderDesktop(); renderLock();
    }
    private void createDashboard() {
        LinearLayout screen = column();
        screen.setBackgroundColor(color(R.color.dashboard_background));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout box = column(); box.setPadding(dp(20), dp(16), dp(20), dp(28)); scroll.addView(box);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1,-1));
        screen.setOnApplyWindowInsetsListener((view,insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(44),dp(44)); iconSize.setMarginEnd(dp(12)); brand.addView(icon,iconSize);
        LinearLayout brandText=column(); text(brandText,"Hyper Wallpaper 兼容",23,true,R.color.dashboard_text);
        text(brandText,BuildConfig.VERSION_NAME,12,false,R.color.dashboard_secondary); brand.addView(brandText,new LinearLayout.LayoutParams(0,-2,1));
        box.addView(brand); margin(brand,0,20);

        LinearLayout desktop=card(box);
        enabledSwitch=targetSwitch(desktop,"桌面","home_switch");
        homeState=text(desktop,"状态待确认",14,true,R.color.dashboard_secondary);homeState.setTag("home_status");
        homeState.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        homeNotice=text(desktop,"",13,false,R.color.dashboard_warning_text);homeNotice.setTag("home_notice");
        homeNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        homeRetry=button(desktop,R.string.dashboard_retry,home::retry);
        enabledSwitch.setOnCheckedChangeListener((view,checked)-> {if(!renderingHome)home.change(checked);});

        LinearLayout lock=card(box);
        lockEnabledSwitch=targetSwitch(lock,"锁屏","lock_switch");
        lockStatus=text(lock,"",14,true,R.color.dashboard_secondary);lockStatus.setTag("lock_status");
        lockStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        lockEnabledSwitch.setOnCheckedChangeListener((view,checked)-> {
            if(renderingLock)return;
            boolean saved=LockRelayService.setDesiredEnabled(this,checked);
            renderLock();
            if(!saved)android.widget.Toast.makeText(this,"锁屏设置未保存，请重试",android.widget.Toast.LENGTH_SHORT).show();
        });
        LinearLayout lockOptions=column();lockOptions.setVisibility(View.GONE);
        Button disclosure=button(lock,R.string.dashboard_lock_options,()->lockOptions.setVisibility(lockOptions.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        disclosure.setTag("lock_options_disclosure");lock.addView(lockOptions);
        nativeSwitch=targetSwitch(lockOptions,"原生锁屏输入","native_input_switch");
        nativeStatus=text(lockOptions,"",12,false,R.color.dashboard_secondary);
        nativeSwitch.setOnCheckedChangeListener((view,checked)-> {
            if(renderingNative)return; boolean saved=LockRelayService.setNativeInputEnabled(this,checked);renderOptions();
            if(!saved)nativeStatus.setText("未保存，请重试");
        });
        forceLocalSwitch=targetSwitch(lockOptions,"强制局部兼容","force_local_switch");
        forceLocalStatus=text(lockOptions,"",12,false,R.color.dashboard_secondary);
        forceLocalSwitch.setOnCheckedChangeListener((view,checked)-> {
            if(renderingForce)return;
            if(!checked){saveForce(false);return;}
            renderOptions();
            new AlertDialog.Builder(this).setTitle("开启强制局部兼容？")
                .setMessage("屏幕顶部20%不识别手势，其余区域可能与系统控件冲突。编辑仍需正常认证。")
                .setNegativeButton("取消",null).setPositiveButton("开启",(dialog,which)->saveForce(true)).show();
        });
        prioritySwitch=targetSwitch(lockOptions,"锁屏手势优先","wallpaper_priority_switch");
        priorityStatus=text(lockOptions,"",12,false,R.color.dashboard_secondary);
        prioritySwitch.setOnCheckedChangeListener((view,checked)-> {
            if(renderingPriority)return;
            if(!checked){savePriority(false);return;}
            renderOptions();
            new AlertDialog.Builder(this).setTitle("开启锁屏手势优先？")
                .setMessage("通知、音乐控件和顶部边缘也参与识别，系统动作可能同时响应；编辑仍需正常解锁。关闭恢复原策略。")
                .setNegativeButton("取消",null).setPositiveButton("开启",(dialog,which)->savePriority(true)).show();
        });
        renderOptions();

        LinearLayout companion=card(box);
        text(companion,"主壁纸",16,true,R.color.dashboard_text);
        wallpaperIdentity=text(companion,"",14,false,R.color.dashboard_secondary);
        wallpaperAvailability=text(companion,"",12,false,R.color.dashboard_secondary);
        button(companion,R.string.dashboard_open_wallpaper,this::openWallpaperApp);

        LinearLayout tools=card(box);
        button(tools,R.string.dashboard_refresh,home::refresh);
        button(tools,R.string.dashboard_debug_logs,()->startActivity(new Intent(this,LockDebugActivity.class)));
        button(tools,R.string.dashboard_scope_details,()->new AlertDialog.Builder(this).setTitle("帮助与恢复")
            .setMessage("桌面与锁屏各自开启，仅作用于本项目壁纸。需要Root／LSPosed。桌面需系统框架作用域，锁屏另需SystemUI；更新后正常重载。\n\n开关表示已保存选择，实际状态分别显示。系统恢复未确认时开启会等待；仍可随时保存关闭。异常时可在LSPosed停用模块并正常重启，编辑仍需系统认证。")
            .setPositiveButton("知道了",null).show());

        LinearLayout about=card(box);
        text(about,"关于与更新",16,true,R.color.dashboard_text);
        text(about,BuildConfig.VERSION_NAME+"（"+BuildConfig.VERSION_CODE+"）",13,false,R.color.dashboard_secondary);
        updateStatus=text(about,"",13,false,R.color.dashboard_secondary);updateStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        checkUpdates=button(about,R.string.about_check_updates,this::checkForUpdates);
        button(about,R.string.about_releases,()->openProjectUrl(updateResult==null?personal.wallpaper.update.ReleaseUpdateChecker.Product.MODULE.releasesUrl():updateResult.pageUrl));
        LinearLayout info=column();info.setVisibility(View.GONE);
        button(about,R.string.dashboard_project_info,()->info.setVisibility(info.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));about.addView(info);
        button(info,R.string.about_copy_version,()-> {
            android.content.ClipboardManager clip=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clip.setPrimaryClip(android.content.ClipData.newPlainText("版本信息","Hyper Wallpaper 兼容\n"+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")\n"+BuildConfig.APPLICATION_ID));
            android.widget.Toast.makeText(this,"已复制版本信息",android.widget.Toast.LENGTH_SHORT).show();
        });
        projectLink(info,"主应用源码","https://github.com/GokigenSumire/OS3_Wallpaper_Changer");
        projectLink(info,"模块源码","https://github.com/GokigenSumire/OS4_E1_Observer");
        projectLink(info,"问题反馈","https://github.com/GokigenSumire/OS4_E1_Observer/issues");
        text(info,"GPL-3.0，来源与声明见项目仓库",12,false,R.color.dashboard_secondary);
        notice=text(box,"",13,false,R.color.dashboard_warning_text);notice.setVisibility(View.GONE);
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        setContentView(screen);
    }
    private Switch targetSwitch(LinearLayout parent,String label,String tag) {
        Switch view=new Switch(this);view.setText(label);view.setTag(tag);view.setTextSize(19);
        view.setTextColor(color(R.color.dashboard_text));view.setMinHeight(dp(56));
        parent.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;
    }
    private void renderDesktop() {
        if(enabledSwitch==null||destroyed)return;
        CompatPreferenceStore.State saved=home.saved();
        renderingHome=true;enabledSwitch.setChecked(saved.available&&saved.enabled);renderingHome=false;
        enabledSwitch.setEnabled(true);
        CompatTargetPresentation state=CompatTargetPresentation.desktop(home.actual,saved,home.pending);
        homeState.setText(state.text);homeState.setTextColor(color(state.positive?R.color.dashboard_positive_text:R.color.dashboard_secondary));
        homeNotice.setText(home.message);homeNotice.setVisibility(home.message.isEmpty()?View.GONE:View.VISIBLE);
        enabledSwitch.setStateDescription(state.text);
        boolean settled=home.actual!=null&&saved.available&&(
            saved.enabled&&(home.actual.kind==DashboardState.Kind.ACTIVE||home.actual.kind==DashboardState.Kind.WAITING_TARGET)
            ||!saved.enabled&&home.actual.kind==DashboardState.Kind.READY);
        homeRetry.setVisibility(!home.pending&&(!settled||home.savingFailed)?View.VISIBLE:View.GONE);
    }
    private void renderLock() {
        if(lockEnabledSwitch==null||destroyed)return;
        boolean desired=LockRelayService.isDesiredEnabled(this);
        renderingLock=true;lockEnabledSwitch.setChecked(desired);renderingLock=false;
        lockEnabledSwitch.setEnabled(true);
        CompatTargetPresentation state=CompatTargetPresentation.lock(desired,LockRelayService.status(this));
        lockStatus.setText(state.text);lockStatus.setTextColor(color(state.positive?R.color.dashboard_positive_text:R.color.dashboard_secondary));
        lockEnabledSwitch.setStateDescription(state.text);
    }
    private void renderOptions() {
        if(nativeSwitch==null)return;
        var nativeInput=LockRelayService.nativeInputState(this);var force=LockRelayService.forceLocalState(this);var priority=LockRelayService.wallpaperPriorityState(this);
        renderingNative=true;nativeSwitch.setChecked(nativeInput.available&&nativeInput.enabled);renderingNative=false;
        renderingForce=true;forceLocalSwitch.setChecked(force.available&&force.enabled);renderingForce=false;
        renderingPriority=true;prioritySwitch.setChecked(priority.available&&priority.enabled);renderingPriority=false;
        nativeStatus.setText(!nativeInput.available?"设置未确认":nativeInput.enabled?"当前锁屏识别，可能替代双击息屏":"使用兼容回退路线");
        forceLocalStatus.setText(!force.available?"设置未确认":force.enabled?"顶部20%不识别":"保守策略");
        priorityStatus.setText(!priority.available?"设置未确认":priority.enabled?"系统动作可能同时响应":"常规优先级");
    }
    private void saveForce(boolean enabled) { boolean saved=LockRelayService.setForceLocalEnabled(this,enabled);renderOptions();if(!saved)forceLocalStatus.setText("未保存，请重试"); }
    private void savePriority(boolean enabled) { boolean saved=LockRelayService.setWallpaperPriorityEnabled(this,enabled);renderOptions();if(!saved)priorityStatus.setText("未保存，请重试"); }

    private void sendHome(String cmd,DesktopToggleController.Ticket ticket,long revision,DesktopToggleController.Callback callback) {
        Intent intent=new Intent(ControlServer.ACTION).setPackage("android");intent.putExtra("cmd",cmd);
        if(!"STATUS".equals(cmd)) {
            intent.putExtra("epoch",ticket==null?-1:ticket.epoch);intent.putExtra("bootSession",ticket==null?null:ticket.boot);intent.putExtra("desiredRevision",revision);
        }
        final boolean[] finished={false};
        Runnable timeout=()-> {if(!finished[0]&&!destroyed){finished[0]=true;callback.accept(new DesktopToggleController.Response(null,null,"NO_CONTROL_RESPONSE"));}};
        main.postDelayed(timeout,8000);
        try {
            sendOrderedBroadcast(intent,null,new BroadcastReceiver() {
                @Override public void onReceive(Context context,Intent received) {
                    if(finished[0]||destroyed)return;finished[0]=true;main.removeCallbacks(timeout);
                    Bundle result=getResultExtras(false);
                    if(result==null||!result.containsKey("module")){callback.accept(new DesktopToggleController.Response(null,null,"NO_CONTROL_RESPONSE"));return;}
                    if(!HomeScope.responseAllowed(result.getString("module"),BuildConfig.VERSION_CODE,BuildConfig.VERSION_NAME)) {
                        callback.accept(new DesktopToggleController.Response(null,null,"MODULE_VERSION_MISMATCH"));return;
                    }
                    CompatPreferenceStore.State saved=home.saved();
                    DashboardState actual=DashboardState.from(result.getString("contrast"),result.getString("hooks"),result.getString("bootstrap"),
                        result.getString("coverage"),result.getString("error"),saved.available,saved.enabled);
                    String error=result.getString("error");if("NONE".equals(error)||"".equals(error))error=null;
                    callback.accept(new DesktopToggleController.Response(actual,new DesktopToggleController.Ticket(result.getLong("epoch",-1),result.getString("bootSession")),error));
                }
            },main,0,null,null);
        } catch(RuntimeException unavailable) {
            finished[0]=true;main.removeCallbacks(timeout);callback.accept(new DesktopToggleController.Response(null,null,"NO_CONTROL_RESPONSE"));
        }
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
    private void checkForUpdates() {
        if (destroyed || !checkUpdates.isEnabled()) return;
        checkUpdates.setEnabled(false);
        updateStatus.setText("正在检查更新…");
        updateExecutor.execute(() -> {
            var result = new personal.wallpaper.update.ReleaseUpdateChecker().check(
                    personal.wallpaper.update.ReleaseUpdateChecker.Product.MODULE, BuildConfig.VERSION_CODE);
            main.post(() -> {
                if (destroyed) return;
                updateResult = result;
                updateStatus.setText(result.message());
                checkUpdates.setEnabled(true);
            });
        });
    }
    private void openProjectUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
        catch (ActivityNotFoundException | SecurityException unavailable) {
            android.widget.Toast.makeText(this, "未找到可打开链接的应用", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    private void projectLink(LinearLayout parent, String label, String url) {
        TextView link = text(parent, label, 15, true, R.color.dashboard_accent);
        link.setMinHeight(dp(48)); link.setGravity(Gravity.CENTER_VERTICAL);
        link.setContentDescription(label + "，" + url);
        link.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
            catch (ActivityNotFoundException | SecurityException unavailable) {
                android.widget.Toast.makeText(this, "未找到可打开链接的应用", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
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
        refreshWallpaperApp();
        renderOptions();
        home.refresh();
        main.removeCallbacks(lockStatusRefresh);
        lockStatusRefresh.run();
    }
    @Override protected void onPause() {
        main.removeCallbacks(lockStatusRefresh);
        super.onPause();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        home.close();
        updateExecutor.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
