package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SourceCodeType;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayList;
import java.util.List;

public class SemanticAnalyzer implements ActionObserver {
    private SymbolTable symbolTable;
    private final List<Symbol> symbolStack = new ArrayList<>();

    @Override
    public void whenAccept(Status currentStatus) {
        symbolStack.clear();
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        final var bodySize = production.body().size();
        final var body = symbolStack.subList(symbolStack.size() - bodySize, symbolStack.size());
        final var head = new Symbol(production.head().toString(), null, null);

        switch (production.toString()) {
            case "D -> int" -> head.type = SourceCodeType.Int;
            case "S -> D id" -> {
                final var type = body.get(0).type;
                final var name = body.get(1).text;
                symbolTable.get(name).setType(type);
            }
            default -> {
                // Other productions do not change declared symbol types.
            }
        }

        body.clear();
        symbolStack.add(head);
    }

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        symbolStack.add(new Symbol(currentToken.getKindId(), currentToken.getText(), null));
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        this.symbolTable = table;
    }

    private static class Symbol {
        private Symbol(String name, String text, SourceCodeType type) {
            this.name = name;
            this.text = text;
            this.type = type;
        }

        private final String name;
        private final String text;
        private SourceCodeType type;
    }
}
