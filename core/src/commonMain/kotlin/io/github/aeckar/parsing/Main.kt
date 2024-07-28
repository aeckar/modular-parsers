@file:Suppress("ConvertToStringTemplate")

package io.github.aeckar.parsing

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
internal val metaGrammar by parser {
    val id by of("a-zA-Z") + anyOf("a-zA-Z0-9_")

    val symbol by junction()

    val rule by id + ':' + symbol + ';'

    val sequence by symbol + multiple(symbol)
    val junction by symbol + multiple('|' + symbol)
    val repetition by symbol + '+'
    val option by symbol + '?'
    val any by symbol + '*'

    start = multiple(rule)

    skip = multipleOf("\u0000-\u0009\u000B-\u001F") or
            text("/*") + anyOf("-") + text("*/") or
            text("//") + of("-\u0009\u000B-")

    symbol.actual = '(' + symbol + ')' or
            junction or
            sequence or
            repetition or
            option or
            id //or
    //literal or
    //switch

    symbol listener {
        option1 {

        }
        option2 {

        }
    }
}
// TODO later enforce a rigid (not error-prone) api
// TODO just put extensions (Symbol.name, Symbol.substring, etc.) in the symbol classes themselves!

internal fun main() {
    println("Hello World!")
}