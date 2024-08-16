package io.github.aeckar.parsing

/**
 * Thrown when a [parser definition][parser] is malformed.
 */
public class MalformedParserException internal constructor(
    message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when a token not matching any named [LexerSymbol] is found during [tokenization][Lexer.tokenize].
 *
 * @param tokens the tokens previously parsed in the input
 */
public class IllegalTokenException internal constructor(
    public val tokens: List<Token>
) : Exception(getMessage(tokens)) {
    private companion object {
        fun getMessage(tokens: List<Token>): String {
            val index = tokens.sumOf { it.substring.length }
            return "Illegal token found at index $index"
        }
    }
}