package com.example.c64.cpu;

public class Opcode {

    private final String mnemonic;

    private final int cycles;

    private final Instruction instruction;

    public Opcode(
            String mnemonic,
            int cycles,
            Instruction instruction) {

        this.mnemonic = mnemonic;
        this.cycles = cycles;
        this.instruction = instruction;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public int getCycles() {
        return cycles;
    }

    public Instruction getInstruction() {
        return instruction;
    }
}