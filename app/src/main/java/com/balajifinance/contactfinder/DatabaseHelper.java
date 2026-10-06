package com.balajifinance.contactfinder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "contact_finder.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_CONTACTS = "contacts";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_PHONE = "phone";
    public static final String COL_DETAILS = "details";
    public static final String COL_SAVED = "saved";
    public static final String COL_EDITED = "edited";
    public static final String COL_DELETED = "deleted";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_SOURCE = "source";
    public static final String COL_CLOUD_ID = "cloud_id";
    public static final String COL_SYNC_STATUS = "sync_status";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_CONTACTS + " ("
                + COL_ID + " TEXT PRIMARY KEY, "
                + COL_NAME + " TEXT, "
                + COL_PHONE + " TEXT, "
                + COL_DETAILS + " TEXT, "
                + COL_SAVED + " INTEGER DEFAULT 0, "
                + COL_EDITED + " INTEGER DEFAULT 0, "
                + COL_DELETED + " INTEGER DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_UPDATED_AT + " INTEGER, "
                + COL_SOURCE + " TEXT, "
                + COL_CLOUD_ID + " TEXT, "
                + COL_SYNC_STATUS + " TEXT"
                + ")";
        db.execSQL(CREATE_TABLE);
        db.execSQL("CREATE INDEX idx_phone ON " + TABLE_CONTACTS + "(phone)");
        db.execSQL("CREATE INDEX idx_name ON " + TABLE_CONTACTS + "(name)");
        db.execSQL("CREATE INDEX idx_updated ON " + TABLE_CONTACTS + "(updated_at)");
        db.execSQL("CREATE INDEX idx_deleted ON " + TABLE_CONTACTS + "(deleted)");
        db.execSQL("CREATE INDEX idx_saved ON " + TABLE_CONTACTS + "(saved)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS);
        onCreate(db);
    }

    public synchronized void upsertContact(Contact c) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, c.id);
        cv.put(COL_NAME, c.name);
        cv.put(COL_PHONE, c.phone);
        cv.put(COL_DETAILS, c.details);
        cv.put(COL_SAVED, c.saved ? 1 : 0);
        cv.put(COL_EDITED, c.edited ? 1 : 0);
        cv.put(COL_DELETED, c.deleted ? 1 : 0);
        cv.put(COL_CREATED_AT, c.createdAt);
        cv.put(COL_UPDATED_AT, c.updatedAt);
        cv.put(COL_SOURCE, c.source);
        cv.put(COL_CLOUD_ID, c.cloudId);
        cv.put(COL_SYNC_STATUS, c.syncStatus);
        db.insertWithOnConflict(TABLE_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void upsertContacts(List<Contact> list) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Contact c : list) {
                ContentValues cv = new ContentValues();
                cv.put(COL_ID, c.id);
                cv.put(COL_NAME, c.name);
                cv.put(COL_PHONE, c.phone);
                cv.put(COL_DETAILS, c.details);
                cv.put(COL_SAVED, c.saved ? 1 : 0);
                cv.put(COL_EDITED, c.edited ? 1 : 0);
                cv.put(COL_DELETED, c.deleted ? 1 : 0);
                cv.put(COL_CREATED_AT, c.createdAt);
                cv.put(COL_UPDATED_AT, c.updatedAt);
                cv.put(COL_SOURCE, c.source);
                cv.put(COL_CLOUD_ID, c.cloudId);
                cv.put(COL_SYNC_STATUS, c.syncStatus);
                db.insertWithOnConflict(TABLE_CONTACTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Contact> getAllContacts() {
        List<Contact> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CONTACTS, null, null, null, null, null, COL_NAME + " COLLATE NOCASE ASC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Contact c = cursorToContact(cursor);
                    list.add(c);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public Contact getContact(String id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CONTACTS, null, COL_ID + "=?", new String[]{id}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursorToContact(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public int getActiveCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CONTACTS + " WHERE " + COL_DELETED + "=0", null);
        int count = 0;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return count;
    }

    private Contact cursorToContact(Cursor cursor) {
        Contact c = new Contact();
        c.id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
        c.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
        c.phone = cursor.getString(cursor.getColumnIndexOrThrow(COL_PHONE));
        c.details = cursor.getString(cursor.getColumnIndexOrThrow(COL_DETAILS));
        c.saved = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SAVED)) == 1;
        c.edited = cursor.getInt(cursor.getColumnIndexOrThrow(COL_EDITED)) == 1;
        c.deleted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DELETED)) == 1;
        c.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
        c.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT));
        c.source = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE));
        c.cloudId = cursor.getString(cursor.getColumnIndexOrThrow(COL_CLOUD_ID));
        c.syncStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATUS));
        return c;
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CONTACTS, null, null);
    }

    public synchronized void deleteContact(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CONTACTS, COL_ID + "=?", new String[]{id});
    }
}
