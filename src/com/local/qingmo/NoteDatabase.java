package com.local.qingmo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

final class NoteDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "qingji.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_NOTES = "notes";

    NoteDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NOTES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL DEFAULT ''," +
                "content TEXT NOT NULL DEFAULT ''," +
                "mood TEXT NOT NULL DEFAULT 'calm'," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "images TEXT NOT NULL DEFAULT ''" +
                ")");
        db.execSQL("CREATE INDEX idx_notes_created_at ON " + TABLE_NOTES + " (created_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN title TEXT NOT NULL DEFAULT ''");
        }
    }

    ArrayList<Note> getAll(String query) {
        ArrayList<Note> notes = new ArrayList<>();
        String normalized = query == null ? "" : query.trim();
        Cursor cursor;
        if (normalized.isEmpty()) {
            cursor = getReadableDatabase().query(
                    TABLE_NOTES,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "created_at DESC, id DESC"
            );
        } else {
            ArrayList<String> moodKeys = new ArrayList<>();
            for (Mood mood : Mood.ALL) {
                if (mood.key.contains(normalized) || mood.label.contains(normalized)) {
                    moodKeys.add(mood.key);
                }
            }
            StringBuilder selection = new StringBuilder("title LIKE ? OR content LIKE ?");
            ArrayList<String> arguments = new ArrayList<>();
            arguments.add("%" + normalized + "%");
            arguments.add("%" + normalized + "%");
            if (!moodKeys.isEmpty()) {
                selection.append(" OR mood IN (");
                for (int index = 0; index < moodKeys.size(); index++) {
                    if (index > 0) {
                        selection.append(',');
                    }
                    selection.append('?');
                    arguments.add(moodKeys.get(index));
                }
                selection.append(')');
            }
            cursor = getReadableDatabase().query(
                    TABLE_NOTES,
                    null,
                    selection.toString(),
                    arguments.toArray(new String[0]),
                    null,
                    null,
                    "created_at DESC, id DESC"
            );
        }
        try {
            while (cursor.moveToNext()) {
                notes.add(fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }
        return notes;
    }

    Note get(long id) {
        Cursor cursor = getReadableDatabase().query(
                TABLE_NOTES,
                null,
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );
        try {
            if (cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    int count() {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_NOTES, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    long insert(Note note) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("title", note.title == null ? "" : note.title);
        values.put("content", note.content == null ? "" : note.content);
        values.put("mood", note.mood == null ? Mood.ALL[0].key : note.mood);
        values.put("created_at", note.createdAt > 0 ? note.createdAt : now);
        values.put("updated_at", now);
        values.put("images", Note.joinImages(note.images));
        note.id = getWritableDatabase().insertOrThrow(TABLE_NOTES, null, values);
        note.updatedAt = now;
        return note.id;
    }

    void update(Note note) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("title", note.title == null ? "" : note.title);
        values.put("content", note.content == null ? "" : note.content);
        values.put("mood", note.mood == null ? Mood.ALL[0].key : note.mood);
        values.put("updated_at", now);
        values.put("images", Note.joinImages(note.images));
        getWritableDatabase().update(
                TABLE_NOTES,
                values,
                "id = ?",
                new String[]{String.valueOf(note.id)}
        );
        note.updatedAt = now;
    }

    void delete(long id) {
        getWritableDatabase().delete(
                TABLE_NOTES,
                "id = ?",
                new String[]{String.valueOf(id)}
        );
    }

    private Note fromCursor(Cursor cursor) {
        Note note = new Note();
        note.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        note.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        note.content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
        note.mood = cursor.getString(cursor.getColumnIndexOrThrow("mood"));
        note.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        note.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        note.images.addAll(Note.splitImages(cursor.getString(cursor.getColumnIndexOrThrow("images"))));
        return note;
    }
}
