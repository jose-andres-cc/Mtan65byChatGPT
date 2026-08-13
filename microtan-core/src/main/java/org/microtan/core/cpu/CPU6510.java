
package org.microtan.core.cpu;

import org.microtan.core.bus.Bus;
import org.microtan.core.trace.TraceConfig;
import org.microtan.core.trace.TraceFormatter;

public class CPU6510 {
    public int A, X, Y, PC, SP = 0xFF;
    public boolean C, Z, I, D, B, V, N;
    // private final Memory memory;
    private final Bus bus;
    //private final KernalHooks hooks;
    private boolean irqPending = false;
    private boolean nmiPending = false;
    private final Opcode[] opcodeTable = new Opcode[256];
    private long cycles = 0;
    private long totalCycles = 0;

private final TraceConfig traceConfig;
private final TraceFormatter traceFormatter;

    // JAC traza
    private long irqCount;
    private boolean dumped = false;

    // public CPU6510(Memory memory, KernalHooks hooks) {
    public CPU6510(Bus bus, //KernalHooks hooks, 
               TraceConfig trace) {
        // this.memory = memory;
        this.bus = bus;
        //this.hooks = hooks;
        this.traceConfig = trace;

        this.traceFormatter =
        new TraceFormatter(traceConfig);


        buildOpcodeTable();
    }

    // private int read(int a){ return memory.read(a)&0xFF; }
    // private void write(int a,int v){ memory.write(a,(byte)(v&0xFF)); }

    private int read(int address) {

        return bus.read(address);
    }

    private void write(int address, int value) {

        bus.write(address, value);
    }

    private int readWord(int a) {
        int lo = read(a);
        int hi = read(a + 1);
        return (hi << 8) | lo;
    }

    // public void reset(int start){ PC=start; }
    // public void reset() {
    // PC = readWord(0xFFFC);
    // }

    public void reset() {

        A = 0;
        X = 0;
        Y = 0;
        SP = 0xFD;

        setStatusRegister(0x24);

        PC = readWord(0xFFFC);

        // JAC traza
        System.out.println(
                String.format(
                        "PC=%02X ",
                        PC));

        // cpuPortDataDirection = 0x2F;
        // cpuPortData = 0x37;
        cycles = 7;
    }

    private void setZN(int v) {
        Z = (v & 0xFF) == 0;
        N = (v & 0x80) != 0;
    }

    /****************************
     * Modos de direccionamiento *
     ****************************/

    private int imm_obsolete() {
        return read(PC++);
    }

    private int immediate() {
        return read(PC++);
    }

    private int zp_obsolete() {
        return read(PC++);
    }

    private int zeroPage() {
        return read(PC++);
    }

    private int zpx_obsolete() {
        return (read(PC++) + X) & 0xFF;
    }

    private int zeroPageX() {
        return (read(PC++) + X) & 0xFF;
    }

    private int zpy_obsolete() {
        return (read(PC++) + Y) & 0xFF;
    }

    private int zeroPageY() {
        return (read(PC++) + Y) & 0xFF;
    }

    private int abs_obsolete() {
        int a = readWord(PC);
        PC += 2;
        return a;
    }

    private int absolute() {
        int a = readWord(PC);
        PC += 2;
        return a;
    }

    private int absX_obsolete(boolean addPageCrossCycle) {

        int base = readWord(PC);

        PC += 2;

        int result = (base + X) & 0xFFFF;

        if (addPageCrossCycle &&
                ((base & 0xFF00) != (result & 0xFF00))) {

            // cycles++;
            boolean pageCrossed_obsolete = true;
        }

        // pageCrossed =
        // (base & 0xFF00) !=
        // (result & 0xFF00);

        return result;
    }

    private int absoluteX(boolean addPageCrossCycle) {
        int base = readWord(PC);

        PC += 2;

        int result = (base + X) & 0xFFFF;

        if (addPageCrossCycle &&
                ((base & 0xFF00) != (result & 0xFF00))) {

            cycles++;
        }

        return result;
    }

    private int absY_obsolete(boolean addPageCrossCycle) {
        // int a=readWord(PC); PC+=2; return (a+Y)&0xFFFF;

        int low = read(PC++);
        int high = read(PC++);

        int base = (high << 8) | low;
        int address = (base + Y) & 0xFFFF;

        if (addPageCrossCycle &&
                ((base & 0xFF00) != (address & 0xFF00))) {

            cycles++;
        }

        return address;

    }

    private int absoluteY(boolean addPageCrossCycle) {

        int low = read(PC++);
        int high = read(PC++);

        int base = (high << 8) | low;
        int address = (base + Y) & 0xFFFF;

        if (addPageCrossCycle &&
                ((base & 0xFF00) != (address & 0xFF00))) {

            cycles++;
        }

        return address;
    }

    private int indirect() {

        int ptr = readWord(PC);

        PC += 2;

        // BUG ORIGINAL DEL 6502
        int lo = read(ptr);

        int hiAddress = (ptr & 0xFF00) |
                ((ptr + 1) & 0x00FF);

        int hi = read(hiAddress);

        return (hi << 8) | lo;
    }

    private int indX_obsolete() {
        int zp = (read(PC++) + X) & 0xFF;
        int lo = read(zp);
        int hi = read((zp + 1) & 0xFF);
        return (hi << 8) | lo;
    }

    private int indirectX() {
        int zp = (read(PC++) + X) & 0xFF;
        int lo = read(zp);
        int hi = read((zp + 1) & 0xFF);
        return (hi << 8) | lo;
    }

    // JAC las llamadas a este metodo necesitan revision
    private int indY_obsolete() {
        int zp = read(PC++);
        int lo = read(zp);
        int hi = read((zp + 1) & 0xFF);
        return (((hi << 8) | lo) + Y) & 0xFFFF;
    }

    private int indirectY(boolean addPageCrossCycle) {

        int zp = read(PC++) & 0xFF;

        int low = read(zp);
        int high = read((zp + 1) & 0xFF);

        int base = (high << 8) | low;

        int address = (base + Y) & 0xFFFF;

        if (addPageCrossCycle &&
                ((base & 0xFF00) != (address & 0xFF00))) {

            cycles++;
        }

        return address;
    }

    private int relative() {

        int offset = read(PC++);

        if ((offset & 0x80) != 0) {
            offset -= 0x100;
        }

        return offset;
    }

    private int fetch(int addr) {
        return read(addr);
    }

    private void push(int v) {
        write(0x100 + SP--, v);
    }

    private int pop() {
        return read(0x100 + ++SP);
    }

    private int popWord() {
        int lo = pop();
        int hi = pop();
        return (hi << 8) | lo;
    }

    private void compare(int reg, int value) {
        int result = (reg - value) & 0x1FF;
        C = reg >= value;
        Z = (reg & 0xFF) == (value & 0xFF);
        N = (result & 0x80) != 0;
    }

    private void branch(boolean condition) {

        int offset = relative();

        tick(2);

        if (!condition) {
            return;
        }

        int oldPC = PC;

        if (offset > 127) {
            offset -= 256;
        }

        PC = (PC + offset) & 0xFFFF;

        tick(1);

        boolean crossed = (oldPC & 0xFF00) != (PC & 0xFF00);

        if (crossed) {
            tick(1);
        }
    }

    public int getStatusRegister() {

        int p = 0;

        if (C)
            p |= 0x01;
        if (Z)
            p |= 0x02;
        if (I)
            p |= 0x04;
        if (D)
            p |= 0x08;
        if (B)
            p |= 0x10;

        p |= 0x20; // bit 5 siempre a 1

        if (V)
            p |= 0x40;
        if (N)
            p |= 0x80;

        return p;
    }

    private void setStatusRegister(int p) {
        p |= 0x20;

        C = (p & 0x01) != 0;
        Z = (p & 0x02) != 0;
        I = (p & 0x04) != 0;
        D = (p & 0x08) != 0;
        B = (p & 0x10) != 0;
        V = (p & 0x40) != 0;
        N = (p & 0x80) != 0;
    }

    private void and(int value) {
        A &= value;
        A &= 0xFF;
        setZN(A);
    }

    private void ora(int value) {
        A |= value;
        A &= 0xFF;
        setZN(A);
    }

    private void eor(int value) {
        A ^= value;
        A &= 0xFF;
        setZN(A);
    }

    private void bit(int value) {

        int result = A & value;

        Z = (result & 0xFF) == 0;

        V = (value & 0x40) != 0;
        N = (value & 0x80) != 0;
    }

    private void inc(int addr) {

        int value = (fetch(addr) + 1) & 0xFF;

        write(addr, value);

        setZN(value);
    }

    private void dec(int addr) {

        int value = (fetch(addr) - 1) & 0xFF;

        write(addr, value);

        setZN(value);
    }

    // helpers propuestos para simplificar el codigo de los opcodes (REVISAR)
    // No aplicamos estos helpers porque habría que resolver el conflicto con los
    // anteriores y de momento no lo necesitamos
    // private int inc(int value) {

    // return (value + 1) & 0xFF;
    // }

    // private int dec(int value) {

    // return (value - 1) & 0xFF;
    // }

    private void adc(int value) {
        value &= 0xFF;
        int carryIn = C ? 1 : 0;
        int result = A + value + carryIn;
        // Carry
        C = result > 0xFF;
        int result8 = result & 0xFF;
        // Overflow signed
        V = ((~(A ^ value) & (A ^ result8)) & 0x80) != 0;
        A = result8;
        setZN(A);
    }

    private void sbc(int value) {
        value &= 0xFF;
        int carryIn = C ? 1 : 0;
        int inverted = value ^ 0xFF;
        int result = A + inverted + carryIn;
        C = result > 0xFF;
        int result8 = result & 0xFF;
        V = ((A ^ result8) & (inverted ^ result8) & 0x80) != 0;
        A = result8;
        setZN(A);
    }

    private int aslValue(int value) {
        value &= 0xFF;
        C = (value & 0x80) != 0;
        value = (value << 1) & 0xFF;
        setZN(value);
        return value;
    }

    private void aslMemory(int addr) {
        int value = fetch(addr);
        value = aslValue(value);
        write(addr, value);
    }

    private int lsrValue(int value) {
        value &= 0xFF;
        C = (value & 0x01) != 0;
        value >>= 1;
        value &= 0xFF;
        setZN(value);
        return value;
    }

    private void lsrMemory(int addr) {
        int value = fetch(addr);
        value = lsrValue(value);
        write(addr, value);
    }

    private int rolValue(int value) {
        value &= 0xFF;
        boolean oldCarry = C;
        C = (value & 0x80) != 0;
        value = (value << 1) & 0xFF;
        if (oldCarry)
            value |= 0x01;
        setZN(value);
        return value;
    }

    private void rolMemory(int addr) {
        int value = fetch(addr);
        value = rolValue(value);
        write(addr, value);
    }

    private int rorValue(int value) {
        value &= 0xFF;
        boolean oldCarry = C;
        C = (value & 0x01) != 0;
        value >>= 1;
        if (oldCarry)
            value |= 0x80;
        value &= 0xFF;
        setZN(value);
        return value;
    }

    private void rorMemory(int addr) {
        int value = fetch(addr);
        value = rorValue(value);
        write(addr, value);
    }

    private void lda(int value) {

        A = value & 0xFF;

        setZN(A);
    }

    private void ldx(int value) {

        X = value & 0xFF;

        setZN(X);
    }

    private void ldy(int value) {

        Y = value & 0xFF;

        setZN(Y);
    }

    // helpers y otros
    private void nopImmediate() {

        immediate();
    }

    private void nop() {
        // No hace nada
    }

    // Helpers
    private void lax(int value) {

        value &= 0xFF;

        A = value;
        X = value;

        setZN(value);
    }

    private void sax(int address) {

        write(address, A & X);
    }

    // helpers
    private void slo(int address) {

        int value = read(address);

        value = aslValue(value);

        write(address, value);

        ora(value);
    }

    private void rla(int address) {

        int value = read(address);

        value = rolValue(value);

        write(address, value);

        and(value);
    }

    private void sre(int address) {

        int value = read(address);

        value = lsrValue(value);

        write(address, value);

        eor(value);
    }

    private void rra(int address) {

        int value = read(address);

        value = rorValue(value);

        write(address, value);

        adc(value);
    }

    // helper
    private void dcp(int address) {

        int value = read(address);

        value = (value - 1) & 0xFF;

        write(address, value);

        // cmp(value);
        compare(A, value);
    }

    private void isc(int address) {

        int value = read(address);

        value = (value + 1) & 0xFF;

        write(address, value);

        sbc(value);
    }

    // helpers
    private void anc(int value) {

        A &= value;

        A &= 0xFF;

        setZN(A);

        C = (A & 0x80) != 0;
    }

    private void alr(int value) {

        A &= value;

        A &= 0xFF;

        A = lsrValue(A);
    }

    private void arr(int value) {

        A &= value;

        A &= 0xFF;

        A = rorValue(A);

        C = (A & 0x40) != 0;

        V = (((A >> 6) ^ (A >> 5)) & 1) != 0;
    }

    private void sbx(int value) {

        int result = (A & X) - value;

        C = result >= 0;

        X = result & 0xFF;

        setZN(X);
    }

    // public void step_execute_based() {
    // if (hooks.handle(this))
    // return;

    // int opcode = read(PC++);
    // execute(opcode);
    // }

    public void step() {
      
        if (nmiPending) {

            handleNMI();

            return;
        }

        if (irqPending && !I) {

            handleIRQ();

            return;
        }

        int instructionPC = PC;

        int opcode = read(PC++);

        Opcode op = opcodeTable[opcode];


        op.getInstruction().execute();

    if (traceConfig.isEnabled()) {

        System.out.println(
            traceFormatter.formatInstruction(
                this,
                op,
                instructionPC));
    }

        tick(op.getCycles());
    }

    // public void execute(int op) {
    // // mvn compile
    // // int op=read(PC++);
    //
    // switch (op) {
    // case 0xA9 -> {
    // A = immediate();
    // setZN(A);
    // }
    // case 0xA5 -> {
    // A = fetch(zeroPage());
    // setZN(A);
    // }
    // case 0xAD -> {
    // A = fetch(absolute());
    // setZN(A);
    // }
    //
    // case 0xA2 -> {
    // X = immediate();
    // setZN(X);
    // }
    // case 0xA6 -> {
    // X = fetch(zeroPage());
    // setZN(X);
    // }
    // case 0xB6 -> {
    // X = fetch(zeroPageY());
    // setZN(X);
    // }
    // case 0xAE -> {
    // X = fetch(absolute());
    // setZN(X);
    // }
    // case 0xBE -> {
    // X = fetch(absoluteY(false));
    // setZN(X);
    // }
    //
    // case 0xA0 -> {
    // Y = immediate();
    // setZN(Y);
    // }
    // case 0xA4 -> {
    // Y = fetch(zeroPage());
    // setZN(Y);
    // }
    // case 0xB4 -> {
    // Y = fetch(zeroPageX());
    // setZN(Y);
    // }
    // case 0xAC -> {
    // Y = fetch(absolute());
    // setZN(Y);
    // }
    // case 0xBC -> {
    // Y = fetch(absoluteX(false));
    // setZN(Y);
    // }
    //
    // case 0x8D -> write(absolute(), A);
    //
    // case 0xAA -> {
    // X = A;
    // setZN(X);
    // }
    // case 0xE8 -> {
    // X = (X + 1) & 0xFF;
    // setZN(X);
    // }
    //
    // case 0x20 -> {
    // int addr = absolute();
    // push((PC - 1) >> 8);
    // push((PC - 1) & 0xFF);
    // PC = addr;
    // }
    //
    // case 0x60 -> PC = popWord() + 1;
    //
    // case 0xC9 -> compare(A, immediate());
    // case 0xC5 -> compare(A, fetch(zeroPage()));
    // case 0xD5 -> compare(A, fetch(zeroPageX()));
    // case 0xCD -> compare(A, fetch(absolute()));
    // case 0xDD -> compare(A, fetch(absoluteX(true)));
    // case 0xD9 -> compare(A, fetch(absoluteY(false)));
    // case 0xC1 -> compare(A, fetch(indirectX()));
    // case 0xD1 -> compare(A, fetch(indY()));
    //
    // case 0xE0 -> compare(X, immediate());
    // case 0xE4 -> compare(X, fetch(zeroPage()));
    // case 0xEC -> compare(X, fetch(absolute()));
    //
    // case 0xC0 -> compare(Y, immediate());
    // case 0xC4 -> compare(Y, fetch(zeroPage()));
    // case 0xCC -> compare(Y, fetch(absolute()));
    //
    // case 0x10 -> branch(!N);
    // case 0x30 -> branch(N);
    // case 0x50 -> branch(!V);
    // case 0x70 -> branch(V);
    // case 0x90 -> branch(!C);
    // case 0xB0 -> branch(C);
    // case 0xD0 -> branch(!Z);
    // case 0xF0 -> branch(Z);
    //
    // case 0x4C -> PC = absolute();
    //
    // case 0x29 -> and(immediate());
    // case 0x25 -> and(fetch(zeroPage()));
    // case 0x35 -> and(fetch(zeroPageX()));
    // case 0x2D -> and(fetch(absolute()));
    // case 0x3D -> and(fetch(absoluteX(false)));
    // case 0x39 -> and(fetch(absoluteY(false)));
    // case 0x21 -> and(fetch(indirectX()));
    // case 0x31 -> and(fetch(indY()));
    //
    // case 0x49 -> eor(immediate());
    // case 0x45 -> eor(fetch(zeroPage()));
    // case 0x55 -> eor(fetch(zeroPageX()));
    // case 0x4D -> eor(fetch(absolute()));
    // case 0x5D -> eor(fetch(absoluteX(false)));
    // case 0x59 -> eor(fetch(absoluteY(false)));
    // case 0x41 -> eor(fetch(indirectX()));
    // case 0x51 -> eor(fetch(indY()));
    //
    // case 0x24 -> bit(fetch(zeroPage()));
    // case 0x2C -> bit(fetch(absolute()));
    //
    // case 0xC8 -> {
    // Y = (Y + 1) & 0xFF;
    // setZN(Y);
    // }
    //
    // case 0x88 -> {
    // Y = (Y - 1) & 0xFF;
    // setZN(Y);
    // }
    //
    // case 0xCA -> {
    // X = (X - 1) & 0xFF;
    // setZN(X);
    // }
    //
    // case 0xE6 -> inc(zeroPage());
    // case 0xF6 -> inc(zeroPageX());
    // case 0xEE -> inc(absolute());
    // case 0xFE -> inc(absoluteX(false));
    //
    // case 0xC6 -> dec(zeroPage());
    // case 0xD6 -> dec(zeroPageX());
    // case 0xCE -> dec(absolute());
    // case 0xDE -> dec(absoluteX(false));
    //
    // case 0x48 -> push(A);
    //
    // case 0x68 -> {
    // A = pop();
    // setZN(A);
    // }
    //
    // case 0x08 -> push(getStatusRegister() | 0x30);
    //
    // case 0x28 -> setStatusRegister(pop());
    //
    // case 0x69 -> adc(immediate());
    // case 0x65 -> adc(fetch(zeroPage()));
    // case 0x75 -> adc(fetch(zeroPageX()));
    // case 0x6D -> adc(fetch(absolute()));
    // case 0x7D -> adc(fetch(absoluteX(true)));
    // case 0x79 -> adc(fetch(absoluteY(false)));
    // case 0x61 -> adc(fetch(indirectX()));
    // case 0x71 -> adc(fetch(indY()));
    // case 0xE9 -> sbc(immediate());
    // case 0xE5 -> sbc(fetch(zeroPage()));
    // case 0xF5 -> sbc(fetch(zeroPageX()));
    // case 0xED -> sbc(fetch(absolute()));
    // case 0xFD -> sbc(fetch(absoluteX(true)));
    // case 0xF9 -> sbc(fetch(absoluteY(false)));
    // case 0xE1 -> sbc(fetch(indirectX()));
    // case 0xF1 -> sbc(fetch(indY()));
    //
    // case 0x18 -> C = false;
    // case 0x38 -> C = true;
    // case 0x58 -> I = false;
    // case 0x78 -> I = true;
    // case 0xB8 -> V = false;
    // case 0xD8 -> D = false;
    // case 0xF8 -> D = true;
    //
    // case 0x0A -> A = aslValue(A);
    //
    // case 0x06 -> aslMemory(zeroPage());
    // case 0x16 -> aslMemory(zeroPageX());
    // case 0x0E -> aslMemory(absolute());
    // case 0x1E -> aslMemory(absoluteX(false));
    // case 0x4A -> A = lsrValue(A);
    //
    // case 0x46 -> lsrMemory(zeroPage());
    // case 0x56 -> lsrMemory(zeroPageX());
    // case 0x4E -> lsrMemory(absolute());
    // case 0x5E -> lsrMemory(absoluteX(false));
    // case 0x2A -> A = rolValue(A);
    //
    // case 0x26 -> rolMemory(zeroPage());
    // case 0x36 -> rolMemory(zeroPageX());
    // case 0x2E -> rolMemory(absolute());
    // case 0x3E -> rolMemory(absoluteX(false));
    // case 0x6A -> A = rorValue(A);
    //
    // case 0x66 -> rorMemory(zeroPage());
    // case 0x76 -> rorMemory(zeroPageX());
    // case 0x6E -> rorMemory(absolute());
    // case 0x7E -> rorMemory(absoluteX(false));
    //
    // case 0xEA -> {
    // }
    //
    // default -> throw new RuntimeException(String.format("Opcode %02X no
    // implementado", op));
    // }
    // }

    private void buildOpcodeTable() {

        for (int i = 0; i < 256; i++) {

            opcodeTable[i] = new Opcode(
                    "???", AddressingMode.IMPLIED,
                    2,
                    () -> {
                        throw new RuntimeException(
                                String.format(
                                        "Opcode %02X no implementado",
                                        read((PC - 1) & 0xFFFF)));
                    });
        }

        registerLoadInstructions();

        registerBranchInstructions();

        registerCompareInstructions();

        registerLogicalInstructions();

        registerArithmeticInstructions();

        registerShiftInstructions();

        registerStackInstructions();

        registerJumpInstructions();

        registerTransferInstructions();

        registerFlagInstructions();

        // registerInterruptInstructions(); // BRK, RTI
        // registerJumpInstructions(); // JMP, JSR, RTS

        registerStoreInstructions();
        registerIncrementInstructions();
        registerMemoryIncDec();
        registerControlInstructions();
        registerSBCInstructions();

        registerIllegalInstructions();

    }

    private void registerLoadInstructions() {

        registerLDA();
        registerLDX();
        registerLDY();
    }

    private void registerLDA() {

        opcodeTable[0xA9] = new Opcode("LDA", AddressingMode.IMMEDIATE,
                2,
                () -> lda(immediate()));

        opcodeTable[0xA5] = new Opcode("LDA", AddressingMode.ZERO_PAGE,
                3,
                () -> lda(fetch(zeroPage())));

        opcodeTable[0xB5] = new Opcode("LDA", AddressingMode.ZERO_PAGE_X,
                4,
                () -> lda(fetch(zeroPageX())));

        opcodeTable[0xAD] = new Opcode("LDA", AddressingMode.ABSOLUTE,
                4,
                () -> lda(fetch(absolute())));

        opcodeTable[0xBD] = new Opcode("LDA", AddressingMode.ABSOLUTE_X,
                4,
                () -> lda(fetch(absoluteX(true))));

        opcodeTable[0xB9] = new Opcode("LDA", AddressingMode.ABSOLUTE_Y,
                4,
                () -> lda(fetch(absoluteY(true))));

        opcodeTable[0xA1] = new Opcode("LDA", AddressingMode.INDIRECT_X,
                6,
                () -> lda(fetch(indirectX())));

        opcodeTable[0xB1] = new Opcode("LDA", AddressingMode.INDIRECT_Y,
                5,
                () -> lda(fetch(indirectY(true))));

    }

    private void registerLDX() {

        opcodeTable[0xA2] = new Opcode("LDX", AddressingMode.IMMEDIATE,
                2,
                () -> ldx(immediate()));

        opcodeTable[0xA6] = new Opcode("LDX", AddressingMode.ZERO_PAGE,
                3,
                () -> ldx(fetch(zeroPage())));

        opcodeTable[0xB6] = new Opcode("LDX", AddressingMode.ZERO_PAGE_Y,
                4,
                () -> ldx(fetch(zeroPageY())));

        opcodeTable[0xAE] = new Opcode("LDX", AddressingMode.ABSOLUTE,
                4,
                () -> ldx(fetch(absolute())));

        opcodeTable[0xBE] = new Opcode("LDX", AddressingMode.ABSOLUTE_Y,
                4,
                () -> ldx(fetch(absoluteY(true))));

    }

    private void registerLDY() {

        opcodeTable[0xA0] = new Opcode("LDY", AddressingMode.IMMEDIATE,
                2,
                () -> ldy(immediate()));

        opcodeTable[0xA4] = new Opcode("LDY", AddressingMode.ZERO_PAGE,
                3,
                () -> ldy(fetch(zeroPage())));

        opcodeTable[0xB4] = new Opcode("LDY", AddressingMode.ZERO_PAGE_X,
                4,
                () -> ldy(fetch(zeroPageX())));

        opcodeTable[0xAC] = new Opcode("LDY", AddressingMode.ABSOLUTE,
                4,
                () -> ldy(fetch(absolute())));

        opcodeTable[0xBC] = new Opcode("LDY", AddressingMode.ABSOLUTE_X,
                4,
                () -> ldy(fetch(absoluteX(true))));

    }

    private void registerBranchInstructions() {

        opcodeTable[0xD0] = new Opcode(
                "BNE",
                AddressingMode.RELATIVE,
                2,
                () -> branch(!Z));

        opcodeTable[0xF0] = new Opcode(
                "BEQ",
                AddressingMode.RELATIVE,
                2,
                () -> branch(Z));

        opcodeTable[0x10] = new Opcode(
                "BPL",
                AddressingMode.RELATIVE,
                2,
                () -> branch(!N));

        opcodeTable[0x30] = new Opcode(
                "BMI",
                AddressingMode.RELATIVE,
                2,
                () -> branch(N));

        opcodeTable[0x90] = new Opcode(
                "BCC",
                AddressingMode.RELATIVE,
                2,
                () -> branch(!C));

        opcodeTable[0xB0] = new Opcode(
                "BCS",
                AddressingMode.RELATIVE,
                2,
                () -> branch(C));

        opcodeTable[0x50] = new Opcode(
                "BVC",
                AddressingMode.RELATIVE,
                2,
                () -> branch(!V));

        opcodeTable[0x70] = new Opcode(
                "BVS",
                AddressingMode.RELATIVE,
                2,
                () -> branch(V));
    }

    private void registerArithmeticInstructions() {

        opcodeTable[0x69] = new Opcode(
                "ADC",
                AddressingMode.IMMEDIATE,
                2,
                () -> adc(immediate()));

        opcodeTable[0x65] = new Opcode(
                "ADC",
                AddressingMode.ZERO_PAGE,
                3,
                () -> adc(fetch(zeroPage())));

        opcodeTable[0x6D] = new Opcode(
                "ADC",
                AddressingMode.ABSOLUTE,
                4,
                () -> adc(fetch(absolute())));

        // 79 - ADC Absolute,Y
        opcodeTable[0x79] = new Opcode(
                "ADC",
                AddressingMode.ABSOLUTE_Y,
                4,
                () -> adc(read(absoluteY(true))));

        // ======================================================
        // ADC
        // ======================================================

        // 75 - ADC Zero Page,X
        opcodeTable[0x75] = new Opcode(
                "ADC",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> adc(read(zeroPageX())));

        // 7D - ADC Absolute,X
        opcodeTable[0x7D] = new Opcode(
                "ADC",
                AddressingMode.ABSOLUTE_X,
                4,
                () -> adc(read(absoluteX(true))));

        // 61 - ADC (Indirect,X)
        opcodeTable[0x61] = new Opcode(
                "ADC",
                AddressingMode.INDIRECT_X,
                6,
                () -> adc(read(indirectX())));

        // 71 - ADC (Indirect),Y
        opcodeTable[0x71] = new Opcode(
                "ADC",
                AddressingMode.INDIRECT_Y,
                5,
                () -> adc(read(indirectY(true))));

    }

    private void registerSBCInstructions() {

        // SBC #imm
        opcodeTable[0xE9] = new Opcode("SBC", AddressingMode.IMMEDIATE,
                2,
                () -> sbc(immediate()));

        // SBC zp
        opcodeTable[0xE5] = new Opcode("SBC", AddressingMode.ZERO_PAGE,
                3,
                () -> sbc(fetch(zeroPage())));

        // SBC zp,X
        opcodeTable[0xF5] = new Opcode("SBC", AddressingMode.ZERO_PAGE_X,
                4,
                () -> sbc(fetch(zeroPageX())));

        // SBC abs
        opcodeTable[0xED] = new Opcode("SBC", AddressingMode.ABSOLUTE,
                4,
                () -> sbc(fetch(absolute())));

        // SBC abs,X
        opcodeTable[0xFD] = new Opcode("SBC", AddressingMode.ABSOLUTE_X,
                4,
                () -> sbc(fetch(absoluteX(true))));

        // SBC abs,Y
        opcodeTable[0xF9] = new Opcode("SBC", AddressingMode.ABSOLUTE_Y,
                4,
                () -> sbc(fetch(absoluteY(true))));

        // SBC (ind,X)
        opcodeTable[0xE1] = new Opcode("SBC", AddressingMode.INDIRECT_X,
                6,
                () -> sbc(fetch(indirectX())));

        // SBC (ind),Y
        opcodeTable[0xF1] = new Opcode("SBC", AddressingMode.INDIRECT_Y,
                5,
                () -> sbc(fetch(indirectY(true))));

    }

    private void registerStackInstructions() {

        opcodeTable[0x48] = new Opcode(
                "PHA",
                AddressingMode.IMPLIED,
                3,
                () -> push(A));

        opcodeTable[0x68] = new Opcode(
                "PLA",
                AddressingMode.IMPLIED,
                4,
                () -> {

                    A = pop();

                    setZN(A);
                });

        opcodeTable[0x08] = new Opcode(
                "PHP",
                AddressingMode.IMPLIED,
                3,
                () -> push(
                        getStatusRegister() | 0x30));

        opcodeTable[0x28] = new Opcode(
                "PLP",
                AddressingMode.IMPLIED,
                4,
                () -> setStatusRegister(pop()));
    }

    private void registerShiftInstructions() {

        opcodeTable[0x0A] = new Opcode(
                "ASL",
                AddressingMode.IMPLIED,
                2,
                () -> A = aslValue(A));

        opcodeTable[0x46] = new Opcode(
                "LSR zp",
                AddressingMode.ZERO_PAGE,
                5,
                () -> {
                    int addr = zeroPage();
                    int value = read(addr);

                    value = lsrValue(value);

                    write(addr, value);
                });

        opcodeTable[0x4A] = new Opcode(
                "LSR",
                AddressingMode.IMPLIED,
                2,
                () -> A = lsrValue(A));

        opcodeTable[0x2A] = new Opcode(
                "ROL",
                AddressingMode.IMPLIED,
                2,
                () -> A = rolValue(A));

        opcodeTable[0x6A] = new Opcode(
                "ROR",
                AddressingMode.IMPLIED,
                2,
                () -> A = rorValue(A));

        // ======================================================
        // ASL
        // ======================================================

        // 06 - ASL Zero Page
        opcodeTable[0x06] = new Opcode("ASL", AddressingMode.ZERO_PAGE, 5, () -> {
            int addr = zeroPage();
            write(addr, aslValue(read(addr)));
        });

        // 16 - ASL Zero Page,X
        opcodeTable[0x16] = new Opcode("ASL", AddressingMode.ZERO_PAGE_X, 6, () -> {
            int addr = zeroPageX();
            write(addr, aslValue(read(addr)));
        });

        // 0E - ASL Absolute
        opcodeTable[0x0E] = new Opcode("ASL", AddressingMode.ABSOLUTE, 6, () -> {
            int addr = absolute();
            write(addr, aslValue(read(addr)));
        });

        // 1E - ASL Absolute,X
        opcodeTable[0x1E] = new Opcode("ASL", AddressingMode.ABSOLUTE_X, 7, () -> {
            int addr = absoluteX(false);
            write(addr, aslValue(read(addr)));
        });

        // ======================================================
        // LSR
        // ======================================================

        // 46 - LSR Zero Page
        opcodeTable[0x46] = new Opcode("LSR", AddressingMode.ZERO_PAGE, 5, () -> {
            int addr = zeroPage();
            write(addr, lsrValue(read(addr)));
        });

        // 56 - LSR Zero Page,X
        opcodeTable[0x56] = new Opcode("LSR", AddressingMode.ZERO_PAGE_X, 6, () -> {
            int addr = zeroPageX();
            write(addr, lsrValue(read(addr)));
        });

        // 4E - LSR Absolute
        opcodeTable[0x4E] = new Opcode("LSR", AddressingMode.ABSOLUTE, 6, () -> {
            int addr = absolute();
            write(addr, lsrValue(read(addr)));
        });

        // 5E - LSR Absolute,X
        opcodeTable[0x5E] = new Opcode("LSR", AddressingMode.ABSOLUTE_X, 7, () -> {
            int addr = absoluteX(false);
            write(addr, lsrValue(read(addr)));
        });

        // ======================================================
        // ROL
        // ======================================================

        // 26 - ROL Zero Page
        opcodeTable[0x26] = new Opcode("ROL", AddressingMode.ZERO_PAGE, 5, () -> {
            int addr = zeroPage();
            write(addr, rolValue(read(addr)));
        });

        // 36 - ROL Zero Page,X
        opcodeTable[0x36] = new Opcode("ROL", AddressingMode.ZERO_PAGE_X, 6, () -> {
            int addr = zeroPageX();
            write(addr, rolValue(read(addr)));
        });

        // 2E - ROL Absolute
        opcodeTable[0x2E] = new Opcode("ROL", AddressingMode.ABSOLUTE, 6, () -> {
            int addr = absolute();
            write(addr, rolValue(read(addr)));
        });

        // 3E - ROL Absolute,X
        opcodeTable[0x3E] = new Opcode("ROL", AddressingMode.ABSOLUTE_X, 7, () -> {
            int addr = absoluteX(false);
            write(addr, rolValue(read(addr)));
        });

        // ======================================================
        // ROR
        // ======================================================

        // 66 - ROR Zero Page
        opcodeTable[0x66] = new Opcode("ROR", AddressingMode.ZERO_PAGE, 5, () -> {
            int addr = zeroPage();
            write(addr, rorValue(read(addr)));
        });

        // 76 - ROR Zero Page,X
        opcodeTable[0x76] = new Opcode("ROR", AddressingMode.ZERO_PAGE_X, 6, () -> {
            int addr = zeroPageX();
            write(addr, rorValue(read(addr)));
        });

        // 6E - ROR Absolute
        opcodeTable[0x6E] = new Opcode("ROR", AddressingMode.ABSOLUTE, 6, () -> {
            int addr = absolute();
            write(addr, rorValue(read(addr)));
        });

        // 7E - ROR Absolute,X
        opcodeTable[0x7E] = new Opcode("ROR", AddressingMode.ABSOLUTE_X, 7, () -> {
            int addr = absoluteX(false);
            write(addr, rorValue(read(addr)));
        });

    }

    private void registerJumpInstructions() {

        opcodeTable[0x00] = new Opcode("BRK", AddressingMode.IMPLIED, 7,
                () -> {

                    PC++;

                    pushWord(PC);

                    int status = getStatusRegister() | 0x10;

                    push(status);

                    I = true;

                    PC = readVector(0xFFFE);
                });

        opcodeTable[0x20] = new Opcode(
                "JSR",
                AddressingMode.ABSOLUTE,
                6,
                () -> {

                    int addr = absolute();

                    pushWord(PC - 1);

                    PC = addr;
                });

        opcodeTable[0x60] = new Opcode(
                "RTS", AddressingMode.IMPLIED,
                6,
                () -> PC = popWord() + 1);

        opcodeTable[0x40] = new Opcode(
                "RTI", AddressingMode.IMPLIED,
                6,
                () -> {

                    setStatusRegister(pop());

                    int lo = pop();
                    int hi = pop();

                    PC = (hi << 8) | lo;
                });

        opcodeTable[0x6C] = new Opcode("JMP", AddressingMode.INDIRECT,
                5,
                () -> PC = indirect());

        opcodeTable[0x4C] = new Opcode(
                "JMP",
                AddressingMode.ABSOLUTE,
                3,
                () -> PC = absolute());

    }

    private void registerCompareInstructions() {

        // ==========================
        // CMP
        // ==========================

        opcodeTable[0xC9] = new Opcode("CMP", AddressingMode.IMMEDIATE,
                2,
                () -> compare(A, immediate()));

        opcodeTable[0xC5] = new Opcode("CMP", AddressingMode.ZERO_PAGE,
                3,
                () -> compare(A, fetch(zeroPage())));

        opcodeTable[0xD5] = new Opcode("CMP", AddressingMode.ZERO_PAGE_X,
                4,
                () -> compare(A, fetch(zeroPageX())));

        opcodeTable[0xCD] = new Opcode("CMP", AddressingMode.ABSOLUTE,
                4,
                () -> compare(A, fetch(absolute())));

        opcodeTable[0xDD] = new Opcode("CMP", AddressingMode.ABSOLUTE_X,
                4,
                () -> compare(A, fetch(absoluteX(true))));

        opcodeTable[0xD9] = new Opcode("CMP", AddressingMode.ABSOLUTE_Y,
                4,
                () -> compare(A, fetch(absoluteY(true))));

        opcodeTable[0xC1] = new Opcode("CMP", AddressingMode.INDIRECT_X,
                6,
                () -> compare(A, fetch(indirectX())));

        opcodeTable[0xD1] = new Opcode("CMP", AddressingMode.INDIRECT_Y,
                5,
                () -> compare(A, fetch(indirectY(true))));

        // ==========================
        // CPX
        // ==========================

        opcodeTable[0xE0] = new Opcode("CPX", AddressingMode.IMMEDIATE,
                2,
                () -> compare(X, immediate()));

        opcodeTable[0xE4] = new Opcode("CPX", AddressingMode.ZERO_PAGE,
                3,
                () -> compare(X, fetch(zeroPage())));

        opcodeTable[0xEC] = new Opcode("CPX", AddressingMode.ABSOLUTE,
                4,
                () -> compare(X, fetch(absolute())));

        // ==========================
        // CPY
        // ==========================

        opcodeTable[0xC0] = new Opcode("CPY", AddressingMode.IMMEDIATE,
                2,
                () -> compare(Y, immediate()));

        opcodeTable[0xC4] = new Opcode("CPY", AddressingMode.ZERO_PAGE,
                3,
                () -> compare(Y, fetch(zeroPage())));

        opcodeTable[0xCC] = new Opcode("CPY", AddressingMode.ABSOLUTE,
                4,
                () -> compare(Y, fetch(absolute())));
    }

    private void registerLogicalInstructions() {

        // =====================================
        // AND
        // =====================================

        opcodeTable[0x29] = new Opcode("AND", AddressingMode.IMMEDIATE,
                2,
                () -> and(immediate()));

        opcodeTable[0x25] = new Opcode("AND", AddressingMode.ZERO_PAGE,
                3,
                () -> and(fetch(zeroPage())));

        opcodeTable[0x35] = new Opcode("AND", AddressingMode.ZERO_PAGE_X,
                4,
                () -> and(fetch(zeroPageX())));

        opcodeTable[0x2D] = new Opcode("AND", AddressingMode.ABSOLUTE,
                4,
                () -> and(fetch(absolute())));

        opcodeTable[0x3D] = new Opcode("AND", AddressingMode.ABSOLUTE_X,
                4,
                () -> and(fetch(absoluteX(true))));

        opcodeTable[0x39] = new Opcode("AND", AddressingMode.ABSOLUTE_Y,
                4,
                () -> and(fetch(absoluteY(true))));

        opcodeTable[0x21] = new Opcode("AND", AddressingMode.INDIRECT_X,
                6,
                () -> and(fetch(indirectX())));

        opcodeTable[0x31] = new Opcode("AND", AddressingMode.INDIRECT_Y,
                5,
                () -> and(fetch(indirectY(true))));

        // =====================================
        // ORA
        // =====================================

        opcodeTable[0x09] = new Opcode("ORA", AddressingMode.IMMEDIATE,
                2,
                () -> ora(immediate()));

        opcodeTable[0x05] = new Opcode("ORA", AddressingMode.ZERO_PAGE,
                3,
                () -> ora(fetch(zeroPage())));

        opcodeTable[0x15] = new Opcode("ORA", AddressingMode.ZERO_PAGE_X,
                4,
                () -> ora(fetch(zeroPageX())));

        opcodeTable[0x0D] = new Opcode("ORA", AddressingMode.ABSOLUTE,
                4,
                () -> ora(fetch(absolute())));

        opcodeTable[0x1D] = new Opcode("ORA", AddressingMode.ABSOLUTE_X,
                4,
                () -> ora(fetch(absoluteX(true))));

        opcodeTable[0x19] = new Opcode("ORA", AddressingMode.ABSOLUTE_Y,
                4,
                () -> ora(fetch(absoluteY(true))));

        opcodeTable[0x01] = new Opcode("ORA", AddressingMode.INDIRECT_X,
                6,
                () -> ora(fetch(indirectX())));

        opcodeTable[0x11] = new Opcode("ORA", AddressingMode.INDIRECT_Y,
                5,
                () -> ora(fetch(indirectY(true))));

        // =====================================
        // EOR
        // =====================================

        opcodeTable[0x49] = new Opcode("EOR", AddressingMode.IMMEDIATE,
                2,
                () -> eor(immediate()));

        opcodeTable[0x45] = new Opcode("EOR", AddressingMode.ZERO_PAGE,
                3,
                () -> eor(fetch(zeroPage())));

        opcodeTable[0x55] = new Opcode("EOR", AddressingMode.ZERO_PAGE_X,
                4,
                () -> eor(fetch(zeroPageX())));

        opcodeTable[0x4D] = new Opcode("EOR", AddressingMode.ABSOLUTE,
                4,
                () -> eor(fetch(absolute())));

        opcodeTable[0x5D] = new Opcode("EOR", AddressingMode.ABSOLUTE_X,
                4,
                () -> eor(fetch(absoluteX(true))));

        opcodeTable[0x59] = new Opcode("EOR", AddressingMode.ABSOLUTE_Y,
                4,
                () -> eor(fetch(absoluteY(true))));

        opcodeTable[0x41] = new Opcode("EOR", AddressingMode.INDIRECT_X,
                6,
                () -> eor(fetch(indirectX())));

        opcodeTable[0x51] = new Opcode("EOR", AddressingMode.INDIRECT_Y,
                5,
                () -> eor(fetch(indirectY(true))));

        // =====================================
        // BIT
        // =====================================

        opcodeTable[0x24] = new Opcode("BIT", AddressingMode.ZERO_PAGE,
                3,
                () -> bit(fetch(zeroPage())));

        opcodeTable[0x2C] = new Opcode("BIT", AddressingMode.ABSOLUTE,
                4,
                () -> bit(fetch(absolute())));
    }

    private void registerTransferInstructions() {

        // ==========================
        // TAX
        // ==========================

        opcodeTable[0xAA] = new Opcode("TAX", AddressingMode.IMPLIED, 2,
                () -> {

                    X = A & 0xFF;

                    setZN(X);
                });

        // ==========================
        // TXA
        // ==========================

        opcodeTable[0x8A] = new Opcode("TXA", AddressingMode.IMPLIED, 2,
                () -> {

                    A = X & 0xFF;

                    setZN(A);
                });

        // ==========================
        // TAY
        // ==========================

        opcodeTable[0xA8] = new Opcode("TAY", AddressingMode.IMPLIED, 2,
                () -> {

                    Y = A & 0xFF;

                    setZN(Y);
                });

        // ==========================
        // TYA
        // ==========================

        opcodeTable[0x98] = new Opcode("TYA", AddressingMode.IMPLIED, 2,
                () -> {

                    A = Y & 0xFF;

                    setZN(A);
                });

        // ==========================
        // TSX
        // ==========================

        opcodeTable[0xBA] = new Opcode("TSX", AddressingMode.IMPLIED, 2,
                () -> {

                    X = SP & 0xFF;

                    setZN(X);
                });

        // ==========================
        // TXS
        // ==========================

        opcodeTable[0x9A] = new Opcode("TXS", AddressingMode.IMPLIED, 2,
                () -> {

                    SP = X & 0xFF;

                    // TXS NO modifica flags
                });
    }

    private void registerFlagInstructions() {

        // ==========================
        // CLC
        // ==========================

        opcodeTable[0x18] = new Opcode("CLC", AddressingMode.IMPLIED, 2,
                () -> C = false);

        // ==========================
        // SEC
        // ==========================

        opcodeTable[0x38] = new Opcode("SEC", AddressingMode.IMPLIED, 2,
                () -> C = true);

        // ==========================
        // CLI
        // ==========================

        opcodeTable[0x58] = new Opcode("CLI", AddressingMode.IMPLIED, 2,
                () -> {
                    System.out.printf("CLI ejecutado PC=%04X%n", PC - 1);
                    I = false;
                });

        // ==========================
        // SEI
        // ==========================

        opcodeTable[0x78] = new Opcode("SEI", AddressingMode.IMPLIED, 2,
                () -> I = true);

        // ==========================
        // CLV
        // ==========================

        opcodeTable[0xB8] = new Opcode("CLV", AddressingMode.IMPLIED, 2,
                () -> V = false);

        // ==========================
        // CLD
        // ==========================

        opcodeTable[0xD8] = new Opcode("CLD", AddressingMode.IMPLIED, 2,
                () -> D = false);

        // ==========================
        // SED
        // ==========================

        opcodeTable[0xF8] = new Opcode("SED", AddressingMode.IMPLIED, 2,
                () -> D = true);
    }

    private void registerStoreXYInstructions() {

        // ==========================
        // STX
        // ==========================

        opcodeTable[0x86] = new Opcode("STX", AddressingMode.ZERO_PAGE,
                3,
                () -> write(zeroPage(), X));

        opcodeTable[0x96] = new Opcode("STX", AddressingMode.ZERO_PAGE_Y,
                4,
                () -> write(zeroPageY(), X));

        opcodeTable[0x8E] = new Opcode("STX", AddressingMode.ABSOLUTE,
                4,
                () -> write(absolute(), X));

        // ==========================
        // STY
        // ==========================

        opcodeTable[0x84] = new Opcode("STY", AddressingMode.ZERO_PAGE,
                3,
                () -> write(zeroPage(), Y));

        opcodeTable[0x94] = new Opcode("STY", AddressingMode.ZERO_PAGE_X,
                4,
                () -> write(zeroPageX(), Y));

        opcodeTable[0x8C] = new Opcode("STY", AddressingMode.ABSOLUTE,
                4,
                () -> write(absolute(), Y));
    }

    private void registerStoreInstructions() {

        // ==========================
        // STA
        // ==========================

        opcodeTable[0x85] = new Opcode(
                "STA",
                AddressingMode.ZERO_PAGE,
                3,
                () -> write(zeroPage(), A));

        opcodeTable[0x95] = new Opcode(
                "STA",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> write(zeroPageX(), A));

        opcodeTable[0x8D] = new Opcode(
                "STA",
                AddressingMode.ABSOLUTE,
                4,
                () -> write(absolute(), A));

        opcodeTable[0x9D] = new Opcode(
                "STA",
                AddressingMode.ABSOLUTE_X,
                5,
                () -> write(absoluteX(false), A));

        opcodeTable[0x99] = new Opcode(
                "STA",
                AddressingMode.ABSOLUTE_Y,
                5,
                () -> write(absoluteY(false), A));

        opcodeTable[0x81] = new Opcode(
                "STA",
                AddressingMode.INDIRECT_X,
                6,
                () -> write(indirectX(), A));

        opcodeTable[0x91] = new Opcode(
                "STA",
                AddressingMode.INDIRECT_Y,
                6,
                () -> write(indirectY(false), A));

        // STX/STY si ya los añadimos
        registerStoreXYInstructions();
    }

    private void registerIncrementInstructions() {

        // ==========================
        // INX
        // ==========================

        opcodeTable[0xE8] = new Opcode("INX", AddressingMode.IMPLIED, 2,
                () -> {

                    X = (X + 1) & 0xFF;

                    setZN(X);
                });

        // ==========================
        // INY
        // ==========================

        opcodeTable[0xC8] = new Opcode("INY", AddressingMode.IMPLIED, 2,
                () -> {

                    Y = (Y + 1) & 0xFF;

                    setZN(Y);
                });

        // ==========================
        // DEX
        // ==========================

        opcodeTable[0xCA] = new Opcode("DEX", AddressingMode.IMPLIED, 2,
                () -> {

                    X = (X - 1) & 0xFF;

                    setZN(X);
                });

        // ==========================
        // DEY
        // ==========================

        opcodeTable[0x88] = new Opcode("DEY", AddressingMode.IMPLIED, 2,
                () -> {

                    Y = (Y - 1) & 0xFF;

                    setZN(Y);
                });

        registerMemoryIncDec();
    }

    private void registerMemoryIncDec() {

        // ==========================
        // INC
        // ==========================

        opcodeTable[0xE6] = new Opcode("INC", AddressingMode.ZERO_PAGE,
                5,
                () -> inc(zeroPage()));

        opcodeTable[0xF6] = new Opcode("INC", AddressingMode.ZERO_PAGE_X,
                6,
                () -> inc(zeroPageX()));

        opcodeTable[0xEE] = new Opcode("INC", AddressingMode.ABSOLUTE,
                6,
                () -> inc(absolute()));

        opcodeTable[0xFE] = new Opcode("INC", AddressingMode.ABSOLUTE_X,
                7,
                () -> inc(absoluteX(false)));

        // ==========================
        // DEC
        // ==========================

        opcodeTable[0xC6] = new Opcode("DEC", AddressingMode.ZERO_PAGE,
                5,
                () -> dec(zeroPage()));

        opcodeTable[0xD6] = new Opcode("DEC", AddressingMode.ZERO_PAGE_X,
                6,
                () -> dec(zeroPageX()));

        opcodeTable[0xCE] = new Opcode("DEC", AddressingMode.ABSOLUTE,
                6,
                () -> dec(absolute()));

        opcodeTable[0xDE] = new Opcode("DEC", AddressingMode.ABSOLUTE_X,
                7,
                () -> dec(absoluteX(false)));
    }

    private void registerControlInstructions() {

        opcodeTable[0xEA] = new Opcode("NOP", AddressingMode.IMPLIED, 2,
                () -> nop());
    }

    private void registerIllegalInstructions() {

        registerIllegalNOPInstructions();

        registerLAXInstructions();
        registerSAXInstructions();

        registerSLOInstructions();
        registerRLAInstructions();
        registerSREInstructions();
        registerRRAInstructions();

        registerDCPInstructions();
        registerISCInstructions();

        registerANCInstructions();
        registerALRInstructions();
        registerARRInstructions();
        registerSBXInstructions();
    }

    private void registerIllegalNOPInstructions() {

        //
        // NOP de 1 byte
        //

        opcodeTable[0x1A] = new Opcode("NOP", AddressingMode.IMPLIED, 2, () -> nop());

        opcodeTable[0x3A] = new Opcode("NOP", AddressingMode.IMPLIED, 2, () -> nop());

        opcodeTable[0x5A] = new Opcode("NOP", AddressingMode.IMPLIED, 2, () -> nop());

        opcodeTable[0x7A] = new Opcode("NOP", AddressingMode.IMPLIED, 2, () -> nop());

        opcodeTable[0xDA] = new Opcode("NOP", AddressingMode.IMPLIED, 2, () -> nop());

        opcodeTable[0xFA] = new Opcode("NOP", AddressingMode.IMPLIED, 2, () -> nop());

        //
        // Immediate
        //

        opcodeTable[0x80] = new Opcode(
                "NOP",
                AddressingMode.IMMEDIATE,
                2,
                () -> immediate());

        opcodeTable[0x82] = new Opcode(
                "NOP",
                AddressingMode.IMMEDIATE,
                2,
                () -> immediate());

        opcodeTable[0x89] = new Opcode(
                "NOP",
                AddressingMode.IMMEDIATE,
                2,
                () -> immediate());

        opcodeTable[0xC2] = new Opcode(
                "NOP",
                AddressingMode.IMMEDIATE,
                2,
                () -> immediate());

        opcodeTable[0xE2] = new Opcode(
                "NOP",
                AddressingMode.IMMEDIATE,
                2,
                () -> immediate());

        //
        // Zero Page
        //

        opcodeTable[0x04] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE,
                3,
                () -> read(zeroPage()));

        opcodeTable[0x44] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE,
                3,
                () -> read(zeroPage()));

        opcodeTable[0x64] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE,
                3,
                () -> read(zeroPage()));

        //
        // Zero Page,X
        //

        opcodeTable[0x14] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> read(zeroPageX()));

        opcodeTable[0x34] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> read(zeroPageX()));

        opcodeTable[0x54] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> read(zeroPageX()));

        opcodeTable[0x74] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> read(zeroPageX()));

        opcodeTable[0xD4] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> read(zeroPageX()));

        opcodeTable[0xF4] = new Opcode(
                "NOP",
                AddressingMode.ZERO_PAGE_X,
                4,
                () -> read(zeroPageX()));

        //
        // Absolute
        //

        opcodeTable[0x0C] = new Opcode(
                "NOP",
                AddressingMode.ABSOLUTE,
                4,
                () -> read(absolute()));

        //
        // Absolute,X
        // (añaden un ciclo por page crossing)
        //

        opcodeTable[0x1C] = new Opcode(
                "NOP",
                AddressingMode.ABSOLUTE_X,
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0x3C] = new Opcode(
                "NOP",
                AddressingMode.ABSOLUTE_X,
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0x5C] = new Opcode(
                "NOP",
                AddressingMode.ABSOLUTE_X,
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0x7C] = new Opcode(
                "NOP",
                AddressingMode.ABSOLUTE_X,
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0xDC] = new Opcode(
                "NOP",
                AddressingMode.ABSOLUTE_X,
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0xFC] = new Opcode(
                "NOP",
                AddressingMode.ABSOLUTE_X,
                4,
                () -> read(absoluteX(true)));
    }

    private void registerLAXInstructions() {

        // A7 - Zero Page
        opcodeTable[0xA7] = new Opcode(
                "LAX",
                AddressingMode.ZERO_PAGE,
                3,
                () -> lax(read(zeroPage())));

        // B7 - Zero Page,Y
        opcodeTable[0xB7] = new Opcode(
                "LAX",
                AddressingMode.ZERO_PAGE_Y,
                4,
                () -> lax(read(zeroPageY())));

        // AF - Absolute
        opcodeTable[0xAF] = new Opcode(
                "LAX",
                AddressingMode.ABSOLUTE,
                4,
                () -> lax(read(absolute())));

        // BF - Absolute,Y
        opcodeTable[0xBF] = new Opcode(
                "LAX",
                AddressingMode.ABSOLUTE_Y,
                4,
                () -> lax(read(absoluteY(true))));

        // A3 - (Indirect,X)
        opcodeTable[0xA3] = new Opcode(
                "LAX",
                AddressingMode.INDIRECT_X,
                6,
                () -> lax(read(indirectX())));

        // B3 - (Indirect),Y
        opcodeTable[0xB3] = new Opcode(
                "LAX",
                AddressingMode.INDIRECT_Y,
                5,
                () -> lax(read(indirectY(true))));
    }

    private void registerSAXInstructions() {

        // 87 - Zero Page
        opcodeTable[0x87] = new Opcode(
                "SAX",
                AddressingMode.ZERO_PAGE,
                3,
                () -> sax(zeroPage()));

        // 97 - Zero Page,Y
        opcodeTable[0x97] = new Opcode(
                "SAX",
                AddressingMode.ZERO_PAGE_Y,
                4,
                () -> sax(zeroPageY()));

        // 8F - Absolute
        opcodeTable[0x8F] = new Opcode(
                "SAX",
                AddressingMode.ABSOLUTE,
                4,
                () -> sax(absolute()));

        // 83 - (Indirect,X)
        opcodeTable[0x83] = new Opcode(
                "SAX",
                AddressingMode.INDIRECT_X,
                6,
                () -> sax(indirectX()));
    }

    private void registerSLOInstructions() {

        opcodeTable[0x07] = new Opcode("SLO", AddressingMode.ZERO_PAGE,
                5,
                () -> slo(zeroPage()));

        opcodeTable[0x17] = new Opcode("SLO", AddressingMode.ZERO_PAGE_X,
                6,
                () -> slo(zeroPageX()));

        opcodeTable[0x0F] = new Opcode("SLO", AddressingMode.ABSOLUTE,
                6,
                () -> slo(absolute()));

        opcodeTable[0x1F] = new Opcode("SLO", AddressingMode.ABSOLUTE_X,
                7,
                () -> slo(absoluteX(false)));

        opcodeTable[0x1B] = new Opcode("SLO", AddressingMode.ABSOLUTE_Y,
                7,
                () -> slo(absoluteY(false)));

        opcodeTable[0x03] = new Opcode("SLO", AddressingMode.INDIRECT_X,
                8,
                () -> slo(indirectX()));

        opcodeTable[0x13] = new Opcode("SLO", AddressingMode.INDIRECT_Y,
                8,
                () -> slo(indirectY(false)));
    }

    private void registerRLAInstructions() {

        opcodeTable[0x27] = new Opcode("RLA", AddressingMode.ZERO_PAGE,
                5,
                () -> rla(zeroPage()));

        opcodeTable[0x37] = new Opcode("RLA", AddressingMode.ZERO_PAGE_X,
                6,
                () -> rla(zeroPageX()));

        opcodeTable[0x2F] = new Opcode("RLA", AddressingMode.ABSOLUTE,
                6,
                () -> rla(absolute()));

        opcodeTable[0x3F] = new Opcode("RLA", AddressingMode.ABSOLUTE_X,
                7,
                () -> rla(absoluteX(false)));

        opcodeTable[0x3B] = new Opcode("RLA", AddressingMode.ABSOLUTE_Y,
                7,
                () -> rla(absoluteY(false)));

        opcodeTable[0x23] = new Opcode("RLA", AddressingMode.INDIRECT_X,
                8,
                () -> rla(indirectX()));

        opcodeTable[0x33] = new Opcode("RLA", AddressingMode.INDIRECT_Y,
                8,
                () -> rla(indirectY(false)));
    }

    private void registerSREInstructions() {

        opcodeTable[0x47] = new Opcode("SRE", AddressingMode.ZERO_PAGE,
                5,
                () -> sre(zeroPage()));

        opcodeTable[0x57] = new Opcode("SRE", AddressingMode.ZERO_PAGE_X,
                6,
                () -> sre(zeroPageX()));

        opcodeTable[0x4F] = new Opcode("SRE", AddressingMode.ABSOLUTE,
                6,
                () -> sre(absolute()));

        opcodeTable[0x5F] = new Opcode("SRE", AddressingMode.ABSOLUTE_X,
                7,
                () -> sre(absoluteX(false)));

        opcodeTable[0x5B] = new Opcode("SRE", AddressingMode.ABSOLUTE_Y,
                7,
                () -> sre(absoluteY(false)));

        opcodeTable[0x43] = new Opcode("SRE", AddressingMode.INDIRECT_X,
                8,
                () -> sre(indirectX()));

        opcodeTable[0x53] = new Opcode("SRE", AddressingMode.INDIRECT_Y,
                8,
                () -> sre(indirectY(false)));
    }

    private void registerRRAInstructions() {

        opcodeTable[0x67] = new Opcode("RRA", AddressingMode.ZERO_PAGE,
                5,
                () -> rra(zeroPage()));

        opcodeTable[0x77] = new Opcode("RRA", AddressingMode.ZERO_PAGE_X,
                6,
                () -> rra(zeroPageX()));

        opcodeTable[0x6F] = new Opcode("RRA", AddressingMode.ABSOLUTE,
                6,
                () -> rra(absolute()));

        opcodeTable[0x7F] = new Opcode("RRA", AddressingMode.ABSOLUTE_X,
                7,
                () -> rra(absoluteX(false)));

        opcodeTable[0x7B] = new Opcode("RRA", AddressingMode.ABSOLUTE_Y,
                7,
                () -> rra(absoluteY(false)));

        opcodeTable[0x63] = new Opcode("RRA", AddressingMode.INDIRECT_X,
                8,
                () -> rra(indirectX()));

        opcodeTable[0x73] = new Opcode("RRA", AddressingMode.INDIRECT_Y,
                8,
                () -> rra(indirectY(false)));
    }

    private void registerDCPInstructions() {

        opcodeTable[0xC7] = new Opcode("DCP", AddressingMode.ZERO_PAGE,
                5,
                () -> dcp(zeroPage()));

        opcodeTable[0xD7] = new Opcode("DCP", AddressingMode.ZERO_PAGE_X,
                6,
                () -> dcp(zeroPageX()));

        opcodeTable[0xCF] = new Opcode("DCP", AddressingMode.ABSOLUTE,
                6,
                () -> dcp(absolute()));

        opcodeTable[0xDF] = new Opcode("DCP", AddressingMode.ABSOLUTE_X,
                7,
                () -> dcp(absoluteX(false)));

        opcodeTable[0xDB] = new Opcode("DCP", AddressingMode.ABSOLUTE_Y,
                7,
                () -> dcp(absoluteY(false)));

        opcodeTable[0xC3] = new Opcode("DCP", AddressingMode.INDIRECT_X,
                8,
                () -> dcp(indirectX()));

        opcodeTable[0xD3] = new Opcode("DCP", AddressingMode.INDIRECT_Y,
                8,
                () -> dcp(indirectY(false)));
    }

    private void registerISCInstructions() {

        opcodeTable[0xE7] = new Opcode("ISC", AddressingMode.ZERO_PAGE,
                5,
                () -> isc(zeroPage()));

        opcodeTable[0xF7] = new Opcode("ISC", AddressingMode.ZERO_PAGE_X,
                6,
                () -> isc(zeroPageX()));

        opcodeTable[0xEF] = new Opcode("ISC", AddressingMode.ABSOLUTE,
                6,
                () -> isc(absolute()));

        opcodeTable[0xFF] = new Opcode("ISC", AddressingMode.ABSOLUTE_X,
                7,
                () -> isc(absoluteX(false)));

        opcodeTable[0xFB] = new Opcode("ISC", AddressingMode.ABSOLUTE_Y,
                7,
                () -> isc(absoluteY(false)));

        opcodeTable[0xE3] = new Opcode("ISC", AddressingMode.INDIRECT_X,
                8,
                () -> isc(indirectX()));

        opcodeTable[0xF3] = new Opcode("ISC", AddressingMode.INDIRECT_Y,
                8,
                () -> isc(indirectY(false)));
    }

    private void registerANCInstructions() {

        opcodeTable[0x0B] = new Opcode(
                "ANC",
                AddressingMode.IMMEDIATE,
                2,
                () -> anc(immediate()));

        opcodeTable[0x2B] = new Opcode(
                "ANC",
                AddressingMode.IMMEDIATE,
                2,
                () -> anc(immediate()));
    }

    private void registerALRInstructions() {

        opcodeTable[0x4B] = new Opcode(
                "ALR",
                AddressingMode.IMMEDIATE,
                2,
                () -> alr(immediate()));
    }

    private void registerARRInstructions() {

        opcodeTable[0x6B] = new Opcode(
                "ARR",
                AddressingMode.IMMEDIATE,
                2,
                () -> arr(immediate()));
    }

    private void registerSBXInstructions() {

        opcodeTable[0xCB] = new Opcode(
                "SBX",
                AddressingMode.IMMEDIATE,
                2,
                () -> sbx(immediate()));
    }

    public void requestIRQ() {
        irqPending = true;
    }

    public void requestNMI() {
        nmiPending = true;
    }

    private void pushWord(int value) {

        push((value >> 8) & 0xFF);
        push(value & 0xFF);
    }

    private int readVector(int address) {

        int lo = read(address);
        int hi = read(address + 1);

        return (hi << 8) | lo;
    }

    public void irq() {

        irqPending = true;
    }

    private void handleIRQ() {

        // JAC traza
        irqCount++;

        if ((irqCount % 100) == 0) {
            System.out.println("IRQ: " + irqCount);
        }

        pushWord(PC);

        int status = getStatusRegister();

        status &= ~0x10; // B = 0

        push(status);

        I = true;

        PC = readVector(0xFFFE);

        irqPending = false;

        cycles = 7;
    }

    public void nmi() {

        nmiPending = true;
    }

    private void handleNMI() {

        pushWord(PC);

        int status = getStatusRegister();

        status &= ~0x10;

        push(status);

        I = true;

        PC = readVector(0xFFFA);

        nmiPending = false;
    }

    private void tick(int extraCycles) {

        cycles += extraCycles;
    }

    public void clock() {

        if (cycles == 0) {

            if (nmiPending) {

                // serviceNMI();
                handleNMI();

            } else if (irqPending && !I) {

                // serviceIRQ();
                handleIRQ();
            }

                int instructionPC = PC;

            int opcode = read(PC++) & 0xFF;

            Opcode instruction = opcodeTable[opcode];

            if (instruction == null) {

                throw new IllegalStateException(
                        String.format(
                                "Opcode no implementado: %02X",
                                opcode));
            }



            cycles = instruction.getCycles();

            // instruction.execute();
            instruction.getInstruction().execute();

    if (traceConfig.isEnabled()) {

        System.out.println(
            traceFormatter.formatInstruction(
                this,
                instruction,
                instructionPC));
    }

        }

        cycles--;

        totalCycles++;

        // JAC trazas

        // if ((totalCycles % 500000) == 0) {

        // System.out.printf(
        // "PC=%04X A=%02X X=%02X Y=%02X SP=%02X%n I=%s%n",
        // PC, A, X, Y, SP, I);
        // // System.out.printf(
        // // "%02X %02X %02X %02X %02X %02X %02X %02X%n",
        // // bus.read(0x0400),
        // // bus.read(0x0401),
        // // bus.read(0x0402),
        // // bus.read(0x0403),
        // // bus.read(0x0404),
        // // bus.read(0x0405),
        // // bus.read(0x0406),
        // // bus.read(0x0407));
        // }

        // if (PC >= 0xFC90 && PC <= 0xFCC5) {
        // System.out.printf(
        // "%04X OP=%02X A=%02X X=%02X Y=%02X P=%02X%n",
        // PC,
        // read(PC),
        // A,
        // X,
        // Y,
        // getStatusRegister());
        // }

        if (PC >= 0xFC90 && PC <= 0xFCC5 && !dumped) {
            dumped = true;
            for (int a = 0xFCB8; a <= 0xFCC5; a++) {
                System.out.printf("%04X: %02X%n", a, bus.read(a));
            }
        }

    }

    public long getCycles() {
        return cycles;
    }

    public long getTotalCycles() {
        return totalCycles;
    }

    // public int getRemainingCycles() {
    public long getRemainingCycles() {
        return cycles;
    }

    // Getters para trazas y depuración
public int getA() {
    return A & 0xFF;
}

public int getX() {
    return X & 0xFF;
}

public int getY() {
    return Y & 0xFF;
}

public int getSP() {
    return SP & 0xFF;
}

public int getPC() {
    return PC & 0xFFFF;
}

public Bus getBus() {
    return bus;
}

public boolean isIrqPending() {
    return irqPending;
}

public boolean isNmiPending() {
    return nmiPending;
}

public boolean isInterruptDisableFlagSet() {
    return I;
}

}
