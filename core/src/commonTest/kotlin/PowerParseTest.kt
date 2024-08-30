package io.github.aeckar.parsing

import kotlin.test.Test

val strings by parser {
    val char by junction()
    val text by '\'' + multiple(char) + '\''
    val escape by '\'' + multiple(char) + '\''
    val id by of("a-zA-Z") + anyOf("a-zA-Z0-9_")

    char.actual = escape or of(!"'\n")
}

val switches by parser {
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

val ebnf by parser {
    val id by strings.import<NameableSymbol<*>>()
    val symbol by junction()
    val rule by id + ':' + symbol + ';'
    val sequence by symbol + multiple(symbol)
    val junction by symbol + multiple('|' + symbol)
    val repetition by symbol + '+'
    val option by symbol + '?'
    val any by symbol + '*'

    symbol.actual = '(' + symbol + ')' or
            junction or
            sequence or
            repetition or
            option or
            any or
            id or
            strings["text"] or
            switches["switch"]

    start = multiple(rule)
    skip = multipleOf("\u0000-\u0020") or
            text("/*") + anyOf("-") + text("*/") or
            text("//") + of("-\u0009\u000B-")
}

class PowerParseTest {
    @Test
    fun parser() {
        val strings = strings
        val switches = switches
        val ebnf = ebnf
        println(ebnf("myRule : 'Hello, world!';"))
    }
}