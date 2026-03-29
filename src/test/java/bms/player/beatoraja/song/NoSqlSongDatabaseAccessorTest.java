package bms.player.beatoraja.song;

import bms.model.BMSModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoSqlSongDatabaseAccessorTest {

	@TempDir
	Path tempDir;

	@Test
	void importsAndPersistsSongsAndFoldersAcrossReopen() throws Exception {
		Path root = tempDir.resolve("songs");
		Path folder = root.resolve("pack");
		Path chart = createChart(folder, "song.bms", "NoSQL Song", true);
		createFile(folder.resolve("notes.txt"), "docs");
		createFile(folder.resolve("preview.ogg"), "preview");

		NoSqlSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

		SongData[] songs = accessor.getSongDatas("path", chart.toString());
		assertEquals(1, songs.length);
		assertEquals("NoSQL Song", songs[0].getTitle());
		assertEquals("preview.ogg", songs[0].getPreview());

		FolderData[] folders = accessor.getFolderDatas("path", folder.toString() + "/");
		assertEquals(1, folders.length);

		NoSqlSongDatabaseAccessor reopened = newAccessor(tempDir.resolve("song.db"), root);
		assertEquals(1, reopened.getSongDatas("path", chart.toString()).length);
	}

	@Test
	void writesCustomBinaryShardFormatInsteadOfJavaSerialization() throws Exception {
		Path root = tempDir.resolve("songs");
		createChart(root.resolve("pack"), "song.bms", "Binary Song", false);
		Path dbPath = tempDir.resolve("song.db");

		NoSqlSongDatabaseAccessor accessor = newAccessor(dbPath, root);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

		Path shardFile = dbPath.resolveSibling(dbPath.getFileName().toString() + ".nosql")
				.resolve("shards")
				.resolve("songs-0.bin");
		byte[] header = Files.readAllBytes(shardFile);
		assertTrue(header.length >= 4);
		assertFalse(header[0] == (byte) 0xAC && header[1] == (byte) 0xED);
		assertEquals('B', header[0]);
		assertEquals('R', header[1]);
	}

	@Test
	void preservesTagFavoriteAndPreviewRefresh() throws Exception {
		Path root = tempDir.resolve("songs");
		Path folder = root.resolve("pack");
		Path chart = createChart(folder, "song.bms", "Preview Song", true);
		Path previewOne = createFile(folder.resolve("preview1.ogg"), "a");

		NoSqlSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

		SongData imported = accessor.getSongDatas("path", chart.toString())[0];
		imported.setTag("tagged");
		imported.setFavorite(SongData.FAVORITE_SONG);
		accessor.setSongDatas(new SongData[] { imported });

		FileTime chartTime = Files.getLastModifiedTime(chart);
		Files.delete(previewOne);
		createFile(folder.resolve("preview2.ogg"), "b");
		Files.setLastModifiedTime(chart, chartTime);

		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);
		SongData updated = accessor.getSongDatas("path", chart.toString())[0];
		assertEquals("tagged", updated.getTag());
		assertEquals(SongData.FAVORITE_SONG, updated.getFavorite());
		assertEquals("preview2.ogg", updated.getPreview());
	}

	@Test
	void removesDeletedSongsAndSupportsUpdateAll() throws Exception {
		Path root = tempDir.resolve("songs");
		Path folder = root.resolve("pack");
		Path oldChart = createChart(folder, "old.bms", "Old Song", false);

		NoSqlSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);
		assertEquals(1, accessor.getSongDatas("path", oldChart.toString()).length);

		Files.delete(oldChart);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);
		assertEquals(0, accessor.getSongDatas("path", oldChart.toString()).length);

		Path newChart = createChart(folder, "new.bms", "New Song", false);
		accessor.updateSongDatas(null, new String[] { root.toString() }, true, null);
		assertEquals(1, accessor.getSongDatas("path", newChart.toString()).length);
	}

	@Test
	void targetedUpdateAndHashLookupOrderWork() throws Exception {
		Path root = tempDir.resolve("songs");
		Path folderA = root.resolve("packA");
		Path folderB = root.resolve("packB");
		Path chartA = createChart(folderA, "a.bms", "Song A", false);
		Path chartB = createChart(folderB, "b.bms", "Song B", false);

		NoSqlSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

		Files.delete(chartA);
		accessor.updateSongDatas(folderA.toString(), new String[] { root.toString() }, false, null);
		assertEquals(0, accessor.getSongDatas("path", chartA.toString()).length);
		assertEquals(1, accessor.getSongDatas("path", chartB.toString()).length);

		SongData songB = accessor.getSongDatas("path", chartB.toString())[0];
		Path chartC = createChart(root.resolve("packC"), "c.bms", "Song C", false);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);
		SongData songC = accessor.getSongDatas("path", chartC.toString())[0];

		SongData[] ordered = accessor.getSongDatas(new String[] { songC.getSha256(), songB.getMd5() });
		assertEquals("Song C", ordered[0].getTitle());
		assertEquals("Song B", ordered[1].getTitle());
	}

	@Test
	void supportsTextSearchAndSqlMirrorQueries() throws Exception {
		Path root = tempDir.resolve("songs");
		Path chart = createChart(root.resolve("pack"), "joined.bms", "Joined Song", false);
		Path dbPath = tempDir.resolve("song.db");
		Path scorePath = tempDir.resolve("score.db");
		Path scoreLogPath = tempDir.resolve("scorelog.db");
		Path infoPath = tempDir.resolve("songinfo.db");

		NoSqlSongDatabaseAccessor accessor = newAccessor(dbPath, root);
		SongInformationAccessor infoAccessor = new SongInformationAccessor(infoPath.toString());
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, infoAccessor);

		assertEquals(1, accessor.getSongDatasByText("Joined").length);

		SongData imported = accessor.getSongDatas("path", chart.toString())[0];
		createScoreDb(scorePath);
		createScoreLogDb(scoreLogPath);
		insertScoreSha256(scorePath, imported.getSha256());

		SongData[] viaScoreJoin = accessor.getSongDatas("score.sha256 IS NOT NULL", scorePath.toString(), scoreLogPath.toString(), null);
		SongData[] viaInfoJoin = accessor.getSongDatas("information.sha256 IS NOT NULL", scorePath.toString(), scoreLogPath.toString(), infoPath.toString());
		assertEquals(1, viaScoreJoin.length);
		assertEquals(1, viaInfoJoin.length);
	}

	@Test
	void reportsProgressAndKeepsPreviousSnapshotOnCrash() throws Exception {
		Path root = tempDir.resolve("songs");
		Path chart = createChart(root.resolve("pack"), "crash.bms", "Crash Song", false);
		Path dbPath = tempDir.resolve("song.db");

		NoSqlSongDatabaseAccessor accessor = newAccessor(dbPath, root);
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);
		assertEquals(1, accessor.getSongDatas("path", chart.toString()).length);

		Files.writeString(chart, Files.readString(chart) + "#COMMENT change\n");
		FileTime originalChartTime = Files.getLastModifiedTime(chart);
		Files.setLastModifiedTime(chart, FileTime.fromMillis(originalChartTime.toMillis() + 2_000));
		List<SongDatabaseAccessor.SongDatabaseImportProgress> progressEvents =
				Collections.synchronizedList(new ArrayList<SongDatabaseAccessor.SongDatabaseImportProgress>());
		accessor.addPlugin((model, song) -> {
			if ("Crash Song".equals(song.getTitle())) {
				throw new RuntimeException("simulated crash");
			}
		});
		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null, progressEvents::add);

		assertFalse(progressEvents.isEmpty());
		assertEquals(SongDatabaseAccessor.SongDatabaseImportPhase.FAILED,
				progressEvents.get(progressEvents.size() - 1).getPhase());

		NoSqlSongDatabaseAccessor reopened = newAccessor(dbPath, root);
		assertEquals(1, reopened.getSongDatas("path", chart.toString()).length);
	}

	@Test
	void reportsDetailedWriteProgressAcrossBatchesAndSnapshotSteps() throws Exception {
		Path root = tempDir.resolve("songs");
		for (int i = 0; i < 260; i++) {
			createChart(root.resolve("pack"), String.format("song-%03d.bms", i), "Song " + i, false);
		}

		NoSqlSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
		List<SongDatabaseAccessor.SongDatabaseImportProgress> progressEvents =
				Collections.synchronizedList(new ArrayList<SongDatabaseAccessor.SongDatabaseImportProgress>());

		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null, progressEvents::add);

		assertTrue(progressEvents.stream().anyMatch(progress ->
				progress.getPhase() == SongDatabaseAccessor.SongDatabaseImportPhase.PARSING
						&& progress.getMessage() != null
						&& progress.getMessage().contains("Parsing songs")));
		assertTrue(progressEvents.stream().anyMatch(progress ->
				progress.getPhase() == SongDatabaseAccessor.SongDatabaseImportPhase.WRITING
						&& progress.getMessage() != null
						&& progress.getMessage().contains("Saving library data")));
		assertFalse(progressEvents.stream().anyMatch(progress ->
				progress.getMessage() != null && progress.getMessage().toLowerCase().contains("map-reduce")));
		assertEquals(SongDatabaseAccessor.SongDatabaseImportPhase.COMPLETE,
				progressEvents.get(progressEvents.size() - 1).getPhase());
	}

	@Test
	void doesNotReportWritingUntilAfterParsingProgressAdvances() throws Exception {
		Path root = tempDir.resolve("songs");
		for (int i = 0; i < 260; i++) {
			createChart(root.resolve("pack"), String.format("song-%03d.bms", i), "Song " + i, false);
		}

		NoSqlSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
		List<SongDatabaseAccessor.SongDatabaseImportProgress> progressEvents =
				Collections.synchronizedList(new ArrayList<SongDatabaseAccessor.SongDatabaseImportProgress>());

		accessor.updateSongDatas(null, new String[] { root.toString() }, false, null, progressEvents::add);

		int firstWritingIndex = -1;
		int lastParsingIndex = -1;
		boolean parsingAdvanced = false;
		for (int i = 0; i < progressEvents.size(); i++) {
			SongDatabaseAccessor.SongDatabaseImportProgress progress = progressEvents.get(i);
			if (progress.getPhase() == SongDatabaseAccessor.SongDatabaseImportPhase.PARSING
					&& progress.getProcessedSongs() > 0) {
				parsingAdvanced = true;
				lastParsingIndex = i;
			}
			if (firstWritingIndex < 0 && progress.getPhase() == SongDatabaseAccessor.SongDatabaseImportPhase.WRITING) {
				firstWritingIndex = i;
			}
		}

		assertTrue(parsingAdvanced);
		assertTrue(firstWritingIndex > lastParsingIndex);
		assertFalse(progressEvents.stream().anyMatch(progress ->
				progress.getMessage() != null && progress.getMessage().contains("Saved songs batch")));
		assertFalse(progressEvents.stream().anyMatch(progress ->
				progress.getMessage() != null && progress.getMessage().contains("Saving songs ")));
	}

	private NoSqlSongDatabaseAccessor newAccessor(Path dbPath, Path root) throws Exception {
		return new NoSqlSongDatabaseAccessor(dbPath.toString(), new String[] { root.toString() });
	}

	private Path createChart(Path folder, String filename, String title, boolean hasPreview) throws IOException {
		String chart = ""
				+ "#PLAYER 1\n"
				+ "#GENRE TEST\n"
				+ "#TITLE " + title + "\n"
				+ "#ARTIST Test Artist\n"
				+ "#BPM 130\n"
				+ "#PLAYLEVEL 3\n"
				+ "#RANK 2\n"
				+ "#TOTAL 100\n"
				+ "#WAV01 sample.wav\n"
				+ "#00111:0100\n";
		return createFile(folder.resolve(filename), chart);
	}

	private Path createFile(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
		return path;
	}

	private void createScoreDb(Path dbPath) throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
			 Statement stmt = conn.createStatement()) {
			stmt.execute("CREATE TABLE score (sha256 TEXT PRIMARY KEY)");
		}
	}

	private void createScoreLogDb(Path dbPath) throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
			 Statement stmt = conn.createStatement()) {
			stmt.execute("CREATE TABLE scorelog (sha256 TEXT)");
		}
	}

	private void insertScoreSha256(Path dbPath, String sha256) throws Exception {
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
			 Statement stmt = conn.createStatement()) {
			stmt.execute("INSERT INTO score (sha256) VALUES ('" + sha256 + "')");
		}
	}
}
