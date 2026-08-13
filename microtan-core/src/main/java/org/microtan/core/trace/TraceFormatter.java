package org.microtan.core.trace;
import org.microtan.core.cpu.AddressingMode;
import org.microtan.core.cpu.CPU6510;
import org.microtan.core.cpu.Opcode;
// import com.example.c64.cia.CIA1;
// import com.example.c64.cia.CIA2;
// import com.example.c64.video.VICII;

public final class TraceFormatter {

    private final TraceConfig traceConfig;

    public TraceFormatter(TraceConfig traceConfig) {
        this.traceConfig = traceConfig;
    }

    /**
     * Formatea la ejecución de una instrucción de la CPU.
     *
     * Ejemplo:
     *
     * 00012345  FCE4  A2 FF     LDX #$FF
     *            A:00 X:FF Y:00 SP:FD P:A4 [N.-..I..]
     *            VIC[R=051 C=23]
     *
     * Las diferentes partes se incluyen según las opciones
     * activadas en TraceConfig.
     */
    public String formatInstruction(
            CPU6510 cpu,
            Opcode opcode,
            int pc) {

        StringBuilder sb = new StringBuilder();

        /*
         * Información básica de la instrucción.
         */
        sb.append(
            String.format(
                "%08d  %04X  %-8s %-3s %-14s",
                cpu.getTotalCycles(),
                pc,
                instructionBytes(
                    cpu,
                    pc,
                    opcode.getMode()),
                opcode.getMnemonic(),
                formatOperand(
                    cpu,
                    pc,
                    opcode.getMode())
            )
        );

        /*
         * Registros de la CPU.
         */
        if (traceConfig.isEnabled(
                TraceOption.REGISTERS)) {

            sb.append(
                formatRegisters(cpu));
        }

        /*
         * Estado del VIC-II.
         */
        if (traceConfig.isEnabled(
                TraceOption.VIC)) {

            sb.append(
                formatVic(cpu));
        }

        /*
         * Estado de las CIA.
         */
        if (traceConfig.isEnabled(
                TraceOption.CIA)) {

            sb.append(
                formatCia(cpu));
        }

        /*
         * Información relacionada con interrupciones.
         */
        if (traceConfig.isEnabled(
                TraceOption.INTERRUPTS)) {

            sb.append(
                formatInterrupts(cpu));
        }

        /*
         * Información de pila.
         *
         * Esta opción puede utilizarse para mostrar
         * SP y/o el contenido relevante de la pila.
         */
        if (traceConfig.isEnabled(
                TraceOption.STACK)) {

            sb.append(
                formatStack(cpu));
        }

        /*
         * Información de memoria.
         *
         * Se deja separada de los registros porque permite
         * ampliar posteriormente la traza con accesos concretos.
         */
        if (traceConfig.isEnabled(
                TraceOption.MEMORY)) {

            sb.append(
                formatMemory(cpu));
        }

        /*
         * Estado del Bus.
         */
        if (traceConfig.isEnabled(
                TraceOption.BUS)) {

            sb.append(
                formatBus(cpu));
        }

        /*
         * Aviso especial para opcodes ilegales.
         */
        if (traceConfig.isEnabled(
                TraceOption.ILLEGAL_OPCODES)
                && isIllegalOpcode(opcode)) {

            sb.append(" ILLEGAL");
        }

        return sb.toString();
    }


    // ---------------------------------------------------------
    // REGISTROS CPU
    // ---------------------------------------------------------

    private String formatRegisters(
            CPU6510 cpu) {

        int p =
            cpu.getStatusRegister();

        return String.format(
            " A:%02X X:%02X Y:%02X SP:%02X P:%02X [%c%c-%c%c%c%c%c]",
            cpu.getA(),
            cpu.getX(),
            cpu.getY(),
            cpu.getSP(),
            p,

            (p & 0x80) != 0 ? 'N' : '.',
            (p & 0x40) != 0 ? 'V' : '.',

            (p & 0x10) != 0 ? 'B' : '.',
            (p & 0x08) != 0 ? 'D' : '.',
            (p & 0x04) != 0 ? 'I' : '.',
            (p & 0x02) != 0 ? 'Z' : '.',
            (p & 0x01) != 0 ? 'C' : '.');
    }


    // ---------------------------------------------------------
    // VIC-II
    // ---------------------------------------------------------

    private String formatVic(
            CPU6510 cpu) {

        // VICII vic =
        //     cpu.getBus().getVic();

        // return String.format(
        //     " VIC[R=%03d C=%02d]",
        //     vic.getRasterY(),
        //     vic.getCycleInLine());
        return " VIC[R=%03d C=%02d]";
        

    }


    // ---------------------------------------------------------
    // CIA
    // ---------------------------------------------------------

    private String formatCia(
            CPU6510 cpu) {

        // CIA1 cia1 =
        //     cpu.getBus().getCia1();

        // CIA2 cia2 =
        //     cpu.getBus().getCia2();

        // return String.format(
        //     " CIA1[TA=%04X TB=%04X] CIA2[TA=%04X TB=%04X]",
        //     cia1.getTimerA(),
        //     cia1.getTimerB(),
        //     cia2.getTimerA(),
        //     cia2.getTimerB());
        return " CIA1[TA=04X TB=04X] CIA2[TA=04X TB=04X]";
    }


    // ---------------------------------------------------------
    // INTERRUPCIONES
    // ---------------------------------------------------------

    private String formatInterrupts(
            CPU6510 cpu) {

        return String.format(
            " IRQ=%s NMI=%s I=%s",
            cpu.isIrqPending() ? "PENDING" : "-",
            cpu.isNmiPending() ? "PENDING" : "-",
            cpu.isInterruptDisableFlagSet() ? "1" : "0");
    }


    // ---------------------------------------------------------
    // STACK
    // ---------------------------------------------------------

    private String formatStack(
            CPU6510 cpu) {

        int sp = cpu.getSP();

        return String.format(
            " STACK[SP=%02X]",
            sp);
    }


    // ---------------------------------------------------------
    // MEMORY
    // ---------------------------------------------------------

    private String formatMemory(
            CPU6510 cpu) {

        /*
         * Esta opción queda preparada para mostrar información
         * sobre el último acceso a memoria.
         *
         * Se puede ampliar posteriormente con:
         *
         *   read/write
         *   dirección
         *   valor
         *   dispositivo
         */
        return " MEM";
    }


    // ---------------------------------------------------------
    // BUS
    // ---------------------------------------------------------

    private String formatBus(
            CPU6510 cpu) {

        /*
         * De momento mostramos el estado general del Bus.
         * Si el Bus conserva el último acceso, aquí se puede
         * mostrar dirección, valor y tipo de acceso.
         */
        return " BUS";
    }


    // ---------------------------------------------------------
    // BYTES DE INSTRUCCIÓN
    // ---------------------------------------------------------

    private String instructionBytes(
            CPU6510 cpu,
            int pc,
            AddressingMode mode) {

        switch (mode) {

            case IMPLIED:
            case ACCUMULATOR:

                return String.format(
                    "%02X",
                    cpu.getBus().peek(pc));

            case IMMEDIATE:
            case ZERO_PAGE:
            case ZERO_PAGE_X:
            case ZERO_PAGE_Y:
            case INDIRECT_X:
            case INDIRECT_Y:
            case RELATIVE:

                return String.format(
                    "%02X %02X",
                    cpu.getBus().peek(pc),
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF));

            case ABSOLUTE:
            case ABSOLUTE_X:
            case ABSOLUTE_Y:
            case INDIRECT:

                return String.format(
                    "%02X %02X %02X",
                    cpu.getBus().peek(pc),
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF),
                    cpu.getBus().peek(
                        (pc + 2) & 0xFFFF));

            default:

                return String.format(
                    "%02X",
                    cpu.getBus().peek(pc));
        }
    }


    // ---------------------------------------------------------
    // OPERANDO
    // ---------------------------------------------------------

    private String formatOperand(
            CPU6510 cpu,
            int pc,
            AddressingMode mode) {

        switch (mode) {

            case IMPLIED:

                return "";

            case ACCUMULATOR:

                return "A";

            case IMMEDIATE:

                return String.format(
                    "#$%02X",
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF));

            case ZERO_PAGE:

                return String.format(
                    "$%02X",
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF));

            case ZERO_PAGE_X:

                return String.format(
                    "$%02X,X",
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF));

            case ZERO_PAGE_Y:

                return String.format(
                    "$%02X,Y",
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF));

            case ABSOLUTE:

                return String.format(
                    "$%04X",
                    word(
                        cpu.getBus().peek((pc + 1) & 0xFFFF),
                        cpu.getBus().peek((pc + 2) & 0xFFFF)));

            case ABSOLUTE_X:

                return String.format(
                    "$%04X,X",
                    word(
                        cpu.getBus().peek((pc + 1) & 0xFFFF),
                        cpu.getBus().peek((pc + 2) & 0xFFFF)));

            case ABSOLUTE_Y:

                return String.format(
                    "$%04X,Y",
                    word(
                        cpu.getBus().peek((pc + 1) & 0xFFFF),
                        cpu.getBus().peek((pc + 2) & 0xFFFF)));

            case INDIRECT:

                return String.format(
                    "($%04X)",
                    word(
                        cpu.getBus().peek((pc + 1) & 0xFFFF),
                        cpu.getBus().peek((pc + 2) & 0xFFFF)));

            case INDIRECT_X:

                return String.format(
                    "($%02X,X)",
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF));

            case INDIRECT_Y:

                return String.format(
                    "($%02X),Y",
                    cpu.getBus().peek(
                        (pc + 1) & 0xFFFF));

            case RELATIVE:

                int offset =
                    (byte) cpu.getBus().peek(
                        (pc + 1) & 0xFFFF);

                int target =
                    (pc + 2 + offset)
                    & 0xFFFF;

                return String.format(
                    "$%04X",
                    target);

            default:

                return "";
        }
    }


    // ---------------------------------------------------------
    // UTILIDADES
    // ---------------------------------------------------------

    private int word(
            int low,
            int high) {

        return low | (high << 8);
    }


    private boolean isIllegalOpcode(
            Opcode opcode) {

        String mnemonic =
            opcode.getMnemonic();

        return mnemonic.equals("NOP")
            || mnemonic.equals("LAX")
            || mnemonic.equals("SAX")
            || mnemonic.equals("SLO")
            || mnemonic.equals("RLA")
            || mnemonic.equals("SRE")
            || mnemonic.equals("RRA")
            || mnemonic.equals("DCP")
            || mnemonic.equals("ISC")
            || mnemonic.equals("ANC")
            || mnemonic.equals("ALR")
            || mnemonic.equals("ARR")
            || mnemonic.equals("SBX")
            || mnemonic.equals("JAM");
    }
}
