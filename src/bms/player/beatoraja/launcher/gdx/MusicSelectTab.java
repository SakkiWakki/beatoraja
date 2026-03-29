package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.Config.SongPreview;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.select.MusicSelector.ChartReplicationMode;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;

import java.util.ResourceBundle;
import java.util.stream.Stream;

public class MusicSelectTab implements LauncherTab {

    private SpinnerWidget scrolldurationlow;
    private SpinnerWidget scrolldurationhigh;
    private CheckBox analogScroll;
    private SpinnerWidget analogTicksPerScroll;
    private CheckBox folderlamp;
    private CheckBox useSongInfo;
    private CheckBox shownoexistingbar;
    private SelectBox<SongPreview> songPreview;
    private CheckBox randomselect;
    private SpinnerWidget maxsearchbar;
    private SelectBox<String> chartReplicationMode;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table grid = new Table(skin);
        grid.defaults().left().pad(6);
        grid.top().left();

        grid.add(new Label(bundle.getString("SCROLL_DURATION_LOW"), skin)).width(200);
        scrolldurationlow = new SpinnerWidget(skin, 1, 1000, 300, 1);
        grid.add(scrolldurationlow);
        grid.row();

        grid.add(new Label(bundle.getString("SCROLL_DURATION_HIGH"), skin));
        scrolldurationhigh = new SpinnerWidget(skin, 1, 1000, 50, 1);
        grid.add(scrolldurationhigh);
        grid.row();

        analogScroll = new CheckBox(" " + bundle.getString("ANALOG_SCROLL"), skin);
        grid.add(analogScroll).colspan(2);
        grid.row();

        grid.add(new Label(bundle.getString("ANALOG_TICKS_PER_SCROLL"), skin));
        analogTicksPerScroll = new SpinnerWidget(skin, 1, 100, 3, 1);
        grid.add(analogTicksPerScroll);
        grid.row();

        folderlamp = new CheckBox(" " + bundle.getString("FOLDER_LAMP"), skin);
        grid.add(folderlamp).colspan(2);
        grid.row();

        useSongInfo = new CheckBox(" " + bundle.getString("USE_SONGINFO"), skin);
        grid.add(useSongInfo).colspan(2);
        grid.row();

        shownoexistingbar = new CheckBox(" " + bundle.getString("SHOW_NO_SONG_EXISTING_BAR"), skin);
        grid.add(shownoexistingbar).colspan(2);
        grid.row();

        grid.add(new Label(bundle.getString("PLAY_PREVIEW"), skin));
        songPreview = new SelectBox<>(skin);
        songPreview.setItems(SongPreview.values());
        grid.add(songPreview).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("MAX_SEARCH_BAR_COUNT"), skin));
        maxsearchbar = new SpinnerWidget(skin, 1, 100, 10, 1);
        grid.add(maxsearchbar);
        grid.row();

        randomselect = new CheckBox(" " + bundle.getString("RANDOM_SELECT"), skin);
        grid.add(randomselect).colspan(2);
        grid.row();

        grid.add(new Label("Chart Replication Mode", skin));
        chartReplicationMode = new SelectBox<>(skin);
        chartReplicationMode.setItems(
                Stream.of(ChartReplicationMode.allMode).map(ChartReplicationMode::name).toArray(String[]::new));
        grid.add(chartReplicationMode).width(200);
        grid.row();

        ScrollPane scrollPane = new ScrollPane(grid, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    @Override
    public void update(Config config) {
        scrolldurationlow.setValue(config.getScrollDurationLow());
        scrolldurationhigh.setValue(config.getScrollDurationHigh());
        analogScroll.setChecked(config.isAnalogScroll());
        analogTicksPerScroll.setValue(config.getAnalogTicksPerScroll());
        folderlamp.setChecked(config.isFolderlamp());
        useSongInfo.setChecked(config.isUseSongInfo());
        shownoexistingbar.setChecked(config.isShowNoSongExistingBar());
        songPreview.setSelected(config.getSongPreview());
        maxsearchbar.setValue(config.getMaxSearchBarCount());
    }

    @Override
    public void updatePlayer(PlayerConfig player) {
        if (player == null) return;
        randomselect.setChecked(player.isRandomSelect());
        chartReplicationMode.setSelected(player.getChartReplicationMode());
    }

    @Override
    public void commit(Config config) {
        config.setScrollDutationLow(scrolldurationlow.getIntValue());
        config.setScrollDutationHigh(scrolldurationhigh.getIntValue());
        config.setAnalogScroll(analogScroll.isChecked());
        config.setAnalogTicksPerScroll(analogTicksPerScroll.getIntValue());
        config.setFolderlamp(folderlamp.isChecked());
        config.setUseSongInfo(useSongInfo.isChecked());
        config.setShowNoSongExistingBar(shownoexistingbar.isChecked());
        config.setSongPreview(songPreview.getSelected());
        config.setMaxSearchBarCount(maxsearchbar.getIntValue());
    }

    @Override
    public void commitPlayer(PlayerConfig player) {
        if (player == null) return;
        player.setRandomSelect(randomselect.isChecked());
        player.setChartReplicationMode(chartReplicationMode.getSelected());
    }
}
