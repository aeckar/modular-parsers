import io.github.aeckar.parsing.lexerParser
import kotlin.test.Test


class LexerParserTest {
    @Test
    fun lexer_parser() {
        val example = lexerParser {
            defaultMode()
            val ws by of("eefs") does pop()
            mode("efwe")
        }
    }
}