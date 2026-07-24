
package org.microtan.core.cpu;

import org.microtan.core.bus.Bus;


// package com.example.c64.cpu;

// import com.example.c64.bus.Bus;
// import com.example.c64.io.KernalHooks;
// import com.example.c64.memory.Memory;

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
    private boolean pageCrossed;

    // JAC traza
    private long irqCount;
    private boolean dumped = false;

    // public CPU6510(Memory memory, KernalHooks hooks) {
    public CPU6510(Bus bus) {
        // this.memory = memory;
        this.bus = bus;
        //this.hooks = hooks;

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

    private int imm() {
        return read(PC++);
    }

    private int immediate() {
        return imm();
    }

    // Modos de direccionamiento
    private int zp() {
        return read(PC++);
    }

    private int zeroPage() {
        return zp();
    }

    private int zpx() {
        return (read(PC++) + X) & 0xFF;
    }

    private int zeroPageX() {
        return zpx();
    }

    private int zpy() {
        return (read(PC++) + Y) & 0xFF;
    }

    private int zeroPageY() {
        return zpy();
    }

    private int abs() {
        int a = readWord(PC);
        PC += 2;
        return a;
    }

    private int absolute() {
        return abs();
    }

    // JAC revisar sus llamadas, porque en algunos casos no se añade el ciclo extra
    // cuando hay cruce de página
    private int absX(boolean addPageCrossCycle) {

        int base = readWord(PC);

        PC += 2;

        int result = (base + X) & 0xFFFF;

        if (addPageCrossCycle &&
                ((base & 0xFF00) != (result & 0xFF00))) {

            // cycles++;
            pageCrossed = true;
        }

        // pageCrossed =
        // (base & 0xFF00) !=
        // (result & 0xFF00);

        return result;
    }

    // JAC las llamadas a este metodo SON CORRECTAS
    private int absoluteX(boolean addPageCrossCycle) {
        return absX(addPageCrossCycle);
    }

    private int absY(boolean addPageCrossCycle) {
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

    // JAC las llamadas a este metodo SON CORRECTAS
    private int absoluteY(boolean addPageCrossCycle) {

        return absY(addPageCrossCycle);
    }

    private int indX() {
        int zp = (read(PC++) + X) & 0xFF;
        int lo = read(zp);
        int hi = read((zp + 1) & 0xFF);
        return (hi << 8) | lo;
    }

    private int indirectX() {
        return indX();
    }

    // JAC las llamadas a este metodo necesitan revision
    private int indY() {
        int zp = read(PC++);
        int lo = read(zp);
        int hi = read((zp + 1) & 0xFF);
        return (((hi << 8) | lo) + Y) & 0xFFFF;
    }

    /**
     * Modo de direccionamiento (Indirect),Y
     *
     * Lee un puntero de 16 bits desde la página cero y después suma Y.
     *
     * Ejemplo:
     * Operando = $20
     * [$20] = $F0
     * [$21] = $12
     * Y = $30
     *
     * Dirección base = $12F0
     * Dirección final = $1320
     *
     * @param addPageCrossCycle true si la instrucción debe añadir un ciclo
     *                          cuando se cruza una página (LDA, ADC, CMP...).
     *                          false para instrucciones RMW ilegales (RRA, ISC...).
     *
     * @return Dirección efectiva de 16 bits.
     */
    private int indirectY(boolean addPageCrossCycle) {

        // Byte del operando (dirección en ZP)
        int zp = read(PC++) & 0xFF;

        // Leer puntero de 16 bits desde la página cero
        int low = read(zp);
        int high = read((zp + 1) & 0xFF); // wrap-around en ZP

        int base = (high << 8) | low;

        // Aplicar índice Y
        int address = (base + Y) & 0xFFFF;

        // Penalización por cruce de página
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

    private int getStatusRegister() {

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

    public void step_execute_based() {
        //if (hooks.handle(this))
//            return;

        int opcode = read(PC++);
        execute(opcode);
    }

    public void step() {

        if (nmiPending) {

            handleNMI();

            return;
        }

        if (irqPending && !I) {

            handleIRQ();

            return;
        }

        int opcode = read(PC++);

        Opcode op = opcodeTable[opcode];

        op.getInstruction().execute();

        tick(op.getCycles());
    }

    public void execute(int op) {
        // mvn compile
        // int op=read(PC++);

        switch (op) {
            case 0xA9 -> {
                A = imm();
                setZN(A);
            }
            case 0xA5 -> {
                A = fetch(zp());
                setZN(A);
            }
            case 0xAD -> {
                A = fetch(abs());
                setZN(A);
            }

            case 0xA2 -> {
                X = imm();
                setZN(X);
            }
            case 0xA6 -> {
                X = fetch(zp());
                setZN(X);
            }
            case 0xB6 -> {
                X = fetch(zpy());
                setZN(X);
            }
            case 0xAE -> {
                X = fetch(abs());
                setZN(X);
            }
            case 0xBE -> {
                X = fetch(absY(false));
                setZN(X);
            }

            case 0xA0 -> {
                Y = imm();
                setZN(Y);
            }
            case 0xA4 -> {
                Y = fetch(zp());
                setZN(Y);
            }
            case 0xB4 -> {
                Y = fetch(zpx());
                setZN(Y);
            }
            case 0xAC -> {
                Y = fetch(abs());
                setZN(Y);
            }
            case 0xBC -> {
                Y = fetch(absX(false));
                setZN(Y);
            }

            case 0x8D -> write(abs(), A);

            case 0xAA -> {
                X = A;
                setZN(X);
            }
            case 0xE8 -> {
                X = (X + 1) & 0xFF;
                setZN(X);
            }

            case 0x20 -> {
                int addr = abs();
                push((PC - 1) >> 8);
                push((PC - 1) & 0xFF);
                PC = addr;
            }

            case 0x60 -> PC = popWord() + 1;

            case 0xC9 -> compare(A, imm());
            case 0xC5 -> compare(A, fetch(zp()));
            case 0xD5 -> compare(A, fetch(zpx()));
            case 0xCD -> compare(A, fetch(abs()));
            case 0xDD -> compare(A, fetch(absX(true)));
            case 0xD9 -> compare(A, fetch(absY(false)));
            case 0xC1 -> compare(A, fetch(indX()));
            case 0xD1 -> compare(A, fetch(indY()));

            case 0xE0 -> compare(X, imm());
            case 0xE4 -> compare(X, fetch(zp()));
            case 0xEC -> compare(X, fetch(abs()));

            case 0xC0 -> compare(Y, imm());
            case 0xC4 -> compare(Y, fetch(zp()));
            case 0xCC -> compare(Y, fetch(abs()));

            case 0x10 -> branch(!N);
            case 0x30 -> branch(N);
            case 0x50 -> branch(!V);
            case 0x70 -> branch(V);
            case 0x90 -> branch(!C);
            case 0xB0 -> branch(C);
            case 0xD0 -> branch(!Z);
            case 0xF0 -> branch(Z);

            case 0x4C -> PC = abs();

            case 0x29 -> and(imm());
            case 0x25 -> and(fetch(zp()));
            case 0x35 -> and(fetch(zpx()));
            case 0x2D -> and(fetch(abs()));
            case 0x3D -> and(fetch(absX(false)));
            case 0x39 -> and(fetch(absY(false)));
            case 0x21 -> and(fetch(indX()));
            case 0x31 -> and(fetch(indY()));

            case 0x49 -> eor(imm());
            case 0x45 -> eor(fetch(zp()));
            case 0x55 -> eor(fetch(zpx()));
            case 0x4D -> eor(fetch(abs()));
            case 0x5D -> eor(fetch(absX(false)));
            case 0x59 -> eor(fetch(absY(false)));
            case 0x41 -> eor(fetch(indX()));
            case 0x51 -> eor(fetch(indY()));

            case 0x24 -> bit(fetch(zp()));
            case 0x2C -> bit(fetch(abs()));

            case 0xC8 -> {
                Y = (Y + 1) & 0xFF;
                setZN(Y);
            }

            case 0x88 -> {
                Y = (Y - 1) & 0xFF;
                setZN(Y);
            }

            case 0xCA -> {
                X = (X - 1) & 0xFF;
                setZN(X);
            }

            case 0xE6 -> inc(zp());
            case 0xF6 -> inc(zpx());
            case 0xEE -> inc(abs());
            case 0xFE -> inc(absX(false));

            case 0xC6 -> dec(zp());
            case 0xD6 -> dec(zpx());
            case 0xCE -> dec(abs());
            case 0xDE -> dec(absX(false));

            case 0x48 -> push(A);

            case 0x68 -> {
                A = pop();
                setZN(A);
            }

            case 0x08 -> push(getStatusRegister() | 0x30);

            case 0x28 -> setStatusRegister(pop());

            case 0x69 -> adc(imm());
            case 0x65 -> adc(fetch(zp()));
            case 0x75 -> adc(fetch(zpx()));
            case 0x6D -> adc(fetch(abs()));
            case 0x7D -> adc(fetch(absX(true)));
            case 0x79 -> adc(fetch(absY(false)));
            case 0x61 -> adc(fetch(indX()));
            case 0x71 -> adc(fetch(indY()));
            case 0xE9 -> sbc(imm());
            case 0xE5 -> sbc(fetch(zp()));
            case 0xF5 -> sbc(fetch(zpx()));
            case 0xED -> sbc(fetch(abs()));
            case 0xFD -> sbc(fetch(absX(true)));
            case 0xF9 -> sbc(fetch(absY(false)));
            case 0xE1 -> sbc(fetch(indX()));
            case 0xF1 -> sbc(fetch(indY()));

            case 0x18 -> C = false;
            case 0x38 -> C = true;
            case 0x58 -> I = false;
            case 0x78 -> I = true;
            case 0xB8 -> V = false;
            case 0xD8 -> D = false;
            case 0xF8 -> D = true;

            case 0x0A -> A = aslValue(A);

            case 0x06 -> aslMemory(zp());
            case 0x16 -> aslMemory(zpx());
            case 0x0E -> aslMemory(abs());
            case 0x1E -> aslMemory(absX(false));
            case 0x4A -> A = lsrValue(A);

            case 0x46 -> lsrMemory(zp());
            case 0x56 -> lsrMemory(zpx());
            case 0x4E -> lsrMemory(abs());
            case 0x5E -> lsrMemory(absX(false));
            case 0x2A -> A = rolValue(A);

            case 0x26 -> rolMemory(zp());
            case 0x36 -> rolMemory(zpx());
            case 0x2E -> rolMemory(abs());
            case 0x3E -> rolMemory(absX(false));
            case 0x6A -> A = rorValue(A);

            case 0x66 -> rorMemory(zp());
            case 0x76 -> rorMemory(zpx());
            case 0x6E -> rorMemory(abs());
            case 0x7E -> rorMemory(absX(false));

            case 0xEA -> {
            }

            default -> throw new RuntimeException(String.format("Opcode %02X no implementado", op));
        }
    }

    private void buildOpcodeTable() {

        for (int i = 0; i < 256; i++) {

            opcodeTable[i] = new Opcode(
                    "???",
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

        opcodeTable[0xA9] = new Opcode("LDA", 2,
                () -> lda(imm()));

        opcodeTable[0xA5] = new Opcode("LDA", 3,
                () -> lda(fetch(zp())));

        opcodeTable[0xB5] = new Opcode("LDA", 4,
                () -> lda(fetch(zpx())));

        opcodeTable[0xAD] = new Opcode("LDA", 4,
                () -> lda(fetch(abs())));

        opcodeTable[0xBD] = new Opcode("LDA", 4,
                () -> {

                    lda(fetch(absX(true)));

                    if (pageCrossed)
                        tick(1);
                });

        opcodeTable[0xB9] = new Opcode("LDA", 4,
                () -> {

                    lda(fetch(absY(true)));

                    if (pageCrossed)
                        tick(1);
                });

        opcodeTable[0xA1] = new Opcode("LDA", 6,
                () -> lda(fetch(indX())));

        opcodeTable[0xB1] = new Opcode("LDA", 5,
                () -> {

                    lda(fetch(indY()));

                    if (pageCrossed)
                        tick(1);
                });
    }

    private void registerLDX() {

        opcodeTable[0xA2] = new Opcode("LDX", 2,
                () -> ldx(imm()));

        opcodeTable[0xA6] = new Opcode("LDX", 3,
                () -> ldx(fetch(zp())));

        opcodeTable[0xB6] = new Opcode("LDX", 4,
                () -> ldx(fetch(zpy())));

        opcodeTable[0xAE] = new Opcode("LDX", 4,
                () -> ldx(fetch(abs())));

        opcodeTable[0xBE] = new Opcode("LDX", 4,
                () -> {

                    ldx(fetch(absY(true)));

                    if (pageCrossed)
                        tick(1);
                });
    }

    private void registerLDY() {

        opcodeTable[0xA0] = new Opcode("LDY", 2,
                () -> ldy(imm()));

        opcodeTable[0xA4] = new Opcode("LDY", 3,
                () -> ldy(fetch(zp())));

        opcodeTable[0xB4] = new Opcode("LDY", 4,
                () -> ldy(fetch(zpx())));

        opcodeTable[0xAC] = new Opcode("LDY", 4,
                () -> ldy(fetch(abs())));

        opcodeTable[0xBC] = new Opcode("LDY", 4,
                () -> {

                    ldy(fetch(absX(true)));

                    if (pageCrossed)
                        tick(1);
                });
    }

    private void registerBranchInstructions() {

        opcodeTable[0xD0] = new Opcode(
                "BNE",
                2,
                () -> branch(!Z));

        opcodeTable[0xF0] = new Opcode(
                "BEQ",
                2,
                () -> branch(Z));

        opcodeTable[0x10] = new Opcode(
                "BPL",
                2,
                () -> branch(!N));

        opcodeTable[0x30] = new Opcode(
                "BMI",
                2,
                () -> branch(N));

        opcodeTable[0x90] = new Opcode(
                "BCC",
                2,
                () -> branch(!C));

        opcodeTable[0xB0] = new Opcode(
                "BCS",
                2,
                () -> branch(C));

        opcodeTable[0x50] = new Opcode(
                "BVC",
                2,
                () -> branch(!V));

        opcodeTable[0x70] = new Opcode(
                "BVS",
                2,
                () -> branch(V));
    }

    private void registerArithmeticInstructions() {

        opcodeTable[0x69] = new Opcode(
                "ADC",
                2,
                () -> adc(imm()));

        opcodeTable[0x65] = new Opcode(
                "ADC",
                3,
                () -> adc(fetch(zp())));

        opcodeTable[0x6D] = new Opcode(
                "ADC",
                4,
                () -> adc(fetch(abs())));

        // 79 - ADC Absolute,Y
        opcodeTable[0x79] = new Opcode(
                "ADC",
                4,
                () -> adc(read(absY(true))));

        // ======================================================
        // ADC
        // ======================================================

        // 75 - ADC Zero Page,X
        opcodeTable[0x75] = new Opcode(
                "ADC",
                4,
                () -> adc(read(zpx())));

        // 7D - ADC Absolute,X
        opcodeTable[0x7D] = new Opcode(
                "ADC",
                4,
                () -> adc(read(absX(true))));

        // 61 - ADC (Indirect,X)
        opcodeTable[0x61] = new Opcode(
                "ADC",
                6,
                () -> adc(read(indX())));

        // 71 - ADC (Indirect),Y
        opcodeTable[0x71] = new Opcode(
                "ADC",
                5,
                () -> adc(read(indY())));

    }

    private void registerSBCInstructions() {

        // SBC #imm
        opcodeTable[0xE9] = new Opcode("SBC", 2,
                () -> sbc(imm()));

        // SBC zp
        opcodeTable[0xE5] = new Opcode("SBC", 3,
                () -> sbc(fetch(zp())));

        // SBC zp,X
        opcodeTable[0xF5] = new Opcode("SBC", 4,
                () -> sbc(fetch(zpx())));

        // SBC abs
        opcodeTable[0xED] = new Opcode("SBC", 4,
                () -> sbc(fetch(abs())));

        // SBC abs,X
        opcodeTable[0xFD] = new Opcode("SBC", 4,
                () -> {
                    sbc(fetch(absX(true)));
                    if (pageCrossed)
                        tick(1);
                });

        // SBC abs,Y
        opcodeTable[0xF9] = new Opcode("SBC", 4,
                () -> {
                    sbc(fetch(absY(true)));
                    if (pageCrossed)
                        tick(1);
                });

        // SBC (ind,X)
        opcodeTable[0xE1] = new Opcode("SBC", 6,
                () -> sbc(fetch(indX())));

        // SBC (ind),Y
        opcodeTable[0xF1] = new Opcode("SBC", 5,
                () -> {
                    sbc(fetch(indY()));
                    if (pageCrossed)
                        tick(1);
                });
    }

    private void registerStackInstructions() {

        opcodeTable[0x48] = new Opcode(
                "PHA",
                3,
                () -> push(A));

        opcodeTable[0x68] = new Opcode(
                "PLA",
                4,
                () -> {

                    A = pop();

                    setZN(A);
                });

        opcodeTable[0x08] = new Opcode(
                "PHP",
                3,
                () -> push(
                        getStatusRegister() | 0x30));

        opcodeTable[0x28] = new Opcode(
                "PLP",
                4,
                () -> setStatusRegister(pop()));
    }

    private void registerShiftInstructions() {

        opcodeTable[0x0A] = new Opcode(
                "ASL",
                2,
                () -> A = aslValue(A));

        opcodeTable[0x46] = new Opcode(
                "LSR zp",
                5,
                () -> {
                    int addr = zp();
                    int value = read(addr);

                    value = lsrValue(value);

                    write(addr, value);
                });

        opcodeTable[0x4A] = new Opcode(
                "LSR",
                2,
                () -> A = lsrValue(A));

        opcodeTable[0x2A] = new Opcode(
                "ROL",
                2,
                () -> A = rolValue(A));

        opcodeTable[0x6A] = new Opcode(
                "ROR",
                2,
                () -> A = rorValue(A));

        // ======================================================
        // ASL
        // ======================================================

        // 06 - ASL Zero Page
        opcodeTable[0x06] = new Opcode("ASL", 5, () -> {
            int addr = zp();
            write(addr, aslValue(read(addr)));
        });

        // 16 - ASL Zero Page,X
        opcodeTable[0x16] = new Opcode("ASL", 6, () -> {
            int addr = zpx();
            write(addr, aslValue(read(addr)));
        });

        // 0E - ASL Absolute
        opcodeTable[0x0E] = new Opcode("ASL", 6, () -> {
            int addr = abs();
            write(addr, aslValue(read(addr)));
        });

        // 1E - ASL Absolute,X
        opcodeTable[0x1E] = new Opcode("ASL", 7, () -> {
            int addr = absX(false);
            write(addr, aslValue(read(addr)));
        });

        // ======================================================
        // LSR
        // ======================================================

        // 46 - LSR Zero Page
        opcodeTable[0x46] = new Opcode("LSR", 5, () -> {
            int addr = zp();
            write(addr, lsrValue(read(addr)));
        });

        // 56 - LSR Zero Page,X
        opcodeTable[0x56] = new Opcode("LSR", 6, () -> {
            int addr = zpx();
            write(addr, lsrValue(read(addr)));
        });

        // 4E - LSR Absolute
        opcodeTable[0x4E] = new Opcode("LSR", 6, () -> {
            int addr = abs();
            write(addr, lsrValue(read(addr)));
        });

        // 5E - LSR Absolute,X
        opcodeTable[0x5E] = new Opcode("LSR", 7, () -> {
            int addr = absX(false);
            write(addr, lsrValue(read(addr)));
        });

        // ======================================================
        // ROL
        // ======================================================

        // 26 - ROL Zero Page
        opcodeTable[0x26] = new Opcode("ROL", 5, () -> {
            int addr = zp();
            write(addr, rolValue(read(addr)));
        });

        // 36 - ROL Zero Page,X
        opcodeTable[0x36] = new Opcode("ROL", 6, () -> {
            int addr = zpx();
            write(addr, rolValue(read(addr)));
        });

        // 2E - ROL Absolute
        opcodeTable[0x2E] = new Opcode("ROL", 6, () -> {
            int addr = abs();
            write(addr, rolValue(read(addr)));
        });

        // 3E - ROL Absolute,X
        opcodeTable[0x3E] = new Opcode("ROL", 7, () -> {
            int addr = absX(false);
            write(addr, rolValue(read(addr)));
        });

        // ======================================================
        // ROR
        // ======================================================

        // 66 - ROR Zero Page
        opcodeTable[0x66] = new Opcode("ROR", 5, () -> {
            int addr = zp();
            write(addr, rorValue(read(addr)));
        });

        // 76 - ROR Zero Page,X
        opcodeTable[0x76] = new Opcode("ROR", 6, () -> {
            int addr = zpx();
            write(addr, rorValue(read(addr)));
        });

        // 6E - ROR Absolute
        opcodeTable[0x6E] = new Opcode("ROR", 6, () -> {
            int addr = abs();
            write(addr, rorValue(read(addr)));
        });

        // 7E - ROR Absolute,X
        opcodeTable[0x7E] = new Opcode("ROR", 7, () -> {
            int addr = absX(false);
            write(addr, rorValue(read(addr)));
        });

    }

    private void registerJumpInstructions() {

        opcodeTable[0x00] = new Opcode("BRK", 7,
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
                6,
                () -> {

                    int addr = abs();

                    pushWord(PC - 1);

                    PC = addr;
                });

        opcodeTable[0x60] = new Opcode(
                "RTS",
                6,
                () -> PC = popWord() + 1);

        opcodeTable[0x40] = new Opcode(
                "RTI",
                6,
                () -> {

                    setStatusRegister(pop());

                    int lo = pop();
                    int hi = pop();

                    PC = (hi << 8) | lo;
                });

        opcodeTable[0x6C] = new Opcode("JMP", 5,
                () -> PC = indirect());

        opcodeTable[0x4C] = new Opcode(
                "JMP",
                3,
                () -> PC = abs());

    }

    private void registerCompareInstructions() {

        // ==========================
        // CMP
        // ==========================

        opcodeTable[0xC9] = new Opcode("CMP", 2,
                () -> compare(A, imm()));

        opcodeTable[0xC5] = new Opcode("CMP", 3,
                () -> compare(A, fetch(zp())));

        opcodeTable[0xD5] = new Opcode("CMP", 4,
                () -> compare(A, fetch(zpx())));

        opcodeTable[0xCD] = new Opcode("CMP", 4,
                () -> compare(A, fetch(abs())));

        opcodeTable[0xDD] = new Opcode("CMP", 4,
                () -> {

                    compare(A, fetch(absX(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        opcodeTable[0xD9] = new Opcode("CMP", 4,
                () -> {

                    compare(A, fetch(absY(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });
        opcodeTable[0xC1] = new Opcode("CMP", 6,
                () -> compare(A, fetch(indX())));

        opcodeTable[0xD1] = new Opcode("CMP", 5,
                () -> {

                    compare(A, fetch(indY()));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        // ==========================
        // CPX
        // ==========================

        opcodeTable[0xE0] = new Opcode("CPX", 2,
                () -> compare(X, imm()));

        opcodeTable[0xE4] = new Opcode("CPX", 3,
                () -> compare(X, fetch(zp())));

        opcodeTable[0xEC] = new Opcode("CPX", 4,
                () -> compare(X, fetch(abs())));

        // ==========================
        // CPY
        // ==========================

        opcodeTable[0xC0] = new Opcode("CPY", 2,
                () -> compare(Y, imm()));

        opcodeTable[0xC4] = new Opcode("CPY", 3,
                () -> compare(Y, fetch(zp())));

        opcodeTable[0xCC] = new Opcode("CPY", 4,
                () -> compare(Y, fetch(abs())));
    }

    private void registerLogicalInstructions() {

        // =====================================
        // AND
        // =====================================

        opcodeTable[0x29] = new Opcode("AND", 2,
                () -> and(imm()));

        opcodeTable[0x25] = new Opcode("AND", 3,
                () -> and(fetch(zp())));

        opcodeTable[0x35] = new Opcode("AND", 4,
                () -> and(fetch(zpx())));

        opcodeTable[0x2D] = new Opcode("AND", 4,
                () -> and(fetch(abs())));

        opcodeTable[0x3D] = new Opcode("AND", 4,
                () -> {
                    and(fetch(absX(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        opcodeTable[0x39] = new Opcode("AND", 4,
                () -> {
                    and(fetch(absY(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        opcodeTable[0x21] = new Opcode("AND", 6,
                () -> and(fetch(indX())));

        opcodeTable[0x31] = new Opcode("AND", 5,
                () -> {
                    and(fetch(indY()));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        // =====================================
        // ORA
        // =====================================

        opcodeTable[0x09] = new Opcode("ORA", 2,
                () -> ora(imm()));

        opcodeTable[0x05] = new Opcode("ORA", 3,
                () -> ora(fetch(zp())));

        opcodeTable[0x15] = new Opcode("ORA", 4,
                () -> ora(fetch(zpx())));

        opcodeTable[0x0D] = new Opcode("ORA", 4,
                () -> ora(fetch(abs())));

        opcodeTable[0x1D] = new Opcode("ORA", 4,
                () -> {
                    ora(fetch(absX(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        opcodeTable[0x19] = new Opcode("ORA", 4,
                () -> {
                    ora(fetch(absY(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        opcodeTable[0x01] = new Opcode("ORA", 6,
                () -> ora(fetch(indX())));

        opcodeTable[0x11] = new Opcode("ORA", 5,
                () -> {
                    ora(fetch(indY()));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        // =====================================
        // EOR
        // =====================================

        opcodeTable[0x49] = new Opcode("EOR", 2,
                () -> eor(imm()));

        opcodeTable[0x45] = new Opcode("EOR", 3,
                () -> eor(fetch(zp())));

        opcodeTable[0x55] = new Opcode("EOR", 4,
                () -> eor(fetch(zpx())));

        opcodeTable[0x4D] = new Opcode("EOR", 4,
                () -> eor(fetch(abs())));

        opcodeTable[0x5D] = new Opcode("EOR", 4,
                () -> {
                    eor(fetch(absX(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        opcodeTable[0x59] = new Opcode("EOR", 4,
                () -> {
                    eor(fetch(absY(true)));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        opcodeTable[0x41] = new Opcode("EOR", 6,
                () -> eor(fetch(indX())));

        opcodeTable[0x51] = new Opcode("EOR", 5,
                () -> {
                    eor(fetch(indY()));

                    if (pageCrossed) {
                        tick(1);
                    }
                });

        // =====================================
        // BIT
        // =====================================

        opcodeTable[0x24] = new Opcode("BIT", 3,
                () -> bit(fetch(zp())));

        opcodeTable[0x2C] = new Opcode("BIT", 4,
                () -> bit(fetch(abs())));
    }

    private void registerTransferInstructions() {

        // ==========================
        // TAX
        // ==========================

        opcodeTable[0xAA] = new Opcode("TAX", 2,
                () -> {

                    X = A & 0xFF;

                    setZN(X);
                });

        // ==========================
        // TXA
        // ==========================

        opcodeTable[0x8A] = new Opcode("TXA", 2,
                () -> {

                    A = X & 0xFF;

                    setZN(A);
                });

        // ==========================
        // TAY
        // ==========================

        opcodeTable[0xA8] = new Opcode("TAY", 2,
                () -> {

                    Y = A & 0xFF;

                    setZN(Y);
                });

        // ==========================
        // TYA
        // ==========================

        opcodeTable[0x98] = new Opcode("TYA", 2,
                () -> {

                    A = Y & 0xFF;

                    setZN(A);
                });

        // ==========================
        // TSX
        // ==========================

        opcodeTable[0xBA] = new Opcode("TSX", 2,
                () -> {

                    X = SP & 0xFF;

                    setZN(X);
                });

        // ==========================
        // TXS
        // ==========================

        opcodeTable[0x9A] = new Opcode("TXS", 2,
                () -> {

                    SP = X & 0xFF;

                    // TXS NO modifica flags
                });
    }

    private void registerFlagInstructions() {

        // ==========================
        // CLC
        // ==========================

        opcodeTable[0x18] = new Opcode("CLC", 2,
                () -> C = false);

        // ==========================
        // SEC
        // ==========================

        opcodeTable[0x38] = new Opcode("SEC", 2,
                () -> C = true);

        // ==========================
        // CLI
        // ==========================

        opcodeTable[0x58] = new Opcode("CLI", 2,
                () -> {
                    System.out.printf("CLI ejecutado PC=%04X%n", PC - 1);
                    I = false;
                });

        // ==========================
        // SEI
        // ==========================

        opcodeTable[0x78] = new Opcode("SEI", 2,
                () -> I = true);

        // ==========================
        // CLV
        // ==========================

        opcodeTable[0xB8] = new Opcode("CLV", 2,
                () -> V = false);

        // ==========================
        // CLD
        // ==========================

        opcodeTable[0xD8] = new Opcode("CLD", 2,
                () -> D = false);

        // ==========================
        // SED
        // ==========================

        opcodeTable[0xF8] = new Opcode("SED", 2,
                () -> D = true);
    }

    private void registerStoreXYInstructions() {

        // ==========================
        // STX
        // ==========================

        opcodeTable[0x86] = new Opcode("STX", 3,
                () -> write(zp(), X));

        opcodeTable[0x96] = new Opcode("STX", 4,
                () -> write(zpy(), X));

        opcodeTable[0x8E] = new Opcode("STX", 4,
                () -> write(abs(), X));

        // ==========================
        // STY
        // ==========================

        opcodeTable[0x84] = new Opcode("STY", 3,
                () -> write(zp(), Y));

        opcodeTable[0x94] = new Opcode("STY", 4,
                () -> write(zpx(), Y));

        opcodeTable[0x8C] = new Opcode("STY", 4,
                () -> write(abs(), Y));
    }

    private void registerStoreInstructions() {

        // ==========================
        // STA
        // ==========================

        opcodeTable[0x85] = new Opcode(
                "STA",
                3,
                () -> write(zp(), A));

        opcodeTable[0x95] = new Opcode(
                "STA",
                4,
                () -> write(zpx(), A));

        opcodeTable[0x8D] = new Opcode(
                "STA",
                4,
                () -> write(abs(), A));

        opcodeTable[0x9D] = new Opcode(
                "STA",
                5,
                () -> write(absX(true), A));

        opcodeTable[0x99] = new Opcode(
                "STA",
                5,
                () -> write(absY(false), A));

        opcodeTable[0x81] = new Opcode(
                "STA",
                6,
                () -> write(indX(), A));

        opcodeTable[0x91] = new Opcode(
                "STA",
                6,
                () -> write(indY(), A));

        // STX/STY si ya los añadimos
        registerStoreXYInstructions();
    }

    private void registerIncrementInstructions() {

        // ==========================
        // INX
        // ==========================

        opcodeTable[0xE8] = new Opcode("INX", 2,
                () -> {

                    X = (X + 1) & 0xFF;

                    setZN(X);
                });

        // ==========================
        // INY
        // ==========================

        opcodeTable[0xC8] = new Opcode("INY", 2,
                () -> {

                    Y = (Y + 1) & 0xFF;

                    setZN(Y);
                });

        // ==========================
        // DEX
        // ==========================

        opcodeTable[0xCA] = new Opcode("DEX", 2,
                () -> {

                    X = (X - 1) & 0xFF;

                    setZN(X);
                });

        // ==========================
        // DEY
        // ==========================

        opcodeTable[0x88] = new Opcode("DEY", 2,
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

        opcodeTable[0xE6] = new Opcode("INC", 5,
                () -> inc(zp()));

        opcodeTable[0xF6] = new Opcode("INC", 6,
                () -> inc(zpx()));

        opcodeTable[0xEE] = new Opcode("INC", 6,
                () -> inc(abs()));

        opcodeTable[0xFE] = new Opcode("INC", 7,
                () -> inc(absX(false)));

        // ==========================
        // DEC
        // ==========================

        opcodeTable[0xC6] = new Opcode("DEC", 5,
                () -> dec(zp()));

        opcodeTable[0xD6] = new Opcode("DEC", 6,
                () -> dec(zpx()));

        opcodeTable[0xCE] = new Opcode("DEC", 6,
                () -> dec(abs()));

        opcodeTable[0xDE] = new Opcode("DEC", 7,
                () -> dec(absX(false)));
    }

    private void registerControlInstructions() {

        opcodeTable[0xEA] = new Opcode("NOP", 2,
                () -> {
                    // no-op
                });
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

        opcodeTable[0x1A] = new Opcode("NOP", 2, () -> nop());

        opcodeTable[0x3A] = new Opcode("NOP", 2, () -> nop());

        opcodeTable[0x5A] = new Opcode("NOP", 2, () -> nop());

        opcodeTable[0x7A] = new Opcode("NOP", 2, () -> nop());

        opcodeTable[0xDA] = new Opcode("NOP", 2, () -> nop());

        opcodeTable[0xFA] = new Opcode("NOP", 2, () -> nop());

        //
        // Immediate
        //

        opcodeTable[0x80] = new Opcode(
                "NOP",
                2,
                () -> immediate());

        opcodeTable[0x82] = new Opcode(
                "NOP",
                2,
                () -> immediate());

        opcodeTable[0x89] = new Opcode(
                "NOP",
                2,
                () -> immediate());

        opcodeTable[0xC2] = new Opcode(
                "NOP",
                2,
                () -> immediate());

        opcodeTable[0xE2] = new Opcode(
                "NOP",
                2,
                () -> immediate());

        //
        // Zero Page
        //

        opcodeTable[0x04] = new Opcode(
                "NOP",
                3,
                () -> read(zeroPage()));

        opcodeTable[0x44] = new Opcode(
                "NOP",
                3,
                () -> read(zeroPage()));

        opcodeTable[0x64] = new Opcode(
                "NOP",
                3,
                () -> read(zeroPage()));

        //
        // Zero Page,X
        //

        opcodeTable[0x14] = new Opcode(
                "NOP",
                4,
                () -> read(zeroPageX()));

        opcodeTable[0x34] = new Opcode(
                "NOP",
                4,
                () -> read(zeroPageX()));

        opcodeTable[0x54] = new Opcode(
                "NOP",
                4,
                () -> read(zeroPageX()));

        opcodeTable[0x74] = new Opcode(
                "NOP",
                4,
                () -> read(zeroPageX()));

        opcodeTable[0xD4] = new Opcode(
                "NOP",
                4,
                () -> read(zeroPageX()));

        opcodeTable[0xF4] = new Opcode(
                "NOP",
                4,
                () -> read(zeroPageX()));

        //
        // Absolute
        //

        opcodeTable[0x0C] = new Opcode(
                "NOP",
                4,
                () -> read(absolute()));

        //
        // Absolute,X
        // (añaden un ciclo por page crossing)
        //

        opcodeTable[0x1C] = new Opcode(
                "NOP",
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0x3C] = new Opcode(
                "NOP",
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0x5C] = new Opcode(
                "NOP",
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0x7C] = new Opcode(
                "NOP",
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0xDC] = new Opcode(
                "NOP",
                4,
                () -> read(absoluteX(true)));

        opcodeTable[0xFC] = new Opcode(
                "NOP",
                4,
                () -> read(absoluteX(true)));
    }

    private void registerLAXInstructions() {

        // A7 - Zero Page
        opcodeTable[0xA7] = new Opcode(
                "LAX",
                3,
                () -> lax(read(zeroPage())));

        // B7 - Zero Page,Y
        opcodeTable[0xB7] = new Opcode(
                "LAX",
                4,
                () -> lax(read(zeroPageY())));

        // AF - Absolute
        opcodeTable[0xAF] = new Opcode(
                "LAX",
                4,
                () -> lax(read(absolute())));

        // BF - Absolute,Y
        opcodeTable[0xBF] = new Opcode(
                "LAX",
                4,
                () -> lax(read(absoluteY(true))));

        // A3 - (Indirect,X)
        opcodeTable[0xA3] = new Opcode(
                "LAX",
                6,
                () -> lax(read(indirectX())));

        // B3 - (Indirect),Y
        opcodeTable[0xB3] = new Opcode(
                "LAX",
                5,
                () -> lax(read(indirectY(true))));
    }

    private void registerSAXInstructions() {

        // 87 - Zero Page
        opcodeTable[0x87] = new Opcode(
                "SAX",
                3,
                () -> sax(zeroPage()));

        // 97 - Zero Page,Y
        opcodeTable[0x97] = new Opcode(
                "SAX",
                4,
                () -> sax(zeroPageY()));

        // 8F - Absolute
        opcodeTable[0x8F] = new Opcode(
                "SAX",
                4,
                () -> sax(absolute()));

        // 83 - (Indirect,X)
        opcodeTable[0x83] = new Opcode(
                "SAX",
                6,
                () -> sax(indirectX()));
    }

    private void registerSLOInstructions() {

        opcodeTable[0x07] = new Opcode("SLO", 5,
                () -> slo(zeroPage()));

        opcodeTable[0x17] = new Opcode("SLO", 6,
                () -> slo(zeroPageX()));

        opcodeTable[0x0F] = new Opcode("SLO", 6,
                () -> slo(absolute()));

        opcodeTable[0x1F] = new Opcode("SLO", 7,
                () -> slo(absoluteX(false)));

        opcodeTable[0x1B] = new Opcode("SLO", 7,
                () -> slo(absoluteY(false)));

        opcodeTable[0x03] = new Opcode("SLO", 8,
                () -> slo(indirectX()));

        opcodeTable[0x13] = new Opcode("SLO", 8,
                () -> slo(indirectY(false)));
    }

    private void registerRLAInstructions() {

        opcodeTable[0x27] = new Opcode("RLA", 5,
                () -> rla(zeroPage()));

        opcodeTable[0x37] = new Opcode("RLA", 6,
                () -> rla(zeroPageX()));

        opcodeTable[0x2F] = new Opcode("RLA", 6,
                () -> rla(absolute()));

        opcodeTable[0x3F] = new Opcode("RLA", 7,
                () -> rla(absoluteX(false)));

        opcodeTable[0x3B] = new Opcode("RLA", 7,
                () -> rla(absoluteY(false)));

        opcodeTable[0x23] = new Opcode("RLA", 8,
                () -> rla(indirectX()));

        opcodeTable[0x33] = new Opcode("RLA", 8,
                () -> rla(indirectY(false)));
    }

    private void registerSREInstructions() {

        opcodeTable[0x47] = new Opcode("SRE", 5,
                () -> sre(zeroPage()));

        opcodeTable[0x57] = new Opcode("SRE", 6,
                () -> sre(zeroPageX()));

        opcodeTable[0x4F] = new Opcode("SRE", 6,
                () -> sre(absolute()));

        opcodeTable[0x5F] = new Opcode("SRE", 7,
                () -> sre(absoluteX(false)));

        opcodeTable[0x5B] = new Opcode("SRE", 7,
                () -> sre(absoluteY(false)));

        opcodeTable[0x43] = new Opcode("SRE", 8,
                () -> sre(indirectX()));

        opcodeTable[0x53] = new Opcode("SRE", 8,
                () -> sre(indirectY(false)));
    }

    private void registerRRAInstructions() {

        opcodeTable[0x67] = new Opcode("RRA", 5,
                () -> rra(zeroPage()));

        opcodeTable[0x77] = new Opcode("RRA", 6,
                () -> rra(zeroPageX()));

        opcodeTable[0x6F] = new Opcode("RRA", 6,
                () -> rra(absolute()));

        opcodeTable[0x7F] = new Opcode("RRA", 7,
                () -> rra(absoluteX(false)));

        opcodeTable[0x7B] = new Opcode("RRA", 7,
                () -> rra(absoluteY(false)));

        opcodeTable[0x63] = new Opcode("RRA", 8,
                () -> rra(indirectX()));

        opcodeTable[0x73] = new Opcode("RRA", 8,
                () -> rra(indirectY(false)));
    }

    private void registerDCPInstructions() {

        opcodeTable[0xC7] = new Opcode("DCP", 5,
                () -> dcp(zeroPage()));

        opcodeTable[0xD7] = new Opcode("DCP", 6,
                () -> dcp(zeroPageX()));

        opcodeTable[0xCF] = new Opcode("DCP", 6,
                () -> dcp(absolute()));

        opcodeTable[0xDF] = new Opcode("DCP", 7,
                () -> dcp(absoluteX(false)));

        opcodeTable[0xDB] = new Opcode("DCP", 7,
                () -> dcp(absoluteY(false)));

        opcodeTable[0xC3] = new Opcode("DCP", 8,
                () -> dcp(indirectX()));

        opcodeTable[0xD3] = new Opcode("DCP", 8,
                () -> dcp(indirectY(false)));
    }

    private void registerISCInstructions() {

        opcodeTable[0xE7] = new Opcode("ISC", 5,
                () -> isc(zeroPage()));

        opcodeTable[0xF7] = new Opcode("ISC", 6,
                () -> isc(zeroPageX()));

        opcodeTable[0xEF] = new Opcode("ISC", 6,
                () -> isc(absolute()));

        opcodeTable[0xFF] = new Opcode("ISC", 7,
                () -> isc(absoluteX(false)));

        opcodeTable[0xFB] = new Opcode("ISC", 7,
                () -> isc(absoluteY(false)));

        opcodeTable[0xE3] = new Opcode("ISC", 8,
                () -> isc(indirectX()));

        opcodeTable[0xF3] = new Opcode("ISC", 8,
                () -> isc(indirectY(false)));
    }

    private void registerANCInstructions() {

        opcodeTable[0x0B] = new Opcode(
                "ANC",
                2,
                () -> anc(immediate()));

        opcodeTable[0x2B] = new Opcode(
                "ANC",
                2,
                () -> anc(immediate()));
    }

    private void registerALRInstructions() {

        opcodeTable[0x4B] = new Opcode(
                "ALR",
                2,
                () -> alr(immediate()));
    }

    private void registerARRInstructions() {

        opcodeTable[0x6B] = new Opcode(
                "ARR",
                2,
                () -> arr(immediate()));
    }

    private void registerSBXInstructions() {

        opcodeTable[0xCB] = new Opcode(
                "SBX",
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

}
