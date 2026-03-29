#!/usr/bin/env python3
"""
Creates a virtual joystick via uinput that GLFW/beatoraja will detect.
For testing.

Usage:
    sudo python3 tools/fake_joystick.py

Controls (keyboard):
    1-7     Toggle buttons 0-6 (keys)
    8       Toggle button 7 (start)
    9       Toggle button 8 (select)
    0       Toggle button 9 (extra)
    a/d     Spin turntable axis left/right
    t       Timer mode: inputs fire after 3s delay (so you can switch window)
    q       Quit
"""

import sys
import time
import select
import termios
import tty

from evdev import UInput, AbsInfo, ecodes

BUTTONS = [
    ecodes.BTN_TRIGGER,
    ecodes.BTN_THUMB,
    ecodes.BTN_THUMB2,
    ecodes.BTN_TOP,
    ecodes.BTN_TOP2,
    ecodes.BTN_PINKIE,
    ecodes.BTN_BASE,
    ecodes.BTN_BASE2,
    ecodes.BTN_BASE3,
    ecodes.BTN_BASE4,
    ecodes.BTN_BASE5,
]

AXIS_MIN = -32768
AXIS_MAX = 32767

capabilities = {
    ecodes.EV_KEY: BUTTONS,
    ecodes.EV_ABS: [
        (ecodes.ABS_X,  AbsInfo(value=0, min=AXIS_MIN, max=AXIS_MAX, fuzz=0, flat=0, resolution=0)),
        (ecodes.ABS_Y,  AbsInfo(value=0, min=AXIS_MIN, max=AXIS_MAX, fuzz=0, flat=0, resolution=0)),
        (ecodes.ABS_RX, AbsInfo(value=0, min=AXIS_MIN, max=AXIS_MAX, fuzz=0, flat=0, resolution=0)),
        (ecodes.ABS_RY, AbsInfo(value=0, min=AXIS_MIN, max=AXIS_MAX, fuzz=0, flat=0, resolution=0)),
    ],
}


def main():
    ui = UInput(capabilities, name="FakeIIDXController", vendor=0x1ccf, product=0x8048)
    print(f"Virtual joystick created: {ui.device.path}")
    print("Buttons: 1-9,0  |  Turntable: a/d  |  Timer mode: t  |  Quit: q")
    print()

    button_states = [False] * len(BUTTONS)
    turntable = 0
    timer_mode = False
    pending = []

    def do_button(idx):
        nonlocal button_states
        button_states[idx] = not button_states[idx]
        ui.write(ecodes.EV_KEY, BUTTONS[idx], 1 if button_states[idx] else 0)
        ui.syn()
        state = "ON " if button_states[idx] else "OFF"
        print(f"  Button {idx}: {state}")
        if timer_mode and button_states[idx]:
            pending.append((lambda i=idx: do_button(i), time.monotonic() + 0.5))

    def do_axis(direction):
        nonlocal turntable
        turntable = int(AXIS_MAX * 0.95) * direction
        ui.write(ecodes.EV_ABS, ecodes.ABS_X, turntable)
        ui.syn()
        print(f"  Turntable: {turntable} ({turntable/AXIS_MAX:.2f})")

    old_settings = termios.tcgetattr(sys.stdin)
    try:
        tty.setcbreak(sys.stdin.fileno())

        while True:
            now = time.monotonic()

            for entry in list(pending):
                if now >= entry[1]:
                    entry[0]()
                    pending.remove(entry)

            if select.select([sys.stdin], [], [], 0.05)[0]:
                ch = sys.stdin.read(1)

                if ch == 'q':
                    break

                if ch == 't':
                    timer_mode = not timer_mode
                    state = "ON (3s delay)" if timer_mode else "OFF (immediate)"
                    print(f"  Timer mode: {state}")
                    continue

                action = None

                key_map = {'1': 0, '2': 1, '3': 2, '4': 3, '5': 4, '6': 5, '7': 6, '8': 7, '9': 8, '0': 9}
                if ch in key_map and key_map[ch] < len(BUTTONS):
                    action = lambda i=key_map[ch]: do_button(i)

                if ch in ('a', 'd'):
                    direction = -1 if ch == 'a' else 1
                    action = lambda d=direction: do_axis(d)

                if action:
                    if timer_mode:
                        print(f"  Firing in 3s...")
                        pending.append((action, now + 3.0))
                    else:
                        action()

    finally:
        termios.tcsetattr(sys.stdin, termios.TCSADRAIN, old_settings)
        ui.close()
        print("\nVirtual joystick removed.")

if __name__ == "__main__":
    main()
