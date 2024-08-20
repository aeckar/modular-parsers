package io.github.aeckar.parsing

import io.github.aeckar.parsing.typesafe.Sequence2
import kotlin.test.Test

// TODO after testing, add ! for lexer symbols/fragments

@Suppress("UNUSED_VARIABLE")
private val textParser by parser {
    val char by junction()
    val escape by '/' + of("tnr'//")
    val text by '\'' + multiple(char) + '\''

    char.actual = escape or of(!"\n'")
}

@Suppress("UNUSED_VARIABLE")
private val switchParser by parser {
    val escape by '/' + of("tnr/-//]")
    val char by escape or of(!"\n]")
    val boundedRange = char + '-' + char
    val upToRange by '-' + char
    val atLeastRange by char + '-'
    val catchAll by '-'
    val ranges by maybe(upToRange) + any(boundedRange or char) + maybe(atLeastRange) or
            catchAll
    val switch by maybe('~') + '[' + ranges + ']'
}

private val kombinator by parser {
    val id by of("a-zA-Z") + anyOf("a-zA-Z0-9_")
    val symbol by junction()
    val rule by id + ':' + symbol + ';'
    val sequence by symbol + multiple(symbol)
    val junction by symbol + multiple('|' + symbol)
    val repetition by symbol + '+'
    val option by symbol + '?'
    val any by symbol + '*'

    val text by textParser.import<Sequence2<*, *>>()

    symbol.actual = '(' + symbol + ')' or
            junction or
            sequence or
            repetition or
            option or
            any or
            id or
            text or
            switchParser["switch"]

    start = multiple(rule)
    skip = multipleOf("\u0000-\u0009\u000B-\u001F") or
            text("/*") + anyOf("-") + text("*/") or
            text("//") + of("-\u0009\u000B-")
}

class PowerParseTest {
    @Test
    fun parser() {
        println(kombinator.parse("myRule : 'Hello, world!'"))
    }
}