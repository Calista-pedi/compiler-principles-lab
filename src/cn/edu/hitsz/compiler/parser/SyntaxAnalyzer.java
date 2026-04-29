package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.NotImplementedException;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.lexer.TokenKind;
import cn.edu.hitsz.compiler.parser.table.LRTable;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.parser.table.Term;
import cn.edu.hitsz.compiler.symtab.SymbolTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * LR syntax analyzer driver.
 */
public class SyntaxAnalyzer {
    private final SymbolTable symbolTable;
    private final List<ActionObserver> observers = new ArrayList<>();
    private final List<Token> tokenList = new ArrayList<>();
    private LRTable lrTable;
    private Status initStatus;

    public SyntaxAnalyzer(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    /**
     * Register an observer.
     *
     * @param observer observer
     */
    public void registerObserver(ActionObserver observer) {
        observers.add(observer);
        observer.setSymbolTable(symbolTable);
    }

    /**
     * Notify observers when performing shift.
     *
     * @param currentStatus current status before shift
     * @param currentToken current token
     */
    public void callWhenInShift(Status currentStatus, Token currentToken) {
        for (final var listener : observers) {
            listener.whenShift(currentStatus, currentToken);
        }
    }

    /**
     * Notify observers when performing reduce.
     *
     * @param currentStatus status after popping the body and before goto
     * @param production production to reduce by
     */
    public void callWhenInReduce(Status currentStatus, Production production) {
        for (final var listener : observers) {
            listener.whenReduce(currentStatus, production);
        }
    }

    /**
     * Notify observers when performing accept.
     *
     * @param currentStatus current status
     */
    public void callWhenInAccept(Status currentStatus) {
        for (final var listener : observers) {
            listener.whenAccept(currentStatus);
        }
    }

    public void loadTokens(Iterable<Token> tokens) {
        tokenList.clear();
        for (final var token : tokens) {
            tokenList.add(token);
        }
    }

    public void loadLRTable(LRTable table) {
        lrTable = table;
        initStatus = table.getInit();
    }

    public void run() {
        if (lrTable == null || initStatus == null) {
            throw new IllegalStateException("LR table has not been loaded");
        }
        if (tokenList.isEmpty()) {
            throw new IllegalStateException("Tokens have not been loaded");
        }

        final var statusStack = new ArrayDeque<Status>();
        final var symbolStack = new ArrayDeque<Term>();
        statusStack.push(initStatus);
        symbolStack.push(TokenKind.eof());

        int tokenIndex = 0;
        while (true) {
            final var currentStatus = statusStack.peek();
            final var currentToken = tokenList.get(tokenIndex);
            final var action = lrTable.getAction(currentStatus, currentToken);

            switch (action.getKind()) {
                case Shift -> {
                    callWhenInShift(currentStatus, currentToken);
                    symbolStack.push(currentToken.getKind());
                    statusStack.push(action.getStatus());
                    tokenIndex++;
                }
                case Reduce -> {
                    final var production = action.getProduction();
                    final var body = production.body();
                    for (int i = body.size() - 1; i >= 0; i--) {
                        if (statusStack.isEmpty() || symbolStack.isEmpty()) {
                            throw new RuntimeException("Stack underflow when reducing " + production);
                        }

                        statusStack.pop();
                        final var actualSymbol = symbolStack.pop();
                        final var expectedSymbol = body.get(i);
                        if (!actualSymbol.equals(expectedSymbol)) {
                            throw new RuntimeException(
                                "Symbol stack mismatch when reducing %s: expected %s but got %s"
                                    .formatted(production, expectedSymbol, actualSymbol)
                            );
                        }
                    }

                    final var gotoBase = statusStack.peek();
                    if (gotoBase == null) {
                        throw new RuntimeException("Missing goto base status when reducing " + production);
                    }

                    final var gotoStatus = lrTable.getGoto(gotoBase, production.head());
                    if (gotoStatus.isError()) {
                        throw new RuntimeException(
                            "Goto error after reducing %s from status %s"
                                .formatted(production, gotoBase)
                        );
                    }

                    callWhenInReduce(gotoBase, production);
                    symbolStack.push(production.head());
                    statusStack.push(gotoStatus);
                }
                case Accept -> {
                    callWhenInAccept(currentStatus);
                    return;
                }
                case Error -> throw new RuntimeException(
                    "LR parse error at status %s with token %s".formatted(currentStatus, currentToken)
                );
                default -> throw new NotImplementedException();
            }
        }
    }
}
