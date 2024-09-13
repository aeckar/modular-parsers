import io.github.aeckar.parsing.containers.BooleanStack
import io.github.aeckar.parsing.containers.IntStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StackTests {
    @Test
    fun `int underflow fails`() {
        val x = IntStack()
        assertFailsWith<NoSuchElementException> { x.removeLast() }
        x += 16
        x.removeLast()
        assertFailsWith<NoSuchElementException> { x.removeLast() }
    }

    @Test
    fun `int fifo behavior`() {
        val x = IntStack()
        x += 7
        x += 2
        x += 5
        assertEquals(5, x.last())
        x.removeLast()
        assertEquals(2, x.removeLast())
    }

    @Test
    fun `boolean underflow fails`() {
        val x = BooleanStack()
        assertFailsWith<NoSuchElementException> { x.removeLast() }
        x += true
        x.removeLast()
        assertFailsWith<NoSuchElementException> { x.removeLast() }
    }

    @Test
    fun `boolean fifo behavior`() {
        val x = BooleanStack()
        x += true
        x += false
        x += true
        assertEquals(true, x.last())
        x.removeLast()
        assertEquals(false, x.removeLast())
    }
}