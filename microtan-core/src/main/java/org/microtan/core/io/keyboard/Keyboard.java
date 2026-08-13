package org.microtan.core.io.keyboard;

/**
 * Modelo del teclado ASCII del Microtan 65.
 *
 * El teclado mantiene el último carácter recibido y un flag que
 * indica que existe una tecla pendiente de procesar.
 */
public class Keyboard {

    /**
     * Último carácter recibido.
     */
    private int data;

    /**
     * Keyboard interrupt flag.
     */
    private boolean interruptFlag;

    private KeyboardInterruptListener interruptListener;


    public synchronized void setInterruptListener(
            KeyboardInterruptListener listener) {

        this.interruptListener = listener;
    }
    
    
    /**
     * Recibe un carácter desde el teclado físico/emulado.
     */
    public synchronized void keyPressed(int value) {

        KeyboardInterruptListener listener;

        synchronized (this) {

            data = value & 0x7F;
            interruptFlag = true;

            listener = interruptListener;
        }

        if (listener != null) {
            listener.keyboardInterrupt();
        }
    }

    /**
     * Devuelve el último carácter recibido.
     *
     * El bit 7 refleja el keyboard interrupt flag.
     */
    public synchronized int readData() {

        int value = data & 0x7F;

        if (interruptFlag) {
            value |= 0x80;
        }

        return value;
    }

    /**
     * Limpia el keyboard interrupt flag.
     *
     * Corresponde a una escritura en BFF0.
     */
    public synchronized void clearInterruptFlag() {

        interruptFlag = false;
    }

    public synchronized boolean isInterruptPending() {

        return interruptFlag;
    }

    public synchronized int getData() {

        return data & 0x7F;
    }

    public synchronized void reset() {

        data = 0;
        interruptFlag = false;
    }

    
}
