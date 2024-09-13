import io.github.aeckar.parsing.containers.TreeNode
import kotlin.test.Test
import kotlin.test.assertEquals

private class MutableTreeNode(val ordinal: Int) : TreeNode<MutableTreeNode>() {
    override val children: MutableList<MutableTreeNode> = mutableListOf()

    override fun toString() = "*"
}

class TreeTests {
    private val tree = MutableTreeNode(4)

    init {
        tree.children.add(MutableTreeNode(0))
        tree.children.add(MutableTreeNode(2).apply { children.add(MutableTreeNode(1)) })
        tree.children.add(MutableTreeNode(3))
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