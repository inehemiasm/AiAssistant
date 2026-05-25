package com.neo.chevere.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

object CodeHighlighter {
    fun highlight(
        code: String,
        language: String?,
        commentColor: Color = Color(0xFF78909C), // Slate grey
        keywordColor: Color = Color(0xFF00E5FF), // Cyber Cyan
        stringColor: Color = Color(0xFF00E676),  // Neon Green
        numberColor: Color = Color(0xFFFFB300),  // Amber/Orange
        typeColor: Color = Color(0xFFD500F9)     // Neon Purple
    ): AnnotatedString {
        val lang = language?.lowercase(Locale.US)?.trim() ?: return AnnotatedString(code)
        
        // Define regex pattern depending on language type
        val pattern = when (lang) {
            "html", "xml", "svg" -> Regex(
                "(<!--[\\s\\S]*?-->)|" +               // Group 1: Comments
                "(<\\/?[a-zA-Z0-9:-]+)|" +            // Group 2: Tag names
                "(\\s[a-zA-Z0-9:-]+=)|" +             // Group 3: Attribute names
                "(\"[^\"]*\"|'[^']*')"                  // Group 4: Attribute values
            )
            "css" -> Regex(
                "(/\\*[\\s\\S]*?\\*/)|" +              // Group 1: Comments
                "([a-zA-Z-]+(?=\\s*:))|" +             // Group 2: Properties
                "(#[a-zA-Z0-9_-]+|\\.[a-zA-Z0-9_-]+|[a-zA-Z]+)|" + // Group 3: Selectors
                "(\"[^\"]*\"|'[^']*'|#[a-fA-F0-9]{3,6}|\\b\\d+(?:px|em|rem|%)?\\b)" // Group 4: Values/Colors
            )
            else -> {
                val keywords = when (lang) {
                    "kotlin", "kt" -> listOf("package", "import", "class", "interface", "object", "fun", "val", "var", "private", "public", "protected", "internal", "override", "suspend", "return", "if", "else", "when", "for", "while", "is", "as", "in", "null", "true", "false", "this", "super", "try", "catch", "finally", "throw", "companion", "init", "constructor", "data", "sealed", "enum")
                    "java" -> listOf("package", "import", "class", "interface", "public", "private", "protected", "static", "final", "void", "int", "double", "float", "long", "boolean", "char", "byte", "short", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "return", "new", "this", "super", "try", "catch", "finally", "throw", "throws", "null", "true", "false", "extends", "implements", "instanceof")
                    "python", "py" -> listOf("def", "class", "import", "from", "as", "return", "if", "elif", "else", "for", "while", "in", "is", "not", "and", "or", "lambda", "try", "except", "finally", "raise", "with", "yield", "pass", "break", "continue", "None", "True", "False", "global", "nonlocal", "assert")
                    "javascript", "js", "typescript", "ts" -> listOf("function", "class", "const", "let", "var", "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "default", "new", "this", "typeof", "instanceof", "in", "of", "null", "undefined", "true", "false", "try", "catch", "finally", "throw", "async", "await", "import", "export", "from", "as")
                    "bash", "sh", "shell", "powershell", "ps1" -> listOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "in", "case", "esac", "function", "return", "exit", "cd", "ls", "pwd", "echo", "cat", "grep", "awk", "sed", "git", "gradlew", "npm", "npx", "node", "python", "pip", "curl", "wget", "ssh")
                    else -> emptyList()
                }
                
                if (keywords.isEmpty()) return AnnotatedString(code)
                val keywordsPattern = keywords.joinToString("|") { "\\b$it\\b" }
                val commentPattern = if (lang in listOf("python", "py", "bash", "sh", "shell", "powershell", "ps1")) {
                    "(#.*)"
                } else {
                    "(//.*|/\\*[\\s\\S]*?\\*/)"
                }
                
                Regex(
                    "$commentPattern|" + // Group 1: Comments
                    "(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"[^\"]*\"|'[^']*'|`[^`]*`)|" + // Group 2: Strings
                    "($keywordsPattern)|" + // Group 3: Keywords
                    "(\\b\\d+\\b)" // Group 4: Numbers
                )
            }
        }

        val builder = AnnotatedString.Builder(code)
        pattern.findAll(code).forEach { matchResult ->
            val groups = matchResult.groups
            if (groups[1] != null) {
                val range = groups[1]!!.range
                builder.addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), range.first, range.last + 1)
            } else if (groups[2] != null) {
                val range = groups[2]!!.range
                val color = if (lang in listOf("html", "xml", "svg", "css")) keywordColor else stringColor
                builder.addStyle(SpanStyle(color = color), range.first, range.last + 1)
            } else if (groups[3] != null) {
                val range = groups[3]!!.range
                val color = if (lang in listOf("html", "xml", "svg", "css")) typeColor else keywordColor
                builder.addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), range.first, range.last + 1)
            } else if (groups[4] != null) {
                val range = groups[4]!!.range
                val color = if (lang in listOf("html", "xml", "svg", "css")) stringColor else numberColor
                builder.addStyle(SpanStyle(color = color), range.first, range.last + 1)
            }
        }
        return builder.toAnnotatedString()
    }
}
