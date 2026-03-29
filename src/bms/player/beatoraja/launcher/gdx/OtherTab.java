package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.Config;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;

import java.util.ResourceBundle;

public class OtherTab implements LauncherTab {

    private CheckBox usecim;
    private CheckBox discord;
    private CheckBox clipboardScreenshot;
    private CheckBox enableIpfs;
    private TextField ipfsurl;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table grid = new Table(skin);
        grid.defaults().left().pad(6);
        grid.top().left();

        usecim = new CheckBox(" " + bundle.getString("CACHE_SKIN_IMAGE"), skin);
        grid.add(usecim).colspan(2);
        grid.row();

        discord = new CheckBox(" " + bundle.getString("DISCORD_RPC"), skin);
        grid.add(discord).colspan(2);
        grid.row();

        clipboardScreenshot = new CheckBox(" " + bundle.getString("CLIPBOARD_SCREENSHOT"), skin);
        grid.add(clipboardScreenshot).colspan(2);
        grid.row();

        // TODO: LR2 score import button (needs JFileChooser + database access)

        enableIpfs = new CheckBox(" " + bundle.getString("ENABLE_IPFS"), skin);
        grid.add(enableIpfs).colspan(2).padTop(10);
        grid.row();

        grid.add(new Label("IPFS URL", skin)).width(80);
        ipfsurl = new TextField("", skin);
        grid.add(ipfsurl).width(300);
        grid.row();

        ScrollPane scrollPane = new ScrollPane(grid, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    @Override
    public void update(Config config) {
        usecim.setChecked(config.isCacheSkinImage());
        discord.setChecked(config.isUseDiscordRPC());
        clipboardScreenshot.setChecked(config.isSetClipboardWhenScreenshot());
        enableIpfs.setChecked(config.isEnableIpfs());
        ipfsurl.setText(config.getIpfsUrl());
    }

    @Override
    public void commit(Config config) {
        config.setCacheSkinImage(usecim.isChecked());
        config.setUseDiscordRPC(discord.isChecked());
        config.setClipboardWhenScreenshot(clipboardScreenshot.isChecked());
        config.setEnableIpfs(enableIpfs.isChecked());
        config.setIpfsUrl(ipfsurl.getText());
    }
}
