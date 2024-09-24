import io.github.aeckar.parsing.containers.readOnly
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ListTests {
    @Test
    fun `read-only traversal`() {
        val elements = listOf(1, "2", 3.0, 4L, 5.0F)
        assertContentEquals(
            expected = elements,
            actual = elements.readOnly().toList()
        )
        assertContentEquals(
            expected = elements.subList(1, 3),
            actual = elements.readOnly().subList(1, 3).toList()
        )
    }
}