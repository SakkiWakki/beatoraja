package bms.player.beatoraja.launcher.gdx;

import bms.model.Mode;
import bms.player.beatoraja.PlayModeConfig;
import bms.player.beatoraja.PlayModeConfig.ControllerConfig;
import bms.player.beatoraja.PlayerConfig;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.ResourceBundle;

public class InputTab implements LauncherTab {

    private enum PlayMode {
        BEAT_5K, BEAT_7K, BEAT_10K, BEAT_14K, POPN_9K, KEYBOARD_24K, KEYBOARD_24K_DOUBLE;
    }

    private SelectBox<PlayMode> inputconfig;
    private SpinnerWidget inputduration;
    private CheckBox jkocHack;
    private CheckBox mouseScratch;
    private SpinnerWidget mouseScratchTimeThreshold;
    private SpinnerWidget mouseScratchDistance;
    private SelectBox<String> mouseScratchMode;

    // Per-controller widgets (simplified: one row per controller slot)
    private Table controllerTable;
    private Skin skin;

    private PlayerConfig player;
    private PlayMode currentMode;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        this.skin = skin;
        Table root = new Table(skin);
        root.top().left().pad(8);
        root.defaults().left().pad(6);

        Table modeRow = new Table(skin);
        modeRow.add(new Label(bundle.getString("MODE"), skin)).padRight(8);
        inputconfig = new SelectBox<>(skin);
        inputconfig.setItems(PlayMode.values());
        inputconfig.setSelected(PlayMode.BEAT_7K);
        inputconfig.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                commitMode();
                loadMode(inputconfig.getSelected());
            }
        });
        modeRow.add(inputconfig).width(200);
        root.add(modeRow).colspan(2).padBottom(8);
        root.row();

        root.add(new Label(bundle.getString("MINIMUM_INPUT_DURATION"), skin)).width(200);
        inputduration = new SpinnerWidget(skin, 0, 1000, 0, 1);
        root.add(inputduration);
        root.row();

        jkocHack = new CheckBox(" JKOC", skin);
        root.add(jkocHack).colspan(2);
        root.row();

        // Controller config table
        root.add(new Label("Controllers", skin, "large")).colspan(2).padTop(12).padBottom(4);
        root.row();
        controllerTable = new Table(skin);
        root.add(controllerTable).colspan(2).growX();
        root.row();

        // Mouse scratch section
        root.add(new Label("Mouse Scratch", skin, "large")).colspan(2).padTop(12).padBottom(4);
        root.row();

        mouseScratch = new CheckBox(" Enable Mouse Scratch", skin);
        root.add(mouseScratch).colspan(2);
        root.row();

        root.add(new Label("Time Threshold", skin));
        mouseScratchTimeThreshold = new SpinnerWidget(skin, 0, 10000, 150, 1);
        root.add(mouseScratchTimeThreshold);
        root.row();

        root.add(new Label("Distance", skin));
        mouseScratchDistance = new SpinnerWidget(skin, 0, 10000, 500, 1);
        root.add(mouseScratchDistance);
        root.row();

        root.add(new Label("Mode", skin));
        mouseScratchMode = new SelectBox<>(skin);
        mouseScratchMode.setItems("Ver. 2 (Newest)", "Ver. 1 (~0.8.3)");
        root.add(mouseScratchMode).width(200);
        root.row();

        ScrollPane scrollPane = new ScrollPane(root, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    private void buildControllerRows(ControllerConfig[] controllers) {
        controllerTable.clear();
        controllerTable.defaults().pad(4);
        controllerTable.add(new Label("Slot", skin)).width(50);
        controllerTable.add(new Label("Name", skin)).width(200);
        controllerTable.add(new Label("Analog", skin)).width(70);
        controllerTable.add(new Label("Threshold", skin));
        controllerTable.add(new Label("Mode", skin)).width(120);
        controllerTable.row();

        for (int i = 0; i < controllers.length; i++) {
            ControllerConfig cc = controllers[i];
            controllerTable.add(new Label((i + 1) + "P", skin));
            controllerTable.add(new Label(cc.getName() != null ? cc.getName() : "-", skin));

            CheckBox analog = new CheckBox("", skin);
            analog.setChecked(cc.isAnalogScratch());
            analog.setName("analog_" + i);
            controllerTable.add(analog);

            SpinnerWidget threshold = new SpinnerWidget(skin, 1, 1000, cc.getAnalogScratchThreshold(), 1);
            threshold.setName("threshold_" + i);
            controllerTable.add(threshold);

            SelectBox<String> mode = new SelectBox<>(skin);
            mode.setItems("Ver. 2", "Ver. 1");
            mode.setSelectedIndex(cc.getAnalogScratchMode() == ControllerConfig.ANALOG_SCRATCH_VER_2 ? 0 : 1);
            mode.setName("mode_" + i);
            controllerTable.add(mode).width(120);
            controllerTable.row();
        }
    }

    private void loadMode(PlayMode mode) {
        this.currentMode = mode;
        if (player == null) return;

        PlayModeConfig conf = player.getPlayConfig(Mode.valueOf(mode.name()));
        inputduration.setValue(conf.getKeyboardConfig().getDuration());
        mouseScratch.setChecked(conf.getKeyboardConfig().getMouseScratchConfig().isMouseScratchEnabled());
        mouseScratchTimeThreshold.setValue(conf.getKeyboardConfig().getMouseScratchConfig().getMouseScratchTimeThreshold());
        mouseScratchDistance.setValue(conf.getKeyboardConfig().getMouseScratchConfig().getMouseScratchDistance());
        mouseScratchMode.setSelectedIndex(conf.getKeyboardConfig().getMouseScratchConfig().getMouseScratchMode());

        ControllerConfig[] controllers = conf.getController();
        if (controllers.length > 0) {
            jkocHack.setChecked(controllers[0].getJKOC());
        }
        buildControllerRows(controllers);
    }

    private void commitMode() {
        if (currentMode == null || player == null) return;

        PlayModeConfig conf = player.getPlayConfig(Mode.valueOf(currentMode.name()));
        conf.getKeyboardConfig().setDuration(inputduration.getIntValue());
        conf.getKeyboardConfig().getMouseScratchConfig().setMouseScratchEnabled(mouseScratch.isChecked());
        conf.getKeyboardConfig().getMouseScratchConfig().setMouseScratchTimeThreshold(mouseScratchTimeThreshold.getIntValue());
        conf.getKeyboardConfig().getMouseScratchConfig().setMouseScratchDistance(mouseScratchDistance.getIntValue());
        conf.getKeyboardConfig().getMouseScratchConfig().setMouseScratchMode(mouseScratchMode.getSelectedIndex());

        ControllerConfig[] controllers = conf.getController();
        for (int i = 0; i < controllers.length; i++) {
            controllers[i].setDuration(inputduration.getIntValue());
            controllers[i].setJKOC(jkocHack.isChecked());

            Actor analogActor = controllerTable.findActor("analog_" + i);
            if (analogActor instanceof CheckBox cb) {
                controllers[i].setAnalogScratch(cb.isChecked());
            }
            Actor thresholdActor = controllerTable.findActor("threshold_" + i);
            if (thresholdActor instanceof SpinnerWidget sw) {
                controllers[i].setAnalogScratchThreshold(sw.getIntValue());
            }
            Actor modeActor = controllerTable.findActor("mode_" + i);
            if (modeActor instanceof SelectBox<?> sb) {
                controllers[i].setAnalogScratchMode(
                        sb.getSelectedIndex() == 0 ? ControllerConfig.ANALOG_SCRATCH_VER_2 : ControllerConfig.ANALOG_SCRATCH_VER_1);
            }
        }
    }

    @Override
    public void updatePlayer(PlayerConfig player) {
        this.player = player;
        currentMode = null;
        inputconfig.setSelected(PlayMode.BEAT_7K);
        loadMode(PlayMode.BEAT_7K);
    }

    @Override
    public void commitPlayer(PlayerConfig player) {
        commitMode();
    }
}
