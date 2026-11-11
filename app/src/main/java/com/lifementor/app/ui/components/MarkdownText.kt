package com.lifementor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 轻量 Markdown 渲染（无第三方依赖）：标题/列表/任务/引用/代码/分割线/粗体/行内代码 */
private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class Paragraph(val text: String) : Block()
    data class Bullet(val text: String) : Block()
    data class Check(val text: String, val done: Boolean) : Block()
    data class Numbered(val num: String, val text: String) : Block()
    data class Quote(val text: String) : Block()
    data class Code(val lines: List<String>) : Block()
    data class TableLine(val text: String) : Block()
    object Rule : Block()
}

private val numberedRegex = Regex("^(\\d+)[.)、]\\s*(.+)$")
private val inlineRegex = Regex("""\*\*([^*]+)\*\*|`([^`]+)`""")
private val inlineBg = Color(0x22888888)

private fun parseMarkdown(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    var inCode = false
    val codeBuf = mutableListOf<String>()

    fun flushPara() {
        if (para.isNotEmpty()) {
            out.add(Block.Paragraph(para.toString().trim()))
            para.setLength(0)
        }
    }

    for (raw in src.lines()) {
        val t = raw.trim()
        if (t.startsWith("```")) {
            if (inCode) {
                out.add(Block.Code(codeBuf.toList()))
                codeBuf.clear()
                inCode = false
            } else {
                flushPara()
                inCode = true
            }
            continue
        }
        if (inCode) {
            codeBuf.add(raw)
            continue
        }
        val numMatch = numberedRegex.find(t)
        when {
            t.isEmpty() -> flushPara()
            t.matches(Regex("^(-{3,}|\\*{3,})$")) -> {
                flushPara(); out.add(Block.Rule)
            }
            t.startsWith("#") -> {
                flushPara()
                val level = t.takeWhile { it == '#' }.count()
                out.add(Block.Heading(level, t.drop(level).trim()))
            }
            t.startsWith("- [") && t.length > 5 -> {
                flushPara()
                out.add(Block.Check(t.substring(6).trim(), done = t[3] == 'x' || t[3] == 'X'))
            }
            t.startsWith("- ") || t.startsWith("* ") -> {
                flushPara(); out.add(Block.Bullet(t.substring(2).trim()))
            }
            t.startsWith(">") -> {
                flushPara(); out.add(Block.Quote(t.drop(1).trim()))
            }
            t.startsWith("|") -> {
                flushPara(); out.add(Block.TableLine(t))
            }
            numMatch != null -> {
                flushPara(); out.add(Block.Numbered(numMatch.groupValues[1], numMatch.groupValues[2]))
            }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(t)
            }
        }
    }
    if (inCode && codeBuf.isNotEmpty()) out.add(Block.Code(codeBuf.toList()))
    flushPara()
    return out
}

private fun inline(raw: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineRegex.findAll(raw)) {
        append(raw.substring(last, m.range.first))
        val bold = m.groupValues[1]
        val code = m.groupValues[2]
        if (bold.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
        } else {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = inlineBg)) { append(code) }
        }
        last = m.range.last + 1
    }
    append(raw.substring(last))
}

@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { parseMarkdown(source) }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        for (b in blocks) {
            when (b) {
                is Block.Heading -> Text(
                    inline(b.text),
                    style = headingStyle(b.level),
                    modifier = Modifier.padding(top = if (b.level <= 2) 16.dp else 10.dp, bottom = 6.dp),
                )
                is Block.Paragraph -> Text(
                    inline(b.text),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                is Block.Bullet -> Row(Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(inline(b.text), style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp)
                }
                is Block.Check -> Row(Modifier.padding(start = 6.dp, top = 3.dp, bottom = 3.dp)) {
                    Text(
                        if (b.done) "☑ " else "☐ ",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        inline(b.text),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 15.sp,
                        textDecoration = if (b.done) TextDecoration.LineThrough else TextDecoration.None,
                    )
                }
                is Block.Numbered -> Row(Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)) {
                    Text("${b.num}.  ", style = MaterialTheme.typography.bodyMedium)
                    Text(inline(b.text), style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp)
                }
                is Block.Quote -> Row(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    Text("│", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    Text(
                        " " + inline(b.text).text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        ),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                is Block.Code -> Text(
                    b.lines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(8.dp),
                        )
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                is Block.TableLine -> Text(
                    b.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .horizontalScroll(rememberScrollState()),
                )
                Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }
        }
        Box(Modifier.padding(bottom = 40.dp))
    }
}

@Composable
private fun headingStyle(level: Int) = when (level) {
    1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
}
