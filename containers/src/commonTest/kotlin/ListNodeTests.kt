import io.github.aeckar.parsing.containers.ListNode
import io.github.aeckar.parsing.containers.Pivot
import io.github.aeckar.parsing.containers.linkedList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private data class SimpleListNode(val ordinal: Int) : ListNode<SimpleListNode>()

class ListNodeTests {
    @Test
    fun `find or insert pivot`() {
        val x = Pivot(1, "15")

    }

    @Test
    fun `append node`() {
        val head = SimpleListNode(0).apply { append(SimpleListNode(1)) }
        head.next().append(SimpleListNode(2))
        assertEquals(head.next().ordinal, 1)
        assertEquals(head.next().next().ordinal, 2)
    }

    @Test
    fun `prepend node`() {
        val tail = SimpleListNode(0).apply { prepend(SimpleListNode(1)) }
        tail.last().prepend(SimpleListNode(2))
        assertEquals(tail.last().ordinal, 1)
        assertEquals(tail.last().last().ordinal, 2)
    }

    @Test
    fun `forward list traversal`() {
        val linkedList = linkedList(::SimpleListNode, 0, 1, 2)
        for ((index, node) in linkedList.withIndex()) {
            assertEquals(node.ordinal, index)
        }
    }

    @Test
    fun `forward traversal`() {
        val linkedList = SimpleListNode(0).apply { append(SimpleListNode(1)) }
        linkedList.next().append(SimpleListNode(2))
        for ((index, node) in linkedList.withIndex()) {
            assertEquals(node.ordinal, index)
        }
    }

    @Test
    fun `backward traversal`() {
        val linkedList = SimpleListNode(2).apply { append(SimpleListNode(1)) }
        linkedList.next().append(SimpleListNode(0))
        for ((index, node) in linkedList.tail().reversed().withIndex()) {
            assertEquals(node.ordinal, index)
        }
    }

    @Test
    fun `forward search or last`() {
        linkedList(::Pivot, 1 to "first", 2 to "second", 3 to "third", 4 to "fourth").apply {
            assertEquals("second", seek { it.position == 2 }.value)
            assertEquals("third", seek { it.position == 3 }.value)
            assertEquals("fourth", seek { false }.value)
        }

    }

    @Test
    fun `backward search or last`() {
        linkedList(::Pivot, 1 to "first", 2 to "second", 3 to "third", 4 to "fourth").tail().apply {
            assertEquals("third", backtrace { it.position == 3 }.value)
            assertEquals("second", backtrace { it.position == 2 }.value)
            assertEquals("first", backtrace { false }.value)
        }
    }

    @Test
    fun `convert to list`() {
        assertContentEquals(
            expected = listOf(SimpleListNode(0), SimpleListNode(1), SimpleListNode(2)),
            actual = linkedList(::SimpleListNode, 0, 1, 2).toList()
        )
    }
}