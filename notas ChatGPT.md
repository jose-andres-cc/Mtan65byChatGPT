proyecto multi-módulo Maven con esta estructura:

microtan65/
│
├── pom.xml                  <-- Parent
│
├── microtan-core/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   └── org/microtan/core/
│       │       ├── bus/
│       │       ├── cpu/
│       │       ├── memory/
│       │       ├── io/
│       │       ├── expansion/
│       │       ├── machine/
│       │       └── util/
│       │       └── video/
│       │
│       └── test/java/
│
├── microtan-ui/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   └── org/microtan/ui/
│       │       ├── video/
│       │       ├── keyboard/
│       │       └── MainWindow.java
│       │
│       └── resources/
│
├── microtan-app/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       │   └── org/microtan/app/
│       │       └── Main.java
│       │
│       └── resources/
│
├── roms/
│       TANBUG.BIN
│       CHARSET.BIN
│
└── docs/



Dependencias: El core nunca depende de Swing.

              +-----------------+
              | microtan-app    |
              +--------+--------+
                       |
          +------------+------------+
          |                         |
          v                         v
+-------------------+     +-------------------+
| microtan-ui       |     | microtan-core     |
+-------------------+     +-------------------+

VideoPanel
----------
VideoController
        │
        ▼
BufferedImage
        │
        ▼
VideoPanel

En detalle
----------
                  microtan-core
                  --------------

RAM ----+
         |
         v
VideoController
         |
CharacterROM
         |
         v
FrameBuffer
         |
=============================
          frontera
=============================
         |
         v
                 microtan-ui
                 ------------

VideoPanel
Swing
