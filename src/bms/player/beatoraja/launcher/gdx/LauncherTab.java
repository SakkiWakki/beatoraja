package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.PlayerConfig;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import java.util.ResourceBundle;

/**
 * Base interface for launcher configuration tabs.
 * Each tab builds its Scene2D widget tree and provides
 * update/commit methods for data binding.
 */
public interface LauncherTab {

    /** Build and return the root Actor for this tab's content. */
    Actor build(Skin skin, ResourceBundle bundle);

    /** Load values from Config into widgets. */
    default void update(Config config) {}

    /** Load values from PlayerConfig into widgets. */
    default void updatePlayer(PlayerConfig player) {}

    /** Write widget values back to Config. */
    default void commit(Config config) {}

    /** Write widget values back to PlayerConfig. */
    default void commitPlayer(PlayerConfig player) {}
}
