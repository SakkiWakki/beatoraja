package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.IRConfig;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.ir.IRConnectionManager;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class IRTab implements LauncherTab {

    private SelectBox<String> irname;
    private Label irhome;
    private TextField iruserid;
    private TextField irpassword;
    private SelectBox<String> irsend;
    private CheckBox importrival;
    private CheckBox importscore;
    private TextButton primarybutton;

    private final Map<String, IRConfig> irmap = new HashMap<>();
    private String primary;
    private IRConfig currentir;
    private PlayerConfig player;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table grid = new Table(skin);
        grid.defaults().left().pad(6);
        grid.top().left();

        grid.add(new Label("IR Connection", skin, "large")).colspan(2).padBottom(8);
        grid.row();

        grid.add(new Label("IR Name", skin)).width(150);
        irname = new SelectBox<>(skin);
        irname.setItems(IRConnectionManager.getAllAvailableIRConnectionName());
        irname.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateIRConnection();
            }
        });
        grid.add(irname).width(200);
        grid.row();

        primarybutton = new TextButton("Set as Primary", skin);
        primarybutton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                primary = irname.getSelected();
                updateIRConnection();
            }
        });
        grid.add(primarybutton).colspan(2);
        grid.row();

        grid.add(new Label("Home", skin));
        irhome = new Label("", skin);
        grid.add(irhome);
        grid.row();

        grid.add(new Label("User ID", skin));
        iruserid = new TextField("", skin);
        grid.add(iruserid).width(200);
        grid.row();

        grid.add(new Label("Password", skin));
        irpassword = new TextField("", skin);
        irpassword.setPasswordCharacter('*');
        irpassword.setPasswordMode(true);
        grid.add(irpassword).width(200);
        grid.row();

        grid.add(new Label("Send", skin));
        irsend = new SelectBox<>(skin);
        irsend.setItems(bundle.getString("IR_SEND_ALWAYS"),
                bundle.getString("IR_SEND_FINISH"),
                bundle.getString("IR_SEND_UPDATE"));
        grid.add(irsend).width(200);
        grid.row();

        importscore = new CheckBox(" Import Score", skin);
        grid.add(importscore).colspan(2);
        grid.row();

        importrival = new CheckBox(" Import Rival", skin);
        grid.add(importrival).colspan(2);
        grid.row();

        ScrollPane scrollPane = new ScrollPane(grid, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    private void updateIRConnection() {
        if (currentir != null) {
            currentir.setUserid(iruserid.getText());
            currentir.setPassword(irpassword.getText());
            currentir.setIrsend(irsend.getSelectedIndex());
            currentir.setImportscore(importscore.isChecked());
            currentir.setImportrival(importrival.isChecked());
        }

        String name = irname.getSelected();
        String homeurl = IRConnectionManager.getHomeURL(name);
        irhome.setText(homeurl != null ? homeurl : "");

        if (!irmap.containsKey(name)) {
            IRConfig ir = new IRConfig();
            ir.setIrname(name);
            irmap.put(name, ir);
        }
        currentir = irmap.get(name);
        iruserid.setText(currentir.getUserid());
        irpassword.setText(currentir.getPassword());
        irsend.setSelectedIndex(currentir.getIrsend());
        importscore.setChecked(currentir.isImportscore());
        importrival.setChecked(currentir.isImportrival());

        primarybutton.setVisible(!(primary != null && name.equals(primary)));
    }

    @Override
    public void updatePlayer(PlayerConfig player) {
        this.player = player;
        irmap.clear();

        for (IRConfig ir : player.getIrconfig()) {
            irmap.put(ir.getIrname(), ir);
        }

        primary = player.getIrconfig().length > 0 ? player.getIrconfig()[0].getIrname() : null;
        String[] names = IRConnectionManager.getAllAvailableIRConnectionName();
        if (primary != null) {
            boolean found = false;
            for (String n : names) {
                if (n.equals(primary)) { found = true; break; }
            }
            if (!found) primary = names.length > 0 ? names[0] : null;
        }
        if (primary != null) {
            irname.setSelected(primary);
        }
        updateIRConnection();
    }

    @Override
    public void commitPlayer(PlayerConfig player) {
        updateIRConnection();

        java.util.List<IRConfig> irlist = new java.util.ArrayList<>();
        String[] names = IRConnectionManager.getAllAvailableIRConnectionName();
        for (String s : names) {
            IRConfig ir = irmap.get(s);
            if (ir != null && ir.getUserid().length() > 0) {
                if (s.equals(primary)) {
                    irlist.add(0, ir);
                } else {
                    irlist.add(ir);
                }
            }
        }
        player.setIrconfig(irlist.toArray(new IRConfig[0]));
    }
}
