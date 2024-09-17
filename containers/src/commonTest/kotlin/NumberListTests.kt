import io.github.aeckar.parsing.containers.IntList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NumberListTests {
    @Test
    fun `underflow fails`() {
        val x = IntList()
        assertFailsWith<IllegalStateException> { x.removeLast() }
        x += 16
        x.removeLast()
        assertFailsWith<IllegalStateException> { x.removeLast() }
    }

    @Test
    fun `fifo behavior`() {
        val x = IntList()
        x += 7
        x += 2
        x += 5
        assertEquals(5, x.last)
        x.removeLast()
        assertEquals(2, x.removeLast())
    }
}