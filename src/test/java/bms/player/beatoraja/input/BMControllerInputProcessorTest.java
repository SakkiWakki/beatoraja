package bms.player.beatoraja.input;

import bms.player.beatoraja.PlayModeConfig.ControllerConfig;
import bms.player.beatoraja.input.BMControllerInputProcessor.BMKeys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BMControllerInputProcessorTest {

    private static class StubController implements JoystickState {
        private final boolean[] buttons = new boolean[32];
        private final float[] axes = new float[8];
        private final String name;

        StubController(String name) {
            this.name = name;
        }

        void setButton(int index, boolean pressed) { buttons[index] = pressed; }
        void setAxis(int index, float value) { axes[index] = value; }

        @Override public boolean getButton(int buttonCode) {
            return buttonCode >= 0 && buttonCode < buttons.length && buttons[buttonCode];
        }
        @Override public float getAxis(int axisCode) {
            return axisCode >= 0 && axisCode < axes.length ? axes[axisCode] : 0;
        }
        @Override public String getName() { return name; }
    }

    private StubController controller;
    private BMSPlayerInputProcessor inputProcessor;

    @BeforeEach
    void setUp() {
        controller = new StubController("TestController");
        inputProcessor = new BMSPlayerInputProcessor();
    }

    private BMControllerInputProcessor createProcessor(ControllerConfig config) {
        BMControllerInputProcessor proc = new BMControllerInputProcessor(
                inputProcessor, "TestController", controller, config);
        proc.setEnable(true);
        proc.clear();
        return proc;
    }

    private ControllerConfig simpleConfig(int[] keys, int start, int select) {
        return new ControllerConfig(keys, start, select);
    }

    // Button press detection

    @Test
    void buttonPressDetected() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1}, BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_1, true);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(0), "Key 0 should be pressed after BUTTON_1 press");
    }

    @Test
    void buttonReleaseDetected() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1}, BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_1, true);
        proc.poll(0);
        assertTrue(inputProcessor.getKeyState(0));

        controller.setButton(BMKeys.BUTTON_1, false);
        proc.poll(20000);

        assertFalse(inputProcessor.getKeyState(0), "Key 0 should be released");
    }

    @Test
    void startButtonPropagated() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1}, BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_9, true);
        proc.poll(0);

        assertTrue(inputProcessor.startPressed(), "Start should be pressed");
    }

    @Test
    void selectButtonPropagated() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1}, BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_10, true);
        proc.poll(0);

        assertTrue(inputProcessor.isSelectPressed(), "Select should be pressed");
    }

    // Debouncing

    @Test
    void debounceIgnoresRapidToggle() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1}, BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setDuration(16);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_1, true);
        proc.poll(0);
        assertTrue(inputProcessor.getKeyState(0));

        controller.setButton(BMKeys.BUTTON_1, false);
        proc.poll(10000);

        assertTrue(inputProcessor.getKeyState(0),
                "Release within debounce window should be ignored");
    }

    @Test
    void debounceAllowsAfterDuration() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1}, BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setDuration(16);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_1, true);
        proc.poll(0);
        assertTrue(inputProcessor.getKeyState(0));

        controller.setButton(BMKeys.BUTTON_1, false);
        proc.poll(17000);

        assertFalse(inputProcessor.getKeyState(0),
                "Release after debounce window should be accepted");
    }

    // Multiple keys

    @Test
    void multipleSimultaneousButtons() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1, BMKeys.BUTTON_2, BMKeys.BUTTON_3},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_1, true);
        controller.setButton(BMKeys.BUTTON_3, true);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(0), "Key 0 should be pressed");
        assertFalse(inputProcessor.getKeyState(1), "Key 1 should not be pressed");
        assertTrue(inputProcessor.getKeyState(2), "Key 2 should be pressed");
    }

    // JKOC hack (axis-to-button for D-pad)

    @Test
    void jkocHackAxisPositive() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(true);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.95f);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(0), "AXIS1_PLUS should trigger via JKOC with axis0 > 0.9");
        assertFalse(inputProcessor.getKeyState(1), "AXIS1_MINUS should not trigger");
    }

    @Test
    void jkocHackAxisNegative() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(true);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, -0.95f);
        proc.poll(0);

        assertFalse(inputProcessor.getKeyState(0), "AXIS1_PLUS should not trigger");
        assertTrue(inputProcessor.getKeyState(1), "AXIS1_MINUS should trigger via JKOC with axis0 < -0.9");
    }

    @Test
    void jkocHackAlsoReadsAxis3() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(true);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        controller.setAxis(3, 0.95f);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(0),
                "JKOC should detect axis 3 > 0.9 as AXIS1_PLUS");
    }

    @Test
    void jkocHackDeadzone() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(true);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.5f);
        proc.poll(0);

        assertFalse(inputProcessor.getKeyState(0), "0.5 should be in JKOC deadzone");
        assertFalse(inputProcessor.getKeyState(1), "0.5 should be in JKOC deadzone");
    }

    // Non-JKOC axis threshold (no analog scratch)

    @Test
    void axisThresholdWithoutAnalogScratch() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(false);
        config.setAnalogScratch(false);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.95f);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(0),
                "AXIS1_PLUS should trigger at > 0.9 without analog scratch");
        assertFalse(inputProcessor.getKeyState(1));
    }

    // Analog scratch algorithm V2

    @Test
    void analogScratchV2DetectsRotation() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_2);
        config.setAnalogScratchThreshold(100);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);

        controller.setAxis(0, 0.02f);
        proc.poll(1000);

        controller.setAxis(0, 0.04f);
        proc.poll(2000);

        assertTrue(inputProcessor.getKeyState(0),
                "Analog scratch V2 should detect positive rotation after 2 ticks");
        assertFalse(inputProcessor.getKeyState(1),
                "Negative direction should not be active during positive rotation");
    }

    @Test
    void analogScratchV2StopsAfterThreshold() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_2);
        config.setAnalogScratchThreshold(5);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);
        controller.setAxis(0, 0.02f);
        proc.poll(1000);
        controller.setAxis(0, 0.04f);
        proc.poll(2000);

        for (int i = 0; i < 15; i++) {
            proc.poll(3000 + i * 1000);
        }

        assertFalse(inputProcessor.getKeyState(0),
                "Scratch should deactivate after exceeding 2*threshold polls without movement");
    }

    // Analog scratch algorithm V1

    @Test
    void analogScratchV1DetectsRotation() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_1);
        config.setAnalogScratchThreshold(100);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);

        controller.setAxis(0, 0.1f);
        proc.poll(1000);

        assertTrue(inputProcessor.getKeyState(0),
                "Analog scratch V1 should detect positive rotation");
    }

    @Test
    void analogScratchV1StopsAfterThreshold() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_1);
        config.setAnalogScratchThreshold(5);
        config.setDuration(0);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);
        controller.setAxis(0, 0.1f);
        proc.poll(1000);
        assertTrue(inputProcessor.getKeyState(0));

        for (int i = 0; i < 10; i++) {
            proc.poll(2000 + i * 1000);
        }

        assertFalse(inputProcessor.getKeyState(0),
                "V1 scratch should deactivate after threshold polls without movement");
    }

    // computeAnalogDiff utility

    @Test
    void computeAnalogDiffSmallPositive() {
        int diff = BMControllerInputProcessor.computeAnalogDiff(0.0f, 0.009f);
        assertEquals(1, diff, "One tick positive");
    }

    @Test
    void computeAnalogDiffSmallNegative() {
        int diff = BMControllerInputProcessor.computeAnalogDiff(0.0f, -0.009f);
        assertEquals(-1, diff, "One tick negative");
    }

    @Test
    void computeAnalogDiffNoMovement() {
        int diff = BMControllerInputProcessor.computeAnalogDiff(0.5f, 0.5f);
        assertEquals(0, diff, "No movement should be 0 ticks");
    }

    @Test
    void computeAnalogDiffWraparound() {
        int diff = BMControllerInputProcessor.computeAnalogDiff(0.99f, -0.99f);
        assertTrue(diff != 0, "Wraparound should detect movement");
    }

    // lastPressedButton tracking

    @Test
    void lastPressedButtonTracked() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1, BMKeys.BUTTON_5},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_5, true);
        proc.poll(0);

        assertEquals(BMKeys.BUTTON_5, proc.getLastPressedButton(),
                "Last pressed button should be BUTTON_5");
    }

    // clear() resets state

    @Test
    void clearResetsLastPressedButton() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_1, true);
        proc.poll(0);

        proc.clear();
        assertEquals(-1, proc.getLastPressedButton(),
                "clear() should reset lastPressedButton to -1");
    }

    // Disabled controller

    @Test
    void disabledControllerIgnoresInput() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.BUTTON_1},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);
        proc.setEnable(false);

        controller.setButton(BMKeys.BUTTON_1, true);
        proc.poll(0);

        assertFalse(inputProcessor.getKeyState(0),
                "Disabled controller should not propagate button presses");
    }

    // BMKeys.toString

    @Test
    void bmKeysToStringReturnsButtonNames() {
        assertEquals("BUTTON 1", BMKeys.toString(BMKeys.BUTTON_1));
        assertEquals("UP (AXIS 1 +)", BMKeys.toString(BMKeys.AXIS1_PLUS));
        assertEquals("Unknown", BMKeys.toString(-1));
        assertEquals("Unknown", BMKeys.toString(BMKeys.MAXID));
    }

    // Negative/out-of-range key assignments

    @Test
    void negativeKeyAssignmentIgnored() {
        ControllerConfig config = simpleConfig(
                new int[]{-1, BMKeys.BUTTON_1},
                -1, -1);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_1, true);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(1), "Valid key assignment should still work");
        assertFalse(inputProcessor.startPressed());
        assertFalse(inputProcessor.isSelectPressed());
    }

    // Regression: scratch direction reversal preserves exact V1 behavior

    @Test
    void analogScratchV1DirectionReversal() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_1);
        config.setAnalogScratchThreshold(100);
        config.setDuration(0);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);
        controller.setAxis(0, 0.1f);
        proc.poll(1000);
        assertTrue(inputProcessor.getKeyState(0), "Should detect positive scratch");
        assertFalse(inputProcessor.getKeyState(1), "Negative should be off");

        controller.setAxis(0, 0.0f);
        proc.poll(2000);
        assertTrue(inputProcessor.getKeyState(1), "Reversing should activate negative");
        assertFalse(inputProcessor.getKeyState(0), "Positive should deactivate on reversal");
    }

    // Regression: scratch direction reversal preserves exact V2 behavior

    @Test
    void analogScratchV2DirectionReversal() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_2);
        config.setAnalogScratchThreshold(100);
        config.setDuration(0);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);
        controller.setAxis(0, 0.02f);
        proc.poll(1000);
        controller.setAxis(0, 0.04f);
        proc.poll(2000);
        assertTrue(inputProcessor.getKeyState(0), "V2 positive scratch active");

        // Reverse direction: V2 deactivates scratch on reversal and resets tick counter
        controller.setAxis(0, 0.02f);
        proc.poll(3000);
        assertFalse(inputProcessor.getKeyState(0), "V2 should deactivate on direction change");
    }

    // Regression: V2 requires 2 ticks to activate (not 1)

    @Test
    void analogScratchV2RequiresTwoTicks() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_2);
        config.setAnalogScratchThreshold(100);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);
        controller.setAxis(0, 0.009f);
        proc.poll(1000);

        assertFalse(inputProcessor.getKeyState(0),
                "V2 should not activate after only 1 tick");
    }

    // Regression: V1 activates on any single movement (unlike V2)

    @Test
    void analogScratchV1ActivatesOnSingleMovement() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_1);
        config.setAnalogScratchThreshold(100);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.5f);
        proc.poll(0);
        controller.setAxis(0, 0.51f);
        proc.poll(1000);

        assertTrue(inputProcessor.getKeyState(0),
                "V1 should activate on any movement, not requiring 2 ticks");
    }

    // Regression: turntable wraparound (-1 to +1 boundary)

    @Test
    void analogScratchV1Wraparound() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_1);
        config.setAnalogScratchThreshold(100);
        config.setDuration(0);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.99f);
        proc.poll(0);
        controller.setAxis(0, -0.99f);
        proc.poll(1000);

        // Small positive wraparound: 0.99 -> -0.99 wraps through +1.0
        boolean posActive = inputProcessor.getKeyState(0);
        boolean negActive = inputProcessor.getKeyState(1);
        assertTrue(posActive || negActive,
                "Wraparound at boundary should still detect movement");
    }

    // Regression: V2 tick counting uses computeAnalogDiff

    @Test
    void analogScratchV2LargeMovementCountsMultipleTicks() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setAnalogScratch(true);
        config.setAnalogScratchMode(ControllerConfig.ANALOG_SCRATCH_VER_2);
        config.setAnalogScratchThreshold(100);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.0f);
        proc.poll(0);
        // Move 0.02 which is ~2 ticks (each tick is ~0.009)
        controller.setAxis(0, 0.02f);
        proc.poll(1000);

        assertTrue(inputProcessor.getKeyState(0),
                "V2 should activate if a single large movement provides >= 2 ticks");
    }

    // Regression: non-JKOC axis threshold is exactly 0.9

    @Test
    void axisThresholdExactBoundary() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS, BMKeys.AXIS1_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(false);
        config.setAnalogScratch(false);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.9f);
        proc.poll(0);
        assertFalse(inputProcessor.getKeyState(0),
                "Exactly 0.9 should NOT trigger (threshold is > 0.9)");

        controller.setAxis(0, 0.901f);
        proc.poll(20000);
        assertTrue(inputProcessor.getKeyState(0),
                "Just above 0.9 should trigger");
    }

    // Regression: JKOC threshold is exactly 0.9

    @Test
    void jkocThresholdExactBoundary() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS1_PLUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(true);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(0, 0.9f);
        proc.poll(0);
        assertFalse(inputProcessor.getKeyState(0),
                "JKOC: exactly 0.9 should NOT trigger (threshold is > 0.9)");
    }

    // Regression: all 32 buttons are individually addressable

    @Test
    void allButtonsAddressable() {
        int[] keys = new int[9];
        for (int i = 0; i < 9; i++) keys[i] = -1;
        keys[0] = BMKeys.BUTTON_32;
        ControllerConfig config = simpleConfig(keys, BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setButton(BMKeys.BUTTON_32, true);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(0),
                "BUTTON_32 (index 31) should be addressable");
    }

    // Regression: all 8 axes are individually addressable

    @Test
    void allAxesAddressable() {
        ControllerConfig config = simpleConfig(
                new int[]{BMKeys.AXIS8_PLUS, BMKeys.AXIS8_MINUS},
                BMKeys.BUTTON_9, BMKeys.BUTTON_10);
        config.setJKOC(false);
        config.setAnalogScratch(false);
        BMControllerInputProcessor proc = createProcessor(config);

        controller.setAxis(7, 0.95f);
        proc.poll(0);

        assertTrue(inputProcessor.getKeyState(0),
                "AXIS8_PLUS should be addressable on axis index 7");
    }
}
