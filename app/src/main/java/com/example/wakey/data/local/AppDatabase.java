package com.example.wakey.data.local;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Photo.class}, version = 6, exportSchema = false)
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
        public void migrate(SupportSQLiteDatabase database) {
            try {
                // 컬럼 추가 시도, 이미 존재하면 예외 발생
                database.execSQL("ALTER TABLE Photo ADD COLUMN country TEXT");
            } catch (Exception e) {
                // 컬럼이 이미 존재하는 경우 (또는 다른 이유로 실패한 경우) 무시
                e.printStackTrace();
            }
        }
    };

    // 3 -> 4 버전 마이그레이션
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
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
            } catch (Exception e) {
                // 예외 처리
                e.printStackTrace();
            }
        }
    };

    // 4 -> 5 버전 마이그레이션
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
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

    // 5 -> 6 버전 마이그레이션 - 인덱스 추가
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                // 날짜 검색 성능 향상을 위한 인덱스
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Photo_dateTaken ON Photo (dateTaken)");

                // 파일 경로 중복 방지 및 빠른 조회를 위한 유니크 인덱스
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Photo_filePath ON Photo (filePath)");

                // 타임스탬프 기반 정렬을 위한 인덱스
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Photo_timestamp ON Photo (timestamp)");

                // 위치 기반 조회를 위한 복합 인덱스 (위도, 경도가 모두 있는 경우)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Photo_location ON Photo (latitude, longitude)");

                // 스토리 존재 여부 확인을 위한 인덱스
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Photo_story ON Photo (story)");

                // 해시태그 검색을 위한 인덱스
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Photo_hashtags ON Photo (hashtags)");

            } catch (Exception e) {
                // 인덱스 생성 실패 시 로그만 남기고 계속 진행
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
                            .addMigrations(
                                    MIGRATION_1_2,
                                    MIGRATION_2_3,
                                    MIGRATION_3_4,
                                    MIGRATION_4_5,
                                    MIGRATION_5_6  // 새로운 마이그레이션 추가
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    // 데이터베이스 인스턴스 제거 (테스트용)
    public static void destroyInstance() {
        instance = null;
    }
}