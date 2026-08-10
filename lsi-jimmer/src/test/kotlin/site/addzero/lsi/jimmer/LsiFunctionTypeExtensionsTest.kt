package site.addzero.lsi.jimmer

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType

class LsiFunctionTypeExtensionsTest {

    @Test
    fun `jimmer signature preserves function shape and nested nullability`() {
        val functionType = LsiFunctionType(
            returnType = LsiPrimitiveType(LsiPrimitiveKind.BOOLEAN),
            receiverType = LsiDeclaredType(LsiSymbolId.type("sample.Scope")),
            parameterTypes = listOf(
                LsiPrimitiveType(LsiPrimitiveKind.INT),
                LsiDeclaredType(
                    declarationId = LsiSymbolId.type("java.lang.String"),
                    nullability = LsiNullability.NULLABLE,
                ),
            ),
            suspending = true,
            nullability = LsiNullability.NULLABLE,
        )

        assertEquals(
            "function:suspend:receiver:type:sample.Scope!:" +
                "parameters:[primitive:int!,type:java.lang.String?]:" +
                "return:primitive:boolean!?",
            functionType.jimmerTypeSignature(),
        )
        assertEquals(
            "function:suspend:receiver:type:sample.Scope!:" +
                "parameters:[primitive:int!,type:java.lang.String?]:" +
                "return:primitive:boolean!",
            functionType.jimmerTypeSignature(ignoreRootNullability = true),
        )
    }
}
