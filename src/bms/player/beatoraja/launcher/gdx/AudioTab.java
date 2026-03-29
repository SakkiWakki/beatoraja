package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.FrequencyType;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.audio.PortAudioDriver;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.portaudio.DeviceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class AudioTab implements LauncherTab {

    private SelectBox<DriverType> audio;
    private SelectBox<String> audioname;
    private SpinnerWidget audiobuffer;
    private SpinnerWidget audiosim;
    private SelectBox<String> audiosamplerate;
    private Slider systemvolume;
    private Slider keyvolume;
    private Slider bgvolume;
    private SelectBox<FrequencyType> audioFreqOption;
    private SelectBox<FrequencyType> audioFastForward;
    private CheckBox loopResultSound;
    private CheckBox loopCourseResultSound;

    private AudioConfig audioConfig;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table grid = new Table(skin);
        grid.defaults().left().pad(6);
        grid.top().left();
        grid.columnDefaults(0).width(250);

        grid.add(new Label(bundle.getString("Audio_Output"), skin));
        audio = new SelectBox<>(skin);
        audio.setItems(DriverType.OpenAL, DriverType.PortAudio);
        audio.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateAudioDriver();
            }
        });
        grid.add(audio).width(200);
        grid.row();

        grid.add(new Label("Audio Driver Name", skin));
        audioname = new SelectBox<>(skin);
        grid.add(audioname).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("AUDIO_BUF_SIZE"), skin));
        audiobuffer = new SpinnerWidget(skin, 4, 4096, 384, 1);
        grid.add(audiobuffer);
        grid.row();

        grid.add(new Label(bundle.getString("AUDIO_SIM_SOURCES"), skin));
        audiosim = new SpinnerWidget(skin, 16, 1024, 256, 1);
        grid.add(audiosim);
        grid.row();

        grid.add(new Label(bundle.getString("AUDIO_SAMPLE_RATE"), skin));
        audiosamplerate = new SelectBox<>(skin);
        audiosamplerate.setItems("Auto", "44100", "48000");
        grid.add(audiosamplerate).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("SYSTEM_VOLUME"), skin));
        systemvolume = new Slider(0f, 1f, 0.01f, false, skin);
        grid.add(systemvolume).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("KEY_VOLUME"), skin));
        keyvolume = new Slider(0f, 1f, 0.01f, false, skin);
        grid.add(keyvolume).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("BG_VOLUME"), skin));
        bgvolume = new Slider(0f, 1f, 0.01f, false, skin);
        grid.add(bgvolume).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("AUDIO_FREQ_OPTION"), skin));
        audioFreqOption = new SelectBox<>(skin);
        audioFreqOption.setItems(FrequencyType.UNPROCESSED, FrequencyType.FREQUENCY);
        grid.add(audioFreqOption).width(200);
        grid.row();

        grid.add(new Label(bundle.getString("AUDIO_FAST_FORWARD"), skin));
        audioFastForward = new SelectBox<>(skin);
        audioFastForward.setItems(FrequencyType.UNPROCESSED, FrequencyType.FREQUENCY);
        grid.add(audioFastForward).width(200);
        grid.row();

        loopResultSound = new CheckBox(" " + bundle.getString("LOOP_RESULT_SOUND"), skin);
        grid.add(loopResultSound).colspan(2);
        grid.row();

        loopCourseResultSound = new CheckBox(" " + bundle.getString("LOOP_COURSE_RESULT_SOUND"), skin);
        grid.add(loopCourseResultSound).colspan(2);
        grid.row();

        ScrollPane scrollPane = new ScrollPane(grid, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    private void updateAudioDriver() {
        switch (audio.getSelected()) {
            case OpenAL:
                audioname.setDisabled(true);
                audioname.clearItems();
                break;
            case PortAudio:
                try {
                    DeviceInfo[] devices = PortAudioDriver.getDevices();
                    List<String> drivers = new ArrayList<>(devices.length);
                    for (DeviceInfo device : devices) {
                        drivers.add(device.name);
                    }
                    if (drivers.isEmpty()) {
                        throw new RuntimeException("No PortAudio drivers found");
                    }
                    audioname.setItems(drivers.toArray(new String[0]));
                    if (audioConfig != null && drivers.contains(audioConfig.getDriverName())) {
                        audioname.setSelected(audioConfig.getDriverName());
                    } else {
                        audioname.setSelectedIndex(0);
                    }
                    audioname.setDisabled(false);
                } catch (Throwable e) {
                    Logger.getGlobal().severe("PortAudio unavailable: " + e.getMessage());
                    audio.setSelected(DriverType.OpenAL);
                }
                break;
        }
    }

    @Override
    public void update(Config config) {
        this.audioConfig = config.getAudioConfig();
        if (audioConfig == null) return;

        audio.setSelected(audioConfig.getDriver());
        audiobuffer.setValue(audioConfig.getDeviceBufferSize());
        audiosim.setValue(audioConfig.getDeviceSimultaneousSources());

        int sr = audioConfig.getSampleRate();
        audiosamplerate.setSelected(sr > 0 ? String.valueOf(sr) : "Auto");

        audioFreqOption.setSelected(audioConfig.getFreqOption());
        audioFastForward.setSelected(audioConfig.getFastForward());
        systemvolume.setValue(audioConfig.getSystemvolume());
        keyvolume.setValue(audioConfig.getKeyvolume());
        bgvolume.setValue(audioConfig.getBgvolume());
        loopResultSound.setChecked(audioConfig.isLoopResultSound());
        loopCourseResultSound.setChecked(audioConfig.isLoopCourseResultSound());

        updateAudioDriver();
    }

    @Override
    public void commit(Config config) {
        AudioConfig ac = config.getAudioConfig();
        if (ac == null) return;

        ac.setDriver(audio.getSelected());
        ac.setDriverName(audioname.getSelected());
        ac.setDeviceBufferSize(audiobuffer.getIntValue());
        ac.setDeviceSimultaneousSources(audiosim.getIntValue());

        String srStr = audiosamplerate.getSelected();
        ac.setSampleRate("Auto".equals(srStr) ? 0 : Integer.parseInt(srStr));

        ac.setFreqOption(audioFreqOption.getSelected());
        ac.setFastForward(audioFastForward.getSelected());
        ac.setSystemvolume(systemvolume.getValue());
        ac.setKeyvolume(keyvolume.getValue());
        ac.setBgvolume(bgvolume.getValue());
        ac.setLoopResultSound(loopResultSound.isChecked());
        ac.setLoopCourseResultSound(loopCourseResultSound.isChecked());
    }
}
