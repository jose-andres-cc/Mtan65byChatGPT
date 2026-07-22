package org.microtan.app;

import org.microtan.core.machine.Microtan65;
import org.microtan.ui.video.FrameListener;

public class MachineRunner implements Runnable {

    /**
     * Frecuencia de la CPU (Hz).
     */
    private static final int CPU_FREQUENCY = 750_000;

    /**
     * Frecuencia de refresco de pantalla (Hz).
     */
    private static final int FRAME_RATE = 50;

    /**
     * Ciclos de CPU ejecutados por frame.
     */
    private static final int CYCLES_PER_FRAME =
            CPU_FREQUENCY / FRAME_RATE;

    /**
     * Duración de un frame (20 ms).
     */
    private static final long FRAME_TIME_NS =
            1_000_000_000L / FRAME_RATE;

    private final Microtan65 machine;

    //private final VideoPanel videoPanel;
private final FrameListener listener;

    private volatile boolean running;

    public MachineRunner(
            Microtan65 machine,
            FrameListener listener) {

        this.machine = machine;
        this.listener = listener;
    }

    public void start() {

        if (running)
            return;

        running = true;

        Thread t = new Thread(this, "MachineRunner");

        t.setDaemon(true);

        t.start();
    }

    public void stop() {

        running = false;
    }

    @Override
    public void run() {

        while (running) {

            long frameStart = System.nanoTime();

            executeFrame();

            //videoPanel.refresh();
            listener.frameCompleted();

            long elapsed = System.nanoTime() - frameStart;

            long remaining = FRAME_TIME_NS - elapsed;

            if (remaining > 0) {

                try {

                    Thread.sleep(
                            remaining / 1_000_000L,
                            (int)(remaining % 1_000_000L));

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    return;
                }
            }
        }
    }

    /**
     * Ejecuta un frame completo.
     */
    private void executeFrame() {

        int executedCycles = 0;

        while (executedCycles < CYCLES_PER_FRAME) {

            int cycles = machine.getCpu().stepWithCycles();
           

            executedCycles += cycles;

            // JAC para que compile
            //machine.getVideoController().tick(cycles);
            //listener.tick(cycles);


        }
    }

}