package com.local.qingmo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGES = 701;
    private int PAPER;
    private int CARD;
    private int INK;
    private int MUTED;
    private int FAINT;
    private int LINE;
    private int CORAL;
    private int CORAL_DARK;
    private int PEACH;
    private int SAGE;
    private static final int MAX_IMAGES = 6;

    private NoteDatabase database;
    private FrameLayout root;
    private String screen = "home";
    private boolean notesLightMode;

    private String searchQuery = "";
    private boolean searchVisible;
    private EditText homeSearch;
    private LinearLayout notesContainer;
    private TextView notesCount;

    private long currentDetailId = -1;

    private long editingId = -1;
    private long editingCreatedAt;
    private String draftTitle = "";
    private String draftContent = "";
    private String draftMood = Mood.ALL[0].key;
    private boolean editorDirty;
    private final ArrayList<String> draftImages = new ArrayList<>();
    private final ArrayList<String> originalImages = new ArrayList<>();
    private final ArrayList<String> newImages = new ArrayList<>();
    private final LinkedHashMap<String, TextView> editorMoodChips = new LinkedHashMap<>();
    private EditText editorTitle;
    private EditText editorContent;
    private LinearLayout editorImages;

    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat fullDate = new SimpleDateFormat("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE);
    private final SimpleDateFormat homeTime = new SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE);
    private final SimpleDateFormat shortDate = new SimpleDateFormat("M月d日", Locale.SIMPLIFIED_CHINESE);
    private final SimpleDateFormat shortDateTime = new SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notesLightMode = getSharedPreferences("qingji-settings", MODE_PRIVATE)
                .getBoolean("notes_light_mode", false);
        applyNotesTheme();
        configureWindow();
        database = new NoteDatabase(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);
        setContentView(root);

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            long requestedNote = getIntent().getLongExtra(TimelineActivity.EXTRA_NOTE_ID, -1);
            if (requestedNote > 0 && database.get(requestedNote) != null) {
                showDetail(requestedNote);
            } else {
                showHome();
            }
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(PAPER));
        window.setStatusBarColor(PAPER);
        window.setNavigationBarColor(PAPER);
        int flags = notesLightMode ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
        if (notesLightMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void applyNotesTheme() {
        if (notesLightMode) {
            PAPER = Color.rgb(242, 236, 226);
            CARD = Color.rgb(250, 247, 240);
            INK = Color.rgb(39, 35, 31);
            MUTED = Color.rgb(112, 104, 95);
            FAINT = Color.rgb(146, 136, 125);
            LINE = Color.rgb(216, 205, 190);
            CORAL = Color.rgb(166, 83, 67);
            CORAL_DARK = Color.rgb(185, 102, 85);
            PEACH = Color.rgb(239, 224, 216);
            SAGE = Color.rgb(95, 127, 107);
        } else {
            PAPER = Color.rgb(20, 18, 16);
            CARD = Color.rgb(33, 30, 27);
            INK = Color.rgb(244, 239, 231);
            MUTED = Color.rgb(184, 174, 164);
            FAINT = Color.rgb(133, 123, 114);
            LINE = Color.rgb(59, 52, 46);
            CORAL = Color.rgb(184, 91, 73);
            CORAL_DARK = Color.rgb(208, 106, 86);
            PEACH = Color.rgb(51, 36, 31);
            SAGE = Color.rgb(194, 145, 84);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        long noteId = intent.getLongExtra(TimelineActivity.EXTRA_NOTE_ID, -1);
        if (noteId > 0 && database != null && database.get(noteId) != null) {
            showDetail(noteId);
        }
    }

    private void toggleNotesTheme() {
        notesLightMode = !notesLightMode;
        getSharedPreferences("qingji-settings", MODE_PRIVATE)
                .edit()
                .putBoolean("notes_light_mode", notesLightMode)
                .apply();
        applyNotesTheme();
        configureWindow();
        root.setBackgroundColor(PAPER);
        showHome();
    }

    private int moodTint(Mood mood) {
        if (!notesLightMode) {
            return mood.tint;
        }
        switch (mood.key) {
            case "happy":
                return 0xFFEEE7D8;
            case "soft":
                return 0xFFE2E9E2;
            case "low":
                return 0xFFE8E3EB;
            case "tired":
                return 0xFFEEE4DE;
            default:
                return 0xFFE5EAEE;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("screen", screen);
        outState.putString("searchQuery", searchQuery);
        outState.putBoolean("searchVisible", searchVisible);
        outState.putLong("detailId", currentDetailId);
        outState.putLong("editingId", editingId);
        outState.putLong("editingCreatedAt", editingCreatedAt);
        outState.putString("draftTitle", captureEditorTitle());
        outState.putString("draftContent", captureEditorContent());
        outState.putString("draftMood", draftMood);
        outState.putBoolean("editorDirty", editorDirty);
        outState.putStringArrayList("draftImages", new ArrayList<>(draftImages));
        outState.putStringArrayList("originalImages", new ArrayList<>(originalImages));
        outState.putStringArrayList("newImages", new ArrayList<>(newImages));
    }

    private void restoreState(Bundle state) {
        screen = state.getString("screen", "home");
        searchQuery = state.getString("searchQuery", "");
        searchVisible = state.getBoolean("searchVisible", false);
        currentDetailId = state.getLong("detailId", -1);
        editingId = state.getLong("editingId", -1);
        editingCreatedAt = state.getLong("editingCreatedAt", 0);
        draftTitle = state.getString("draftTitle", "");
        draftContent = state.getString("draftContent", "");
        draftMood = state.getString("draftMood", Mood.ALL[0].key);
        editorDirty = state.getBoolean("editorDirty", false);
        copyStrings(state.getStringArrayList("draftImages"), draftImages);
        copyStrings(state.getStringArrayList("originalImages"), originalImages);
        copyStrings(state.getStringArrayList("newImages"), newImages);

        if ("editor".equals(screen)) {
            buildEditor();
        } else if ("detail".equals(screen) && database.get(currentDetailId) != null) {
            showDetail(currentDetailId);
        } else {
            screen = "home";
            showHome();
        }
    }

    private boolean isToday(long timestamp) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();
        return then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private String homeTimestamp(long timestamp) {
        if (isToday(timestamp)) {
            return "今天 " + homeTime.format(new Date(timestamp));
        }
        return shortDateTime.format(new Date(timestamp));
    }

    private String editorDateLabel(long timestamp) {
        String base = fullDate.format(new Date(timestamp));
        return isToday(timestamp) ? "今天 · " + base : base;
    }

    private void showHome() {
        screen = "home";
        currentDetailId = -1;
        hideKeyboard();
        root.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout page = column();
        page.setPadding(dp(20), dp(18), dp(20), dp(170));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        page.addView(buildHomeHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        page.addView(buildHeroCard());
        page.addView(buildQuickMoods());

        LinearLayout recentHeader = row();
        setMargins(recentHeader, 2, 24, 2, 10);
        LinearLayout recentTitle = column();
        TextView title = text("最近写下", 21, INK, true);
        recentTitle.addView(title);
        notesCount = text("", 12, MUTED, false);
        recentTitle.addView(notesCount);
        recentHeader.addView(recentTitle, new LinearLayout.LayoutParams(0, wrap(), 1));
        TextView searchButton = pill("搜索", Color.TRANSPARENT, MUTED, LINE);
        searchButton.setContentDescription("搜索随笔");
        clickable(searchButton, view -> {
            if (searchVisible && !searchQuery.isEmpty()) {
                searchVisible = false;
                searchQuery = "";
            } else {
                searchVisible = !searchVisible;
            }
            showHome();
            if (searchVisible && homeSearch != null) {
                homeSearch.requestFocus();
                homeSearch.post(() -> {
                    InputMethodManager keyboard =
                            (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (keyboard != null) {
                        keyboard.showSoftInput(homeSearch, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            }
        });
        recentHeader.addView(searchButton);
        page.addView(recentHeader);

        if (searchVisible) {
            page.addView(buildSearchField());
        }

        notesContainer = column();
        setMargins(notesContainer, 0, 2, 0, 0);
        page.addView(notesContainer);

        TextView privacy = text("所有文字和照片，只保存在这台手机里", 11, FAINT, false);
        privacy.setGravity(Gravity.CENTER);
        setMargins(privacy, 0, 26, 0, 0);
        page.addView(privacy);

        root.addView(scroll, match());
        TextView add = text("＋", 30, Color.WHITE, false);
        add.setGravity(Gravity.CENTER);
        Drawable addBackground = round(CORAL, 32, Color.TRANSPARENT, 0);
        add.setBackground(ripple(addBackground, 0x44FFFFFF, 32));
        add.setElevation(dp(10));
        add.setContentDescription("写一篇新随笔");
        clickable(add, view -> openNewEditor(null));
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(64), dp(64));
        addParams.gravity = Gravity.BOTTOM | Gravity.END;
        addParams.setMargins(0, 0, dp(22), dp(92));
        root.addView(add, addParams);
        addModuleSwitcher("notes");

        refreshNotes();
    }

    private View buildHomeHeader() {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout mark = new FrameLayout(this);
        mark.setBackground(round(
                notesLightMode ? 0xFFFFE8DD : 0xFF3A2420,
                15,
                Color.TRANSPARENT,
                0
        ));
        TextView markText = text("花", 20, CORAL_DARK, true);
        markText.setGravity(Gravity.CENTER);
        mark.addView(markText, match());
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleBlock = column();
        setMargins(titleBlock, 12, 0, 0, 0);
        TextView brand = text("青茉日记", 23, INK, true);
        brand.setLetterSpacing(0.03f);
        titleBlock.addView(brand);
        titleBlock.addView(text("把今天轻轻收好", 11, MUTED, false));
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, wrap(), 1));

        TextView themeToggle = text(notesLightMode ? "☾" : "☼", 18, MUTED, false);
        themeToggle.setGravity(Gravity.CENTER);
        themeToggle.setBackground(round(notesLightMode ? 0x66FFFFFF : 0xFF211E1B, 19, LINE, 1));
        themeToggle.setContentDescription(notesLightMode ? "切换到深色随笔模式" : "切换到浅色随笔模式");
        clickable(themeToggle, view -> toggleNotesTheme());
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        themeParams.setMargins(0, 0, dp(8), 0);
        header.addView(themeToggle, themeParams);

        TextView date = text(shortDate.format(new Date()), 12, MUTED, false);
        date.setGravity(Gravity.CENTER);
        date.setBackground(round(notesLightMode ? 0x66FFFFFF : 0xFF1B1815, 18, LINE, 1));
        date.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.addView(date);
        return header;
    }

    private View buildHeroCard() {
        FrameLayout hero = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                notesLightMode
                        ? new int[]{0xFFFFF1E7, 0xFFFBE3DA}
                        : new int[]{0xFF2B211D, 0xFF211A17}
        );
        background.setCornerRadius(dp(28));
        background.setStroke(dp(1), notesLightMode ? 0x33F2C9BB : 0x66A34B3A);
        hero.setBackground(background);
        hero.setClipToOutline(true);
        hero.setElevation(dp(1));
        setMargins(hero, 0, 22, 0, 0);

        DoodleView doodle = new DoodleView(this);
        FrameLayout.LayoutParams doodleParams = new FrameLayout.LayoutParams(dp(152), dp(172));
        doodleParams.gravity = Gravity.END | Gravity.BOTTOM;
        hero.addView(doodle, doodleParams);

        LinearLayout message = column();
        message.setPadding(dp(22), dp(21), dp(146), dp(18));
        LinearLayout today = row();
        today.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = text("●", 9, CORAL, false);
        today.addView(dot);
        TextView label = text("  今天", 12, CORAL_DARK, true);
        today.addView(label);
        message.addView(today);

        TextView headline = text("给此刻，\n留一点空白", 24, INK, true);
        headline.setLineSpacing(dp(2), 1f);
        setMargins(headline, 0, 8, 0, 8);
        message.addView(headline);
        message.addView(text(fullDate.format(new Date()), 12, MUTED, false));
        hero.addView(message, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(172)
        ));
        return hero;
    }

    private View buildQuickMoods() {
        LinearLayout section = column();
        setMargins(section, 2, 22, 2, 0);
        LinearLayout heading = row();
        heading.setGravity(Gravity.BOTTOM);
        heading.addView(text("此刻心情", 15, INK, true), new LinearLayout.LayoutParams(0, wrap(), 1));
        heading.addView(text("点一下，顺手记下来", 11, FAINT, false));
        section.addView(heading);

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setClipToPadding(false);
        LinearLayout moods = row();
        moods.setPadding(0, dp(10), 0, 0);
        for (Mood mood : Mood.ALL) {
            moods.addView(quickMoodView(mood));
        }
        horizontal.addView(moods);
        section.addView(horizontal);
        return section;
    }

    private View quickMoodView(Mood mood) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(round(moodTint(mood), 23, 0x44FFFFFF, 1));
        TextView emoji = text(mood.emoji, 20, INK, false);
        emoji.setGravity(Gravity.CENTER);
        circle.addView(emoji, match());
        item.addView(circle, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView label = text(mood.label, 11, MUTED, false);
        label.setGravity(Gravity.CENTER);
        setMargins(label, 0, 5, 0, 0);
        item.addView(label);
        item.setContentDescription("以" + mood.label + "的心情写随笔");
        clickable(item, view -> openNewEditor(mood.key));
        return item;
    }

    private View buildSearchField() {
        LinearLayout holder = row();
        holder.setGravity(Gravity.CENTER_VERTICAL);
        holder.setBackground(round(CARD, 18, LINE, 1));
        holder.setPadding(dp(14), dp(2), dp(8), dp(2));
        setMargins(holder, 0, 4, 0, 12);
        TextView icon = text("⌕", 22, MUTED, false);
        holder.addView(icon, new LinearLayout.LayoutParams(dp(26), wrap()));

        homeSearch = new EditText(this);
        homeSearch.setText(searchQuery);
        homeSearch.setSingleLine(true);
        homeSearch.setTextSize(14);
        homeSearch.setTextColor(INK);
        homeSearch.setHintTextColor(FAINT);
        homeSearch.setHint("搜索文字或心情");
        homeSearch.setBackgroundColor(Color.TRANSPARENT);
        homeSearch.setPadding(dp(4), 0, dp(4), 0);
        homeSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        holder.addView(homeSearch, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView clear = text("清除", 11, MUTED, false);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(10), dp(8), dp(10), dp(8));
        clickable(clear, view -> {
            searchQuery = "";
            if (homeSearch != null) {
                homeSearch.setText("");
            }
            refreshNotes();
        });
        holder.addView(clear);
        homeSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                refreshNotes();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        return holder;
    }

    private void refreshNotes() {
        if (notesContainer == null) {
            return;
        }
        notesContainer.removeAllViews();
        ArrayList<Note> notes = database.getAll(searchQuery);
        if (notesCount != null) {
            String query = searchQuery.trim();
            if (query.isEmpty()) {
                notesCount.setText(database.count() == 0 ? "从第一笔开始" : "共 " + notes.size() + " 篇");
            } else {
                notesCount.setText("找到 " + notes.size() + " 篇");
            }
        }
        if (notes.isEmpty()) {
            notesContainer.addView(buildEmptyState(!searchQuery.trim().isEmpty()));
            return;
        }
        for (int index = 0; index < notes.size(); index++) {
            View card = buildNoteCard(notes.get(index));
            setMargins(card, 0, index == 0 ? 2 : 12, 0, 0);
            notesContainer.addView(card);
        }
    }

    private View buildEmptyState(boolean hasQuery) {
        LinearLayout empty = column();
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(36), dp(20), dp(30));
        empty.setBackground(round(notesLightMode ? 0x88FFFFFF : 0xFF211E1B, 24, LINE, 1));
        setMargins(empty, 0, 4, 0, 0);

        TextView symbol = text(hasQuery ? "○" : "✎", 28, CORAL, false);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(round(PEACH, 25, Color.TRANSPARENT, 0));
        empty.addView(symbol, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView title = text(hasQuery ? "没有找到相关记录" : "第一笔，留给今天", 17, INK, true);
        title.setGravity(Gravity.CENTER);
        setMargins(title, 0, 14, 0, 5);
        empty.addView(title);
        TextView caption = text(
                hasQuery ? "换个词试试，或者清空搜索" : "不必写得完整，一句话也很好",
                12,
                MUTED,
                false
        );
        caption.setGravity(Gravity.CENTER);
        empty.addView(caption);
        if (!hasQuery) {
            TextView action = pill("写下第一篇", CORAL, Color.WHITE, Color.TRANSPARENT);
            setMargins(action, 0, 18, 0, 0);
            clickable(action, view -> openNewEditor(null));
            empty.addView(action);
        }
        return empty;
    }

    private View buildNoteCard(Note note) {
        Mood mood = Mood.byKey(note.mood);
        String displayTitle = displayTitle(note);
        LinearLayout card = column();
        card.setPadding(dp(18), dp(16), dp(18), dp(17));
        card.setBackground(ripple(round(CARD, 22, LINE, 1), 0x22D06A56, 22));
        card.setElevation(dp(1));
        card.setContentDescription("打开" + displayTitle + "，写于" + homeTimestamp(note.createdAt));
        clickable(card, view -> showDetail(note.id));

        LinearLayout meta = row();
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView moodTag = text(mood.emoji + "  " + mood.label, 11, INK, true);
        moodTag.setGravity(Gravity.CENTER);
        moodTag.setBackground(round(moodTint(mood), 13, Color.TRANSPARENT, 0));
        moodTag.setPadding(dp(9), dp(5), dp(9), dp(5));
        meta.addView(moodTag);
        meta.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        meta.addView(text(homeTimestamp(note.createdAt), 11, FAINT, false));
        card.addView(meta);

        TextView titleView = text(displayTitle, 18, INK, true);
        titleView.setMaxLines(2);
        setMargins(titleView, 0, 11, 0, 0);
        card.addView(titleView);

        String preview = contentPreview(note.content, displayTitle, 3, 150);
        TextView body = text(
                preview.isEmpty()
                        ? (note.images.isEmpty() ? "正文为空" : "含 " + note.images.size() + " 张照片")
                        : preview,
                14,
                MUTED,
                false
        );
        body.setMaxLines(3);
        body.setLineSpacing(dp(4), 1.05f);
        setMargins(body, 0, 7, 0, 0);
        card.addView(body);

        if (!note.images.isEmpty()) {
            card.addView(buildCardImages(note.images), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        if (note.content != null && note.content.length() > 150) {
            TextView more = text("继续读 ›", 11, CORAL_DARK, true);
            more.setGravity(Gravity.END);
            setMargins(more, 0, 9, 0, 0);
            card.addView(more);
        }
        return card;
    }

    private String displayTitle(Note note) {
        return deriveTitle(note.title, note.content);
    }

    private String contentPreview(String content, String title, int maxLines, int maxChars) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        String[] lines = content.split("\\r?\\n");
        StringBuilder preview = new StringBuilder();
        int lineCount = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (lineCount == 0 && line.equals(title)) {
                continue;
            }
            if (preview.length() > 0) {
                preview.append('\n');
            }
            preview.append(line);
            lineCount++;
            if (lineCount >= maxLines) {
                break;
            }
        }
        if (preview.length() > maxChars) {
            return preview.substring(0, maxChars) + "…";
        }
        return preview.toString();
    }

    private View buildCardImages(List<String> images) {
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setClipToPadding(false);
        LinearLayout row = row();
        row.setPadding(0, dp(12), 0, 0);
        int shown = Math.min(images.size(), 4);
        for (int index = 0; index < shown; index++) {
            FrameLayout cell = new FrameLayout(this);
            Bitmap bitmap = ImageStore.load(this, images.get(index), dp(220), dp(220));
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setClipToOutline(true);
            image.setBackground(round(
                    notesLightMode ? 0xFFF2EAE4 : 0xFF2A2622,
                    13,
                    Color.TRANSPARENT,
                    0
            ));
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            }
            cell.addView(image, match());
            if (index == shown - 1 && images.size() > shown) {
                TextView more = text("+" + (images.size() - shown), 15, Color.WHITE, true);
                more.setGravity(Gravity.CENTER);
                more.setBackground(round(0x77000000, 13, Color.TRANSPARENT, 0));
                cell.addView(more, match());
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(78), dp(78));
            if (index > 0) {
                params.setMargins(dp(8), 0, 0, 0);
            }
            row.addView(cell, params);
        }
        horizontal.addView(row);
        return horizontal;
    }

    private void openNewEditor(String presetMood) {
        editingId = -1;
        editingCreatedAt = System.currentTimeMillis();
        draftTitle = "";
        draftContent = "";
        draftMood = presetMood == null ? Mood.ALL[0].key : presetMood;
        draftImages.clear();
        originalImages.clear();
        newImages.clear();
        editorDirty = false;
        buildEditor();
    }

    private void openExistingEditor(Note note) {
        if (note == null) {
            showHome();
            return;
        }
        editingId = note.id;
        editingCreatedAt = note.createdAt;
        draftTitle = note.title == null ? "" : note.title;
        draftContent = note.content == null ? "" : note.content;
        draftMood = note.mood;
        draftImages.clear();
        draftImages.addAll(note.images);
        originalImages.clear();
        originalImages.addAll(note.images);
        newImages.clear();
        editorDirty = false;
        buildEditor();
    }

    private void buildEditor() {
        screen = "editor";
        root.removeAllViews();
        editorMoodChips.clear();

        LinearLayout page = column();
        page.setBackgroundColor(PAPER);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(13), dp(20), dp(10));
        TextView cancel = pill("取消", Color.TRANSPARENT, MUTED, LINE);
        clickable(cancel, view -> attemptLeaveEditor());
        header.addView(cancel);

        LinearLayout heading = column();
        heading.setGravity(Gravity.CENTER);
        TextView title = text(editingId > 0 ? "编辑随笔" : "记下此刻", 17, INK, true);
        title.setGravity(Gravity.CENTER);
        heading.addView(title);
        header.addView(heading, new LinearLayout.LayoutParams(0, wrap(), 1));

        TextView save = pill("保存", CORAL, Color.WHITE, Color.TRANSPARENT);
        clickable(save, view -> saveEditor());
        header.addView(save);
        page.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        page.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(18), dp(20), dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        TextView date = text(editorDateLabel(editingCreatedAt), 12, MUTED, false);
        content.addView(date);
        TextView question = text("今天，心里是什么天气？", 20, INK, true);
        setMargins(question, 0, 9, 0, 13);
        content.addView(question);

        HorizontalScrollView moodScroll = new HorizontalScrollView(this);
        moodScroll.setHorizontalScrollBarEnabled(false);
        moodScroll.setClipToPadding(false);
        LinearLayout moods = row();
        for (Mood mood : Mood.ALL) {
            TextView chip = moodChip(mood);
            editorMoodChips.put(mood.key, chip);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(wrap(), wrap());
            if (moods.getChildCount() > 0) {
                chipParams.setMargins(dp(7), 0, 0, 0);
            }
            moods.addView(chip, chipParams);
        }
        moodScroll.addView(moods);
        content.addView(moodScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        updateEditorMoodChips();

        LinearLayout writingCard = column();
        writingCard.setBackground(round(CARD, 24, LINE, 1));
        writingCard.setPadding(dp(17), dp(12), dp(17), dp(14));
        writingCard.setElevation(dp(1));
        setMargins(writingCard, 0, 18, 0, 0);

        editorTitle = new EditText(this);
        editorTitle.setText(draftTitle);
        editorTitle.setSingleLine(true);
        editorTitle.setTextSize(22);
        editorTitle.setTextColor(INK);
        editorTitle.setHintTextColor(FAINT);
        editorTitle.setHint("给这段记录起个标题");
        editorTitle.setTypeface(Typeface.DEFAULT_BOLD);
        editorTitle.setBackgroundColor(Color.TRANSPARENT);
        editorTitle.setPadding(dp(1), dp(8), dp(1), dp(8));
        editorTitle.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        writingCard.addView(editorTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        editorTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                draftTitle = s.toString();
                editorDirty = true;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        View titleDivider = new View(this);
        titleDivider.setBackgroundColor(LINE);
        writingCard.addView(titleDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        setMargins(titleDivider, 0, 4, 0, 2);

        editorContent = new EditText(this);
        editorContent.setText(draftContent);
        editorContent.setSelection(editorContent.length());
        editorContent.setTextSize(16);
        editorContent.setTextColor(INK);
        editorContent.setHintTextColor(FAINT);
        editorContent.setHint("写下正文……");
        editorContent.setGravity(Gravity.TOP | Gravity.START);
        editorContent.setBackgroundColor(Color.TRANSPARENT);
        editorContent.setPadding(dp(1), dp(8), dp(1), dp(8));
        editorContent.setMinHeight(dp(244));
        editorContent.setLineSpacing(dp(6), 1.05f);
        editorContent.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        editorContent.setVerticalScrollBarEnabled(false);
        writingCard.addView(editorContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        editorContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                draftContent = s.toString();
                editorDirty = true;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        content.addView(writingCard);

        LinearLayout imageHeader = row();
        imageHeader.setGravity(Gravity.CENTER_VERTICAL);
        setMargins(imageHeader, 2, 21, 2, 10);
        imageHeader.addView(text("照片", 15, INK, true), new LinearLayout.LayoutParams(0, wrap(), 1));
        imageHeader.addView(text("最多 " + MAX_IMAGES + " 张", 11, FAINT, false));
        content.addView(imageHeader);

        editorImages = new LinearLayout(this);
        editorImages.setOrientation(LinearLayout.VERTICAL);
        content.addView(editorImages, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refreshEditorImages();

        LinearLayout tip = row();
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setBackground(round(notesLightMode ? 0x66FFFFFF : 0xFF211E1B, 16, LINE, 1));
        tip.setPadding(dp(13), dp(11), dp(13), dp(11));
        setMargins(tip, 0, 18, 0, 0);
        TextView lock = text("▣", 14, SAGE, false);
        tip.addView(lock);
        TextView tipText = text("图片会压缩后存入本机，不会上传到任何服务器", 11, MUTED, false);
        tipText.setLineSpacing(dp(2), 1f);
        setMargins(tipText, 9, 0, 0, 0);
        tip.addView(tipText, new LinearLayout.LayoutParams(0, wrap(), 1));
        content.addView(tip);

        root.addView(page, match());
        final View focusTarget = draftTitle.trim().isEmpty() ? editorTitle : editorContent;
        focusTarget.requestFocus();
        focusTarget.post(() -> {
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null && !editorDirty) {
                keyboard.showSoftInput(focusTarget, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private TextView moodChip(Mood mood) {
        TextView chip = text(mood.emoji + "  " + mood.label, 12, INK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(13), dp(9), dp(13), dp(9));
        chip.setTag(mood.key);
        clickable(chip, view -> {
            draftMood = mood.key;
            editorDirty = true;
            updateEditorMoodChips();
        });
        return chip;
    }

    private void updateEditorMoodChips() {
        for (Map.Entry<String, TextView> entry : editorMoodChips.entrySet()) {
            Mood mood = Mood.byKey(entry.getKey());
            TextView chip = entry.getValue();
            boolean selected = mood.key.equals(draftMood);
            chip.setTextColor(selected ? CORAL_DARK : MUTED);
            chip.setBackground(round(
                    selected ? moodTint(mood) : (notesLightMode ? 0x99FFFFFF : 0xFF211E1B),
                    18,
                    selected ? CORAL : LINE,
                    selected ? 2 : 1
            ));
        }
    }

    private void refreshEditorImages() {
        if (editorImages == null) {
            return;
        }
        editorImages.removeAllViews();
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        editorImages.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        for (String name : new ArrayList<>(draftImages)) {
            FrameLayout cell = imageCell(name);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(92);
            params.height = dp(92);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(cell, params);
        }
        if (draftImages.size() < MAX_IMAGES) {
            TextView add = text("＋\n添加照片", 11, MUTED, false);
            add.setGravity(Gravity.CENTER);
            add.setLineSpacing(dp(2), 1f);
            add.setBackground(ripple(
                    dashedBackground(notesLightMode ? 0x66FFFFFF : 0xFF211E1B, LINE, 15),
                    notesLightMode ? 0x22FF8A7A : 0x22D06A56,
                    15
            ));
            add.setContentDescription("从相册添加照片");
            clickable(add, view -> chooseImages());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(92);
            params.height = dp(92);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(add, params);
        }
    }

    private FrameLayout imageCell(String name) {
        FrameLayout cell = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true);
        image.setBackground(round(
                notesLightMode ? 0xFFF2EAE4 : 0xFF2A2622,
                15,
                Color.TRANSPARENT,
                0
        ));
        Bitmap bitmap = ImageStore.load(this, name, dp(300), dp(300));
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        }
        cell.addView(image, match());

        TextView remove = text("×", 15, Color.WHITE, true);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(round(0xAA2F2724, 11, Color.TRANSPARENT, 0));
        remove.setContentDescription("移除这张照片");
        clickable(remove, view -> removeDraftImage(name));
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(23), dp(23));
        removeParams.gravity = Gravity.TOP | Gravity.END;
        removeParams.setMargins(0, dp(4), dp(4), 0);
        cell.addView(remove, removeParams);
        return cell;
    }

    private void chooseImages() {
        draftContent = captureEditorContent();
        int remaining = MAX_IMAGES - draftImages.size();
        if (remaining <= 0) {
            toast("一篇随笔最多放 " + MAX_IMAGES + " 张照片");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGES || resultCode != RESULT_OK || data == null) {
            return;
        }
        draftContent = captureEditorContent();
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        int remaining = MAX_IMAGES - draftImages.size();
        if (uris.size() > remaining) {
            uris = new ArrayList<>(uris.subList(0, remaining));
            toast("已选取前 " + remaining + " 张照片");
        }
        if (uris.isEmpty()) {
            return;
        }

        toast("正在整理照片…");
        final List<Uri> selected = new ArrayList<>(uris);
        imageExecutor.execute(() -> {
            final ArrayList<String> saved = new ArrayList<>();
            int failures = 0;
            for (Uri uri : selected) {
                try {
                    saved.add(ImageStore.save(getApplicationContext(), uri));
                } catch (Exception error) {
                    failures++;
                }
            }
            final int failureCount = failures;
            runOnUiThread(() -> {
                if (!"editor".equals(screen)) {
                    for (String image : saved) {
                        ImageStore.delete(this, image);
                    }
                    return;
                }
                draftImages.addAll(saved);
                newImages.addAll(saved);
                if (!saved.isEmpty()) {
                    editorDirty = true;
                }
                refreshEditorImages();
                if (failureCount > 0) {
                    toast(failureCount + " 张照片无法读取，已跳过");
                }
            });
        });
    }

    private void removeDraftImage(String name) {
        if (!draftImages.remove(name)) {
            return;
        }
        if (newImages.remove(name)) {
            ImageStore.delete(this, name);
        }
        editorDirty = true;
        refreshEditorImages();
    }

    private void saveEditor() {
        draftTitle = captureEditorTitle();
        draftContent = captureEditorContent();
        String normalizedTitle = draftTitle.trim();
        String normalized = draftContent.trim();
        if (normalizedTitle.isEmpty() && normalized.isEmpty() && draftImages.isEmpty()) {
            toast("给记录起个标题，或写一点内容");
            if (editorTitle != null) {
                editorTitle.requestFocus();
            }
            return;
        }
        Note note = new Note();
        note.id = editingId;
        note.title = deriveTitle(normalizedTitle, normalized);
        note.content = normalized;
        note.mood = draftMood;
        note.createdAt = editingCreatedAt;
        note.images.addAll(draftImages);
        long savedId;
        if (editingId > 0) {
            database.update(note);
            for (String oldImage : originalImages) {
                if (!draftImages.contains(oldImage)) {
                    ImageStore.delete(this, oldImage);
                }
            }
            savedId = editingId;
        } else {
            savedId = database.insert(note);
        }
        editingId = -1;
        editorDirty = false;
        newImages.clear();
        originalImages.clear();
        draftImages.clear();
        hideKeyboard();
        toast("已经轻轻收好");
        showDetail(savedId);
    }

    private String captureEditorContent() {
        if (editorContent == null) {
            return draftContent;
        }
        return editorContent.getText().toString();
    }

    private String captureEditorTitle() {
        if (editorTitle == null) {
            return draftTitle;
        }
        return editorTitle.getText().toString();
    }

    private String deriveTitle(String explicitTitle, String content) {
        String title = explicitTitle == null ? "" : explicitTitle.trim();
        if (!title.isEmpty()) {
            return title.length() > 60 ? title.substring(0, 60) : title;
        }
        String[] lines = content == null ? new String[0] : content.split("\\r?\\n");
        for (String line : lines) {
            String candidate = line.trim();
            if (!candidate.isEmpty()) {
                return candidate.length() > 32 ? candidate.substring(0, 32) : candidate;
            }
        }
        return "无题随笔";
    }

    private void attemptLeaveEditor() {
        draftContent = captureEditorContent();
        if (!editorDirty) {
            leaveEditorWithoutSaving();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("要放下这一笔吗？")
                .setMessage("这次修改还没有保存。")
                .setPositiveButton("继续写", null)
                .setNegativeButton("放弃修改", (dialog, which) -> leaveEditorWithoutSaving())
                .show();
    }

    private void leaveEditorWithoutSaving() {
        for (String image : newImages) {
            ImageStore.delete(this, image);
        }
        newImages.clear();
        editorDirty = false;
        long destination = editingId;
        editingId = -1;
        hideKeyboard();
        if (destination > 0 && database.get(destination) != null) {
            showDetail(destination);
        } else {
            showHome();
        }
    }

    private void showDetail(long id) {
        Note note = database.get(id);
        if (note == null) {
            showHome();
            return;
        }
        screen = "detail";
        currentDetailId = id;
        root.removeAllViews();

        LinearLayout page = column();
        page.setBackgroundColor(PAPER);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(13), dp(20), dp(10));
        TextView back = pill("‹  返回", Color.TRANSPARENT, MUTED, LINE);
        clickable(back, view -> showHome());
        header.addView(back);
        TextView heading = text("一篇随笔", 17, INK, true);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, wrap(), 1));
        TextView edit = pill("编辑", notesLightMode ? 0xFFFFE8DD : 0xFF3A2420, CORAL_DARK, Color.TRANSPARENT);
        clickable(edit, view -> openExistingEditor(note));
        header.addView(edit);
        page.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        page.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(22), dp(20), dp(112));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        Mood mood = Mood.byKey(note.mood);
        TextView moodTag = text(mood.emoji + "  " + mood.label, 12, INK, true);
        moodTag.setGravity(Gravity.CENTER);
        moodTag.setBackground(round(moodTint(mood), 16, Color.TRANSPARENT, 0));
        moodTag.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout meta = row();
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(moodTag);
        TextView time = text(shortDateTime.format(new Date(note.createdAt)), 12, MUTED, false);
        meta.addView(time);
        setMargins(time, 10, 0, 0, 0);
        content.addView(meta);

        TextView title = text(displayTitle(note), 24, INK, true);
        title.setLineSpacing(dp(2), 1f);
        setMargins(title, 0, 15, 0, 14);
        content.addView(title);

        LinearLayout article = column();
        article.setBackground(round(CARD, 24, LINE, 1));
        article.setPadding(dp(19), dp(19), dp(19), dp(20));
        article.setElevation(dp(1));
        if (note.content != null && !note.content.trim().isEmpty()) {
            TextView body = text(note.content.trim(), 16, INK, false);
            body.setTextIsSelectable(true);
            body.setLineSpacing(dp(7), 1.04f);
            article.addView(body);
        } else {
            article.addView(text(
                    note.images.isEmpty() ? "这一页只写了标题。" : "这一页只留下了照片。",
                    14,
                    MUTED,
                    false
            ));
        }
        if (!note.images.isEmpty()) {
            for (int index = 0; index < note.images.size(); index++) {
                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setMaxHeight(dp(520));
                image.setClipToOutline(true);
                image.setBackground(round(
                        notesLightMode ? 0xFFF5EEE9 : 0xFF2A2622,
                        16,
                        Color.TRANSPARENT,
                        0
                ));
                Bitmap bitmap = ImageStore.load(
                        this,
                        note.images.get(index),
                        Math.max(dp(320), getResources().getDisplayMetrics().widthPixels),
                        dp(480)
                );
                if (bitmap != null) {
                    image.setImageBitmap(bitmap);
                }
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                int top = note.content == null || note.content.trim().isEmpty() ? (index == 0 ? 0 : 12) : 17;
                imageParams.setMargins(0, top, 0, 0);
                article.addView(image, imageParams);
            }
        }
        content.addView(article, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (note.updatedAt - note.createdAt > 5_000L) {
            TextView edited = text("最后编辑于 " + shortDateTime.format(new Date(note.updatedAt)), 10, FAINT, false);
            edited.setGravity(Gravity.CENTER);
            setMargins(edited, 0, 13, 0, 0);
            content.addView(edited);
        }

        TextView delete = text("删除这篇随笔", 12, 0xFFB46A61, false);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(dp(14), dp(13), dp(14), dp(13));
        clickable(delete, view -> confirmDelete(note));
        setMargins(delete, 0, 24, 0, 0);
        content.addView(delete);

        TextView privacy = text("仅保存在这台手机上", 10, FAINT, false);
        privacy.setGravity(Gravity.CENTER);
        setMargins(privacy, 0, 6, 0, 0);
        content.addView(privacy);

        root.addView(page, match());
        addModuleSwitcher("notes");
    }

    private void addModuleSwitcher(String active) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
        );
        params.gravity = Gravity.BOTTOM;
        params.setMargins(dp(16), 0, dp(16), dp(12));
        root.addView(buildModuleSwitcher(active), params);
    }

    private View buildModuleSwitcher(String active) {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(5), dp(5), dp(5), dp(5));
        bar.setBackground(round(notesLightMode ? 0xE6FFFFFF : 0xFF191715, 24, LINE, 1));
        bar.setElevation(dp(9));

        TextView notes = moduleTab("✎  随笔", "notes".equals(active));
        clickable(notes, view -> {
            if (!"notes".equals(active)) {
                showHome();
            }
        });
        bar.addView(notes, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView films = moduleTab("▣  影视", "films".equals(active));
        clickable(films, view -> {
            if (!"films".equals(active)) {
                startModule(FilmActivity.class);
            }
        });
        LinearLayout.LayoutParams filmParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        filmParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(films, filmParams);

        TextView anime = moduleTab("◈  动漫", "anime".equals(active));
        clickable(anime, view -> {
            if (!"anime".equals(active)) {
                startModule(AnimeActivity.class);
            }
        });
        LinearLayout.LayoutParams animeParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        animeParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(anime, animeParams);

        TextView timeline = moduleTab("◷  时间线", "timeline".equals(active));
        clickable(timeline, view -> {
            if (!"timeline".equals(active)) {
                startModule(TimelineActivity.class);
            }
        });
        LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        timelineParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(timeline, timelineParams);
        return bar;
    }

    private void startModule(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
        overridePendingTransition(R.anim.module_enter, R.anim.module_exit);
    }

    private TextView moduleTab(String label, boolean selected) {
        TextView tab = text(label, 12, selected ? CORAL_DARK : MUTED, true);
        tab.setGravity(Gravity.CENTER);
        tab.setTextSize(11);
        tab.setBackground(round(
                selected ? (notesLightMode ? 0xFFFFE8DD : 0xFF3A2420) : Color.TRANSPARENT,
                20,
                Color.TRANSPARENT,
                0
        ));
        tab.setContentDescription(selected ? label + "，当前模块" : "切换到" + label);
        return tab;
    }

    private void confirmDelete(Note note) {
        new AlertDialog.Builder(this)
                .setTitle("删除这篇随笔？")
                .setMessage("文字和照片都会从本机移除，删除后无法恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    database.delete(note.id);
                    for (String image : note.images) {
                        ImageStore.delete(this, image);
                    }
                    toast("已删除");
                    showHome();
                })
                .setNegativeButton("保留", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if ("editor".equals(screen)) {
            attemptLeaveEditor();
        } else if ("detail".equals(screen)) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdown();
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(CharSequence value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            view.setTypeface(Typeface.DEFAULT);
        }
        return view;
    }

    private TextView pill(String label, int fill, int textColor, int strokeColor) {
        TextView view = text(label, 12, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(38));
        view.setPadding(dp(14), dp(9), dp(14), dp(9));
        view.setBackground(ripple(round(fill, 19, strokeColor, strokeColor == Color.TRANSPARENT ? 0 : 1), 0x33D06A56, 19));
        return view;
    }

    private GradientDrawable round(int fill, float radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0 && strokeColor != Color.TRANSPARENT) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable dashedBackground(int fill, int strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor, dp(5), dp(4));
        return drawable;
    }

    private Drawable ripple(Drawable content, int rippleColor, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private void clickable(View view, View.OnClickListener listener) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(listener);
    }

    private void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        ViewGroup.MarginLayoutParams params;
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            params = (ViewGroup.MarginLayoutParams) raw;
        } else {
            params = new LinearLayout.LayoutParams(
                    raw == null ? ViewGroup.LayoutParams.MATCH_PARENT : raw.width,
                    raw == null ? ViewGroup.LayoutParams.WRAP_CONTENT : raw.height
            );
            view.setLayoutParams(params);
        }
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
    }

    private int wrap() {
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void copyStrings(List<String> source, List<String> target) {
        target.clear();
        if (source != null) {
            target.addAll(source);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }
}
