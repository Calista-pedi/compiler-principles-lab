package cn.edu.hitsz.compiler.parser;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.lexer.Token;
import cn.edu.hitsz.compiler.parser.table.Production;
import cn.edu.hitsz.compiler.parser.table.Status;
import cn.edu.hitsz.compiler.symtab.SymbolTable;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IRGenerator implements ActionObserver {
    private final List<Symbol> symbolStack = new ArrayList<>();
    private final List<Instruction> instructions = new ArrayList<>();

    @Override
    public void whenShift(Status currentStatus, Token currentToken) {
        final var symbol = new Symbol(currentToken.getKindId(), currentToken.getText(), null);
        switch (currentToken.getKindId()) {
            case "id" -> symbol.value = IRVariable.named(currentToken.getText());
            case "IntConst" -> symbol.value = IRImmediate.of(Integer.parseInt(currentToken.getText()));
            default -> {
                // Punctuation and keywords carry no IR value by themselves.
            }
        }
        symbolStack.add(symbol);
    }

    @Override
    public void whenReduce(Status currentStatus, Production production) {
        final var bodySize = production.body().size();
        final var body = symbolStack.subList(symbolStack.size() - bodySize, symbolStack.size());
        final var head = new Symbol(production.head().toString(), null, null);

        switch (production.toString()) {
            case "S -> id = E" -> instructions.add(
                Instruction.createMov((IRVariable) body.get(0).value, body.get(2).value)
            );
            case "S -> return E" -> instructions.add(Instruction.createRet(body.get(1).value));
            case "E -> E + A" -> {
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createAdd(temp, body.get(0).value, body.get(2).value));
                head.value = temp;
            }
            case "E -> E - A" -> {
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createSub(temp, body.get(0).value, body.get(2).value));
                head.value = temp;
            }
            case "A -> A * B" -> {
                final var temp = IRVariable.temp();
                instructions.add(Instruction.createMul(temp, body.get(0).value, body.get(2).value));
                head.value = temp;
            }
            case "E -> A", "A -> B" -> head.value = body.get(0).value;
            case "B -> ( E )" -> head.value = body.get(1).value;
            case "B -> id", "B -> IntConst" -> head.value = body.get(0).value;
            default -> {
                // Declarations and statement-list productions do not emit IR.
            }
        }

        body.clear();
        symbolStack.add(head);
    }

    @Override
    public void whenAccept(Status currentStatus) {
        symbolStack.clear();
    }

    @Override
    public void setSymbolTable(SymbolTable table) {
        // IR generation only needs token text; the symbol table is maintained by semantic analysis.
    }

    public List<Instruction> getIR() {
        return Collections.unmodifiableList(instructions);
    }

    public void dumpIR(String path) {
        FileUtils.writeLines(path, getIR().stream().map(Instruction::toString).toList());
    }

    private static class Symbol {
        private Symbol(String name, String text, IRValue value) {
            this.name = name;
            this.text = text;
            this.value = value;
        }

        private final String name;
        private final String text;
        private IRValue value;
    }
}
