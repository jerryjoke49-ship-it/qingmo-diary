package com.local.qingmo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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

public final class AnimeActivity extends Activity {
    static final String EXTRA_ANIME_ID = "com.local.qingmo.OPEN_ANIME_ID";
    private static final String ANIME_URL = "file:///android_asset/film/anime.html";
    private static final int REQUEST_IMPORT = 901;
    private static final int REQUEST_EXPORT = 902;
    private int PAPER;
    private int BAR;
    private int MUTED;
    private int LINE;
    private int ACCENT;
    private boolean lightMode;
    private WebView webView;
    private ValueCallback<Uri[]> pendingFileCallback;
    private String pendingExportJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadTheme();
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(PAPER));
        window.setStatusBarColor(PAPER);
        window.setNavigationBarColor(PAPER);
        int flags = lightMode ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
        if (lightMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        buildInterface();
        String url = ANIME_URL + "?theme=" + (lightMode ? "light" : "dark");
        String requestedAnime = getIntent().getStringExtra(EXTRA_ANIME_ID);
        if (requestedAnime != null && !requestedAnime.trim().isEmpty()) {
            url += "&openId=" + Uri.encode(requestedAnime.trim());
        }
        webView.loadUrl(url);
    }

    private void loadTheme() {
        lightMode = getSharedPreferences("qingji-settings", MODE_PRIVATE)
                .getBoolean("notes_light_mode", false);
        if (lightMode) {
            PAPER = Color.rgb(242, 236, 226);
            BAR = Color.rgb(250, 247, 240);
            MUTED = Color.rgb(112, 104, 95);
            LINE = Color.rgb(216, 205, 190);
            ACCENT = Color.rgb(166, 83, 67);
        } else {
            PAPER = Color.rgb(20, 18, 16);
            BAR = Color.rgb(25, 23, 21);
            MUTED = Color.rgb(184, 174, 164);
            LINE = Color.rgb(59, 52, 46);
            ACCENT = Color.rgb(208, 106, 86);
        }
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        webView = new WebView(this);
        webView.setBackgroundColor(PAPER);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        WebView.setWebContentsDebuggingEnabled(false);
        webView.setWebViewClient(new WebViewClient() {
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
        webView.addJavascriptInterface(new AnimeBridge(), "AndroidAnimeBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json",
                        "text/plain",
                        "image/*"
                });
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivityForResult(intent, REQUEST_IMPORT);
                } catch (ActivityNotFoundException error) {
                    pendingFileCallback = null;
                    callback.onReceiveValue(null);
                    toast("手机上没有可用的文件选择器");
                }
                return true;
            }

            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    final JsResult result
            ) {
                new AlertDialog.Builder(AnimeActivity.this)
                        .setMessage(message)
                        .setPositiveButton("知道了", (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        FrameLayout navigationHost = new FrameLayout(this);
        navigationHost.setPadding(dp(12), dp(6), dp(12), dp(12));
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62),
                Gravity.BOTTOM
        );
        navigationHost.addView(buildModuleSwitcher(), barParams);
        root.addView(navigationHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(74)
        ));
        setContentView(root);
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

    private View buildModuleSwitcher() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(4), dp(5), dp(4), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(BAR);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), LINE);
        bar.setBackground(background);
        bar.setElevation(dp(9));

        bar.addView(tab("✎  随笔", false, 1f, view -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
            finish();
        }), tabParams(0));
        bar.addView(tab("▣  影视", false, 1f, view -> {
            startActivity(new Intent(this, FilmActivity.class));
            overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
            finish();
        }), tabParams(1));
        bar.addView(tab("◈  动漫", true, 1f, view -> { }), tabParams(2));
        bar.addView(tab("◷  时间线", false, 1f, view -> {
            startActivity(new Intent(this, TimelineActivity.class));
            overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
            finish();
        }), tabParams(3));
        return bar;
    }

    private LinearLayout.LayoutParams tabParams(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
        if (index > 0) params.setMargins(dp(3), 0, 0, 0);
        return params;
    }

    private TextView tab(String label, boolean selected, float weight, View.OnClickListener listener) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(11);
        view.setTextColor(selected ? ACCENT : MUTED);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected
                ? (lightMode ? Color.rgb(239, 224, 216) : Color.rgb(58, 36, 32))
                : Color.TRANSPARENT);
        background.setCornerRadius(dp(20));
        view.setBackground(background);
        view.setOnClickListener(listener);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            if (pendingFileCallback != null) {
                pendingFileCallback.onReceiveValue(result);
                pendingFileCallback = null;
            }
            return;
        }
        if (requestCode == REQUEST_EXPORT) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                notifyExport(false, "已取消导出");
            } else {
                writeExport(data.getData());
            }
        }
    }

    private final class AnimeBridge {
        @JavascriptInterface
        public void exportAnimeBackup(String json, String filename) {
            runOnUiThread(() -> beginExport(json));
        }
    }

    private void beginExport(String json) {
        if (json == null || json.trim().isEmpty()) {
            notifyExport(false, "没有可导出的数据");
            return;
        }
        pendingExportJson = json;
        String stamp = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "青茉日记-动漫备份-" + stamp + ".json");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void writeExport(Uri target) {
        if (pendingExportJson == null) {
            notifyExport(false, "没有可导出的数据");
            return;
        }
        try (OutputStream stream = getContentResolver().openOutputStream(target, "wt")) {
            if (stream == null) {
                throw new IllegalStateException("无法创建文件");
            }
            Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            writer.write(pendingExportJson);
            writer.flush();
            notifyExport(true, "动漫备份已保存");
            toast("动漫备份已保存");
        } catch (Exception error) {
            notifyExport(false, "保存失败");
            toast("备份保存失败：" + error.getMessage());
        } finally {
            pendingExportJson = null;
        }
    }

    private void notifyExport(boolean success, String message) {
        if (webView == null) {
            return;
        }
        String script = "window.onAnimeExportResult && window.onAnimeExportResult(" +
                success + "," + JSONObject.quote(message) + ");";
        webView.evaluateJavascript(script, null);
    }

    @Override
    public void onBackPressed() {
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
            webView.removeJavascriptInterface("AndroidAnimeBridge");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
