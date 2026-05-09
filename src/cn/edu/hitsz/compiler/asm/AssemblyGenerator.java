package cn.edu.hitsz.compiler.asm;

import cn.edu.hitsz.compiler.ir.IRImmediate;
import cn.edu.hitsz.compiler.ir.IRValue;
import cn.edu.hitsz.compiler.ir.IRVariable;
import cn.edu.hitsz.compiler.ir.Instruction;
import cn.edu.hitsz.compiler.ir.InstructionKind;
import cn.edu.hitsz.compiler.utils.FileUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 实验四: 实现汇编生成
 */
public class AssemblyGenerator {
    private static final List<String> REGISTERS = List.of("t0", "t1", "t2", "t3", "t4", "t5", "t6");
    private static final int IMMEDIATE_BITS = 12;

    private final List<Instruction> instructions = new ArrayList<>();
    private final Map<IRVariable, Integer> lastUsage = new HashMap<>();
    private final List<String> assembly = new ArrayList<>();
    private final Map<IRVariable, String> variableToRegister = new HashMap<>();
    private final Map<String, IRVariable> registerToVariable = new HashMap<>();
    private final Map<IRVariable, Integer> spillSlots = new LinkedHashMap<>();
    private final Deque<String> freeRegisters = new ArrayDeque<>();

    /**
     * 加载前端提供的中间代码, 并做简单预处理.
     *
     * @param originInstructions 前端提供的中间代码
     */
    public void loadIR(List<Instruction> originInstructions) {
        instructions.clear();
        lastUsage.clear();
        assembly.clear();
        variableToRegister.clear();
        registerToVariable.clear();
        spillSlots.clear();
        freeRegisters.clear();

        for (final var instruction : originInstructions) {
            instructions.addAll(normalizeInstruction(instruction));
        }

        for (int index = 0; index < instructions.size(); index++) {
            for (final var operand : instructions.get(index).getOperands()) {
                if (operand instanceof IRVariable variable) {
                    lastUsage.put(variable, index);
                }
            }
        }
    }


    /**
     * 执行代码生成.
     */
    public void run() {
        assembly.clear();
        variableToRegister.clear();
        registerToVariable.clear();
        spillSlots.clear();
        freeRegisters.clear();
        freeRegisters.addAll(REGISTERS);

        final var body = new ArrayList<String>();
        for (int index = 0; index < instructions.size(); index++) {
            final var instruction = instructions.get(index);
            switch (instruction.getKind()) {
                case MOV -> emitMov(index, instruction, body);
                case ADD -> emitAdd(index, instruction, body);
                case SUB -> emitSub(index, instruction, body);
                case MUL -> emitMul(index, instruction, body);
                case RET -> emitRet(index, instruction, body);
                default -> throw new RuntimeException("Unsupported instruction kind: " + instruction.getKind());
            }

            releaseDeadVariables(index);
        }

        assembly.add(".text");
        if (!spillSlots.isEmpty()) {
            assembly.add("    addi sp, sp, -" + spillSlots.size() * 4);
        }
        assembly.addAll(body);
    }


    /**
     * 输出汇编代码到文件.
     *
     * @param path 输出文件路径
     */
    public void dump(String path) {
        FileUtils.writeLines(path, assembly);
    }

    private List<Instruction> normalizeInstruction(Instruction instruction) {
        final var result = new ArrayList<Instruction>();
        final var kind = instruction.getKind();
        if (kind == InstructionKind.MOV || kind == InstructionKind.RET) {
            result.add(instruction);
            return result;
        }

        final var target = instruction.getResult();
        final var lhs = instruction.getLHS();
        final var rhs = instruction.getRHS();

        if (lhs instanceof IRImmediate leftImmediate && rhs instanceof IRImmediate rightImmediate) {
            final int foldedValue = switch (kind) {
                case ADD -> leftImmediate.getValue() + rightImmediate.getValue();
                case SUB -> leftImmediate.getValue() - rightImmediate.getValue();
                case MUL -> leftImmediate.getValue() * rightImmediate.getValue();
                default -> throw new RuntimeException("Unexpected binary instruction kind: " + kind);
            };
            result.add(Instruction.createMov(target, IRImmediate.of(foldedValue)));
            return result;
        }

        switch (kind) {
            case ADD -> {
                if (lhs instanceof IRImmediate && rhs instanceof IRVariable) {
                    result.add(Instruction.createAdd(target, rhs, lhs));
                } else {
                    result.add(instruction);
                }
            }
            case SUB -> {
                if (lhs instanceof IRImmediate immediate) {
                    final var temp = IRVariable.temp();
                    result.add(Instruction.createMov(temp, immediate));
                    result.add(Instruction.createSub(target, temp, rhs));
                } else {
                    result.add(instruction);
                }
            }
            case MUL -> {
                final var normalizedLhs = ensureVariable(lhs, result);
                final var normalizedRhs = ensureVariable(rhs, result);
                result.add(Instruction.createMul(target, normalizedLhs, normalizedRhs));
            }
            default -> throw new RuntimeException("Unexpected instruction kind: " + kind);
        }

        return result;
    }

    private IRValue ensureVariable(IRValue value, List<Instruction> container) {
        if (value instanceof IRVariable) {
            return value;
        }

        final var temp = IRVariable.temp();
        container.add(Instruction.createMov(temp, value));
        return temp;
    }

    private void emitMov(int index, Instruction instruction, List<String> body) {
        final var result = instruction.getResult();
        final var from = instruction.getFrom();

        if (from instanceof IRImmediate immediate) {
            final var targetRegister = allocateResultRegister(result, index, Set.of(), body);
            body.add(format("li %s, %s".formatted(targetRegister, immediate.getValue()), instruction));
            return;
        }

        final var sourceVariable = (IRVariable) from;
        final var sourceRegister = ensureVariableRegister(sourceVariable, index, Set.of(), body);
        final var targetRegister = allocateResultRegister(result, index, Set.of(sourceRegister), body);
        if (!targetRegister.equals(sourceRegister)) {
            body.add(format("mv %s, %s".formatted(targetRegister, sourceRegister), instruction));
        }
    }

    private void emitAdd(int index, Instruction instruction, List<String> body) {
        final var result = instruction.getResult();
        final var lhsRegister = ensureVariableRegister((IRVariable) instruction.getLHS(), index, Set.of(), body);
        final var rhs = instruction.getRHS();

        if (rhs instanceof IRImmediate immediate && fitsImmediate(immediate.getValue())) {
            final var targetRegister = allocateResultRegister(result, index, Set.of(lhsRegister), body);
            body.add(format("addi %s, %s, %s".formatted(targetRegister, lhsRegister, immediate.getValue()), instruction));
            return;
        }

        final String rhsRegister;
        if (rhs instanceof IRImmediate immediate) {
            final var targetRegister = allocateResultRegister(result, index, Set.of(lhsRegister), body);
            rhsRegister = materializeImmediate(immediate, index, Set.of(lhsRegister, targetRegister), body);
            body.add(format("add %s, %s, %s".formatted(targetRegister, lhsRegister, rhsRegister), instruction));
        } else {
            rhsRegister = ensureVariableRegister((IRVariable) rhs, index, Set.of(lhsRegister), body);
            final var targetRegister = allocateResultRegister(result, index, Set.of(lhsRegister, rhsRegister), body);
            body.add(format("add %s, %s, %s".formatted(targetRegister, lhsRegister, rhsRegister), instruction));
        }
    }

    private void emitSub(int index, Instruction instruction, List<String> body) {
        final var result = instruction.getResult();
        final var lhsRegister = ensureVariableRegister((IRVariable) instruction.getLHS(), index, Set.of(), body);
        final var rhs = instruction.getRHS();

        if (rhs instanceof IRImmediate immediate && fitsImmediate(-immediate.getValue())) {
            final var targetRegister = allocateResultRegister(result, index, Set.of(lhsRegister), body);
            body.add(format("addi %s, %s, %s".formatted(targetRegister, lhsRegister, -immediate.getValue()), instruction));
            return;
        }

        final String rhsRegister;
        if (rhs instanceof IRImmediate immediate) {
            final var targetRegister = allocateResultRegister(result, index, Set.of(lhsRegister), body);
            rhsRegister = materializeImmediate(immediate, index, Set.of(lhsRegister, targetRegister), body);
            body.add(format("sub %s, %s, %s".formatted(targetRegister, lhsRegister, rhsRegister), instruction));
        } else {
            rhsRegister = ensureVariableRegister((IRVariable) rhs, index, Set.of(lhsRegister), body);
            final var targetRegister = allocateResultRegister(result, index, Set.of(lhsRegister, rhsRegister), body);
            body.add(format("sub %s, %s, %s".formatted(targetRegister, lhsRegister, rhsRegister), instruction));
        }
    }

    private void emitMul(int index, Instruction instruction, List<String> body) {
        final var result = instruction.getResult();
        final var lhsRegister = ensureVariableRegister((IRVariable) instruction.getLHS(), index, Set.of(), body);
        final var rhsRegister = ensureVariableRegister((IRVariable) instruction.getRHS(), index, Set.of(lhsRegister), body);
        final var targetRegister = allocateResultRegister(result, index, Set.of(lhsRegister, rhsRegister), body);
        body.add(format("mul %s, %s, %s".formatted(targetRegister, lhsRegister, rhsRegister), instruction));
    }

    private void emitRet(int index, Instruction instruction, List<String> body) {
        final var returnValue = instruction.getReturnValue();
        if (returnValue instanceof IRImmediate immediate) {
            body.add(format("li a0, %s".formatted(immediate.getValue()), instruction));
            return;
        }

        final var register = ensureVariableRegister((IRVariable) returnValue, index, Set.of(), body);
        body.add(format("mv a0, %s".formatted(register), instruction));
    }

    private String ensureVariableRegister(IRVariable variable, int index, Set<String> excludedRegisters, List<String> body) {
        final var existing = variableToRegister.get(variable);
        if (existing != null) {
            return existing;
        }

        final var register = acquireRegister(index, excludedRegisters, body);
        bind(variable, register);
        final var slot = spillSlots.get(variable);
        if (slot != null) {
            body.add("    lw %s, %s(sp)".formatted(register, slot * 4));
        }
        return register;
    }

    private String allocateResultRegister(IRVariable result, int index, Set<String> excludedRegisters, List<String> body) {
        final var existing = variableToRegister.get(result);
        if (existing != null) {
            return existing;
        }

        final var register = acquireRegister(index, excludedRegisters, body);
        bind(result, register);
        return register;
    }

    private String materializeImmediate(IRImmediate immediate, int index, Set<String> excludedRegisters, List<String> body) {
        final var temp = IRVariable.temp();
        final var register = allocateResultRegister(temp, index, excludedRegisters, body);
        body.add("    li %s, %s".formatted(register, immediate.getValue()));
        return register;
    }

    private String acquireRegister(int index, Set<String> excludedRegisters, List<String> body) {
        if (!freeRegisters.isEmpty()) {
            return freeRegisters.removeFirst();
        }

        final var deadRegister = findDeadRegister(index, excludedRegisters);
        if (deadRegister != null) {
            unbind(deadRegister);
            return deadRegister;
        }

        final var spillRegister = chooseSpillRegister(index, excludedRegisters);
        final var spilledVariable = registerToVariable.get(spillRegister);
        final var slot = spillSlots.computeIfAbsent(spilledVariable, ignored -> spillSlots.size());
        body.add("    sw %s, %s(sp)".formatted(spillRegister, slot * 4));
        unbind(spillRegister);
        return spillRegister;
    }

    private String findDeadRegister(int index, Set<String> excludedRegisters) {
        for (final var register : REGISTERS) {
            if (excludedRegisters.contains(register)) {
                continue;
            }

            final var variable = registerToVariable.get(register);
            if (variable != null && lastUsage.getOrDefault(variable, -1) <= index) {
                return register;
            }
        }
        return null;
    }

    private String chooseSpillRegister(int index, Set<String> excludedRegisters) {
        String candidate = null;
        int farthestUsage = Integer.MIN_VALUE;
        for (final var register : REGISTERS) {
            if (excludedRegisters.contains(register)) {
                continue;
            }

            final var variable = registerToVariable.get(register);
            if (variable == null) {
                continue;
            }

            final var usage = lastUsage.getOrDefault(variable, -1);
            if (usage > farthestUsage) {
                farthestUsage = usage;
                candidate = register;
            }
        }

        if (candidate == null) {
            throw new RuntimeException("No register can be allocated for current instruction at index " + index);
        }
        return candidate;
    }

    private void releaseDeadVariables(int index) {
        final var deadVariables = new ArrayList<IRVariable>();
        for (final var entry : variableToRegister.entrySet()) {
            if (lastUsage.getOrDefault(entry.getKey(), -1) <= index) {
                deadVariables.add(entry.getKey());
            }
        }

        for (final var variable : deadVariables) {
            final var register = variableToRegister.remove(variable);
            registerToVariable.remove(register);
            freeRegisters.addLast(register);
        }
    }

    private void bind(IRVariable variable, String register) {
        final var oldVariable = registerToVariable.get(register);
        if (oldVariable != null) {
            variableToRegister.remove(oldVariable);
        }

        final var oldRegister = variableToRegister.get(variable);
        if (oldRegister != null) {
            registerToVariable.remove(oldRegister);
            freeRegisters.remove(oldRegister);
        }

        variableToRegister.put(variable, register);
        registerToVariable.put(register, variable);
        freeRegisters.remove(register);
    }

    private void unbind(String register) {
        final var variable = registerToVariable.remove(register);
        if (variable != null) {
            variableToRegister.remove(variable);
        }
    }

    private boolean fitsImmediate(int value) {
        final int limit = 1 << (IMMEDIATE_BITS - 1);
        return value >= -limit && value < limit;
    }

    private String format(String asm, Instruction instruction) {
        return "    %s\t\t# %s".formatted(asm, instruction);
    }
}
