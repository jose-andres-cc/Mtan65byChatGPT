package org.microtan.ui;

import javax.swing.JFrame;

import org.microtan.core.machine.Microtan65;

public class MainWindow {

    private final JFrame frame = new JFrame("Microtan 65");

    private final Microtan65 machine;

    public MainWindow(Microtan65 machine) {

        this.machine = machine;

    }

    public void show() {

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.pack();

        frame.setVisible(true);

    }

}