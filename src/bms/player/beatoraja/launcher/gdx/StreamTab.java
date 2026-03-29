package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.PlayerConfig;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;

import java.util.ResourceBundle;

public class StreamTab implements LauncherTab {

    private CheckBox enableRequest;
    private CheckBox notifyRequest;
    private SpinnerWidget maxRequestCount;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table grid = new Table(skin);
        grid.defaults().left().pad(6);
        grid.top().left();

        enableRequest = new CheckBox(" " + bundle.getString("ENABLE_REQUEST"), skin);
        grid.add(enableRequest).colspan(2);
        grid.row();

        notifyRequest = new CheckBox(" " + bundle.getString("NOTIFY_REQUEST"), skin);
        grid.add(notifyRequest).colspan(2);
        grid.row();

        grid.add(new Label(bundle.getString("MAX_REQUEST_COUNT"), skin)).width(200);
        maxRequestCount = new SpinnerWidget(skin, 0, 100, 10, 1);
        grid.add(maxRequestCount);
        grid.row();

        ScrollPane scrollPane = new ScrollPane(grid, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    @Override
    public void updatePlayer(PlayerConfig player) {
        if (player == null) return;
        enableRequest.setChecked(player.getRequestEnable());
        notifyRequest.setChecked(player.getRequestNotify());
        maxRequestCount.setValue(player.getMaxRequestCount());
    }

    @Override
    public void commitPlayer(PlayerConfig player) {
        if (player == null) return;
        player.setRequestEnable(enableRequest.isChecked());
        player.setRequestNotify(notifyRequest.isChecked());
        player.setMaxRequestCount(maxRequestCount.getIntValue());
    }
}
