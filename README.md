# modular-parsers
#### *An idiomatic DSL for creating and combining (lexer-)parsers*
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) ![Maintained?: yes](https://img.shields.io/badge/Maintained%3F-yes-green.svg)

---
## Overview
TODO
## Getting Started
To download the library from Maven Central, use the following dependency in Gradle.
```kotlin
   implementation("io.github.aeckar:modular-parsers:1.0.0")
```
An example parser for [EBNF](https://en.wikipedia.org/wiki/Extended_Backus%E2%80%93Naur_form) can be found below.
```kotlin
@file:Suppress("LocalVariableName")
import io.github.aeckar.parsing.parser

// Adapted from https://github.com/antlr/grammars-v4/blob/master/ebnf/bnf.g4
val ebnf by parser {
    // ---- fragments ----
    val ID = of("a-zA-Z") + multipleOf("a-zA-Z0-9/- ")
    val ASSIGN = text("::=")
    val WS = of(" \r\n\t")          // Match specific characters/character ranges
    
    // ---- symbols ----
    val text by ID                  // Delegation using 'by' registers symbol
    val ruleID by ID
    val id by '<' + ruleID + '>'    // Characters are first-class symbols
    val element by junction()       // Declare here, define later
    val alternative by any(element)
    val alternatives by alternative + any('|' + alternative)
    val lhs by id
    val rhs by alternatives
    val rule by lhs + ASSIGN + rhs
    val ruleList by any(rule) + eof()
    val optional by '[' + alternatives + ']'
    val zeroOrMore by '{' + alternatives + '}'
    val oneOrMore by '(' + alternatives + ')'

    // ---- configuration ----
    element.actual = optional or
            zeroOrMore or
            oneOrMore or
            text or
            id
    
    start = ruleList
    skip = WS
}

fun main() {
    val ast = ebnf.parse("<postalcode> ::= <letter> <number> <letter> <number> <letter> <number>")
    println(ast.treeString())   // Print entire tree from root `ast`
}
```
## Compatibility with ANTLR4 grammars (.g4)
TODO

## Design
TODO
