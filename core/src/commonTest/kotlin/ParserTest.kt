import io.github.aeckar.parsing.parser
import kotlin.test.Test

// TODO ebnf to mp as well
class ParserTest {
    @Test
    fun f() {
        val example by parser {
            val term by junction()
            val expression by junction()

            val digit by of("0-9")
            val number by multiple(digit)
            val factor by '(' + expression + ')' or
                    number

            term.actual = factor + '*' + term or
                    factor + '/' + term or
                    factor
            expression.actual = term + '+' + expression or
                    term + '-' + expression or
                    term

            start = expression
            skip = multiple(" ")  // prefer multiple to any for skip symbols
        }
        val ast = example.parse("(1 + 2) * 3")
        println()
        println(ast?.treeString())
    }
}