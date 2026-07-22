package org.microtan.app;

import javax.swing.SwingUtilities;

import org.microtan.core.machine.Microtan65;
import org.microtan.ui.MainWindow;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            try {

                Microtan65 machine = new Microtan65();

                MainWindow window = new MainWindow(machine.getVideoController());

                window.setLocationRelativeTo(null);

                window.setVisible(true);

                MachineRunner runner =
                        new MachineRunner(
                                machine,
                                window);

                runner.start();

            }
            catch (Exception e) {

                e.printStackTrace();

                System.exit(1);

            }

        });

    }

}