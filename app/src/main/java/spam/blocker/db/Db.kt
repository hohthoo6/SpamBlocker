package spam.blocker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import spam.blocker.util.Notification.ensureBuiltInChannels
import spam.blocker.util.Util.isFreshInstall
import spam.blocker.util.logi

class Db private constructor(
    val ctx: Context
) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 54
        const val DB_NAME = "spam_blocker.db"

        // ---- notification channel table ----
        const val TABLE_NOTIFICATION_CHANNEL = "notification_channel"
        const val COLUMN_ID = "id"
        const val COLUMN_CHANNEL_ID = "channel_id"
        const val COLUMN_ICON = "icon"
        const val COLUMN_ICON_COLOR = "icon_color"
        const val COLUMN_GROUP = "grouping"
        const val COLUMN_MUTE = "mute"
        const val COLUMN_SOUND = "sound"
        const val COLUMN_LED = "led"
        const val COLUMN_LED_COLOR = "led_color"
        const val COLUMN_IMPORTANCE = "importance"
        const val COLUMN_REPEAT = "repeat"
        const val COLUMN_REPEAT_INTERVAL = "repeat_interval"

        // ---- call history table ----
        const val TABLE_CALL = "call"
        const val COLUMN_PEER = "peer" // peer number
        const val COLUMN_CNAP = "cnap" // caller display name
        const val COLUMN_TIME = "time"
        const val COLUMN_RESULT = "result" // Int, as RESULT_... in Def
        const val COLUMN_REASON = "reason"
        const val COLUMN_READ = "read" // Boolean
        const val COLUMN_IS_TEST = "is_test" // Boolean
        const val COLUMN_EXTRA_INFO = "extra_info" // text
        const val COLUMN_EXPANDED = "expanded" // Boolean
        const val COLUMN_FULL_SCREENING_LOG = "full_screening_log" // text
        const val COLUMN_ANYTHING_WRONG_SCREENING = "anything_wrong" // Boolean
        const val COLUMN_SIM_SLOT = "sim_slot"

        // ---- number regex rule table ----
        const val TABLE_NUMBER_RULE = "number_filter"
        const val COLUMN_PATTERN = "pattern"
        const val COLUMN_PATTERN_EXTRA = "pattern_extra"
        const val COLUMN_PATTERN_FLAGS = "pattern_flags"
        const val COLUMN_PATTERN_EXTRA_FLAGS = "pattern_extra_flags"
        const val COLUMN_PATTERN_MODE_TYPE = "pattern_mode_type"
        const val COLUMN_PATTERN_EXTRA_MODE_TYPE = "pattern_extra_mode_type"
        const val COLUMN_DESC = "description"
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_IS_BLACK = "is_blacklist"
        const val COLUMN_FLAGS = "flags"
        const val COLUMN_SCHEDULE = "schedule"
        const val COLUMN_BLOCK_TYPE = "block_type"
        const val COLUMN_BLOCK_TYPE_CONFIG = "block_type_config"

        @Volatile
        private var instance: Db? = null

        fun getInstance(context: Context): Db {
            return instance ?: synchronized(this) {
                instance ?: Db(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // notification channel database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NOTIFICATION_CHANNEL (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_CHANNEL_ID TEXT UNIQUE, " +
                    "$COLUMN_IMPORTANCE INTEGER, " +
                    "$COLUMN_MUTE INTEGER, " +
                    "$COLUMN_SOUND TEXT, " +
                    "$COLUMN_ICON TEXT, " +
                    "$COLUMN_ICON_COLOR INTEGER, " +
                    "$COLUMN_LED INTEGER, " +
                    "$COLUMN_LED_COLOR INTEGER, " +
                    "$COLUMN_REPEAT INTEGER, " +
                    "$COLUMN_REPEAT_INTERVAL INTEGER, " +
                    "$COLUMN_GROUP TEXT " +
                    ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_channel_id ON $TABLE_NOTIFICATION_CHANNEL($COLUMN_CHANNEL_ID)")

        if (isFreshInstall(ctx)) {
            ensureBuiltInChannels(ctx, db)
        }

        // call history
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_CALL (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_PEER TEXT, " +
                    "$COLUMN_CNAP TEXT, " +
                    "$COLUMN_TIME INTEGER, " +
                    "$COLUMN_RESULT INTEGER, " +
                    "$COLUMN_SIM_SLOT INTEGER, " +
                    "$COLUMN_REASON LONG, " +
                    "$COLUMN_READ INTEGER, " +
                    "$COLUMN_IS_TEST INTEGER, " +
                    "$COLUMN_EXTRA_INFO TEXT, " +
                    "$COLUMN_EXPANDED INTEGER, " +
                    "$COLUMN_FULL_SCREENING_LOG TEXT, " +
                    "$COLUMN_ANYTHING_WRONG_SCREENING INTEGER " +
                    ")"
        )

        // number regex rules
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NUMBER_RULE (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_PATTERN TEXT, " +
                    "$COLUMN_PATTERN_EXTRA TEXT, " +
                    "$COLUMN_PATTERN_FLAGS INTEGER, " +
                    "$COLUMN_PATTERN_EXTRA_FLAGS INTEGER, " +
                    "$COLUMN_PATTERN_MODE_TYPE INTEGER, " +
                    "$COLUMN_PATTERN_EXTRA_MODE_TYPE INTEGER, " +
                    "$COLUMN_DESC TEXT, " +
                    "$COLUMN_PRIORITY INTEGER, " +
                    "$COLUMN_IS_BLACK INTEGER, " +
                    "$COLUMN_FLAGS INTEGER, " +
                    "$COLUMN_CHANNEL_ID TEXT, " +
                    "$COLUMN_SCHEDULE TEXT, " +
                    "$COLUMN_BLOCK_TYPE INTEGER, " +
                    "$COLUMN_BLOCK_TYPE_CONFIG TEXT, " +
                    "$COLUMN_SIM_SLOT INTEGER " +
                    ")"
        )
    }

    fun addColumnIfNotExist(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String
    ) {
        val cursor = db.rawQuery(
            "SELECT count(*) FROM pragma_table_info('$tableName') WHERE name = '$columnName'",
            arrayOf()
        )
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()

        if (count == 0) {
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnType")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logi("upgrading db $oldVersion -> $newVersion")

        // This build only keeps the call history + notification channels, the old
        //   tables from removed features are simply dropped.
        val removedTables = listOf(
            "number_filter", "content_filter", "quick_copy",
            "spam", "push_alert", "api_query", "api_report", "bot",
            "bayesian_filter", "sms"
        )
        removedTables.forEach {
            db.execSQL("DROP TABLE IF EXISTS $it")
        }

        onCreate(db)
    }
}