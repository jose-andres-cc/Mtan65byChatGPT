package org.microtan.app;

import org.microtan.core.machine.Microtan65;
import org.microtan.ui.MainWindow;

public class Main {

    public static void main(String[] args) throws Exception {

        Microtan65 machine = new Microtan65();

        MainWindow window = new MainWindow(machine);

        machine.start();

        window.show();

    }

}