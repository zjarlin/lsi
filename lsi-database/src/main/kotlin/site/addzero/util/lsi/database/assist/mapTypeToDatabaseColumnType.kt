package site.addzero.util.lsi.database.assist

import site.addzero.util.lsi.assist.TypeChecker
import site.addzero.util.lsi.database.model.DatabaseColumnType

/**
 * 类型映射逻辑
 * 将 Java/Kotlin 类型映射到数据库列类型
 *
 * 注意：必须先检查Long再检查Int，因为TypeChecker.isIntType()会匹配所有INTEGER category的类型（包括Long）
 */
 fun mapTypeToDatabaseColumnType(typeName: String): DatabaseColumnType {
    return when {
        TypeChecker.isLongType(typeName) -> DatabaseColumnType.BIGINT
        TypeChecker.isIntType(typeName) -> DatabaseColumnType.INT
        TypeChecker.isBigDecimalType(typeName) -> DatabaseColumnType.DECIMAL
        TypeChecker.isFloatType(typeName) -> DatabaseColumnType.FLOAT
        TypeChecker.isDoubleType(typeName) -> DatabaseColumnType.DOUBLE
        TypeChecker.isStringType(typeName) -> DatabaseColumnType.VARCHAR
        TypeChecker.isDateType(typeName) -> DatabaseColumnType.DATE
        TypeChecker.isDateTimeType(typeName) -> DatabaseColumnType.DATETIME
        TypeChecker.isBooleanType(typeName) -> DatabaseColumnType.BOOLEAN
        TypeChecker.isCharType(typeName) -> DatabaseColumnType.CHAR
        else -> DatabaseColumnType.VARCHAR
    }
}