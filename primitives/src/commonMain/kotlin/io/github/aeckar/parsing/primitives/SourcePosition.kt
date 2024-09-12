package io.github.aeckar.parsing.primitives

internal data class SourcePosition(
    val bufferSection: Int,
    val position: Int
) : Comparable<SourcePosition> {
    override fun compareTo(other: SourcePosition): Int {
        val bufferDiff = bufferSection - other.bufferSection
        if (bufferDiff != 0) {
            return bufferDiff
        }
        return position - other.position
    }
}