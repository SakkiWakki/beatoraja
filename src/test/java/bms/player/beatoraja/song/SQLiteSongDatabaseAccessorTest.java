package bms.player.beatoraja.song;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.BMSONDecoder;

class SQLiteSongDatabaseAccessorTest {

    private static final String[] CHART_EXTENSIONS = { "bms", "bme", "bml", "pms", "bmson" };

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "imports extension={0}, txt={1}, preview={2}")
    @MethodSource("importMetadataMatrix")
    void importsMetadataAcrossExtensionAndFolderVariants(String extension, boolean hasTxt, boolean hasPreview)
            throws Exception {
        Path root = tempDir.resolve("songs");
        Path folder = root.resolve("pack");
        Path chart = createChart(folder, "matrix." + extension, "Matrix Song", hasPreview);
        if (hasTxt) {
            createFile(folder.resolve("notes.txt"), "text metadata");
        }
        if (hasPreview) {
            createFile(folder.resolve("preview.ogg"), "preview");
        }

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        SongData[] songs = accessor.getSongDatas("path", chart.toString());
        assertEquals(1, songs.length);
        assertEquals("Matrix Song", songs[0].getTitle());
        assertEquals(hasPreview ? "preview.ogg" : "", songs[0].getPreview());
        assertEquals(hasTxt, (songs[0].getContent() & SongData.CONTENT_TEXT) != 0);

        FolderData[] folders = accessor.getFolderDatas("path", folder.toString() + "/");
        assertEquals(1, folders.length);
        assertEquals("pack", folders[0].getTitle());
    }

    @ParameterizedTest(name = "preserves tag/favorite with extension={0}")
    @MethodSource("chartExtensions")
    void preservesTagAndFavoriteAndRefreshesPreviewForUnchangedCharts(String extension) throws Exception {
        Path root = tempDir.resolve("songs");
        Path folder = root.resolve("pack");
        Path chart = createChart(folder, "previewed." + extension, "Preview Song", true);
        Path previewOne = createFile(folder.resolve("preview1.ogg"), "a");

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        SongData imported = accessor.getSongDatas("path", chart.toString())[0];
        imported.setTag("favorite-tag");
        imported.setFavorite(SongData.FAVORITE_SONG);
        accessor.setSongDatas(new SongData[] { imported });

        FileTime originalChartTime = Files.getLastModifiedTime(chart);
        Files.delete(previewOne);
        createFile(folder.resolve("preview2.ogg"), "b");
        Files.setLastModifiedTime(chart, originalChartTime);

        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        SongData reimported = accessor.getSongDatas("path", chart.toString())[0];
        assertEquals("favorite-tag", reimported.getTag());
        assertEquals(SongData.FAVORITE_SONG, reimported.getFavorite());
        assertEquals("preview2.ogg", reimported.getPreview());
    }

    @ParameterizedTest(name = "removes deleted songs with extension={0}")
    @MethodSource("chartExtensions")
    void removesDeletedSongsOnIncrementalUpdate(String extension) throws Exception {
        Path root = tempDir.resolve("songs");
        Path folder = root.resolve("pack");
        Path chart = createChart(folder, "delete-me." + extension, "Delete Me", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);
        assertEquals(1, accessor.getSongDatas("path", chart.toString()).length);

        Files.delete(chart);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        assertEquals(0, accessor.getSongDatas("path", chart.toString()).length);
    }

    @ParameterizedTest(name = "targeted update preserves unrelated folders with extension={0}")
    @MethodSource("chartExtensions")
    void targetedPathUpdateOnlyTouchesRequestedFolder(String extension) throws Exception {
        Path root = tempDir.resolve("songs");
        Path folderA = root.resolve("packA");
        Path folderB = root.resolve("packB");
        Path chartA = createChart(folderA, "a." + extension, "Song A", false);
        Path chartB = createChart(folderB, "b." + extension, "Song B", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        Files.delete(chartA);
        accessor.updateSongDatas(folderA.toString(), new String[] { root.toString() }, false, null);

        assertEquals(0, accessor.getSongDatas("path", chartA.toString()).length);
        assertEquals(1, accessor.getSongDatas("path", chartB.toString()).length);
    }

    @ParameterizedTest(name = "updateAll rebuilds database with extension={0}")
    @MethodSource("chartExtensions")
    void updateAllRebuildsFromDiskState(String extension) throws Exception {
        Path root = tempDir.resolve("songs");
        Path folder = root.resolve("pack");
        Path oldChart = createChart(folder, "old." + extension, "Old Song", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);
        assertEquals(1, accessor.getSongDatas("path", oldChart.toString()).length);

        Files.delete(oldChart);
        Path newChart = createChart(folder, "new." + extension, "New Song", false);
        accessor.updateSongDatas(null, new String[] { root.toString() }, true, null);

        assertEquals(0, accessor.getSongDatas("path", oldChart.toString()).length);
        assertEquals(1, accessor.getSongDatas("path", newChart.toString()).length);
    }

    @Test
    void prunesSongsOutsideConfiguredRoots() throws Exception {
        Path rootA = tempDir.resolve("rootA");
        Path rootB = tempDir.resolve("rootB");
        Path chartA = createChart(rootA.resolve("packA"), "a.bms", "Song A", false);
        Path chartB = createChart(rootB.resolve("packB"), "b.bms", "Song B", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), rootA);
        accessor.updateSongDatas(null, new String[] { rootA.toString(), rootB.toString() }, false, null);
        assertEquals(1, accessor.getSongDatas("path", chartA.toString()).length);
        assertEquals(1, accessor.getSongDatas("path", chartB.toString()).length);

        accessor.updateSongDatas(null, new String[] { rootA.toString() }, false, null);

        assertEquals(1, accessor.getSongDatas("path", chartA.toString()).length);
        assertEquals(0, accessor.getSongDatas("path", chartB.toString()).length);
    }

    @Test
    void doesNotRecurseIntoSubdirectoriesWhenParentContainsCharts() throws Exception {
        Path root = tempDir.resolve("songs");
        Path parent = root.resolve("parent");
        Path child = parent.resolve("child");
        createChart(parent, "parent.bms", "Parent Song", false);
        Path childChart = createChart(child, "child.bms", "Child Song", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        assertEquals(1, accessor.getSongDatas("title", "Parent Song").length);
        assertEquals(0, accessor.getSongDatas("path", childChart.toString()).length);
    }

    @Test
    void persistsCommittedDataAcrossReopen() throws Exception {
        Path root = tempDir.resolve("songs");
        Path chart = createChart(root.resolve("pack"), "durable.bms", "Durable Song", true);
        Path dbPath = tempDir.resolve("song.db");

        SQLiteSongDatabaseAccessor accessor = newAccessor(dbPath, root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        SQLiteSongDatabaseAccessor reopened = newAccessor(dbPath, root);
        SongData[] songs = reopened.getSongDatas("path", chart.toString());
        assertEquals(1, songs.length);
        assertEquals("Durable Song", songs[0].getTitle());
        assertIntegrityOk(dbPath);
    }

    @Test
    void rollsBackSongDatabaseOnImporterCrashAndRecoversOnRetry() throws Exception {
        Path rootA = tempDir.resolve("rootA");
        Path rootB = tempDir.resolve("rootB");
        Path prunedChart = createChart(rootB.resolve("packB"), "pruned.bms", "Pruned Song", false);
        Path crashChart = createChart(rootA.resolve("packA"), "crash.bms", "Crash Song", false);
        Path dbPath = tempDir.resolve("song.db");

        SQLiteSongDatabaseAccessor accessor = new SQLiteSongDatabaseAccessor(dbPath.toString(),
                new String[] { rootA.toString(), rootB.toString() });
        accessor.updateSongDatas(null, new String[] { rootA.toString(), rootB.toString() }, false, null);
        assertEquals(2, countRows(dbPath, "song"));

        FileTime originalCrashTime = Files.getLastModifiedTime(crashChart);
        Files.writeString(crashChart, Files.readString(crashChart) + "#COMMENT crash\n");
        Files.setLastModifiedTime(crashChart, FileTime.fromMillis(originalCrashTime.toMillis() + 2_000));

        accessor.addPlugin((model, song) -> {
            if ("Crash Song".equals(song.getTitle())) {
                throw new RuntimeException("simulated crash");
            }
        });
        accessor.updateSongDatas(null, new String[] { rootA.toString() }, false, null);

        SQLiteSongDatabaseAccessor afterCrash = newAccessor(dbPath, rootA);
        assertEquals(1, afterCrash.getSongDatas("path", crashChart.toString()).length);
        assertEquals(1, afterCrash.getSongDatas("path", prunedChart.toString()).length);
        assertEquals(2, countRows(dbPath, "song"));
        assertIntegrityOk(dbPath);

        SQLiteSongDatabaseAccessor resumed = newAccessor(dbPath, rootA);
        resumed.updateSongDatas(null, new String[] { rootA.toString() }, false, null);

        SQLiteSongDatabaseAccessor afterResume = newAccessor(dbPath, rootA);
        assertEquals(0, afterResume.getSongDatas("path", prunedChart.toString()).length);
        assertEquals(1, afterResume.getSongDatas("path", crashChart.toString()).length);
        assertEquals(1, countRows(dbPath, "song"));
        assertIntegrityOk(dbPath);
    }

    @Test
    void rollsBackSongInfoDatabaseOnCrashAndRecoversOnRetry() throws Exception {
        Path root = tempDir.resolve("songs");
        createChart(root.resolve("pack"), "first.bms", "First Song", false);
        createChart(root.resolve("pack"), "second.bms", "Second Song", false);
        Path dbPath = tempDir.resolve("song.db");
        Path infoPath = tempDir.resolve("songinfo.db");

        SQLiteSongDatabaseAccessor accessor = newAccessor(dbPath, root);
        SongInformationAccessor failingInfo = new FailingSongInformationAccessor(infoPath.toString(), 1);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, failingInfo);

        assertEquals(0, countRows(dbPath, "song"));
        assertEquals(0, countRows(infoPath, "information"));
        assertIntegrityOk(dbPath);
        assertIntegrityOk(infoPath);

        SQLiteSongDatabaseAccessor resumed = newAccessor(dbPath, root);
        SongInformationAccessor normalInfo = new SongInformationAccessor(infoPath.toString());
        resumed.updateSongDatas(null, new String[] { root.toString() }, false, normalInfo);

        assertEquals(2, countRows(dbPath, "song"));
        assertEquals(2, countRows(infoPath, "information"));
        assertIntegrityOk(dbPath);
        assertIntegrityOk(infoPath);
    }

    @Test
    void reportsImportProgressAcrossScanParseWriteAndComplete() throws Exception {
        Path root = tempDir.resolve("songs");
        createChart(root.resolve("pack"), "first.bms", "First Song", false);
        createChart(root.resolve("pack"), "second.bmson", "Second Song", true);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        List<SongDatabaseAccessor.SongDatabaseImportProgress> progressEvents =
                Collections.synchronizedList(new ArrayList<SongDatabaseAccessor.SongDatabaseImportProgress>());

        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null, progressEvents::add);

        assertFalse(progressEvents.isEmpty());
        assertEquals(SongDatabaseAccessor.SongDatabaseImportPhase.SCANNING, progressEvents.get(0).getPhase());
        assertTrue(progressEvents.stream()
                .anyMatch(progress -> progress.getPhase() == SongDatabaseAccessor.SongDatabaseImportPhase.PARSING));
        assertTrue(progressEvents.stream()
                .anyMatch(progress -> progress.getPhase() == SongDatabaseAccessor.SongDatabaseImportPhase.WRITING));

        SongDatabaseAccessor.SongDatabaseImportProgress last = progressEvents.get(progressEvents.size() - 1);
        assertEquals(SongDatabaseAccessor.SongDatabaseImportPhase.COMPLETE, last.getPhase());
        assertEquals(2, last.getProcessedSongs());
        assertEquals(2, last.getTotalSongs());
    }

    @Test
    void reportsFailedProgressWhenImporterCrashes() throws Exception {
        Path root = tempDir.resolve("songs");
        createChart(root.resolve("pack"), "crash.bms", "Crash Song", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.addPlugin((model, song) -> {
            if ("Crash Song".equals(song.getTitle())) {
                throw new RuntimeException("simulated crash");
            }
        });

        List<SongDatabaseAccessor.SongDatabaseImportProgress> progressEvents =
                Collections.synchronizedList(new ArrayList<SongDatabaseAccessor.SongDatabaseImportProgress>());

        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null, progressEvents::add);

        assertFalse(progressEvents.isEmpty());
        SongDatabaseAccessor.SongDatabaseImportProgress last = progressEvents.get(progressEvents.size() - 1);
        assertEquals(SongDatabaseAccessor.SongDatabaseImportPhase.FAILED, last.getPhase());
    }

    @ParameterizedTest(name = "persists computed song information for extension={0}")
    @MethodSource("analysisExtensions")
    void persistsComputedSongInformation(String extension) throws Exception {
        Path root = tempDir.resolve("songs");
        Path chart = createChart(root.resolve("pack"), "info." + extension, "Info Song", true);
        Path dbPath = tempDir.resolve("song.db");
        Path infoPath = tempDir.resolve("songinfo.db");

        SQLiteSongDatabaseAccessor accessor = newAccessor(dbPath, root);
        SongInformationAccessor infoAccessor = new SongInformationAccessor(infoPath.toString());
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, infoAccessor);

        SongInformation persisted = new SongInformationAccessor(infoPath.toString())
                .getInformation(readExpectedSongData(chart, extension).getSha256());
        SongInformation expected = readExpectedSongData(chart, extension).getInformation();

        assertTrue(persisted != null);
        assertEquals(expected.getSha256(), persisted.getSha256());
        assertEquals(expected.getN(), persisted.getN());
        assertEquals(expected.getLn(), persisted.getLn());
        assertEquals(expected.getS(), persisted.getS());
        assertEquals(expected.getLs(), persisted.getLs());
        assertEquals(expected.getDistribution(), persisted.getDistribution());
        assertEquals(expected.getSpeedchange(), persisted.getSpeedchange());
        assertEquals(expected.getLanenotes(), persisted.getLanenotes());
        assertEquals(expected.getDensity(), persisted.getDensity());
        assertEquals(expected.getPeakdensity(), persisted.getPeakdensity());
        assertEquals(expected.getEnddensity(), persisted.getEnddensity());
        assertEquals(expected.getMainbpm(), persisted.getMainbpm());
        assertEquals(expected.getTotal(), persisted.getTotal());
    }

    @ParameterizedTest(name = "keeps chart hash and in-memory analysis for extension={0}")
    @MethodSource("analysisExtensions")
    void keepsChartHashAndInMemoryAnalysis(String extension) throws Exception {
        Path root = tempDir.resolve("songs");
        Path chart = createChart(root.resolve("pack"), "hash." + extension, "Hash Song", true);
        Path dbPath = tempDir.resolve("song.db");

        SQLiteSongDatabaseAccessor accessor = newAccessor(dbPath, root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        SongData imported = accessor.getSongDatas("path", chart.toString())[0];
        SongData expected = readExpectedSongData(chart, extension);

        assertEquals(expected.getCharthash(), imported.getCharthash());
        assertTrue(expected.getInformation() != null);
        assertFalse(expected.getCharthash() == null || expected.getCharthash().isEmpty());
    }

    @Test
    void importsSongsAcrossMultipleParseBatches() throws Exception {
        Path root = tempDir.resolve("songs");
        Path folder = root.resolve("pack");
        for (int i = 0; i < 600; i++) {
            createChart(folder, "song-" + i + ".bms", "Song " + i, false);
        }

        Path dbPath = tempDir.resolve("song.db");
        Path infoPath = tempDir.resolve("songinfo.db");
        SQLiteSongDatabaseAccessor accessor = newAccessor(dbPath, root);
        SongInformationAccessor infoAccessor = new SongInformationAccessor(infoPath.toString());

        accessor.updateSongDatas(null, new String[] { root.toString() }, false, infoAccessor);

        assertEquals(600, countRows(dbPath, "song"));
        assertEquals(600, countRows(infoPath, "information"));
        assertIntegrityOk(dbPath);
        assertIntegrityOk(infoPath);
    }

    @Test
    void getsSongsByHashesUsingRequestedOrderAndBothHashTypes() throws Exception {
        Path root = tempDir.resolve("songs");
        Path firstChart = createChart(root.resolve("pack"), "first.bms", "First Song", false);
        Path secondChart = createChart(root.resolve("pack"), "second.bms", "Second Song", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        SongData first = accessor.getSongDatas("path", firstChart.toString())[0];
        SongData second = accessor.getSongDatas("path", secondChart.toString())[0];

        SongData[] byHashes = accessor.getSongDatas(new String[] { second.getSha256(), first.getMd5() });
        assertEquals(2, byHashes.length);
        assertEquals("Second Song", byHashes[0].getTitle());
        assertEquals("First Song", byHashes[1].getTitle());
    }

    @Test
    void textSearchFindsSongsAcrossTitleArtistAndGenreFields() throws Exception {
        Path root = tempDir.resolve("songs");
        createChart(root.resolve("pack"), "alpha.bms", "Alpha Song", false);
        createFile(root.resolve("pack").resolve("beta.bms"), ""
                + "#PLAYER 1\n"
                + "#GENRE SEARCHABLE\n"
                + "#TITLE Hidden\n"
                + "#ARTIST Needle Artist\n"
                + "#BPM 120\n"
                + "#PLAYLEVEL 3\n"
                + "#RANK 2\n"
                + "#TOTAL 100\n"
                + "#WAV01 sample.wav\n"
                + "#00111:0100\n");

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        assertEquals(1, accessor.getSongDatasByText("Alpha").length);
        assertEquals(1, accessor.getSongDatasByText("Needle Artist").length);
        assertEquals(1, accessor.getSongDatasByText("SEARCHABLE").length);
    }

    @Test
    void setSongDatasPersistsUpdatedFields() throws Exception {
        Path root = tempDir.resolve("songs");
        Path chart = createChart(root.resolve("pack"), "editable.bms", "Editable Song", false);

        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), root);
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, null);

        SongData song = accessor.getSongDatas("path", chart.toString())[0];
        song.setTag("updated-tag");
        song.setFavorite(SongData.FAVORITE_SONG | SongData.FAVORITE_CHART);
        song.setPreview("manual-preview.ogg");
        accessor.setSongDatas(new SongData[] { song });

        SongData updated = accessor.getSongDatas("path", chart.toString())[0];
        assertEquals("updated-tag", updated.getTag());
        assertEquals(SongData.FAVORITE_SONG | SongData.FAVORITE_CHART, updated.getFavorite());
        assertEquals("manual-preview.ogg", updated.getPreview());
    }

    @Test
    void getSongDatasSqlSupportsScoreAndInformationJoins() throws Exception {
        Path root = tempDir.resolve("songs");
        Path chart = createChart(root.resolve("pack"), "joined.bms", "Joined Song", false);
        Path dbPath = tempDir.resolve("song.db");
        Path scorePath = tempDir.resolve("score.db");
        Path scoreLogPath = tempDir.resolve("scorelog.db");
        Path infoPath = tempDir.resolve("songinfo.db");

        SQLiteSongDatabaseAccessor accessor = newAccessor(dbPath, root);
        SongInformationAccessor infoAccessor = new SongInformationAccessor(infoPath.toString());
        accessor.updateSongDatas(null, new String[] { root.toString() }, false, infoAccessor);

        SongData imported = accessor.getSongDatas("path", chart.toString())[0];
        createScoreDb(scorePath);
        createScoreLogDb(scoreLogPath);
        insertScoreSha256(scorePath, imported.getSha256());

        SongData[] viaScoreJoin = accessor.getSongDatas("score.sha256 IS NOT NULL", scorePath.toString(),
                scoreLogPath.toString(), null);
        SongData[] viaInfoJoin = accessor.getSongDatas("information.sha256 IS NOT NULL", scorePath.toString(),
                scoreLogPath.toString(), infoPath.toString());

        assertEquals(1, viaScoreJoin.length);
        assertEquals("Joined Song", viaScoreJoin[0].getTitle());
        assertEquals(1, viaInfoJoin.length);
        assertEquals(imported.getSha256(), viaInfoJoin[0].getSha256());
    }

    @Test
    void updateSongDatasWithEmptyRootsReportsFailureAndDoesNothing() throws Exception {
        SQLiteSongDatabaseAccessor accessor = newAccessor(tempDir.resolve("song.db"), tempDir.resolve("songs"));
        List<SongDatabaseAccessor.SongDatabaseImportProgress> progressEvents =
                Collections.synchronizedList(new ArrayList<SongDatabaseAccessor.SongDatabaseImportProgress>());

        accessor.updateSongDatas(null, new String[0], false, null, progressEvents::add);

        assertEquals(1, progressEvents.size());
        assertEquals(SongDatabaseAccessor.SongDatabaseImportPhase.FAILED, progressEvents.get(0).getPhase());
        assertEquals(0, progressEvents.get(0).getProcessedSongs());
        assertEquals(0, progressEvents.get(0).getTotalSongs());
    }

    private static Stream<Arguments> importMetadataMatrix() {
        return Stream.of(CHART_EXTENSIONS)
                .flatMap(extension -> Stream.of(false, true)
                        .flatMap(hasTxt -> Stream.of(false, true)
                                .map(hasPreview -> Arguments.of(extension, hasTxt, hasPreview))));
    }

    private static Stream<String> chartExtensions() {
        return Stream.of(CHART_EXTENSIONS);
    }

    private static Stream<String> analysisExtensions() {
        return Stream.of("bms", "bmson");
    }

    private SQLiteSongDatabaseAccessor newAccessor(Path dbPath, Path root) throws Exception {
        return new SQLiteSongDatabaseAccessor(dbPath.toString(), new String[] { root.toString() });
    }

    private Path createChart(Path folder, String filename, String title, boolean hasPreview) throws IOException {
        if (filename.endsWith(".bmson")) {
            return createBmsonChart(folder, filename, title, hasPreview);
        }
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

    private Path createBmsonChart(Path folder, String filename, String title, boolean hasPreview) throws IOException {
        String chart = "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"" + title + "\",\n"
                + "    \"artist\": \"Test Artist\",\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-7k\",\n"
                + "    \"chart_name\": \"NORMAL\",\n"
                + "    \"level\": 3,\n"
                + "    \"init_bpm\": 130,\n"
                + "    \"judge_rank\": 100,\n"
                + "    \"total\": 100,\n"
                + (hasPreview ? "    \"preview_music\": \"preview.ogg\",\n" : "")
                + "    \"resolution\": 240\n"
                + "  },\n"
                + "  \"lines\": [{ \"y\": 960 }],\n"
                + "  \"sound_channels\": [\n"
                + "    {\n"
                + "      \"name\": \"sample.wav\",\n"
                + "      \"notes\": [\n"
                + "        { \"x\": 1, \"y\": 0, \"l\": 0, \"c\": false }\n"
                + "      ]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"bga\": {\n"
                + "    \"bga_header\": [],\n"
                + "    \"bga_events\": [],\n"
                + "    \"layer_events\": [],\n"
                + "    \"poor_events\": []\n"
                + "  }\n"
                + "}\n";
        return createFile(folder.resolve(filename), chart);
    }

    private Path createFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        assertTrue(Files.exists(path));
        return path;
    }

    private SongData readExpectedSongData(Path chart, String extension) throws Exception {
        BMSModel model = extension.equals("bmson")
                ? new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE).decode(chart)
                : new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(chart);
        return new SongData(model, false);
    }

    private void assertIntegrityOk(Path dbPath) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            assertTrue(rs.next());
            assertEquals("ok", rs.getString(1));
        }
    }

    private int countRows(Path dbPath, String tableName) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
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

    private static class FailingSongInformationAccessor extends SongInformationAccessor {
        private final AtomicInteger updates = new AtomicInteger();
        private final int failAfter;

        private FailingSongInformationAccessor(String filepath, int failAfter) throws ClassNotFoundException {
            super(filepath);
            this.failAfter = failAfter;
        }

        @Override
        public void update(BMSModel model) {
            super.update(model);
            maybeFail();
        }

        @Override
        public void update(SongInformation info) {
            super.update(info);
            maybeFail();
        }

        private void maybeFail() {
            if (updates.incrementAndGet() > failAfter) {
                throw new RuntimeException("simulated songinfo crash");
            }
        }
    }
}
