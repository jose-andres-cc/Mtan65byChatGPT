package org.microtan.core.io;

import org.microtan.core.io.keyboard.Keyboard;
import org.microtan.core.bus.MemoryDevice;

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
public class MicrotanIo implements MemoryDevice {

    private static final int BFF0 = 0x3F0;
    private static final int BFF1 = 0x3F1;
    private static final int BFF2 = 0x3F2;
    private static final int BFF3 = 0x3F3;

    private final Keyboard keyboard;

    public MicrotanIo(Keyboard keyboard) {
        this.keyboard = keyboard;
    }

    @Override
    public int read(int address) {

        switch (address & 0x03FF) {

            case BFF0:
                // Graphics ON.
                //
                // Todavía no necesitamos devolver un valor
                // específico para esta funcionalidad.
                return 0x00;

            case BFF3:
                return keyboard.readData();

            default:
                return 0xFF;
        }
    }

    @Override
    public void write(int address, int value) {

        switch (address & 0x03FF) {

            case BFF0:
                keyboard.clearInterruptFlag();
                break;

            case BFF1:
                // Delayed NMI.
                // Se implementará posteriormente.
                break;

            case BFF2:
                // Keyboard/keypad strobe.
                // Para un teclado ASCII no es necesario.
                break;

            case BFF3:
                // Graphics OFF.
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