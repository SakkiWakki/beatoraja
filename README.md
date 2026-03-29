# beatoraja (Unofficial Fork)
This project aims to improve the QoL of beatoraja, such as speeding up import times, saving behaviour, etc. 
# TODO
- [ ] Remake launcher with human hands to avoid clanker logic

This is an **unofficial fork** of [beatoraja](https://github.com/exch-bms2/beatoraja) focused on native Linux and Wayland support.

Key changes from upstream:
- **GDX/Scene2D launcher** replacing JavaFX (eliminates JavaFX dependency)
- **Native Wayland support** via LWJGL 3.4.1 / GLFW 3.4
- **Gradle build system** replacing Ant
- **BMS charset auto-detection** (UTF-8, EUC-KR, Shift-JIS) via [forked jbms-parser](https://github.com/SakkiWakki/jbms-parser/tree/charset-autodetection)
- **NoSQL song database** replacing SQLite

For the original project, see: https://github.com/exch-bms2/beatoraja

On Wayland, you may have to manually resize. Note that there's a problem with OpenGL that causes fonts to become black rectangles after resizing. You can fix this by just traversing to another folder. 

# AI Usage
Claude was used for implementing my ideas for refactoring and optimization. Also generating coverage tests to ensure that the majority of the behaviour remains the same .

---

# System Requirement
- Java 26+ 64bit
- OpenGL 3.0+
- LWJGL 3.4.1 (bundled via Gradle)

# Building

Prerequisites:
- JDK 26+ ([Arch](https://archlinux.org/packages/extra/x86_64/jdk-openjdk/), [SDKMAN](https://sdkman.io/), or [Oracle](https://jdk.java.net/26/))
- Gradle 9+ (or use the system package manager)

```bash
git clone https://github.com/SakkiWakki/beatoraja.git
cd beatoraja
gradle build
```

This produces `build/libs/beatoraja.jar` (fat jar with all dependencies).

To run tests:
```bash
gradle test
```

# How To Run

Using the included launch script (Linux):
```bash
./beatoraja.sh
```

Or manually:
```bash
java --enable-native-access=ALL-UNNAMED -Xms1g -Xmx4g -jar build/libs/beatoraja.jar
```

Command-line options:
```
java -jar beatoraja.jar -(a|p|r1|r2|r3|r4|s) [BMS path]
```

# License
- GNU General Public License v3
