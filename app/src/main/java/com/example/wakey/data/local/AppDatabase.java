package com.example.wakey.data.local;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Photo.class}, version = 5, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    // 1 -> 2 버전 마이그레이션
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                // 컬럼 추가를 시도하고, 이미 존재하면 예외가 발생합니다
                database.execSQL("ALTER TABLE Photo ADD COLUMN hashtags TEXT");
            } catch (Exception e) {
                // 컬럼이 이미 존재하는 경우 (또는 다른 이유로 실패한 경우) 무시
                e.printStackTrace();
            }
        }
    };

    // 2 -> 3 버전 마이그레이션
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            try {
                // 컬럼 추가 시도, 이미 존재하면 예외 발생
                database.execSQL("ALTER TABLE Photo ADD COLUMN country TEXT");
            } catch (Exception e)
            {
                // 컬럼이 이미 존재하는 경우 (또는 다른 이유로 실패한 경우) 무시
                e.printStackTrace();
            }
        }
    };

    // 3 -> 4 버전 마이그레이션
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            try {
                Cursor cursor = database.query("SELECT * FROM Photo LIMIT 1");
                String[] columns = cursor.getColumnNames();
                boolean hasLocationDo = false;

                for (String column : columns) {
                    if (column.equals("locationDo")) {
                        hasLocationDo = true;
                        break;
                    }
                }

                if (!hasLocationDo) {
                    database.execSQL("ALTER TABLE Photo ADD COLUMN locationDo TEXT");
                }

                cursor.close();
            } catch (Exception e)
            {
                // 예외 처리
                e.printStackTrace();
            }
        }
    };

    // 4 -> 5 버전 마이그레이션
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database)
        {
            try {
                // ALTER TABLE을 직접 시도하고 실패하면 무시
                try {
                    database.execSQL("ALTER TABLE Photo ADD COLUMN story TEXT");
                } catch (Exception e) {
                    // 이미 story 컬럼이 존재하면 무시
                    e.printStackTrace();
                }
            } catch (Exception e) {
                // 예외 처리
                e.printStackTrace();
            }
        }
    };

    public abstract PhotoDao photoDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "AppDatabase"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)  // 마이그레이션 추가
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return instance;
    }
}
