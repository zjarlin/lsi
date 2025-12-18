package site.addzero.util.lsi_impl.impl.ksp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * LSI-KSP基础功能测试
 * 这个测试类验证LSI-KSP模块的基本功能是否正常工作
 */
class KspLsiBasicTest {

    @Test
    fun `should have all core classes available`() {
        // 验证所有核心类都可以被实例化（通过类加载器检查）
        assertDoesNotThrow {
            Class.forName("site.addzero.util.lsi_impl.impl.ksp.clazz.KspLsiClass")
            Class.forName("site.addzero.util.lsi_impl.impl.ksp.field.KspLsiField")
            Class.forName("site.addzero.util.lsi_impl.impl.ksp.method.KspLsiMethod")
            Class.forName("site.addzero.util.lsi_impl.impl.ksp.type.KspLsiType")
            Class.forName("site.addzero.util.lsi_impl.impl.ksp.anno.KspLsiAnnotation")
        }
    }

    @Test
    fun `should have extension functions available`() {
        // 验证扩展函数类可以被加载
        assertDoesNotThrow {
            Class.forName("site.addzero.util.lsi_impl.impl.ksp.KspLsiExtensionsKt")
        }
    }
}