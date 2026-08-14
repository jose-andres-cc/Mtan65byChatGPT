package org.microtan.core.io.via;

import org.microtan.core.bus.MemoryDevice;

/**
 * Emulación del MOS 6522 Versatile Interface Adapter.
 *
 * Mapa de registros:
 *
 * 00  ORB / IRB
 * 01  ORA / IRA
 * 02  DDRB
 * 03  DDRA
 * 04  T1C-L
 * 05  T1C-H
 * 06  T1L-L
 * 07  T1L-H
 * 08  T2C-L
 * 09  T2C-H
 * 0A  SR
 * 0B  ACR
 * 0C  PCR
 * 0D  IFR
 * 0E  IER
 * 0F  ORA / IRA
 *
 * Las direcciones 01 y 0F son dos formas de acceder a ORA,
 * con diferencias en el comportamiento de handshake del
 * hardware real.
 */

/**
 * MOS 6522 VIA.
 *
 * Registro    Offset
 * ------------------
 * ORB/IRB       $00
 * ORA/IRA       $01
 * DDRB          $02
 * DDRA          $03
 * T1C-L         $04
 * T1C-H         $05
 * T1L-L         $06
 * T1L-H         $07
 * T2C-L         $08
 * T2C-H         $09
 * SR            $0A
 * ACR           $0B
 * PCR           $0C
 * IFR           $0D
 * IER           $0E
 * ORA/IRA       $0F
 */
public class VIA6522 implements MemoryDevice {

    public interface IrqListener {
        void irqChanged(boolean active);
    }

    // ---------------------------------------------------------------------
    // Registers
    // ---------------------------------------------------------------------

    private static final int ORB = 0x00;
    private static final int ORA = 0x01;
    private static final int DDRB = 0x02;
    private static final int DDRA = 0x03;

    private static final int T1CL = 0x04;
    private static final int T1CH = 0x05;
    private static final int T1LL = 0x06;
    private static final int T1LH = 0x07;

    private static final int T2CL = 0x08;
    private static final int T2CH = 0x09;

    private static final int SR = 0x0A;
    private static final int ACR = 0x0B;
    private static final int PCR = 0x0C;
    private static final int IFR = 0x0D;
    private static final int IER = 0x0E;

    private static final int ORA_NO_HANDSHAKE = 0x0F;

    // ---------------------------------------------------------------------
    // IFR bits
    // ---------------------------------------------------------------------

    public static final int IFR_CA2 = 0x01;
    public static final int IFR_CA1 = 0x02;
    public static final int IFR_SR  = 0x04;
    public static final int IFR_CB2 = 0x08;
    public static final int IFR_CB1 = 0x10;
    public static final int IFR_T2  = 0x20;
    public static final int IFR_T1  = 0x40;

    private static final int IFR_IRQ = 0x80;

    // ---------------------------------------------------------------------
    // ACR bits
    // ---------------------------------------------------------------------

    /**
     * Timer 1 free-running mode.
     */
    private static final int ACR_T1_FREE_RUN = 0x40;

    /**
     * Timer 1 PB7 output enable/mode.
     */
    private static final int ACR_T1_PB7 = 0x80;

    /**
     * Timer 2 mode:
     *
     * 0 = interval timer
     * 1 = pulse counter on PB6
     */
    private static final int ACR_T2_PULSE_COUNT = 0x20;

    // ---------------------------------------------------------------------
    // IER
    // ---------------------------------------------------------------------

    private static final int IER_SET = 0x80;

    // ---------------------------------------------------------------------
    // Registers/state
    // ---------------------------------------------------------------------

    private int orb;
    private int ora;

    private int ddrb;
    private int ddra;

    /*
     * T1:
     *
     * latch = valor programado
     * counter = contador actual
     */
    private int timer1Counter;
    private int timer1Latch;

    /*
     * En one-shot, una vez producido el timeout no se genera
     * ningún nuevo IFR6 hasta que T1 se vuelva a cargar.
     */
    private boolean timer1TimedOut;

    /*
     * Estado de PB7 generado por T1.
     */
    private boolean timer1Pb7;

    /*
     * T2:
     */
    private int timer2Counter;
    private int timer2Latch;

    /*
     * En one-shot, una vez producido el timeout no se genera
     * ningún nuevo IFR5 hasta una nueva carga de T2.
     */
    private boolean timer2TimedOut;

    /*
     * Estado anterior de PB6 para detectar flanco descendente.
     */
    private boolean pb6State = true;

    private int shiftRegister;

    private int acr;
    private int pcr;

    /*
     * IFR solo almacena bits 0-6.
     * El bit 7 se genera dinámicamente.
     */
    private int ifr;

    /*
     * IER solo almacena bits 0-6.
     */
    private int ier;

    private int portAInput = 0xFF;
    private int portBInput = 0xFF;

    private IrqListener irqListener;

    public VIA6522() {
        reset();
    }

    // ---------------------------------------------------------------------
    // IRQ
    // ---------------------------------------------------------------------

    public void setIrqListener(IrqListener listener) {
        this.irqListener = listener;
        updateIrq();
    }

    /**
     * Devuelve true si existe alguna interrupción activa y habilitada.
     */
    public boolean isIrqActive() {
        return (ifr & ier & 0x7F) != 0;
    }

    private void updateIrq() {

        if (irqListener != null) {
            irqListener.irqChanged(isIrqActive());
        }
    }

    private void setInterrupt(int mask) {

        ifr |= mask & 0x7F;

        updateIrq();
    }

    private void clearInterrupt(int mask) {

        ifr &= ~(mask & 0x7F);

        updateIrq();
    }

    // ---------------------------------------------------------------------
    // MemoryDevice
    // ---------------------------------------------------------------------

    @Override
    public int read(int address) {

        switch (address & 0x0F) {

            case ORB:
                return readPortB();

            case ORA:
                return readPortA();

            case DDRB:
                return ddrb;

            case DDRA:
                return ddra;

            case T1CL:
                /*
                 * La lectura del low counter limpia IFR6.
                 */
                int t1Low = timer1Counter & 0xFF;

                clearInterrupt(IFR_T1);

                return t1Low;

            case T1CH:
                return (timer1Counter >>> 8) & 0xFF;

            case T1LL:
                return timer1Latch & 0xFF;

            case T1LH:
                return (timer1Latch >>> 8) & 0xFF;

            case T2CL:
                /*
                 * La lectura del low counter limpia IFR5.
                 */
                int t2Low = timer2Counter & 0xFF;

                clearInterrupt(IFR_T2);

                return t2Low;

            case T2CH:
                return (timer2Counter >>> 8) & 0xFF;

            case SR:
                return shiftRegister;

            case ACR:
                return acr;

            case PCR:
                return pcr;

            case IFR:
                return readIFR();

            case IER:
                /*
                 * El bit 7 siempre se lee como 1.
                 */
                return ier | 0x80;

            case ORA_NO_HANDSHAKE:
                return readPortA();

            default:
                return 0xFF;
        }
    }

    @Override
    public void write(int address, int value) {

        value &= 0xFF;

        switch (address & 0x0F) {

            case ORB:
                orb = value;
                break;

            case ORA:
                ora = value;
                break;

            case DDRB:
                ddrb = value;
                break;

            case DDRA:
                ddra = value;
                break;

            // -------------------------------------------------------------
            // Timer 1
            // -------------------------------------------------------------

            case T1CL:

                /*
                 * Escritura de T1CL:
                 *
                 * solamente modifica el latch bajo.
                 */
                timer1Latch =
                        (timer1Latch & 0xFF00)
                        | value;

                break;

            case T1CH:

                /*
                 * T1CH hace:
                 *
                 * 1. carga el high latch
                 * 2. copia el latch completo al counter
                 * 3. inicia/reinicia el timer
                 * 4. limpia IFR6
                 */
                timer1Latch =
                        (timer1Latch & 0x00FF)
                        | (value << 8);

                timer1Counter = timer1Latch;

                timer1TimedOut = false;

                clearInterrupt(IFR_T1);

                /*
                 * En modo T1/PB7, la carga inicia el pulso.
                 */
                if ((acr & ACR_T1_PB7) != 0) {
                    timer1Pb7 = false;
                }

                break;

            case T1LL:

                /*
                 * Solo modifica el low latch.
                 *
                 * Esto permite cambiar el siguiente período de T1
                 * mientras T1 está funcionando en free-run.
                 */
                timer1Latch =
                        (timer1Latch & 0xFF00)
                        | value;

                break;

            case T1LH:

                /*
                 * Solo modifica el high latch.
                 */
                timer1Latch =
                        (timer1Latch & 0x00FF)
                        | (value << 8);

                break;

            // -------------------------------------------------------------
            // Timer 2
            // -------------------------------------------------------------

            case T2CL:

                /*
                 * T2CL es un latch write-only.
                 */
                timer2Latch =
                        (timer2Latch & 0xFF00)
                        | value;

                break;

            case T2CH:

                /*
                 * T2CH carga:
                 *
                 * high counter
                 * low counter desde latch
                 *
                 * y reinicia la lógica de timeout.
                 */
                timer2Counter =
                        (value << 8)
                        | (timer2Latch & 0x00FF);

                timer2TimedOut = false;

                clearInterrupt(IFR_T2);

                break;

            // -------------------------------------------------------------
            // Other registers
            // -------------------------------------------------------------

            case SR:
                shiftRegister = value;
                break;

            case ACR:

                acr = value;

                /*
                 * Si cambiamos T2 de pulse-count a interval timer
                 * no conservamos un estado de flanco artificial.
                 */
                pb6State =
                        (portBInput & 0x40) != 0;

                break;

            case PCR:
                pcr = value;
                break;

            case IFR:

                /*
                 * Escribir 1 limpia el flag.
                 * Escribir 0 no tiene efecto.
                 */
                clearInterrupt(value);

                break;

            case IER:

                writeIER(value);

                break;

            case ORA_NO_HANDSHAKE:

                ora = value;
                break;

            default:
                break;
        }
    }

    @Override
    public int size() {
        return (0x10); // 16
    }

    // ---------------------------------------------------------------------
    // IFR / IER
    // ---------------------------------------------------------------------

    private int readIFR() {

        int result = ifr & 0x7F;

        /*
         * IFR7 no es un flag independiente.
         *
         * Se activa cuando existe al menos un IFR habilitado.
         */
        if ((ifr & ier & 0x7F) != 0) {
            result |= IFR_IRQ;
        }

        return result;
    }

    private void writeIER(int value) {

        if ((value & IER_SET) != 0) {

            /*
             * Bit 7 = 1 -> activar las máscaras indicadas.
             */
            ier |= value & 0x7F;

        } else {

            /*
             * Bit 7 = 0 -> desactivar las máscaras indicadas.
             */
            ier &= ~(value & 0x7F);
        }

        updateIrq();
    }

    // ---------------------------------------------------------------------
    // Clock
    // ---------------------------------------------------------------------

    /**
     * Avanza la VIA un ciclo PHI2.
     */
    public void tick() {

        tickTimer1();

        /*
         * T2 solo recibe PHI2 en modo intervalo.
         */
        if ((acr & ACR_T2_PULSE_COUNT) == 0) {
            tickTimer2();
        }
    }

    /**
     * Avanza la VIA varios ciclos PHI2.
     *
     * Útil cuando CPU.step() devuelve el número de ciclos consumidos.
     */
    public void tick(int cycles) {

        for (int i = 0; i < cycles; i++) {
            tick();
        }
    }

    // ---------------------------------------------------------------------
    // Timer 1
    // ---------------------------------------------------------------------

    private void tickTimer1() {

        /*
         * No existe un estado "stopped" en el sentido de dejar
         * congelado el contador. Después del timeout en one-shot
         * el contador continúa decrementando/rollover, pero no
         * vuelve a generar IFR6.
         */
        if (timer1Counter == 0) {

            if ((acr & ACR_T1_FREE_RUN) != 0) {

                /*
                 * Free-running:
                 *
                 * timeout -> IFR6
                 * timeout -> recarga desde latch
                 */
                setInterrupt(IFR_T1);

                timer1Counter = timer1Latch;

                /*
                 * En modo PB7 el estado se invierte.
                 */
                if ((acr & ACR_T1_PB7) != 0) {
                    timer1Pb7 = !timer1Pb7;
                }

                return;
            }

            /*
             * One-shot.
             */
            if (!timer1TimedOut) {

                timer1TimedOut = true;

                setInterrupt(IFR_T1);

                /*
                 * PB7 vuelve a HIGH después del timeout.
                 */
                if ((acr & ACR_T1_PB7) != 0) {
                    timer1Pb7 = true;
                }
            }

            /*
             * Después del timeout el contador continúa
             * decrementando desde cero.
             */
            timer1Counter = 0xFFFF;

            return;
        }

        timer1Counter =
                (timer1Counter - 1) & 0xFFFF;
    }

    // ---------------------------------------------------------------------
    // Timer 2
    // ---------------------------------------------------------------------

    private void tickTimer2() {

        /*
         * T2 one-shot:
         *
         * el timeout solo genera IFR5 una vez.
         */
        if (timer2Counter == 0) {

            if (!timer2TimedOut) {

                timer2TimedOut = true;

                setInterrupt(IFR_T2);
            }

            /*
             * Después del timeout T2 sigue decrementando.
             */
            timer2Counter = 0xFFFF;

            return;
        }

        timer2Counter =
                (timer2Counter - 1) & 0xFFFF;
    }

    /**
     * Genera un pulso negativo en PB6.
     *
     * Este método debe utilizarse cuando T2 está configurado
     * en modo pulse-count.
     */
    public void pulsePB6() {

        if ((acr & ACR_T2_PULSE_COUNT) == 0) {
            return;
        }

        decrementTimer2FromPulse();
    }

    private void decrementTimer2FromPulse() {

        if (timer2Counter == 0) {

            /*
             * En pulse-count mode, después del timeout
             * el contador continúa decrementando y no se
             * generan nuevos IFR5 hasta una nueva carga.
             */
            if (!timer2TimedOut) {

                timer2TimedOut = true;

                setInterrupt(IFR_T2);
            }

            timer2Counter = 0xFFFF;

            return;
        }

        timer2Counter =
                (timer2Counter - 1) & 0xFFFF;
    }

    // ---------------------------------------------------------------------
    // Ports
    // ---------------------------------------------------------------------

    private int readPortA() {

        return (ora & ddra)
                | (portAInput & ~ddra);
    }

    private int readPortB() {

        int value =
                (orb & ddrb)
                | (portBInput & ~ddrb);

        /*
         * Si T1 controla PB7, el valor de PB7 procede
         * del temporizador.
         */
        if ((acr & ACR_T1_PB7) != 0 &&
            (ddrb & 0x80) != 0) {

            if (timer1Pb7) {
                value |= 0x80;
            } else {
                value &= 0x7F;
            }
        }

        return value;
    }

    /**
     * Establece las entradas externas de Port A.
     */
    public void setPortAInput(int value) {

        portAInput = value & 0xFF;
    }

    /**
     * Establece las entradas externas de Port B.
     *
     * Si T2 está en modo pulse-count, detectamos aquí
     * el flanco descendente de PB6.
     */
    public void setPortBInput(int value) {

        value &= 0xFF;

        boolean newPb6State =
                (value & 0x40) != 0;

        /*
         * Flanco descendente:
         *
         * 1 -> 0
         */
        if (pb6State && !newPb6State) {

            if ((acr & ACR_T2_PULSE_COUNT) != 0) {
                decrementTimer2FromPulse();
            }
        }

        pb6State = newPb6State;
        portBInput = value;
    }

    // ---------------------------------------------------------------------
    // Port outputs
    // ---------------------------------------------------------------------

    public int getPortAOutput() {

        return ora & ddra;
    }

    public int getPortBOutput() {

        int value = orb & ddrb;

        if ((acr & ACR_T1_PB7) != 0 &&
            (ddrb & 0x80) != 0) {

            if (timer1Pb7) {
                value |= 0x80;
            } else {
                value &= 0x7F;
            }
        }

        return value;
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    public int getDdra() {
        return ddra;
    }

    public int getDdrb() {
        return ddrb;
    }

    public int getAcr() {
        return acr;
    }

    public int getPcr() {
        return pcr;
    }

    public int getTimer1Counter() {
        return timer1Counter;
    }

    public int getTimer1Latch() {
        return timer1Latch;
    }

    public int getTimer2Counter() {
        return timer2Counter;
    }

    public int getTimer2Latch() {
        return timer2Latch;
    }

    public int getIfr() {
        return ifr & 0x7F;
    }

    public int getIer() {
        return ier & 0x7F;
    }

    // ---------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------

    public void reset() {

        orb = 0;
        ora = 0;

        ddrb = 0;
        ddra = 0;

        timer1Counter = 0;
        timer1Latch = 0;
        timer1TimedOut = false;
        timer1Pb7 = true;

        timer2Counter = 0;
        timer2Latch = 0;
        timer2TimedOut = false;

        shiftRegister = 0;

        acr = 0;
        pcr = 0;

        ifr = 0;
        ier = 0;

        portAInput = 0xFF;
        portBInput = 0xFF;

        pb6State = true;

        updateIrq();
    }
}