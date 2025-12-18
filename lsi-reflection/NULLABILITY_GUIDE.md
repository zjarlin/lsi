# 可空性判断指南

## 概述

LSI Reflection 模块现在支持基于注解的可空性判断，兼容多种主流的可空性注解框架。

## 支持的注解框架

### 可空注解 (@Nullable)

支持以下框架的 `@Nullable` 注解（通过简称匹配）：

- **JSpecify**: `org.jspecify.annotations.Nullable`
- **JSR-305**: `javax.annotation.Nullable`, `javax.annotation.CheckForNull`
- **JetBrains**: `org.jetbrains.annotations.Nullable`
- **Android**: `androidx.annotation.Nullable`, `android.support.annotation.Nullable`
- **Eclipse**: `org.eclipse.jdt.annotation.Nullable`
- **Spring**: `org.springframework.lang.Nullable`
- **FindBugs**: `edu.umd.cs.findbugs.annotations.Nullable`

### 非空注解 (@NonNull / @NotNull)

支持以下框架的非空注解（通过简称匹配）：

- **JSpecify**: `org.jspecify.annotations.NonNull`
- **JSR-305**: `javax.annotation.Nonnull`, `javax.annotation.NonNull`
- **JetBrains**: `org.jetbrains.annotations.NotNull`
- **Android**: `androidx.annotation.NonNull`
- **Eclipse**: `org.eclipse.jdt.annotation.NonNull`
- **Spring**: `org.springframework.lang.NonNull`
- **Lombok**: `lombok.NonNull`
- **FindBugs**: `edu.umd.cs.findbugs.annotations.NonNull`

### JSpecify 特殊注解

- **@NullMarked**: 标记类或包，表示默认所有类型都是非空的
- **@NullUnmarked**: 覆盖 @NullMarked，表示该作用域内可空性未指定

## API 使用

### Class.isNullable()

判断 **Class 类型本身** 是否可空（不检查注解）。

```kotlin
import site.addzero.util.lsi_impl.impl.reflection.clazz.isNullable

// 基本类型不可空
val intClass = Int::class.javaPrimitiveType
println(intClass?.isNullable()) // false

// 引用类型可空
val stringClass = String::class.java
println(stringClass.isNullable()) // true
```

**判断逻辑**：
- 基本类型（int, long, boolean 等）→ false（不可空）
- 引用类型 → true（可空）

### Field.isNullable()

判断 **字段** 是否可空（检查类型和注解）。

```kotlin
import site.addzero.util.lsi_impl.impl.reflection.field.isNullable

class User {
    val id: Int = 0                           // 基本类型
    @Nullable val name: String? = null         // 显式 @Nullable
    @NonNull val email: String = ""            // 显式 @NonNull
    val address: String? = null                // 无注解
}

val idField = User::class.java.getDeclaredField("id")
println(idField.isNullable()) // false - 基本类型

val nameField = User::class.java.getDeclaredField("name")
println(nameField.isNullable()) // true - 有 @Nullable

val emailField = User::class.java.getDeclaredField("email")
println(emailField.isNullable()) // false - 有 @NonNull

val addressField = User::class.java.getDeclaredField("address")
println(addressField.isNullable()) // true - 保守策略，假设可空
```

**判断逻辑**（按优先级）：

1. **基本类型** → false（不可空）
2. **显式 @Nullable** → true（可空）
3. **显式 @NonNull/@NotNull** → false（不可空）
4. **@NullMarked 上下文** → false（默认非空）
5. **默认保守策略** → true（假设可空）

### Parameter.isNullable()

判断 **方法参数** 是否可空（检查类型和注解）。

```kotlin
import site.addzero.util.lsi_impl.impl.reflection.field.isNullable
import java.lang.reflect.Method

class UserService {
    fun createUser(
        id: Int,
        @Nullable name: String?,
        @NonNull email: String
    ) {
        // ...
    }
}

val method = UserService::class.java.getMethod("createUser", 
    Int::class.javaPrimitiveType, String::class.java, String::class.java)

val params = method.parameters
println(params[0].isNullable()) // false - int 基本类型
println(params[1].isNullable()) // true - @Nullable
println(params[2].isNullable()) // false - @NonNull
```

判断逻辑与 `Field.isNullable()` 相同。

## JSpecify @NullMarked 示例

### 包级别 @NullMarked

```java
// package-info.java
@NullMarked
package com.example.myapp;

import org.jspecify.annotations.NullMarked;
```

```java
package com.example.myapp;

// 在这个包中，所有类型默认非空
public class User {
    private String name;        // 非空（因为包级 @NullMarked）
    @Nullable private String nickname;  // 显式可空
    
    public User(String name) {
        this.name = name;
    }
}
```

### 类级别 @NullMarked

```java
@NullMarked
public class Product {
    private String name;        // 非空
    private String description; // 非空
    @Nullable private String notes;  // 显式可空
}
```

### 字段级别 @NullUnmarked

```java
@NullMarked
public class Order {
    private String orderId;     // 非空
    
    @NullUnmarked
    private String comment;     // 可空性未指定（回退到保守策略）
}
```

## 在 DDL Generator 中的应用

```kotlin
import site.addzero.util.lsi_impl.impl.reflection.field.isNullable

fun generateColumnDefinition(field: Field):LsiField {
    val columnName = field.name.toUnderLineCase()
    val javaType = field.type.name
    
    // 使用 Field.isNullable() 判断字段是否可空
    val nullable = field.isNullable()
    
    return ColumnDefinition(
        name = columnName,
        typeName = javaType,
        nullable = nullable,
        // ...
    )
}
```

## 注意事项

### 1. Kotlin 类型系统与 JVM

Kotlin 的可空类型在编译后会生成注解：
- `String?` → `@Nullable String`
- `String` → `@NotNull String`（如果开启了 Kotlin 编译器的注解生成）

但在反射时，Kotlin 的类型信息可能丢失，所以建议：
- 使用显式的 JVM 注解（如 JSpecify、JSR-305）
- 或使用 LSI 的 Kotlin PSI 支持（lsi-kt 模块）

### 2. 保守策略

当无法确定可空性时（没有注解、没有 @NullMarked），默认返回 `true`（假设可空）。

这是为了避免 NullPointerException：
- 假设可空 → 生成 `NULL` 列 → 安全
- 假设非空 → 生成 `NOT NULL` 列 → 可能导致插入失败

### 3. 性能考虑

`Field.isNullable()` 和 `Parameter.isNullable()` 会检查注解，有一定开销。如果需要频繁调用，可以考虑缓存结果。

## 测试

测试用例位于：
```
checkouts/metaprogramming-lsi/lsi-reflection/src/test/kotlin/
  site/addzero/util/lsi_impl/impl/reflection/field/FieldNullabilityTest.kt
```

运行测试：
```bash
./gradlew :checkouts:lsi:lsi-reflection:test
```

## 参考资料

- [JSpecify 官方文档](https://jspecify.dev/docs/user-guide/)
- [JSpecify GitHub](https://github.com/jspecify/jspecify)
- [Baeldung: JSpecify Null Safety Guide](https://www.baeldung.com/java-jspecify-null-safety)
