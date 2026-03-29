package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.Config.DisplayMode;
import bms.player.beatoraja.MainLoader;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.Resolution;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;

import java.util.ResourceBundle;

public class VideoTab implements LauncherTab {

    private SelectBox<DisplayMode> displayMode;
    private SelectBox<Resolution> resolution;
    private CheckBox vSync;
    private SpinnerWidget maxFps;
    private SelectBox<String> bgaOp;
    private SpinnerWidget missLayerTime;
    private SelectBox<String> bgaExpand;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table grid = new Table(skin);
        grid.defaults().left().pad(6);
        grid.top().left();

        // Display section header
        grid.add(new Label(bundle.getString("DISPLAY"), skin, "large")).colspan(2).padBottom(8);
        grid.row();

        grid.add(new Label(bundle.getString("DISPLAYMODE"), skin)).width(200);
        displayMode = new SelectBox<>(skin);
        displayMode.setItems(DisplayMode.values());
        displayMode.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateResolutions();
            }
        });
        grid.add(displayMode).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("RESOLUTION"), skin)).width(200);
        resolution = new SelectBox<>(skin);
        grid.add(resolution).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("VSYNC"), skin));
        vSync = new CheckBox("", skin);
        grid.add(vSync);
        grid.row();

        grid.add(new Label(bundle.getString("MAXFPS"), skin));
        maxFps = new SpinnerWidget(skin, 0, 1000, 60, 1);
        grid.add(maxFps);
        grid.row();

        // BGA section header
        grid.add(new Label(bundle.getString("BGA"), skin, "large")).colspan(2).padTop(12).padBottom(8);
        grid.row();

        grid.add(new Label(bundle.getString("BGA"), skin));
        bgaOp = new SelectBox<>(skin);
        bgaOp.setItems("On", "Auto", "Off");
        grid.add(bgaOp).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("MISSLAYER_DURATION"), skin));
        missLayerTime = new SpinnerWidget(skin, 0, 10000, 500, 1);
        grid.add(missLayerTime);
        grid.row();

        grid.add(new Label(bundle.getString("BGA_EXPAND"), skin));
        bgaExpand = new SelectBox<>(skin);
        bgaExpand.setItems("Full", "Keep Aspect Ratio", "Off");
        grid.add(bgaExpand).width(200);
        grid.row();

        ScrollPane scrollPane = new ScrollPane(grid, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    private void updateResolutions() {
        Resolution oldValue = resolution.getSelected();
        Array<Resolution> items = new Array<>();

        if (displayMode.getSelected() == DisplayMode.FULLSCREEN) {
            Graphics.DisplayMode[] displays = MainLoader.getAvailableDisplayMode();
            for (Resolution r : Resolution.values()) {
                for (Graphics.DisplayMode display : displays) {
                    if (display.width == r.width && display.height == r.height) {
                        items.add(r);
                        break;
                    }
                }
            }
        } else {
            Graphics.DisplayMode display = MainLoader.getDesktopDisplayMode();
            for (Resolution r : Resolution.values()) {
                if (r.width <= display.width && r.height <= display.height) {
                    items.add(r);
                }
            }
        }

        resolution.setItems(items);
        if (items.contains(oldValue, true)) {
            resolution.setSelected(oldValue);
        } else if (items.size > 0) {
            resolution.setSelectedIndex(items.size - 1);
        }
    }

    @Override
    public void update(Config config) {
        displayMode.setSelected(config.getDisplaymode());
        updateResolutions();
        resolution.setSelected(config.getResolution());
        vSync.setChecked(config.isVsync());
        bgaOp.setSelectedIndex(config.getBga());
        bgaExpand.setSelectedIndex(config.getBgaExpand());
        maxFps.setValue(config.getMaxFramePerSecond());
    }

    @Override
    public void updatePlayer(PlayerConfig player) {
        missLayerTime.setValue(player.getMisslayerDuration());
    }

    @Override
    public void commit(Config config) {
        Resolution selectedResolution = resolution.getSelected();
        config.setResolution(selectedResolution);
        config.setDisplaymode(displayMode.getSelected());
        if (selectedResolution != null) {
            config.setWindowWidth(selectedResolution.width);
            config.setWindowHeight(selectedResolution.height);
            config.setUseResolution(true);
        }
        config.setVsync(vSync.isChecked());
        config.setBga(bgaOp.getSelectedIndex());
        config.setBgaExpand(bgaExpand.getSelectedIndex());
        config.setMaxFramePerSecond(maxFps.getIntValue());
    }

    @Override
    public void commitPlayer(PlayerConfig player) {
        player.setMisslayerDuration(missLayerTime.getIntValue());
    }
}
