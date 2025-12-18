package site.addzero.util.lsi.database

/**
 * 数据库列类型枚举
 * 用于表示数据库中的列类型
 */
enum class DatabaseColumnType {
    // 数值类型
    INT,
    BIGINT,
    SMALLINT,
    TINYINT,
    DECIMAL,
    FLOAT,
    DOUBLE,
    // 字符串类型
    VARCHAR,
    CHAR,
    TEXT,
    LONGTEXT,

    // 日期时间类型
    DATE,
    TIME,
    DATETIME,
    TIMESTAMP,

    // 布尔类型
    BOOLEAN,

    // 二进制类型
    BLOB,
    BYTES
}
