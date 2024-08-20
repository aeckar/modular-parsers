package io.github.aeckar.parsing

import kotlin.test.Test

/*
            id: [a-zA-Z] [a-zA-Z0-9_]*;

            symbol: "(" symbol ")"
                | junction
                | sequence
                | repetition
                | option
                | id
                | literal
                | switch
                ;

            rule: id ':' symbol ';'

            sequence: symbol symbol+;
            junction: symbol ('|' symbol)+;
            repetition: symbol '+';
            option: symbol '?';
            any: symbol '*';

            metaGrammar: rule+;
            skip: ([\u0000-\u0009\u000B-\u001F]+ | '/*'  [-]* '*/' | '//' [-\u0009\u000B-])+;
 */


/*
        literal: '\'' char+ '\''

        char: escape | (~[\n'])
        escape: '\\' [tnr'\\]
 */

/*
            switch: '~'? '[' ((upToRange? (boundedRange | singleChar)* atLeastRange?) | catchAll) ']'

            boundedRange: char '-' char
            singleChar: char

            upToRange: '-' char
            atLeastRange: char '-'
            catchAll: '-'

            char: escape | (~[\n\]])
            escape: '\\' [tnr\-\\\]]
 */
private val literals by parser {
    val char by junction()
    val escape by '\\' + of("tnr'\\")
    val literal by '\'' + multiple(char)

    char.actual =
}

private val metaGrammar by parser {
    val id by of("a-zA-Z") + anyOf("a-zA-Z0-9_")

    val symbol by junction()

    val rule by id + ':' + symbol + ';'

    val sequence by symbol + multiple(symbol)
    val junction by symbol + multiple('|' + symbol)
    val repetition by symbol + '+'
    val option by symbol + '?'
    val any by symbol + '*'

    val literal by literals.import<Text>()
    val switch by literals.import()

    start = multiple(rule)

    skip = multipleOf("\u0000-\u0009\u000B-\u001F") or
        text("/*") + anyOf("-") + text("*/") or
        text("//") + of("-\u0009\u000B-")

    symbol.actual = '(' + symbol + ')' or
        junction or
        sequence or
        repetition or
        option or
        any or
        id or
        literal or
        literals["switch"]

    symbol listener {
        this.matchOrdinal
    }

    literal extendsListener {

    }

}

class PowerParseTest {
    @Test
    fun parser() {

    }
}