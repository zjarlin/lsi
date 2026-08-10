package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LsiCodeFragmentTest {

    @Test
    fun `models embeddable code and explicit imports together`() {
        val codeBlock = LsiCodeBlock.build {
            text("source.by()")
        }
        val sourceImport = LsiImport("demo.model", "by")

        val fragment = LsiCodeFragment(
            codeBlock = codeBlock,
            imports = listOf(sourceImport),
        )

        assertEquals(codeBlock, fragment.codeBlock)
        assertEquals(listOf(sourceImport), fragment.imports)
    }

    @Test
    fun `rejects duplicate explicit imports`() {
        val sourceImport = LsiImport("demo.model", "by")

        assertFailsWith<IllegalArgumentException> {
            LsiCodeFragment(
                codeBlock = LsiCodeBlock.EMPTY,
                imports = listOf(sourceImport, sourceImport),
            )
        }
    }
}
