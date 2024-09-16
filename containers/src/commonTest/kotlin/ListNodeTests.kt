import io.github.aeckar.parsing.containers.*
import kotlin.collections.toList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private fun simpleListNode(): (Int) -> SimpleListNode = ::SimpleListNode

private data class SimpleListNode(val ordinal: Int) : ListNode<SimpleListNode>()

class ListNodeTests {
    @Test
    fun `get or insert pivot`() {
        val initial = Pivot(1, "head")
        check(initial.getOrInsert(1) { "first" } === initial)
        initial.apply {
            getOrInsert(-8) { "negative eighth" }
            getOrInsert(2) { "second" }
            getOrInsert(17) { "seventeenth" }
            getOrInsert(5) { "fifth" }
            getOrInsert(20) { "twentieth" }
        }
        assertContentEquals(
            expected = listOf(-8, 1, 2, 5, 17, 20),
            actual = initial.head().toList().map { it.position }
        )
    }

    @Test
    fun `append node`() {
        val head = SimpleListNode(0).apply { insertAfter(SimpleListNode(1)) }
        head.next().insertAfter(SimpleListNode(2))
        assertEquals(head.next().ordinal, 1)
        assertEquals(head.next().next().ordinal, 2)
        head.insertAfter(SimpleListNode(-1))
        assertContentEquals(
            expected = listOf(0, -1, 1, 2),
            actual = head.toList().map { it.ordinal }
        )
    }

    @Test
    fun `prepend node`() {
        val tail = SimpleListNode(0).apply { insertBefore(SimpleListNode(1)) }
        tail.last().insertBefore(SimpleListNode(2))
        assertEquals(tail.last().ordinal, 1)
        assertEquals(tail.last().last().ordinal, 2)
        tail.insertBefore(SimpleListNode(-1))
        assertContentEquals(
            expected = listOf(0, -1, 1, 2),
            actual = tail.reversed().toList().map { it.ordinal }
        )
    }

    @Test
    fun `forward list traversal`() {
        val linkedList = linkedListOf(simpleListNode(), 0, 1, 2)
        for ((index, node) in linkedList.withIndex()) {
            assertEquals(node.ordinal, index)
        }
    }

    @Test
    fun `forward traversal`() {
        val linkedList = SimpleListNode(0).apply { insertAfter(SimpleListNode(1)) }
        linkedList.next().insertAfter(SimpleListNode(2))
        for ((index, node) in linkedList.withIndex()) {
            assertEquals(node.ordinal, index)
        }
    }

    @Test
    fun `backward traversal`() {
        val linkedList = SimpleListNode(2).apply { insertAfter(SimpleListNode(1)) }
        linkedList.next().insertAfter(SimpleListNode(0))
        for ((index, node) in linkedList.tail().reversed().withIndex()) {
            assertEquals(node.ordinal, index)
        }
    }

    @Test
    fun `forward search or last`() {
        linkedListOf(pivot(), 1 to "first", 2 to "second", 3 to "third", 4 to "fourth").head().apply {
            assertEquals("second", seek { it.position == 2 }.value)
            assertEquals("third", seek { it.position == 3 }.value)
            assertEquals("fourth", seek { false }.value)
        }

    }

    @Test
    fun `backward search or last`() {
        linkedListOf(pivot(), 1 to "first", 2 to "second", 3 to "third", 4 to "fourth").tail().apply {
            assertEquals("third", backtrace { it.position == 3 }.value)
            assertEquals("second", backtrace { it.position == 2 }.value)
            assertEquals("first", backtrace { false }.value)
        }
    }

    @Test
    fun `convert to list`() {
        assertContentEquals(
            expected = listOf(0, 1, 2),
            actual = linkedListOf(simpleListNode(), 0, 1, 2).toList().map { it.ordinal }
        )
    }
}