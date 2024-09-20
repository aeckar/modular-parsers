import io.github.aeckar.parsing.containers.PivotIterator
import io.github.aeckar.parsing.containers.RevertibleIterator
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

private const val SOURCE_TXT = "A3Z8q9B5C2"
private val LIST = listOf("first", 2, 3.0)

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
    }

    private fun PivotIterator<*, *, Array<Int>>.testPivoting() {
        save()
        save()
        next()
        removeSave()
        next()
        here()[0] = 16
        revert()
        advance(2)
        assertEquals(16, here()[0])
        advance(2)
        assertFailsWith<NoSuchElementException> { next() }
    }

    @Test
    fun `revertible list iterator`() {
        assertContentEquals(LIST, LIST.revertibleIterator().asSequence().toList())
        LIST.revertibleIterator().testReverting(LIST)
    }

    @Test
    fun `revertible string iterator`() {
        SOURCE_TXT.revertibleIterator().testReverting(SOURCE_TXT.toList())
    }

    @Test
    fun `revertible source iterator`() {
        val source = SystemFileSystem.source(Path("src/commonTest/resources/source.txt"))
        source.revertibleIterator().testReverting(SOURCE_TXT.toList())
        // play with SECTION_SIZE, too
    }

    @Test
    fun `pivoting list iterator`() {
        assertContentEquals(LIST, LIST.pivotIterator { arrayOf(0) }.asSequence().toList())
        LIST.pivotIterator { arrayOf(0) }.testReverting(LIST)
        LIST.pivotIterator { arrayOf(0) }.testPivoting()
    }

    @Test
    fun `pivoting string iterator`() {
        SOURCE_TXT.pivotIterator { arrayOf(0) }.testReverting(SOURCE_TXT.toList())
        SOURCE_TXT.pivotIterator { arrayOf(0) }.testPivoting()
    }

    @Test
    fun `pivoting source iterator`() {
        fun getSource() = SystemFileSystem.source(Path("src/commonTest/resources/source.txt"))

        assertEquals(
            expected = getSource().buffered().readString(),
            actual = getSource().pivotIterator { arrayOf(0) }.asSequence().joinToString(separator = "")
        )
        getSource().pivotIterator { arrayOf(0) }.testReverting(SOURCE_TXT.toList())
        getSource().pivotIterator { arrayOf(0) }.testPivoting()
        // play with SECTION_SIZE, too
    }
}