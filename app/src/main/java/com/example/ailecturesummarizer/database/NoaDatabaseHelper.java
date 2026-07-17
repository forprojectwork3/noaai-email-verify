package com.example.ailecturesummarizer.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class NoaDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "noa_database.db";
    private static final int DATABASE_VERSION = 2;

    // Table names
    public static final String TABLE_SUMMARIES = "summaries";
    public static final String TABLE_USERS = "users";
    public static final String TABLE_NOTIFICATIONS = "notifications";

    // Summaries Columns
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_VIDEO_ID = "video_id";
    public static final String COLUMN_SUMMARY_TEXT = "summary_text";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    // Users Columns
    public static final String COLUMN_USER_ID = "id";
    public static final String COLUMN_USER_NAME = "name";
    public static final String COLUMN_USER_EMAIL = "email";
    public static final String COLUMN_USER_PASS = "password_hash";
    public static final String COLUMN_USER_PROVIDER = "auth_provider"; // LOCAL, GOOGLE, FACEBOOK
    public static final String COLUMN_USER_ROLE = "role"; // USER, ADMIN

    // Notifications Columns
    public static final String COLUMN_NOTIF_ID = "id";
    public static final String COLUMN_NOTIF_TITLE = "title";
    public static final String COLUMN_NOTIF_MESSAGE = "message";
    public static final String COLUMN_NOTIF_TIMESTAMP = "timestamp";

    // Create tables SQL
    private static final String TABLE_CREATE_SUMMARIES =
            "CREATE TABLE " + TABLE_SUMMARIES + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_VIDEO_ID + " TEXT UNIQUE, " +
            COLUMN_SUMMARY_TEXT + " TEXT, " +
            COLUMN_TIMESTAMP + " INTEGER" +
            ");";

    private static final String TABLE_CREATE_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
            COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_USER_NAME + " TEXT, " +
            COLUMN_USER_EMAIL + " TEXT UNIQUE, " +
            COLUMN_USER_PASS + " TEXT, " +
            COLUMN_USER_PROVIDER + " TEXT, " +
            COLUMN_USER_ROLE + " TEXT" +
            ");";

    private static final String TABLE_CREATE_NOTIFICATIONS =
            "CREATE TABLE " + TABLE_NOTIFICATIONS + " (" +
            COLUMN_NOTIF_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_NOTIF_TITLE + " TEXT, " +
            COLUMN_NOTIF_MESSAGE + " TEXT, " +
            COLUMN_NOTIF_TIMESTAMP + " INTEGER" +
            ");";

    private static NoaDatabaseHelper instance;

    // Singleton pattern to prevent memory leaks and database locking issues
    public static synchronized NoaDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new NoaDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private NoaDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE_SUMMARIES);
        db.execSQL(TABLE_CREATE_USERS);
        db.execSQL(TABLE_CREATE_NOTIFICATIONS);
        seedDefaultAdmin(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(TABLE_CREATE_USERS);
            db.execSQL(TABLE_CREATE_NOTIFICATIONS);
            seedDefaultAdmin(db);
        }
    }

    /**
     * Seeds the default admin account into the database.
     */
    private void seedDefaultAdmin(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_NAME, "Noa Admin");
        values.put(COLUMN_USER_EMAIL, "admin@noa.ai");
        values.put(COLUMN_USER_PASS, HashUtils.hashPassword("admin123"));
        values.put(COLUMN_USER_PROVIDER, "LOCAL");
        values.put(COLUMN_USER_ROLE, "ADMIN");
        db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /* ── AUTHENTICATION METHODS ── */

    /**
     * Authenticates a user by checking their email and password hash.
     */
    public synchronized boolean authenticateUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            String hashedPassword = HashUtils.hashPassword(password);
            cursor = db.query(
                    TABLE_USERS,
                    new String[]{COLUMN_USER_ID},
                    COLUMN_USER_EMAIL + " = ? AND " + COLUMN_USER_PASS + " = ?",
                    new String[]{email.trim().toLowerCase(), hashedPassword},
                    null, null, null
            );
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Registers a new user. Returns true if successful, false if email exists or fails.
     */
    public synchronized boolean registerUser(String name, String email, String password, String provider, String role) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_NAME, name);
        values.put(COLUMN_USER_EMAIL, email.trim().toLowerCase());
        
        String hashedPassword = (password != null) ? HashUtils.hashPassword(password) : "";
        values.put(COLUMN_USER_PASS, hashedPassword);
        values.put(COLUMN_USER_PROVIDER, provider);
        values.put(COLUMN_USER_ROLE, role);

        long result = db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    /**
     * Checks if a user email already exists.
     */
    public synchronized boolean checkUserExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    TABLE_USERS,
                    new String[]{COLUMN_USER_ID},
                    COLUMN_USER_EMAIL + " = ?",
                    new String[]{email.trim().toLowerCase()},
                    null, null, null
            );
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Retrieves a user's role by email.
     */
    public synchronized String getUserRole(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        String role = "USER";
        try {
            cursor = db.query(
                    TABLE_USERS,
                    new String[]{COLUMN_USER_ROLE},
                    COLUMN_USER_EMAIL + " = ?",
                    new String[]{email.trim().toLowerCase()},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int colIdx = cursor.getColumnIndex(COLUMN_USER_ROLE);
                if (colIdx != -1) {
                    role = cursor.getString(colIdx);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return role;
    }

    /**
     * Retrieves a user's display name by email.
     */
    public synchronized String getUserName(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        String name = "";
        try {
            cursor = db.query(
                    TABLE_USERS,
                    new String[]{COLUMN_USER_NAME},
                    COLUMN_USER_EMAIL + " = ?",
                    new String[]{email.trim().toLowerCase()},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int colIdx = cursor.getColumnIndex(COLUMN_USER_NAME);
                if (colIdx != -1) {
                    name = cursor.getString(colIdx);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return name;
    }

    /* ── NOTIFICATIONS METHODS ── */

    /**
     * Inserts a new global notification alert.
     */
    public synchronized void insertNotification(String title, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NOTIF_TITLE, title);
        values.put(COLUMN_NOTIF_MESSAGE, message);
        values.put(COLUMN_NOTIF_TIMESTAMP, System.currentTimeMillis());
        db.insert(TABLE_NOTIFICATIONS, null, values);
    }

    /**
     * Retrieves all notifications in descending order of timestamp.
     */
    public synchronized Cursor getAllNotifications() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_NOTIFICATIONS,
                null,
                null,
                null,
                null,
                null,
                COLUMN_NOTIF_TIMESTAMP + " DESC"
        );
    }

    /* ── SUMMARIES METHODS ── */

    /**
     * Insert a new summary mapped to the Video ID. If it already exists, replace it.
     */
    public synchronized void insertSummary(String videoId, String summaryText) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_VIDEO_ID, videoId);
        values.put(COLUMN_SUMMARY_TEXT, summaryText);
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());

        db.insertWithOnConflict(TABLE_SUMMARIES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Query/Retrieve a saved summary using a Video ID. Returns null if not found.
     */
    public synchronized String getSummary(String videoId) {
        if (videoId == null) return null;

        SQLiteDatabase db = this.getReadableDatabase();
        String summary = null;
        Cursor cursor = null;

        try {
            cursor = db.query(
                    TABLE_SUMMARIES,
                    new String[]{COLUMN_SUMMARY_TEXT},
                    COLUMN_VIDEO_ID + " = ?",
                    new String[]{videoId},
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(COLUMN_SUMMARY_TEXT);
                if (index != -1) {
                    summary = cursor.getString(index);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return summary;
    }
}
