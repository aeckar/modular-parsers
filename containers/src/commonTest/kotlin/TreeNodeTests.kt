import io.github.aeckar.parsing.containers.TreeNode
import kotlin.test.Test
import kotlin.test.assertEquals

private class SimpleTreeNode(val ordinal: Int) : TreeNode<SimpleTreeNode> {
    override val children: MutableList<SimpleTreeNode> = mutableListOf()

    override fun toString() = "*"
}

class TreeNodeTests {
    private val tree = SimpleTreeNode(4)

    init {
        tree.children.add(SimpleTreeNode(0))
        tree.children.add(SimpleTreeNode(2).apply { children.add(SimpleTreeNode(1)) })
        tree.children.add(SimpleTreeNode(3))
    }

    @Test
    fun `correct tree string`() {
        assertEquals("""
            *
            ├── *
            ├── *
            │   └── *
            └── *
        """.trimIndent(), tree.treeString())
    }

    @Test
    fun `correct iteration order`() {
        for ((index, node) in tree.withIndex()) {
            assertEquals(index, node.ordinal)
        }
    }
}