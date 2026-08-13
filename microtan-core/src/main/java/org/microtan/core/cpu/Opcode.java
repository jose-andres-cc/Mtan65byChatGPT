package org.microtan.core.cpu;

public class Opcode {

    private final String mnemonic;

    private final AddressingMode mode;

    private final int cycles;

    private final Instruction instruction;

    public Opcode(
            String mnemonic,
            AddressingMode mode,
            int cycles,
            Instruction instruction) {

        this.mnemonic = mnemonic;
        this.mode = mode;
        this.cycles = cycles;
        this.instruction = instruction;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public AddressingMode getMode() {
        return mode;
    }

    public int getCycles() {
        return cycles;
    }

    public Instruction getInstruction() {
        return instruction;
    }
}