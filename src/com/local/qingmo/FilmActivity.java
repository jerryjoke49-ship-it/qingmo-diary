package com.local.qingmo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class FilmActivity extends Activity {
    private static final int REQUEST_EXPORT = 801;
    private static final int REQUEST_IMPORT = 802;
    private static final String FILM_HOME = "file:///android_asset/film/index.html";
    private static final String FILM_EDITOR = "file:///android_asset/film/editor.html";

    private int PAPER;
    private int BAR;
    private int INK;
    private int MUTED;
    private int LINE;
    private int ACCENT;
    private boolean lightMode;

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;
    private String pendingExportJson;
    private String pendingExportName;
    private boolean migrationHintShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadTheme();
        configureWindow();
        buildInterface();
        webView.loadUrl(FILM_HOME + "?theme=" + (lightMode ? "light" : "dark"));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(PAPER));
        window.setStatusBarColor(PAPER);
        window.setNavigationBarColor(PAPER);
        int flags = lightMode ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
        if (lightMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void loadTheme() {
        lightMode = getSharedPreferences("qingji-settings", MODE_PRIVATE)
                .getBoolean("notes_light_mode", false);
        if (lightMode) {
            PAPER = Color.rgb(242, 236, 226);
            BAR = Color.rgb(250, 247, 240);
            INK = Color.rgb(39, 35, 31);
            MUTED = Color.rgb(112, 104, 95);
            LINE = Color.rgb(216, 205, 190);
            ACCENT = Color.rgb(166, 83, 67);
        } else {
            PAPER = Color.rgb(20, 18, 16);
            BAR = Color.rgb(25, 23, 21);
            INK = Color.rgb(244, 239, 231);
            MUTED = Color.rgb(184, 174, 164);
            LINE = Color.rgb(59, 52, 46);
            ACCENT = Color.rgb(208, 106, 86);
        }
    }

    private void applyWebTheme(WebView view) {
        String theme = lightMode ? "light" : "dark";
        view.evaluateJavascript(
                "document.documentElement.dataset.theme='" + theme + "';" +
                        "document.documentElement.style.colorScheme='" + theme + "';" +
                        "try{localStorage.setItem('qingji:theme','" + theme + "');}catch(e){}",
                null
        );
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);

        webView = new WebView(this);
        webView.setBackgroundColor(PAPER);
        configureWebView();
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
        );
        barParams.gravity = Gravity.BOTTOM;
        barParams.setMargins(dp(16), 0, dp(16), dp(12));
        root.addView(buildModuleSwitcher(), barParams);
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        WebView.setWebContentsDebuggingEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.addJavascriptInterface(new FilmBridge(), "AndroidFilmBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                applyWebTheme(view);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                applyWebTheme(view);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (pendingFileCallback != null) {
                    // A previous picker was replaced before it completed.
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json",
                        "text/plain",
                        "application/octet-stream",
                        "image/*"
                });
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivityForResult(intent, REQUEST_IMPORT);
                } catch (ActivityNotFoundException error) {
                    pendingFileCallback = null;
                    filePathCallback.onReceiveValue(null);
                    toast("手机上没有可用的文件选择器");
                    return true;
                }
                return true;
            }

            @Override
            public boolean onJsConfirm(
                    WebView view,
                    String url,
                    String message,
                    final JsResult result
            ) {
                AlertDialog dialog = new AlertDialog.Builder(FilmActivity.this)
                        .setTitle("确认操作")
                        .setMessage(message)
                        .setPositiveButton("确定", (ignored, which) -> result.confirm())
                        .setNegativeButton("取消", (ignored, which) -> result.cancel())
                        .create();
                dialog.setOnCancelListener(ignored -> result.cancel());
                dialog.show();
                return true;
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) {
            return true;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("file".equals(scheme)) {
            return false;
        }
        if ("http".equals(scheme) || "https".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException error) {
                toast("没有可打开该链接的应用");
            }
            return true;
        }
        return true;
    }

    private View buildModuleSwitcher() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(5), dp(5), dp(5), dp(5));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(BAR);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), LINE);
        bar.setBackground(background);
        bar.setElevation(dp(9));

        TextView notes = tab("✎  随笔", false);
        notes.setOnClickListener(view -> requestLeaveForNotes());
        bar.addView(notes, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView films = tab("▣  影视", true);
        films.setOnClickListener(view -> { });
        LinearLayout.LayoutParams filmParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        filmParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(films, filmParams);

        TextView anime = tab("◈  动漫", false);
        anime.setOnClickListener(view -> checkUnsavedFilmChanges(
                "现在切换到动漫，会丢失编辑页中尚未保存的修改。",
                this::goToAnimeModule
        ));
        LinearLayout.LayoutParams animeParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        animeParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(anime, animeParams);

        TextView timeline = tab("◷  时间线", false);
        timeline.setOnClickListener(view -> checkUnsavedFilmChanges(
                "现在切换到时间线，会丢失编辑页中尚未保存的修改。",
                this::goToTimelineModule
        ));
        LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        timelineParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(timeline, timelineParams);
        return bar;
    }

    private void requestLeaveForNotes() {
        if (webView == null) {
            finish();
            return;
        }
        checkUnsavedFilmChanges(
                "现在切换到随笔，会丢失编辑页中尚未保存的修改。",
                this::goToNotesModule
        );
    }

    private void goToNotesModule() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
        finish();
    }

    private void goToTimelineModule() {
        startActivity(new Intent(this, TimelineActivity.class));
        overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
        finish();
    }

    private void goToAnimeModule() {
        startActivity(new Intent(this, AnimeActivity.class));
        overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
        finish();
    }

    private void checkUnsavedFilmChanges(String message, Runnable proceed) {
        webView.evaluateJavascript(
                "window.hasUnsavedFilmChanges ? window.hasUnsavedFilmChanges() : false",
                value -> {
                    if (!"true".equals(value)) {
                        proceed.run();
                        return;
                    }
                    new AlertDialog.Builder(FilmActivity.this)
                            .setTitle("还有未保存的影视记录")
                            .setMessage(message)
                            .setPositiveButton("继续编辑", null)
                            .setNegativeButton("放弃修改", (ignored, which) -> {
                                webView.evaluateJavascript(
                                        "window.clearFilmDirtyChanges && window.clearFilmDirtyChanges()",
                                        null
                                );
                                proceed.run();
                            })
                            .show();
                }
        );
    }

    private TextView tab(String label, boolean selected) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(11);
        view.setTextColor(selected ? ACCENT : MUTED);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(
                selected
                        ? (lightMode ? Color.rgb(239, 224, 216) : Color.rgb(58, 36, 32))
                        : Color.TRANSPARENT
        );
        background.setCornerRadius(dp(20));
        view.setBackground(background);
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(selected ? label + "，当前模块" : "切换到" + label);
        return view;
    }

    private void maybeShowMigrationHint(String url) {
        if (migrationHintShown || url == null || !url.contains("/film/index.html")) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences("qingji-settings", Context.MODE_PRIVATE);
        if (preferences.getBoolean("film_migration_hint_seen", false)) {
            return;
        }
        migrationHintShown = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("从网页迁移已有记录")
                .setMessage("如果你在原网页写过评分、影评或标签，请先在网页点“导出备份”，再进入片单编辑页点“导入备份”。")
                .setPositiveButton("知道了", (ignored, which) ->
                        preferences.edit().putBoolean("film_migration_hint_seen", true).apply())
                .setNeutralButton("打开编辑", (ignored, which) -> {
                    preferences.edit().putBoolean("film_migration_hint_seen", true).apply();
                    webView.loadUrl(FILM_EDITOR);
                })
                .create();
        dialog.setOnCancelListener(ignored ->
                preferences.edit().putBoolean("film_migration_hint_seen", true).apply());
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            deliverFileChooserResult(result);
            return;
        }
        if (requestCode == REQUEST_EXPORT) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                notifyExportResult(false, "已取消导出");
                clearPendingExport();
                return;
            }
            writeExport(data.getData());
        }
    }

    private void deliverFileChooserResult(Uri[] result) {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(result);
            pendingFileCallback = null;
        }
    }

    private void writeExport(Uri target) {
        if (pendingExportJson == null) {
            notifyExportResult(false, "没有可导出的数据");
            return;
        }
        try (OutputStream stream = getContentResolver().openOutputStream(target, "wt")) {
            if (stream == null) {
                throw new IllegalStateException("无法创建文件");
            }
            Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            writer.write(pendingExportJson);
            writer.flush();
            notifyExportResult(true, "备份已保存");
            toast("影视记录备份已保存");
        } catch (Exception error) {
            notifyExportResult(false, "保存失败");
            toast("备份保存失败：" + error.getMessage());
        } finally {
            clearPendingExport();
        }
    }

    private void notifyExportResult(boolean success, String message) {
        if (webView == null) {
            return;
        }
        String script = "window.onFilmBackupExportResult && window.onFilmBackupExportResult(" +
                success + "," + JSONObject.quote(message) + ");";
        webView.evaluateJavascript(script, null);
    }

    private void clearPendingExport() {
        pendingExportJson = null;
        pendingExportName = null;
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        String url = webView.getUrl();
        if (url != null && url.contains("/film/editor.html")) {
            checkUnsavedFilmChanges("离开编辑页会丢失尚未保存的修改。", this::performWebBack);
            return;
        }
        performWebBack();
    }

    private void performWebBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
            }
        }
    }

    @Override
    protected void onStop() {
        if (webView != null && isFinishing() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidFilmBridge");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private final class FilmBridge {
        @JavascriptInterface
        public void exportFilmBackup(String json, String filename) {
            runOnUiThread(() -> beginExport(json, filename));
        }
    }

    private void beginExport(String json, String filename) {
        if (json == null || json.trim().isEmpty()) {
            notifyExportResult(false, "没有可导出的数据");
            return;
        }
        pendingExportJson = json;
        pendingExportName = safeFilename(filename);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, pendingExportName);
        try {
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (ActivityNotFoundException error) {
            notifyExportResult(false, "手机上没有可用的文件保存器");
            clearPendingExport();
        }
    }

    private String safeFilename(String filename) {
        String clean = filename == null ? "" : filename.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (clean.isEmpty()) {
            String stamp = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            clean = "银幕档案-影视记录-" + stamp + ".json";
        }
        return clean;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
