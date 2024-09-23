import io.github.aeckar.parsing.containers.Pivot
import io.github.aeckar.parsing.containers.PivotIterator
import io.github.aeckar.parsing.containers.RevertibleIterator
import io.github.aeckar.parsing.containers.pivot
import io.github.aeckar.parsing.containers.pivotIterator
import io.github.aeckar.parsing.containers.revertibleIterator
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val STRING = "A3Z8q9B5C2"
private val LIST = STRING.toList()

class IteratorTests {
    private fun RevertibleIterator<*, *>.testReverting(elements: List<*>) {
        assertFailsWith<IllegalStateException> { revert() }
        save()
        save()
        next()
        removeSave()
        assertEquals(elements[1], next())
        revert()
        assertEquals(elements[0], next())
        advance(100)
        assertFailsWith<NoSuchElementException> { next() }
    }

    private fun PivotIterator<*, *, MutableList<Int>>.testPivoting() {
        save()                      // [0] <-
        save()                      // [0, 0] <-
        save()                      // [0, 0, 0] <-
        next()                      // get 0, move to 1
        removeSave()                // [0, 0] -> (do nothing)
        next()                      // get 1, move to 2
        here()[0] = 12              // value at 2
        revert()                    // [0] -> move to 0
        advance(2)                  // move to 2
        assertEquals(12, here()[0]) // value at 2                       // TODO failing for source iterator
        revert()                    // [] -> move to 0
        save()                      // [0] <-
        here()[0] = 10              // value at 0
        advance(7)                  // move to 7
        here()[0] = 17              // value at 7
        next()                      // get 7, move to 8
        here()                      // initialize 8
        revert()                    // [] -> move to 0
        advance(1)                  // move to 1
        here()[0] = 11              // value at 1
        assertContentEquals(
            expected = listOf(listOf(10), listOf(11), listOf(12), listOf(17), listOf(0)),
            actual = pivots().map { it.value }
        )
    }

    @Test
    fun `revertible list iterator`() {
        assertContentEquals(LIST, LIST.revertibleIterator().asSequence().toList())
        LIST.revertibleIterator().testReverting(LIST)
    }

    @Test
    fun `revertible string iterator`() {
        STRING.revertibleIterator().testReverting(STRING.toList())
    }

    @Test
    fun `revertible source iterator`() {
        val source = SystemFileSystem.source(Path("src/commonTest/resources/source.txt"))
        source.revertibleIterator().testReverting(STRING.toList())
        // play with SECTION_SIZE, too
    }

    @Test
    fun `pivoting list iterator`() {
        assertContentEquals(LIST, LIST.pivotIterator { mutableListOf(0) }.asSequence().toList())
        LIST.pivotIterator { mutableListOf(0) }.testReverting(LIST)
        LIST.pivotIterator { mutableListOf(0) }.testPivoting()
    }

    @Test
    fun `pivoting string iterator`() {
        STRING.pivotIterator { mutableListOf(0) }.testReverting(STRING.toList())
        STRING.pivotIterator { mutableListOf(0) }.testPivoting()
    }

    @Test
    fun `pivoting source iterator`() {
        fun getSource() = SystemFileSystem.source(Path("src/commonTest/resources/source.txt"))

        assertEquals(
            expected = getSource().buffered().readString(),
            actual = getSource().pivotIterator { mutableListOf(0) }.asSequence().joinToString(separator = "")
        )
        getSource().pivotIterator { mutableListOf(0) }.testReverting(STRING.toList())
        getSource().pivotIterator { mutableListOf(0) }.testPivoting()
        // play with SECTION_SIZE, too
    }
}