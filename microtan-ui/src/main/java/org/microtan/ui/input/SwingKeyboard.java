package org.microtan.ui.input;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;

import org.microtan.core.io.keyboard.Keyboard;

public class SwingKeyboard extends KeyAdapter {

    private final Keyboard keyboard;

    public SwingKeyboard(Keyboard keyboard) {
        this.keyboard = keyboard;
    }

    public void attachTo(JComponent component) {

        component.setFocusable(true);

        component.addKeyListener(this);

        component.requestFocusInWindow();
    }

    @Override
    public void keyPressed(KeyEvent event) {

        int code = translate(event);

        if (code >= 0) {
            keyboard.keyPressed(code);
        }
    }

    private int translate(KeyEvent event) {

        switch (event.getKeyCode()) {

            case KeyEvent.VK_ENTER:
                return 0x0D;

            case KeyEvent.VK_ESCAPE:
                return 0x1B;

            case KeyEvent.VK_BACK_SPACE:
                return 0x7F;

            case KeyEvent.VK_TAB:
                return 0x09;

            case KeyEvent.VK_SPACE:
                return 0x20;

            default:
                break;
        }

        char c = event.getKeyChar();

        if (c >= 0x20 && c <= 0x7E) {

            /*
             * TANBUG espera comandos en mayúsculas.
             *
             * El teclado ASCII original genera códigos ASCII,
             * por lo que aquí normalizamos las letras minúsculas
             * para facilitar su uso desde un teclado moderno.
             */
            if (c >= 'a' && c <= 'z') {
                c = Character.toUpperCase(c);
            }

            return c;
        }

        return -1;
    }
}
