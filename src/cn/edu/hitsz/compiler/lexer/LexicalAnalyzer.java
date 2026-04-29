package cn.edu.hitsz.compiler.lexer;

import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * TODO: 实验一: 实现词法分析
 * <br>
 * 你可能需要参考的框架代码如下:
 *
 * @see Token 词法单元的实现
 * @see TokenKind 词法单元类型的实现
 */
public class LexicalAnalyzer {
    private final SymbolTable symbolTable;
    private String sourceCode = "";
    private final List<Token> tokens = new ArrayList<>();

    public LexicalAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }


    /**
     * 从给定的路径中读取并加载文件内容
     *
     * @param path 路径
     */
    public void loadFile(String path) {
        sourceCode = FileUtils.readFile(path);
    }

    /**
     * 执行词法分析, 准备好用于返回的 token 列表 <br>
     * 需要维护实验一所需的符号表条目, 而得在语法分析中才能确定的符号表条目的成员可以先设置为 null
     */
    public void run() {
        tokens.clear();

        int index = 0;
        while (index < sourceCode.length()) {
            final var current = sourceCode.charAt(index);

            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }

            if (Character.isLetter(current) || current == '_') {
                final var begin = index;
                index++;
                while (index < sourceCode.length()) {
                    final var next = sourceCode.charAt(index);
                    if (Character.isLetterOrDigit(next) || next == '_') {
                        index++;
                    } else {
                        break;
                    }
                }

                final var text = sourceCode.substring(begin, index);
                switch (text) {
                    case "int" -> tokens.add(Token.simple("int"));
                    case "return" -> tokens.add(Token.simple("return"));
                    default -> {
                        tokens.add(Token.normal("id", text));
                        if (!symbolTable.has(text)) {
                            symbolTable.add(text);
                        }
                    }
                }
                continue;
            }

            if (Character.isDigit(current)) {
                final var begin = index;
                index++;
                while (index < sourceCode.length() && Character.isDigit(sourceCode.charAt(index))) {
                    index++;
                }

                tokens.add(Token.normal("IntConst", sourceCode.substring(begin, index)));
                continue;
            }

            switch (current) {
                case '=' -> tokens.add(Token.simple("="));
                case ',' -> tokens.add(Token.simple(","));
                case ';' -> tokens.add(Token.simple("Semicolon"));
                case '+' -> tokens.add(Token.simple("+"));
                case '-' -> tokens.add(Token.simple("-"));
                case '*' -> tokens.add(Token.simple("*"));
                case '/' -> tokens.add(Token.simple("/"));
                case '(' -> tokens.add(Token.simple("("));
                case ')' -> tokens.add(Token.simple(")"));
                default -> throw new RuntimeException("Unexpected character: " + current);
            }
            index++;
        }

        tokens.add(Token.eof());
    }

    /**
     * 获得词法分析的结果, 保证在调用了 run 方法之后调用
     *
     * @return Token 列表
     */
    public Iterable<Token> getTokens() {
        return tokens;
    }

    public void dumpTokens(String path) {
        FileUtils.writeLines(
            path,
            StreamSupport.stream(getTokens().spliterator(), false).map(Token::toString).toList()
        );
    }
}
