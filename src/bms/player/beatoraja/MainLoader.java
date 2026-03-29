package bms.player.beatoraja;

import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Graphics;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import org.lwjgl.glfw.GLFW;

import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.ir.IRConnectionManager;
import bms.player.beatoraja.launcher.gdx.LauncherApp;
import bms.player.beatoraja.song.NoSqlSongDatabaseAccessor;
import bms.player.beatoraja.song.SongData;
import bms.player.beatoraja.song.SongDatabaseAccessor;
import bms.player.beatoraja.song.SongUtils;

/**
 * 起動用クラス
 *
 * @author exch
 */
public class MainLoader {

	private static final boolean ALLOWS_32BIT_JAVA = false;

	private static SongDatabaseAccessor songdb;

	private static final Set<String> illegalSongs = new HashSet<String>();

	private static Path bmsPath;

	private static VersionChecker version;

	public static void main(String[] args) {
		initGlfwPlatform();

		if(!ALLOWS_32BIT_JAVA && !System.getProperty( "os.arch" ).contains( "64")) {
			JOptionPane.showMessageDialog(null, "This Application needs 64bit-Java.", "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		Logger logger = Logger.getGlobal();
		try {
			logger.addHandler(new FileHandler("beatoraja_log.xml"));
		} catch (Throwable e) {
			e.printStackTrace();
		}

		BMSPlayerMode auto = null;
		for (String s : args) {
			if (s.startsWith("-")) {
				if (s.equals("-a")) {
					auto = BMSPlayerMode.AUTOPLAY;
				}
				if (s.equals("-p")) {
					auto = BMSPlayerMode.PRACTICE;
				}
				if (s.equals("-r") || s.equals("-r1")) {
					auto = BMSPlayerMode.REPLAY_1;
				}
				if (s.equals("-r2")) {
					auto = BMSPlayerMode.REPLAY_2;
				}
				if (s.equals("-r3")) {
					auto = BMSPlayerMode.REPLAY_3;
				}
				if (s.equals("-r4")) {
					auto = BMSPlayerMode.REPLAY_4;
				}
				if (s.equals("-s")) {
					auto = BMSPlayerMode.PLAY;
				}
			} else {
				bmsPath = Paths.get(s);
				if(auto == null) {
					auto = BMSPlayerMode.PLAY;
				}
			}
		}



		if (Files.exists(Config.configpath) && (bmsPath != null || auto != null)) {
			IRConnectionManager.getAllAvailableIRConnectionName();
			play(bmsPath, auto, true, null, null, bmsPath != null);
		} else {
			openLauncher();
		}
	}

	public static void play(Path f, BMSPlayerMode auto, boolean forceExit, Config config, PlayerConfig player, boolean songUpdated) {
		if(config == null) {
			config = Config.read();
		}

		for(SongData song : getScoreDatabaseAccessor().getSongDatas(SongUtils.illegalsongs)) {
			MainLoader.putIllegalSong(song.getSha256());
		}
		if(illegalSongs.size() > 0) {
			JOptionPane.showMessageDialog(null, "This Application detects " + illegalSongs.size() + " illegal BMS songs. \n Remove them, update song database and restart.", "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		try {
			final MainController main = new MainController(f, config, player, auto, songUpdated);

			Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
			cfg.setWindowedMode(getWindowWidth(config), getWindowHeight(config));
			cfg.setResizable(config.getDisplaymode() != Config.DisplayMode.FULLSCREEN);

			// fullscreen
			switch (config.getDisplaymode()) {
				case FULLSCREEN:
					Graphics.DisplayMode desktop = Lwjgl3ApplicationConfiguration.getDisplayMode();
					for (Graphics.DisplayMode display : Lwjgl3ApplicationConfiguration.getDisplayModes()) {
						if (display.width == config.getResolution().width && display.height == config.getResolution().height) {
							desktop = display;
							break;
						}
					}
					cfg.setFullscreenMode(desktop);
					break;
				case BORDERLESS:
					Graphics.DisplayMode borderless = Lwjgl3ApplicationConfiguration.getDisplayMode();
					cfg.setDecorated(false);
					cfg.setWindowedMode(borderless.width, borderless.height);
					break;
				case WINDOW:
					cfg.setDecorated(true);
					cfg.setWindowedMode(getWindowWidth(config), getWindowHeight(config));
					break;
			}
			// vSync
			cfg.useVsync(config.isVsync());
			cfg.setIdleFPS(config.getMaxFramePerSecond());
			cfg.setTitle(MainController.getVersion());
			cfg.setAudioConfig(config.getAudioConfig().getDeviceSimultaneousSources(), config.getAudioConfig().getDeviceBufferSize(), 9);
			if(config.getAudioConfig().getDriver() != DriverType.OpenAL) {
				cfg.disableAudio(true);
			}
			new Lwjgl3Application(new ApplicationListener() {
				
				public void resume() {
					main.resume();
				}
				
				public void resize(int width, int height) {
					main.resize(width, height);
				}
				
				public void render() {
					main.render();
				}
				
				public void pause() {
					main.pause();
				}
				
				public void dispose() {
					main.dispose();
				}
				
				public void create() {
					main.create();
				}
			}, cfg);
			if (forceExit) {
				System.exit(0);
			}
		} catch (Throwable e) {
			e.printStackTrace();
			Logger.getGlobal().severe(e.getClass().getName() + " : " + e.getMessage());
		}
	}

	private static int getWindowWidth(Config config) {
		return config.isUseResolution() ? config.getResolution().width : config.getWindowWidth();
	}

	private static int getWindowHeight(Config config) {
		return config.isUseResolution() ? config.getResolution().height : config.getWindowHeight();
	}

	public static Graphics.DisplayMode[] getAvailableDisplayMode() {
		return Lwjgl3ApplicationConfiguration.getDisplayModes();
	}

	public static Graphics.DisplayMode getDesktopDisplayMode() {
		return Lwjgl3ApplicationConfiguration.getDisplayMode();
	}

	public static SongDatabaseAccessor getScoreDatabaseAccessor() {
		if(songdb == null) {
			try {
				Config config = Config.read();
				Class.forName("org.sqlite.JDBC");
				songdb = new NoSqlSongDatabaseAccessor(config.getSongpath(), config.getBmsroot());
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		return songdb;
	}

	public static VersionChecker getVersionChecker() {
		if(version == null) {
			version = new GithubVersionChecker();
		}
		return version;
	}

	public static void setVersionChecker(VersionChecker version) {
		if(version != null) {
			MainLoader.version = version;
		}
	}

	public static Path getBMSPath() {
		return bmsPath;
	}

	public static void putIllegalSong(String hash) {
		illegalSongs.add(hash);
	}

	public static String[] getIllegalSongs() {
		return illegalSongs.toArray(new String[illegalSongs.size()]);
	}

	public static int getIllegalSongCount() {
		return illegalSongs.size();
	}

	/**
	 * Set GLFW platform hints before glfwInit(). Must be called before any
	 * Lwjgl3Application is created. On Wayland, this selects the Wayland
	 * backend with libdecor disabled (falls back to xdg-decoration).
	 */
	private static void initGlfwPlatform() {
		try {
			if (GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_WAYLAND)) {
				GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_WAYLAND);
				GLFW.glfwInitHint(GLFW.GLFW_WAYLAND_LIBDECOR, GLFW.GLFW_WAYLAND_DISABLE_LIBDECOR);
			}
		} catch (Throwable e) {
			Logger.getGlobal().warning("GLFW platform hint failed: " + e.getMessage());
		}
	}

	public static void openLauncher() {
		Config config = Config.read();
		String[] playerIds = PlayerConfig.readAllPlayerID(config.getPlayerpath());
		PlayerConfig player;
		if (playerIds.length > 0) {
			player = PlayerConfig.readPlayerConfig(config.getPlayerpath(), playerIds[0]);
		} else {
			player = new PlayerConfig();
		}

		// Track whether Start was pressed (as opposed to Exit)
		final boolean[] startRequested = {false};

		Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
		cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
		cfg.setDecorated(true);
		// Use a conservative default size. On Wayland, GLFW monitor APIs return
		// physical pixels but setWindowedMode expects logical coordinates
		// (glfw/glfw#2527), so querying the monitor size and using it directly
		// creates an oversized window on scaled displays. A fixed 800x600
		// is safe on all configurations.
		cfg.setWindowedMode(800, 600);
		cfg.setResizable(true);
		cfg.setTitle(MainController.getVersion() + " configuration");

		LauncherApp app = new LauncherApp(config, player,
				() -> {
					// Start button: flag for game launch, then close launcher
					startRequested[0] = true;
					com.badlogic.gdx.Gdx.app.exit();
				},
				() -> {
					// Exit button
					com.badlogic.gdx.Gdx.app.exit();
				});

		// Lwjgl3Application blocks until the window is closed
		new Lwjgl3Application(app, cfg);

		// After launcher window closes, start game if requested
		if (startRequested[0]) {
			play(bmsPath, BMSPlayerMode.PLAY, true, config, player, app.isSongUpdated());
		} else {
			System.exit(0);
		}
	}

	public interface VersionChecker {
		public String getMessage();
		public String getDownloadURL();
	}

	private static class GithubVersionChecker implements VersionChecker {

		private String dlurl;
		private String message;

		public String getMessage() {
			if(message == null) {
				getInformation();
			}
			return message;
		}

		public String getDownloadURL() {
			if(message == null) {
				getInformation();
			}
			return dlurl;
		}

		private void getInformation() {
			try {
				URL url = new URL("https://api.github.com/repos/exch-bms2/beatoraja/releases/latest");
				ObjectMapper mapper = new ObjectMapper();
				GithubLastestRelease lastestData = mapper.readValue(url, GithubLastestRelease.class);
				final String name = lastestData.name;
				if (MainController.getVersion().contains(name)) {
					message = "最新版を利用中です";
				} else {
					message = String.format("最新版[%s]を利用可能です。", name);
					dlurl = "https://mocha-repository.info/download/beatoraja" + name + ".zip";
				}
			} catch (Exception e) {
				Logger.getGlobal().warning("最新版URL取得時例外:" + e.getMessage());
				message = "バージョン情報を取得できませんでした";
			}
		}
	}

	@JsonIgnoreProperties(ignoreUnknown=true)
	static class GithubLastestRelease{
		public String name;
	}

}
