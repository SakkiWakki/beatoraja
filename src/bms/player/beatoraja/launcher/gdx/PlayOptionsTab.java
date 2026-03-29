package bms.player.beatoraja.launcher.gdx;

import bms.model.Mode;
import bms.player.beatoraja.PlayConfig;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.play.JudgeAlgorithm;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.ResourceBundle;

public class PlayOptionsTab implements LauncherTab {

    private enum PlayMode {
        BEAT_5K, BEAT_7K, BEAT_10K, BEAT_14K, POPN_9K, KEYBOARD_24K, KEYBOARD_24K_DOUBLE;
    }

    // Mode selector
    private SelectBox<PlayMode> playconfig;

    // Per-mode play config widgets
    private SpinnerWidget hispeed;
    private SpinnerWidget gvalue;
    private CheckBox enableConstant;
    private SpinnerWidget constFadeinTime;
    private SpinnerWidget hispeedmargin;
    private CheckBox hispeedautoadjust;
    private SelectBox<String> fixhispeed;
    private CheckBox enableLanecover;
    private SpinnerWidget lanecover;
    private SpinnerWidget lanecovermarginlow;
    private SpinnerWidget lanecovermarginhigh;
    private SpinnerWidget lanecoverswitchduration;
    private CheckBox enableLift;
    private SpinnerWidget lift;
    private CheckBox enableHidden;
    private SpinnerWidget hidden;
    private SelectBox<String> judgealgorithm;

    // Global player options
    private SelectBox<String> scoreop;
    private SelectBox<String> scoreop2;
    private SelectBox<String> doubleop;
    private SelectBox<String> gaugeop;
    private SelectBox<String> lntype;
    private SpinnerWidget notesdisplaytiming;
    private CheckBox notesdisplaytimingautoadjust;
    private CheckBox bpmguide;
    private SelectBox<String> gaugeautoshift;
    private SelectBox<String> bottomshiftablegauge;
    private CheckBox customjudge;
    private SpinnerWidget njudgepg;
    private SpinnerWidget njudgegr;
    private SpinnerWidget njudgegd;
    private SpinnerWidget sjudgepg;
    private SpinnerWidget sjudgegr;
    private SpinnerWidget sjudgegd;
    private SelectBox<String> minemode;
    private SelectBox<String> scrollmode;
    private SelectBox<String> longnotemode;
    private Slider longnoterate;
    private SpinnerWidget hranthresholdbpm;
    private SelectBox<String> seventoninepattern;
    private SelectBox<String> seventoninetype;
    private SpinnerWidget exitpressduration;
    private CheckBox chartpreview;
    private CheckBox guidese;
    private CheckBox windowhold;
    private SpinnerWidget extranotedepth;
    private CheckBox judgeregion;
    private CheckBox markprocessednote;
    private CheckBox showhiddennote;
    private SelectBox<String> target;
    private SelectBox<String> autosavereplay1;
    private SelectBox<String> autosavereplay2;
    private SelectBox<String> autosavereplay3;
    private SelectBox<String> autosavereplay4;

    private PlayMode currentMode = null;
    private PlayerConfig player;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table root = new Table(skin);
        root.top().left().pad(8);

        // Mode selector
        Table modeRow = new Table(skin);
        modeRow.add(new Label(bundle.getString("MODE"), skin)).padRight(8);
        playconfig = new SelectBox<>(skin);
        playconfig.setItems(PlayMode.values());
        playconfig.setSelected(PlayMode.BEAT_7K);
        playconfig.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                switchPlayConfig();
            }
        });
        modeRow.add(playconfig).width(200);
        root.add(modeRow).left().colspan(4).padBottom(8);
        root.row();

        // Per-mode config grid
        Table modeGrid = new Table(skin);
        modeGrid.defaults().left().pad(6);

        modeGrid.add(new Label(bundle.getString("HI-SPEED"), skin));
        hispeed = new SpinnerWidget(skin, 1.0f, 9.9f, 1.0f, 0.1f);
        modeGrid.add(hispeed);
        modeGrid.add(new Label(bundle.getString("GVALUE"), skin));
        gvalue = new SpinnerWidget(skin, 1, 5000, 500, 1);
        modeGrid.add(gvalue);
        modeGrid.row();

        enableLanecover = new CheckBox(" " + bundle.getString("ENABLE_LANECOVER"), skin);
        modeGrid.add(enableLanecover);
        lanecover = new SpinnerWidget(skin, 0, 1000, 100, 1);
        modeGrid.add(lanecover);
        modeGrid.add(new Label(bundle.getString("LANECOVER_MARGIN_LOW"), skin));
        lanecovermarginlow = new SpinnerWidget(skin, 0, 1000, 1, 1);
        modeGrid.add(lanecovermarginlow);
        modeGrid.row();

        modeGrid.add(new Label(bundle.getString("LANECOVER_MARGIN_HIGH"), skin));
        lanecovermarginhigh = new SpinnerWidget(skin, 0, 1000, 10, 1);
        modeGrid.add(lanecovermarginhigh);
        modeGrid.add(new Label(bundle.getString("LANECOVER_SWITCH_DURATION"), skin));
        lanecoverswitchduration = new SpinnerWidget(skin, 0, 1000000, 500, 1);
        modeGrid.add(lanecoverswitchduration);
        modeGrid.row();

        enableLift = new CheckBox(" " + bundle.getString("ENABLE_LIFT"), skin);
        modeGrid.add(enableLift);
        lift = new SpinnerWidget(skin, 0, 1000, 100, 1);
        modeGrid.add(lift);
        enableHidden = new CheckBox(" " + bundle.getString("ENABLE_HIDDEN"), skin);
        modeGrid.add(enableHidden);
        hidden = new SpinnerWidget(skin, 0, 1000, 100, 1);
        modeGrid.add(hidden);
        modeGrid.row();

        modeGrid.add(new Label(bundle.getString("NOTESDISPLAYTIMING"), skin));
        notesdisplaytiming = new SpinnerWidget(skin, PlayerConfig.JUDGETIMING_MIN, PlayerConfig.JUDGETIMING_MAX, 0, 1);
        modeGrid.add(notesdisplaytiming);
        notesdisplaytimingautoadjust = new CheckBox(" " + bundle.getString("NOTESDISPLAYTIMING_AUTOADJUST"), skin);
        modeGrid.add(notesdisplaytimingautoadjust).colspan(2);
        modeGrid.row();

        modeGrid.add(new Label(bundle.getString("HISPEED_FIX"), skin));
        fixhispeed = new SelectBox<>(skin);
        fixhispeed.setItems("OFF", "START BPM", "MAX BPM", "MAIN BPM", "MIN BPM");
        modeGrid.add(fixhispeed).width(150);
        modeGrid.add(new Label(bundle.getString("HISPEED_MARGIN"), skin));
        hispeedmargin = new SpinnerWidget(skin, 0f, 9.99f, 0.25f, 0.01f);
        modeGrid.add(hispeedmargin);
        modeGrid.row();

        hispeedautoadjust = new CheckBox(" " + bundle.getString("HISPEED_AUTO_ADJUST"), skin);
        modeGrid.add(hispeedautoadjust).colspan(4);
        modeGrid.row();

        enableConstant = new CheckBox(" Enable Constant", skin);
        modeGrid.add(enableConstant);
        constFadeinTime = new SpinnerWidget(skin, 0, 10000, 0, 1);
        modeGrid.add(constFadeinTime);
        modeGrid.row();

        // Note modifier row
        modeGrid.add(new Label(bundle.getString("NOTE_MODIFIER"), skin));
        scoreop = new SelectBox<>(skin);
        scoreop.setItems("OFF", "MIRROR", "RANDOM", "R-RANDOM", "S-RANDOM", "SPIRAL", "H-RANDOM", "ALL-SCR", "RANDOM-EX", "S-RANDOM-EX");
        modeGrid.add(scoreop).width(150);
        modeGrid.add(new Label(bundle.getString("NOTE_MODIFIER2"), skin));
        scoreop2 = new SelectBox<>(skin);
        scoreop2.setItems("OFF", "MIRROR", "RANDOM", "R-RANDOM", "S-RANDOM", "SPIRAL", "H-RANDOM", "ALL-SCR", "RANDOM-EX", "S-RANDOM-EX");
        modeGrid.add(scoreop2).width(150);
        modeGrid.row();

        modeGrid.add(new Label(bundle.getString("DOUBLE_OPTION"), skin));
        doubleop = new SelectBox<>(skin);
        doubleop.setItems("OFF", "FLIP", "BATTLE", "BATTLE AS");
        modeGrid.add(doubleop).width(150);
        modeGrid.add(new Label(bundle.getString("GAUGE_TYPE"), skin));
        gaugeop = new SelectBox<>(skin);
        gaugeop.setItems("ASSIST EASY", "EASY", "NORMAL", "HARD", "EX-HARD", "HAZARD");
        modeGrid.add(gaugeop).width(150);
        modeGrid.row();

        modeGrid.add(new Label(bundle.getString("LNTYPE"), skin));
        lntype = new SelectBox<>(skin);
        lntype.setItems("LONG NOTE", "CHARGE NOTE", "HELL CHARGE NOTE");
        modeGrid.add(lntype).width(150);
        modeGrid.row();

        String[] judgAlgNames = new String[]{
                bundle.getString("JUDGEALG_LR2"), bundle.getString("JUDGEALG_AC"),
                bundle.getString("JUDGEALG_BOTTOM_PRIORITY")};
        modeGrid.add(new Label("Judge Algorithm", skin));
        judgealgorithm = new SelectBox<>(skin);
        judgealgorithm.setItems(judgAlgNames);
        modeGrid.add(judgealgorithm).width(200);
        modeGrid.row();

        root.add(modeGrid).left().colspan(4).growX();
        root.row();

        // Assist options section
        Table assistGrid = new Table(skin);
        assistGrid.defaults().left().pad(6);

        bpmguide = new CheckBox(" " + bundle.getString("BPM_GUIDE"), skin);
        assistGrid.add(bpmguide).colspan(2);
        assistGrid.row();

        assistGrid.add(new Label("Gauge Auto Shift", skin));
        gaugeautoshift = new SelectBox<>(skin);
        gaugeautoshift.setItems("NONE", "CONTINUE", "SURVIVAL TO GROOVE", "BEST CLEAR", "SELECT TO UNDER");
        assistGrid.add(gaugeautoshift).width(200);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("BOTTOM_SHIFTABLE_GAUGE"), skin));
        bottomshiftablegauge = new SelectBox<>(skin);
        bottomshiftablegauge.setItems("ASSIST EASY", "EASY", "NORMAL");
        assistGrid.add(bottomshiftablegauge).width(200);
        assistGrid.row();

        customjudge = new CheckBox(" " + bundle.getString("EXPAND_JUDGE"), skin);
        assistGrid.add(customjudge).colspan(4);
        assistGrid.row();

        Table judgeRow1 = new Table(skin);
        judgeRow1.add(new Label("KEY PG", skin)).width(60);
        njudgepg = new SpinnerWidget(skin, 25, 400, 100, 25);
        judgeRow1.add(njudgepg);
        judgeRow1.add(new Label("KEY GR", skin)).width(60);
        njudgegr = new SpinnerWidget(skin, 0, 400, 100, 25);
        judgeRow1.add(njudgegr);
        judgeRow1.add(new Label("KEY GD", skin)).width(60);
        njudgegd = new SpinnerWidget(skin, 0, 400, 100, 25);
        judgeRow1.add(njudgegd);
        assistGrid.add(judgeRow1).colspan(4);
        assistGrid.row();

        Table judgeRow2 = new Table(skin);
        judgeRow2.add(new Label("SCR PG", skin)).width(60);
        sjudgepg = new SpinnerWidget(skin, 25, 400, 100, 25);
        judgeRow2.add(sjudgepg);
        judgeRow2.add(new Label("SCR GR", skin)).width(60);
        sjudgegr = new SpinnerWidget(skin, 0, 400, 100, 25);
        judgeRow2.add(sjudgegr);
        judgeRow2.add(new Label("SCR GD", skin)).width(60);
        sjudgegd = new SpinnerWidget(skin, 0, 400, 100, 25);
        judgeRow2.add(sjudgegd);
        assistGrid.add(judgeRow2).colspan(4);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("MINE_MODE"), skin));
        minemode = new SelectBox<>(skin);
        minemode.setItems("OFF", "REMOVE", "ADD RANDOM", "ADD NEAR", "ADD ALL");
        assistGrid.add(minemode).width(150);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("SCROLL_MODE"), skin));
        scrollmode = new SelectBox<>(skin);
        scrollmode.setItems("OFF", "REMOVE", "ADD");
        assistGrid.add(scrollmode).width(150);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("LONGNOTE_MODE"), skin));
        longnotemode = new SelectBox<>(skin);
        longnotemode.setItems("OFF", "REMOVE", "ADD LN", "ADD CN", "ADD HCN", "ADD ALL");
        assistGrid.add(longnotemode).width(150);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("LONGNOTE_MODIFYRATE"), skin));
        longnoterate = new Slider(0f, 1f, 0.01f, false, skin);
        assistGrid.add(longnoterate).width(200);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("H_RANDOM_THRESHOLD_BPM"), skin));
        hranthresholdbpm = new SpinnerWidget(skin, 1, 1000, 120, 1);
        assistGrid.add(hranthresholdbpm);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("SEVEN_TO_NINE_PATTERN"), skin));
        seventoninepattern = new SelectBox<>(skin);
        seventoninepattern.setItems("OFF", "SC1KEY2~8", "SC1KEY3~9", "SC2KEY3~9", "SC8KEY1~7", "SC9KEY1~7", "SC9KEY2~8");
        assistGrid.add(seventoninepattern).width(150);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("SEVEN_TO_NINE_TYPE"), skin));
        seventoninetype = new SelectBox<>(skin);
        seventoninetype.setItems(bundle.getString("SEVEN_TO_NINE_OFF"),
                bundle.getString("SEVEN_TO_NINE_NO_MASHING"),
                bundle.getString("SEVEN_TO_NINE_ALTERNATION"));
        assistGrid.add(seventoninetype).width(200);
        assistGrid.row();

        assistGrid.add(new Label(bundle.getString("EXIT_PRESS_DURATION"), skin));
        exitpressduration = new SpinnerWidget(skin, 0, 100000, 1000, 100);
        assistGrid.add(exitpressduration);
        assistGrid.row();

        extranotedepth = new SpinnerWidget(skin, 0, 100, 0, 1);
        assistGrid.add(new Label(bundle.getString("EXTRA_NOTE"), skin));
        assistGrid.add(extranotedepth);
        assistGrid.row();

        // Misc checkboxes
        chartpreview = new CheckBox(" " + bundle.getString("CHART_PREVIEW"), skin);
        assistGrid.add(chartpreview).colspan(2);
        assistGrid.row();
        guidese = new CheckBox(" " + bundle.getString("GUIDE_SE"), skin);
        assistGrid.add(guidese).colspan(2);
        assistGrid.row();
        windowhold = new CheckBox(" " + bundle.getString("WINDOW_HOLD"), skin);
        assistGrid.add(windowhold).colspan(2);
        assistGrid.row();
        judgeregion = new CheckBox(" " + bundle.getString("JUDGE_REGION"), skin);
        assistGrid.add(judgeregion).colspan(2);
        assistGrid.row();
        markprocessednote = new CheckBox(" " + bundle.getString("MARK_PROCESSED_NOTE"), skin);
        assistGrid.add(markprocessednote).colspan(2);
        assistGrid.row();
        showhiddennote = new CheckBox(" " + bundle.getString("SHOW_HIDDEN_NOTE"), skin);
        assistGrid.add(showhiddennote).colspan(2);
        assistGrid.row();

        // Target
        assistGrid.add(new Label(bundle.getString("TARGET_SCORE"), skin));
        target = new SelectBox<>(skin);
        assistGrid.add(target).width(200);
        assistGrid.row();

        // Auto-save replay
        String[] autosaves = new String[]{
                bundle.getString("NONE"), bundle.getString("BETTER_SCORE"),
                bundle.getString("BETTER_OR_SAME_SCORE"), bundle.getString("BETTER_MISSCOUNT"),
                bundle.getString("BETTER_OR_SAME_MISSCOUNT"), bundle.getString("BETTER_COMBO"),
                bundle.getString("BETTER_OR_SAME_COMBO"), bundle.getString("BETTER_LAMP"),
                bundle.getString("BETTER_OR_SAME_LAMP"), bundle.getString("BETTER_ALL"),
                bundle.getString("ALWAYS")};
        assistGrid.add(new Label("Auto Save Replay 1", skin));
        autosavereplay1 = new SelectBox<>(skin);
        autosavereplay1.setItems(autosaves);
        assistGrid.add(autosavereplay1).width(200);
        assistGrid.row();
        assistGrid.add(new Label("Auto Save Replay 2", skin));
        autosavereplay2 = new SelectBox<>(skin);
        autosavereplay2.setItems(autosaves);
        assistGrid.add(autosavereplay2).width(200);
        assistGrid.row();
        assistGrid.add(new Label("Auto Save Replay 3", skin));
        autosavereplay3 = new SelectBox<>(skin);
        autosavereplay3.setItems(autosaves);
        assistGrid.add(autosavereplay3).width(200);
        assistGrid.row();
        assistGrid.add(new Label("Auto Save Replay 4", skin));
        autosavereplay4 = new SelectBox<>(skin);
        autosavereplay4.setItems(autosaves);
        assistGrid.add(autosavereplay4).width(200);
        assistGrid.row();

        root.add(assistGrid).left().colspan(4).growX().padTop(8);

        ScrollPane scrollPane = new ScrollPane(root, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    private void switchPlayConfig() {
        if (player == null) return;
        // Save current mode's per-mode config
        if (currentMode != null) {
            commitPlayConfig(currentMode);
        }
        // Load new mode
        currentMode = playconfig.getSelected();
        loadPlayConfig(currentMode);
    }

    private void loadPlayConfig(PlayMode mode) {
        PlayConfig conf = player.getPlayConfig(Mode.valueOf(mode.name())).getPlayconfig();
        hispeed.setValue(conf.getHispeed());
        gvalue.setValue(conf.getDuration());
        enableConstant.setChecked(conf.isEnableConstant());
        constFadeinTime.setValue(conf.getConstantFadeinTime());
        hispeedmargin.setValue(conf.getHispeedMargin());
        fixhispeed.setSelectedIndex(conf.getFixhispeed());
        enableLanecover.setChecked(conf.isEnablelanecover());
        lanecover.setValue((int) (conf.getLanecover() * 1000));
        lanecovermarginlow.setValue((int) (conf.getLanecovermarginlow() * 1000));
        lanecovermarginhigh.setValue((int) (conf.getLanecovermarginhigh() * 1000));
        lanecoverswitchduration.setValue(conf.getLanecoverswitchduration());
        enableLift.setChecked(conf.isEnablelift());
        enableHidden.setChecked(conf.isEnablehidden());
        lift.setValue((int) (conf.getLift() * 1000));
        hidden.setValue((int) (conf.getHidden() * 1000));
        judgealgorithm.setSelectedIndex(JudgeAlgorithm.getIndex(conf.getJudgetype()));
        hispeedautoadjust.setChecked(conf.isEnableHispeedAutoAdjust());
    }

    private void commitPlayConfig(PlayMode mode) {
        PlayConfig conf = player.getPlayConfig(Mode.valueOf(mode.name())).getPlayconfig();
        conf.setHispeed(hispeed.getValue());
        conf.setDuration(gvalue.getIntValue());
        conf.setEnableConstant(enableConstant.isChecked());
        conf.setConstantFadeinTime(constFadeinTime.getIntValue());
        conf.setHispeedMargin(hispeedmargin.getValue());
        conf.setFixhispeed(fixhispeed.getSelectedIndex());
        conf.setEnablelanecover(enableLanecover.isChecked());
        conf.setLanecover(lanecover.getIntValue() / 1000f);
        conf.setLanecovermarginlow(lanecovermarginlow.getIntValue() / 1000f);
        conf.setLanecovermarginhigh(lanecovermarginhigh.getIntValue() / 1000f);
        conf.setLanecoverswitchduration(lanecoverswitchduration.getIntValue());
        conf.setEnablelift(enableLift.isChecked());
        conf.setEnablehidden(enableHidden.isChecked());
        conf.setLift(lift.getIntValue() / 1000f);
        conf.setHidden(hidden.getIntValue() / 1000f);
        conf.setJudgetype(JudgeAlgorithm.values()[judgealgorithm.getSelectedIndex()].name());
        conf.setHispeedAutoAdjust(hispeedautoadjust.isChecked());
    }

    @Override
    public void updatePlayer(PlayerConfig player) {
        this.player = player;
        if (player == null) return;

        scoreop.setSelectedIndex(player.getRandom());
        scoreop2.setSelectedIndex(player.getRandom2());
        doubleop.setSelectedIndex(player.getDoubleoption());
        gaugeop.setSelectedIndex(player.getGauge());
        lntype.setSelectedIndex(player.getLnmode());
        notesdisplaytiming.setValue(player.getJudgetiming());
        notesdisplaytimingautoadjust.setChecked(player.isNotesDisplayTimingAutoAdjust());
        bpmguide.setChecked(player.isBpmguide());
        gaugeautoshift.setSelectedIndex(player.getGaugeAutoShift());
        bottomshiftablegauge.setSelectedIndex(player.getBottomShiftableGauge());
        customjudge.setChecked(player.isCustomJudge());
        njudgepg.setValue(player.getKeyJudgeWindowRatePerfectGreat());
        njudgegr.setValue(player.getKeyJudgeWindowRateGreat());
        njudgegd.setValue(player.getKeyJudgeWindowRateGood());
        sjudgepg.setValue(player.getScratchJudgeWindowRatePerfectGreat());
        sjudgegr.setValue(player.getScratchJudgeWindowRateGreat());
        sjudgegd.setValue(player.getScratchJudgeWindowRateGood());
        minemode.setSelectedIndex(player.getMineMode());
        scrollmode.setSelectedIndex(player.getScrollMode());
        longnotemode.setSelectedIndex(player.getLongnoteMode());
        longnoterate.setValue((float) player.getLongnoteRate());
        hranthresholdbpm.setValue(player.getHranThresholdBPM());
        seventoninepattern.setSelectedIndex(player.getSevenToNinePattern());
        seventoninetype.setSelectedIndex(player.getSevenToNineType());
        exitpressduration.setValue(player.getExitPressDuration());
        chartpreview.setChecked(player.isChartPreview());
        guidese.setChecked(player.isGuideSE());
        windowhold.setChecked(player.isWindowHold());
        extranotedepth.setValue(player.getExtranoteDepth());
        judgeregion.setChecked(player.isShowjudgearea());
        markprocessednote.setChecked(player.isMarkprocessednote());
        showhiddennote.setChecked(player.isShowhiddennote());

        String[] targets = player.getTargetlist();
        target.setItems(targets);
        target.setSelected(player.getTargetid());

        int[] replays = player.getAutoSaveReplay();
        autosavereplay1.setSelectedIndex(replays[0]);
        autosavereplay2.setSelectedIndex(replays[1]);
        autosavereplay3.setSelectedIndex(replays[2]);
        autosavereplay4.setSelectedIndex(replays[3]);

        currentMode = null;
        playconfig.setSelected(PlayMode.BEAT_7K);
        switchPlayConfig();
    }

    @Override
    public void commitPlayer(PlayerConfig player) {
        if (player == null) return;

        // Save current per-mode config
        if (currentMode != null) {
            commitPlayConfig(currentMode);
        }

        player.setRandom(scoreop.getSelectedIndex());
        player.setRandom2(scoreop2.getSelectedIndex());
        player.setDoubleoption(doubleop.getSelectedIndex());
        player.setGauge(gaugeop.getSelectedIndex());
        player.setLnmode(lntype.getSelectedIndex());
        player.setJudgetiming(notesdisplaytiming.getIntValue());
        player.setNotesDisplayTimingAutoAdjust(notesdisplaytimingautoadjust.isChecked());
        player.setBpmguide(bpmguide.isChecked());
        player.setGaugeAutoShift(gaugeautoshift.getSelectedIndex());
        player.setBottomShiftableGauge(bottomshiftablegauge.getSelectedIndex());
        player.setCustomJudge(customjudge.isChecked());
        player.setKeyJudgeWindowRatePerfectGreat(njudgepg.getIntValue());
        player.setKeyJudgeWindowRateGreat(njudgegr.getIntValue());
        player.setKeyJudgeWindowRateGood(njudgegd.getIntValue());
        player.setScratchJudgeWindowRatePerfectGreat(sjudgepg.getIntValue());
        player.setScratchJudgeWindowRateGreat(sjudgegr.getIntValue());
        player.setScratchJudgeWindowRateGood(sjudgegd.getIntValue());
        player.setMineMode(minemode.getSelectedIndex());
        player.setScrollMode(scrollmode.getSelectedIndex());
        player.setLongnoteMode(longnotemode.getSelectedIndex());
        player.setLongnoteRate(longnoterate.getValue());
        player.setHranThresholdBPM(hranthresholdbpm.getIntValue());
        player.setSevenToNinePattern(seventoninepattern.getSelectedIndex());
        player.setSevenToNineType(seventoninetype.getSelectedIndex());
        player.setExitPressDuration(exitpressduration.getIntValue());
        player.setChartPreview(chartpreview.isChecked());
        player.setGuideSE(guidese.isChecked());
        player.setWindowHold(windowhold.isChecked());
        player.setExtranoteDepth(extranotedepth.getIntValue());
        player.setShowjudgearea(judgeregion.isChecked());
        player.setMarkprocessednote(markprocessednote.isChecked());
        player.setShowhiddennote(showhiddennote.isChecked());
        player.setTargetid(target.getSelected());
        player.setAutoSaveReplay(new int[]{
                autosavereplay1.getSelectedIndex(), autosavereplay2.getSelectedIndex(),
                autosavereplay3.getSelectedIndex(), autosavereplay4.getSelectedIndex()});
    }
}
