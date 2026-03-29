# beatoraja (Unofficial Linux/Wayland Fork)

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

Codename beatoraja is a Cross-platform rhythm game based on Java and libGDX.
It works on Windows, Mac OS, and Linux.

# Features
- 3 types of Long Note mode : Long Notes, Charge Notes, Hell Charge Notes, and Back Spin Scratch like IIDX
- show note timing duration (like IIDX green number), judge details (fast/slow or +-ms)
- 8 types of groove gauge (ex. assist-easy, ex-hard, ex-grade)
- 11 types of clear lamp (ex. assist, light-assist, ex-hard, perfect, and max)
- real-time play speed controller (x0.25 - x4.0. auto play mode, replay mode only)
- various assist options : legacy note, expand judge, bpm guide, and no mine
- pms judge (max 1 miss / 1 notes, combo is reset when miss)
- support bmson 0.2.1, 1.0.0
- practice mode
- import difficulty table folder, create course with various constraint (mirror/random OK, no hispeed, and so on)
- import LunaticRave2 skin (now working in progress. not supporting DirectXArchive(.dxa) and DirectDrawSurface(.dds) file)
- import LunaticRave2 scores (clear lamp, score. not including score verifier like scorehash)

# System Requirement
- Java 26+ 64bit
- OpenGL 3.0+
- LWJGL 3.4.1 (bundled via Gradle)

# How To Use

> java -jar beatoraja.jar -(a|p|r1|r2|r3|r4|s) [BMS path]

- options
  - a : autoplay
  - p : practice
  - r1-r4 : start replay data 1-4
  - s : skip configuration

beatoraja uses a large amount of heap memory. So it is recommended that you use options of extending heap memory : e.g. -Xms1g -Xmx4g.

On JRE 32bit, maximum heap memory size is limited to 1.4G-1.6G. See http://www.oracle.com/technetwork/java/hotspotfaq-138619.html#gc_heap_32bit

**Don't use this application for playing copyrighted contents.**

# License
- GNU General Public License v3
