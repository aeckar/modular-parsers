package io.github.aeckar.parsing.pivot

internal data class SourcePosition(val bufferSection: String, val sectionPosition: Int) : Comparable<SourcePosition> {
    override fun compareTo(other: SourcePosition): Int {

    }
}