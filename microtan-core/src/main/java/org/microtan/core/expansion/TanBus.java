//            TANBUS
//               │
//  ┌────────────┼─────────────┐
//  │            │             │
// Microtan    TANEX        TANRAM
//  │            │             │
//  └────────────┼─────────────┘
//               │
//              Bus


package org.microtan.core.expansion;

import java.util.ArrayList;
import java.util.List;

import org.microtan.core.bus.Bus;

public class TanBus {

    private final Bus bus = new Bus();

    private final List<ExpansionBoard> boards = new ArrayList<>();

    public void addBoard(ExpansionBoard board) {
        board.install(bus);
        boards.add(board);
    }

    public Bus getBus() {
        return bus;
    }

    public void reset() {
        boards.forEach(ExpansionBoard::reset);
    }

    public void tick() {
        boards.forEach(ExpansionBoard::tick);
    }
}