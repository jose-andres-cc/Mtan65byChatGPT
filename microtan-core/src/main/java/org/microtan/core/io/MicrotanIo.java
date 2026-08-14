package org.microtan.core.io;

import org.microtan.core.io.keyboard.Keyboard;
import org.microtan.core.bus.MemoryDevice;
import org.microtan.core.io.via.VIA6522;

/**
 * Dispositivos de E/S propios de la placa Microtan 65.
 *
 * Dirección dentro de la página BC00-BFFF:
 *
 * BFF0 - lectura: graphics on
 *        escritura: clear keyboard interrupt flag
 *
 * BFF1 - delayed NMI
 *
 * BFF2 - keyboard/keypad write
 *
 * BFF3 - lectura: keyboard data
 *        escritura: graphics off
 * 
 * A futuro:
 * BC00-BFFF
    │
    └── IoPage
         ├── BFC0-BFCF → VIA #1
         ├── BFD0-BFD3 → Serial
         ├── BFE0-BFEF → VIA #2
         └── BFF0-BFF3 → Microtan I/O
 * 
 * 
 * 
 */

/**
 * Página de E/S del Microtan:
 *
 * BC00-BFFF
 *
 * BFC0-BFCF -> VIA 1
 * BFD0-BFD3 -> Serial (futuro)
 * BFE0-BFEF -> VIA 2 (futuro)
 * BFF0-BFF3 -> Microtan I/O / teclado
 */

public class MicrotanIo implements MemoryDevice {

    // private static final int BFF0 = 0x3F0;
    // private static final int BFF1 = 0x3F1;
    // private static final int BFF2 = 0x3F2;
    // private static final int BFF3 = 0x3F3;

    private final Keyboard keyboard;

    private static final int VIA1_BASE = 0x3C0;
    private static final int VIA2_BASE = 0x3E0;

    private static final int KEYBOARD_BASE = 0x3F0;

    private final VIA6522 via1;
    // duplicado private final Keyboard keyboard;


    public MicrotanIo(
            VIA6522 via1,
            Keyboard keyboard) {
        this.keyboard = keyboard;
        this.via1 = via1;
    }

    @Override
    public int read(int address) {

        address &= 0x03FF;

        if (address >= VIA1_BASE &&
            address < VIA1_BASE + 0x10) {

            return via1.read(address - VIA1_BASE);
        }

        if (address >= KEYBOARD_BASE &&
            address < KEYBOARD_BASE + 0x10) {

            return readKeyboardIo(
                    address - KEYBOARD_BASE);
        }

        /*
         * Segunda VIA todavía no instalada.
         */
        if (address >= VIA2_BASE &&
            address < VIA2_BASE + 0x10) {

            return 0xFF;
        }

        return 0xFF;
    }

    @Override
    public void write(int address, int value) {

        address &= 0x03FF;

        if (address >= VIA1_BASE &&
            address < VIA1_BASE + 0x10) {

            via1.write(
                    address - VIA1_BASE,
                    value);

            return;
        }

        if (address >= KEYBOARD_BASE &&
            address < KEYBOARD_BASE + 0x10) {

            writeKeyboardIo(
                    address - KEYBOARD_BASE,
                    value);

            return;
        }
    }

    private int readKeyboardIo(int address) {

        switch (address) {

            case 0x00:
                // BFF0: graphics ON
                return 0x00;

            case 0x03:
                // BFF3: keyboard data
                return keyboard.readData();

            default:
                return 0xFF;
        }
    }

    private void writeKeyboardIo(
            int address,
            int value) {

        switch (address) {

            case 0x00:
                // BFF0: clear keyboard interrupt
                keyboard.clearInterruptFlag();
                break;

            case 0x01:
                // BFF1: delayed NMI
                break;

            case 0x02:
                // BFF2: hexadecimal keypad
                break;

            case 0x03:
                // BFF3: graphics OFF
                break;

            default:
                break;
        }
    }

    @Override
    public int size() {
        return 0x400; // 1024;
    }
}