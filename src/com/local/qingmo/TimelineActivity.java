package com.local.qingmo;

import android.app.Activity;
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
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public final class TimelineActivity extends Activity {
    static final String EXTRA_NOTE_ID = "com.local.qingmo.OPEN_NOTE_ID";
    private static final String TIMELINE_URL = "file:///android_asset/film/timeline.html";
    private int PAPER;
    private int BAR;
    private int MUTED;
    private int LINE;
    private int ACCENT;
    private boolean lightMode;

    private WebView webView;

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
        webView.loadUrl(TIMELINE_URL + "?theme=" + (lightMode ? "light" : "dark"));
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        WebView.setWebContentsDebuggingEnabled(false);

        webView.addJavascriptInterface(new TimelineBridge(), "AndroidTimelineBridge");
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

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
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
                }
                return true;
            }
        });

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
        bar.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(BAR);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), LINE);
        bar.setBackground(background);
        bar.setElevation(dp(9));

        TextView notes = tab("✎  随笔", false);
        notes.setOnClickListener(view -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
            finish();
        });
        bar.addView(notes, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView films = tab("▣  影视", false);
        films.setOnClickListener(view -> {
            startActivity(new Intent(this, FilmActivity.class));
            overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
            finish();
        });
        LinearLayout.LayoutParams filmParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        filmParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(films, filmParams);

        TextView anime = tab("◈  动漫", false);
        anime.setOnClickListener(view -> {
            startActivity(new Intent(this, AnimeActivity.class));
            overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
            finish();
        });
        LinearLayout.LayoutParams animeParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        animeParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(anime, animeParams);

        TextView timeline = tab("◷  时间线", true);
        timeline.setOnClickListener(view -> { });
        LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        timelineParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(timeline, timelineParams);
        return bar;
    }

    private TextView tab(String label, boolean selected) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(11);
        view.setTextColor(selected ? ACCENT : MUTED);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(
                selected
                        ? (lightMode ? Color.rgb(239, 224, 216) : Color.rgb(58, 36, 32))
                        : Color.TRANSPARENT
        );
        background.setCornerRadius(dp(20));
        view.setBackground(background);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
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
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidTimelineBridge");
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private final class TimelineBridge {
        @JavascriptInterface
        public String getNotesJson() {
            NoteDatabase database = new NoteDatabase(TimelineActivity.this);
            ArrayList<Note> notes = database.getAll("");
            database.close();
            JSONArray result = new JSONArray();
            for (Note note : notes) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("id", note.id);
                    item.put("title", note.title == null ? "" : note.title);
                    item.put("content", note.content);
                    item.put("mood", note.mood);
                    item.put("createdAt", note.createdAt);
                    item.put("imageCount", note.images.size());
                    result.put(item);
                } catch (Exception ignored) {
                    // A malformed row should not hide the rest of the timeline.
                }
            }
            return result.toString();
        }

        @JavascriptInterface
        public void openNote(String rawId) {
            long id;
            try {
                id = Long.parseLong(rawId);
            } catch (NumberFormatException error) {
                return;
            }
            runOnUiThread(() -> {
                startActivity(new Intent(TimelineActivity.this, MainActivity.class)
                        .putExtra(EXTRA_NOTE_ID, id)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
                finish();
            });
        }

        @JavascriptInterface
        public void openAnime(String animeId) {
            if (animeId == null || animeId.trim().isEmpty()) {
                return;
            }
            runOnUiThread(() -> startActivity(
                    new Intent(TimelineActivity.this, AnimeActivity.class)
                            .putExtra(AnimeActivity.EXTRA_ANIME_ID, animeId.trim())
            ));
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
