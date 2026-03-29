package bms.player.beatoraja.song;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.BMSONDecoder;
import bms.player.beatoraja.Validatable;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class NoSqlSongDatabaseAccessor implements SongDatabaseAccessor {

	private static final int PARSE_BATCH_SIZE = Runtime.getRuntime().availableProcessors() * 4;
	private static final int SHARD_COUNT = 16;
	private static final String STORE_VERSION = "nosql-v1";
	private static final String SHARDS_DIR = "shards";
	private static final String FOLDERS_FILE = "folders.bin";
	private static final String META_FILE = "meta.properties";
	private static final String BACKUP_SUFFIX = ".previous";
	private static final int BINARY_MAGIC = 0x42524A31;

	private static final String CREATE_QUERY_MIRROR_SQL =
			"CREATE TABLE song (" +
					"md5 TEXT, sha256 TEXT, title TEXT, subtitle TEXT, genre TEXT, artist TEXT, subartist TEXT, tag TEXT, " +
					"path TEXT PRIMARY KEY, folder TEXT, stagefile TEXT, banner TEXT, backbmp TEXT, preview TEXT, parent TEXT, " +
					"level INTEGER, difficulty INTEGER, maxbpm INTEGER, minbpm INTEGER, length INTEGER, mode INTEGER, judge INTEGER, " +
					"feature INTEGER, content INTEGER, date INTEGER, favorite INTEGER, adddate INTEGER, notes INTEGER, charthash TEXT" +
					")";

	private final Path storeDir;
	private final Path backupDir;
	private final Path queryMirrorPath;
	private final Path root;
	private final ResultSetHandler<List<SongData>> songHandler = new BeanListHandler<SongData>(SongData.class);
	private final List<SQLiteSongDatabaseAccessor.SongDatabaseAccessorPlugin> plugins = new ArrayList<SQLiteSongDatabaseAccessor.SongDatabaseAccessorPlugin>();

	private volatile Snapshot snapshot;
	private volatile boolean queryMirrorDirty = true;

	public NoSqlSongDatabaseAccessor(String filepath, String[] bmsroot) throws ClassNotFoundException {
		Class.forName("org.sqlite.JDBC");
		this.storeDir = Paths.get(filepath + ".nosql");
		this.backupDir = storeDir.resolveSibling(storeDir.getFileName().toString() + BACKUP_SUFFIX);
		this.queryMirrorPath = storeDir.resolve("query-mirror.db");
		this.root = Paths.get(".");
		this.snapshot = loadSnapshot();
	}

	public void addPlugin(SQLiteSongDatabaseAccessor.SongDatabaseAccessorPlugin plugin) {
		plugins.add(plugin);
	}

	@Override
	public SongData[] getSongDatas(String key, String value) {
		try {
			return Validatable.removeInvalidElements(findSongsByKey(snapshot, key, value)).toArray(SongData.EMPTY);
		} catch (Exception e) {
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
			return SongData.EMPTY;
		}
	}

	@Override
	public SongData[] getSongDatas(String[] hashes) {
		try {
			List<SongData> results = new ArrayList<SongData>();
			Set<String> seenPaths = new HashSet<String>();
			for (String hash : hashes) {
				List<String> paths = hash.length() > 32 ? snapshot.sha256Index.get(hash) : snapshot.md5Index.get(hash);
				if (paths == null) {
					continue;
				}
				for (String path : paths) {
					if (seenPaths.add(path)) {
						PersistedSong song = snapshot.songByPath(path);
						if (song != null) {
							results.add(song.toSongData());
						}
					}
				}
			}
			return Validatable.removeInvalidElements(results).toArray(SongData.EMPTY);
		} catch (Exception e) {
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
			return SongData.EMPTY;
		}
	}

	@Override
	public SongData[] getSongDatas(String sql, String score, String scorelog, String info) {
		try {
			Path mirror = ensureQueryMirror();
			try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + mirror);
				 Statement stmt = conn.createStatement()) {
				stmt.execute("ATTACH DATABASE '" + score + "' as scoredb");
				stmt.execute("ATTACH DATABASE '" + scorelog + "' as scorelogdb");
				List<SongData> songs;
				if (info != null) {
					stmt.execute("ATTACH DATABASE '" + info + "' as infodb");
					String query = "SELECT DISTINCT md5, song.sha256 AS sha256, title, subtitle, genre, artist, subartist,path,folder,stagefile,banner,backbmp,parent,level,difficulty,"
							+ "maxbpm,minbpm,song.mode AS mode, judge, feature, content, song.date AS date, favorite, song.notes AS notes, adddate, preview, length, charthash"
							+ " FROM song INNER JOIN (information LEFT OUTER JOIN (score LEFT OUTER JOIN scorelog ON score.sha256 = scorelog.sha256) ON information.sha256 = score.sha256) "
							+ "ON song.sha256 = information.sha256 WHERE " + sql;
					ResultSet rs = stmt.executeQuery(query);
					songs = songHandler.handle(rs);
					stmt.execute("DETACH DATABASE infodb");
				} else {
					String query = "SELECT DISTINCT md5, song.sha256 AS sha256, title, subtitle, genre, artist, subartist,path,folder,stagefile,banner,backbmp,parent,level,difficulty,"
							+ "maxbpm,minbpm,song.mode AS mode, judge, feature, content, song.date AS date, favorite, song.notes AS notes, adddate, preview, length, charthash"
							+ " FROM song LEFT OUTER JOIN (score LEFT OUTER JOIN scorelog ON score.sha256 = scorelog.sha256) ON song.sha256 = score.sha256 WHERE " + sql;
					ResultSet rs = stmt.executeQuery(query);
					songs = songHandler.handle(rs);
				}
				stmt.execute("DETACH DATABASE scorelogdb");
				stmt.execute("DETACH DATABASE scoredb");
				return Validatable.removeInvalidElements(songs).toArray(SongData.EMPTY);
			}
		} catch (Throwable e) {
			e.printStackTrace();
			return SongData.EMPTY;
		}
	}

	@Override
	public void setSongDatas(SongData[] songs) {
		try {
			MutableSnapshot mutable = snapshot.mutableCopy();
			for (SongData song : songs) {
				mutable.putSong(PersistedSong.fromSongData(song));
			}
			Snapshot updated = mutable.freeze();
			writeSnapshot(updated, null, 0, 0);
			snapshot = updated;
			invalidateQueryMirror();
		} catch (Exception e) {
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
		}
	}

	@Override
	public SongData[] getSongDatasByText(String text) {
		String lowered = text.toLowerCase(Locale.ROOT);
		List<SongData> matches = new ArrayList<SongData>();
		for (PersistedSong song : snapshot.allSongs()) {
			String haystack = (song.title + " " + song.subtitle + " " + song.artist + " " + song.subartist + " " + song.genre)
					.toLowerCase(Locale.ROOT);
			if (haystack.contains(lowered)) {
				matches.add(song.toSongData());
			}
		}
		return Validatable.removeInvalidElements(matches).toArray(SongData.EMPTY);
	}

	@Override
	public FolderData[] getFolderDatas(String key, String value) {
		List<FolderData> results = new ArrayList<FolderData>();
		if ("path".equals(key)) {
			PersistedFolder folder = snapshot.foldersByPath.get(value);
			if (folder != null) {
				results.add(folder.toFolderData());
			}
		} else if ("parent".equals(key)) {
			for (PersistedFolder folder : snapshot.allFolders()) {
				if (value.equals(folder.parent)) {
					results.add(folder.toFolderData());
				}
			}
		} else {
			for (PersistedFolder folder : snapshot.allFolders()) {
				if (value.equals(folder.get(key))) {
					results.add(folder.toFolderData());
				}
			}
		}
		return results.toArray(FolderData.EMPTY);
	}

	@Override
	public void updateSongDatas(String updatepath, String[] bmsroot, boolean updateAll, SongInformationAccessor info) {
		updateSongDatas(updatepath, bmsroot, updateAll, info, null);
	}

	@Override
	public void updateSongDatas(String updatepath, String[] bmsroot, boolean updateAll, SongInformationAccessor info,
			SongDatabaseImportListener listener) {
		if (bmsroot == null || bmsroot.length == 0) {
			Logger.getGlobal().warning("楽曲ルートフォルダが登録されていません");
			if (listener != null) {
				listener.onProgress(new SongDatabaseImportProgress(SongDatabaseImportPhase.FAILED, 0, 0,
						"No BMS root folders are configured."));
			}
			return;
		}
		new SongDatabaseUpdater(snapshot, updateAll, bmsroot, info, listener)
				.updateSongDatas(updatepath == null ? Stream.of(bmsroot).map(Paths::get) : Stream.of(Paths.get(updatepath)));
	}

	private List<SongData> findSongsByKey(Snapshot snapshot, String key, String value) {
		List<SongData> songs = new ArrayList<SongData>();
		if ("path".equals(key)) {
			PersistedSong song = snapshot.songByPath(value);
			if (song != null) {
				songs.add(song.toSongData());
			}
			return songs;
		}
		if ("folder".equals(key)) {
			List<String> paths = snapshot.folderIndex.get(value);
			if (paths != null) {
				for (String path : paths) {
					PersistedSong song = snapshot.songByPath(path);
					if (song != null) {
						songs.add(song.toSongData());
					}
				}
			}
			return songs;
		}
		if ("parent".equals(key)) {
			List<String> paths = snapshot.parentIndex.get(value);
			if (paths != null) {
				for (String path : paths) {
					PersistedSong song = snapshot.songByPath(path);
					if (song != null) {
						songs.add(song.toSongData());
					}
				}
			}
			return songs;
		}
		for (PersistedSong song : snapshot.allSongs()) {
			if (value.equals(song.get(key))) {
				songs.add(song.toSongData());
			}
		}
		return songs;
	}

	private synchronized Path ensureQueryMirror() throws IOException, SQLException {
		if (!queryMirrorDirty && Files.exists(queryMirrorPath)) {
			return queryMirrorPath;
		}
		Files.createDirectories(storeDir);
		Path tempMirror = queryMirrorPath.resolveSibling("query-mirror-" + System.nanoTime() + ".db");
		Files.deleteIfExists(tempMirror);
		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempMirror);
			 Statement stmt = conn.createStatement()) {
			stmt.execute(CREATE_QUERY_MIRROR_SQL);
			conn.setAutoCommit(false);
			try (PreparedStatement insert = conn.prepareStatement(
					"INSERT INTO song (md5, sha256, title, subtitle, genre, artist, subartist, tag, path, folder, stagefile, banner, backbmp, preview, parent, level, difficulty, maxbpm, minbpm, length, mode, judge, feature, content, date, favorite, adddate, notes, charthash) "
							+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
				for (PersistedSong song : snapshot.allSongs()) {
					song.bind(insert);
					insert.addBatch();
				}
				insert.executeBatch();
			}
			conn.commit();
		}
		Files.deleteIfExists(queryMirrorPath);
		Files.move(tempMirror, queryMirrorPath, StandardCopyOption.REPLACE_EXISTING);
		queryMirrorDirty = false;
		return queryMirrorPath;
	}

	private Snapshot loadSnapshot() {
		restoreBackupIfNeeded();
		if (!Files.isDirectory(storeDir)) {
			return Snapshot.empty();
		}
		try {
			Properties meta = new Properties();
			try (InputStream in = Files.newInputStream(storeDir.resolve(META_FILE))) {
				meta.load(in);
			}
			if (!STORE_VERSION.equals(meta.getProperty("version"))) {
				return Snapshot.empty();
			}
			List<Map<String, PersistedSong>> shards = new ArrayList<Map<String, PersistedSong>>(SHARD_COUNT);
			Path shardsDir = storeDir.resolve(SHARDS_DIR);
			for (int i = 0; i < SHARD_COUNT; i++) {
				Path shardFile = shardsDir.resolve("songs-" + i + ".bin");
				if (Files.exists(shardFile)) {
					shards.add(readSongShard(shardFile));
				} else {
					shards.add(new HashMap<String, PersistedSong>());
				}
			}
			Map<String, PersistedFolder> folders = Files.exists(storeDir.resolve(FOLDERS_FILE))
					? readFolders(storeDir.resolve(FOLDERS_FILE))
					: new HashMap<String, PersistedFolder>();
			return Snapshot.fromData(shards, folders);
		} catch (Exception e) {
			Logger.getGlobal().severe("NoSQL song database load failed: " + e.getMessage());
			return Snapshot.empty();
		}
	}

	private void writeSnapshot(Snapshot snapshot, SnapshotWriteListener listener, int completedSteps, int totalSteps) throws IOException {
		Path tempDir = storeDir.resolveSibling(storeDir.getFileName().toString() + ".tmp-" + System.nanoTime());
		Path shardsDir = tempDir.resolve(SHARDS_DIR);
		Files.createDirectories(shardsDir);
		AtomicInteger stepCounter = new AtomicInteger(completedSteps);
		IntRange.range(0, SHARD_COUNT).parallelStream().forEach(index -> {
			try {
				writeSongShard(shardsDir.resolve("songs-" + index + ".bin"), snapshot.songShards.get(index));
				if (listener != null) {
					int progress = stepCounter.incrementAndGet();
					listener.onStep(progress, totalSteps, "Saving library data part " + (index + 1) + "/" + SHARD_COUNT);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		writeFolders(tempDir.resolve(FOLDERS_FILE), snapshot.foldersByPath);
		if (listener != null) {
			listener.onStep(stepCounter.incrementAndGet(), totalSteps, "Writing folder index");
		}
		Properties meta = new Properties();
		meta.setProperty("version", STORE_VERSION);
		try (OutputStream out = Files.newOutputStream(tempDir.resolve(META_FILE))) {
			meta.store(out, "NoSQL song database");
		}
		if (listener != null) {
			listener.onStep(stepCounter.incrementAndGet(), totalSteps, "Writing metadata");
		}
		Files.deleteIfExists(backupDir);
		if (Files.isDirectory(storeDir)) {
			Files.move(storeDir, backupDir, StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			Files.move(tempDir, storeDir, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			if (!Files.exists(storeDir) && Files.isDirectory(backupDir)) {
				Files.move(backupDir, storeDir, StandardCopyOption.REPLACE_EXISTING);
			}
			deleteRecursively(tempDir);
			throw e;
		}
		if (listener != null) {
			listener.onStep(stepCounter.incrementAndGet(), totalSteps, "Swapping active snapshot");
		}
		if (Files.isDirectory(backupDir)) {
			deleteRecursively(backupDir);
		}
	}

	private void invalidateQueryMirror() {
		queryMirrorDirty = true;
		try {
			Files.deleteIfExists(queryMirrorPath);
		} catch (IOException ignored) {
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T readLegacyObject(Path path) throws IOException, ClassNotFoundException {
		try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			return (T) in.readObject();
		}
	}

	private Map<String, PersistedSong> readSongShard(Path path) throws IOException, ClassNotFoundException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			if (in.readInt() != BINARY_MAGIC) {
				throw new IOException("Unexpected binary shard header");
			}
			int version = in.readInt();
			if (version != 1) {
				throw new IOException("Unsupported shard version: " + version);
			}
			int size = in.readInt();
			Map<String, PersistedSong> shard = new HashMap<String, PersistedSong>(Math.max(16, size * 2));
			for (int i = 0; i < size; i++) {
				PersistedSong song = new PersistedSong();
				song.readFrom(in);
				shard.put(song.path, song);
			}
			return shard;
		} catch (IOException e) {
			return readLegacyObject(path);
		}
	}

	private Map<String, PersistedFolder> readFolders(Path path) throws IOException, ClassNotFoundException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			if (in.readInt() != BINARY_MAGIC) {
				throw new IOException("Unexpected folder header");
			}
			int version = in.readInt();
			if (version != 1) {
				throw new IOException("Unsupported folder version: " + version);
			}
			int size = in.readInt();
			Map<String, PersistedFolder> folders = new HashMap<String, PersistedFolder>(Math.max(16, size * 2));
			for (int i = 0; i < size; i++) {
				PersistedFolder folder = PersistedFolder.readFrom(in);
				folders.put(folder.path, folder);
			}
			return folders;
		} catch (IOException e) {
			return readLegacyObject(path);
		}
	}

	private void writeSongShard(Path path, Map<String, PersistedSong> shard) throws IOException {
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
			out.writeInt(BINARY_MAGIC);
			out.writeInt(1);
			out.writeInt(shard.size());
			for (PersistedSong song : shard.values()) {
				song.writeTo(out);
			}
		}
	}

	private void writeFolders(Path path, Map<String, PersistedFolder> folders) throws IOException {
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
			out.writeInt(BINARY_MAGIC);
			out.writeInt(1);
			out.writeInt(folders.size());
			for (PersistedFolder folder : folders.values()) {
				folder.writeTo(out);
			}
		}
	}

	private static void writeString(DataOutputStream out, String value) throws IOException {
		byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	private static String readString(DataInputStream in) throws IOException {
		int length = in.readInt();
		if (length < 0) {
			throw new IOException("Negative string length");
		}
		byte[] bytes = new byte[length];
		in.readFully(bytes);
		return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
	}

	private void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (Stream<Path> files = Files.walk(path)) {
			files.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	private void restoreBackupIfNeeded() {
		try {
			if (!Files.isDirectory(storeDir) && Files.isDirectory(backupDir)) {
				Files.move(backupDir, storeDir, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Logger.getGlobal().severe("NoSQL song database backup restore failed: " + e.getMessage());
		}
	}

	private class SongDatabaseUpdater {
		private final Snapshot baseSnapshot;
		private final boolean updateAll;
		private final String[] bmsroot;
		private final SongInformationAccessor info;
		private final SongDatabaseImportListener listener;

		private SongDatabaseUpdater(Snapshot baseSnapshot, boolean updateAll, String[] bmsroot,
				SongInformationAccessor info, SongDatabaseImportListener listener) {
			this.baseSnapshot = baseSnapshot;
			this.updateAll = updateAll;
			this.bmsroot = bmsroot;
			this.info = info;
			this.listener = listener;
		}

		private void updateSongDatas(Stream<Path> paths) {
			long time = System.currentTimeMillis();
			UpdaterState property = new UpdaterState(baseSnapshot, info, listener, bmsroot);
			property.reportProgress(SongDatabaseImportPhase.SCANNING, 0, 0, "Scanning song folders...");
			boolean success = false;
			if (info != null) {
				info.startUpdate();
			}
			try {
				if (!updateAll) {
					property.pruneOutsideRoots();
					property.preloadExistingState();
				}
				paths.forEach(path -> {
					try {
						new BMSFolder(path, bmsroot).processDirectory(property);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});

				property.totalSongs = property.songJobs.size();
				property.reportProgress(SongDatabaseImportPhase.PARSING, 0, property.totalSongs,
						"Parsing songs (0/" + property.totalSongs + ")");
				int totalWriteSteps = 1 + SHARD_COUNT + 3;
				int completedWriteSteps = 0;
				
				// Process in batches with bounded concurrency to limit memory pressure
				final int maxConcurrency = Runtime.getRuntime().availableProcessors();
				final java.util.concurrent.Semaphore parseSemaphore = new java.util.concurrent.Semaphore(maxConcurrency);
				for (int start = 0; start < property.songJobs.size(); start += PARSE_BATCH_SIZE) {
					final int end = Math.min(start + PARSE_BATCH_SIZE, property.songJobs.size());
					try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
						List<Future<SongImportResult>> futures = property.songJobs.subList(start, end).stream()
								.map(job -> executor.submit(() -> {
									parseSemaphore.acquire();
									try {
										return decodeSong(job, property);
									} finally {
										parseSemaphore.release();
									}
								}))
								.toList();

						List<SongImportResult> batchResults = new ArrayList<>(futures.size());
						for (Future<SongImportResult> future : futures) {
							try {
								SongImportResult result = future.get();
								if (result != null) {
									batchResults.add(result);
								}
							} catch (ExecutionException e) {
								Throwable cause = e.getCause();
								if (cause instanceof RuntimeException re) {
									throw re;
								}
								Logger.getGlobal().severe("Failed to parse song: " + e.getMessage());
							} catch (InterruptedException e) {
								Logger.getGlobal().severe("Failed to parse song: " + e.getMessage());
							}
						}
						applyParsedSongs(property, batchResults);
					}
				}

				property.reportProgress(SongDatabaseImportPhase.WRITING, completedWriteSteps, totalWriteSteps,
						"Updating library index");
				applyFinalChanges(property);
				completedWriteSteps++;
				property.reportProgress(SongDatabaseImportPhase.WRITING, completedWriteSteps, totalWriteSteps,
						"Updated library index");
				Snapshot updated = property.freeze();
				writeSnapshot(updated, (processed, total, message) ->
						property.reportProgress(SongDatabaseImportPhase.WRITING, processed, total, message),
						completedWriteSteps, totalWriteSteps);
				snapshot = updated;
				invalidateQueryMirror();
				success = true;
			} catch (Exception e) {
				property.reportProgress(SongDatabaseImportPhase.FAILED, (int) property.parsedSongs.sum(), property.totalSongs,
						"Import failed. See log for details.");
				Logger.getGlobal().severe("楽曲データベース更新時の例外:" + e.getMessage());
				e.printStackTrace();
			} finally {
				if (info != null) {
					if (success) {
						info.endUpdate();
					} else {
						info.rollbackUpdate();
					}
				}
			}
			if (success) {
				property.reportProgress(SongDatabaseImportPhase.COMPLETE, property.totalSongs, property.totalSongs,
						"Import complete (" + property.count.sum() + " songs updated)");
			}
			long now = System.currentTimeMillis();
			long totalCount = property.count.sum();
			Logger.getGlobal().info("楽曲更新完了 : Time - " + (now - time) + "ms 1曲あたりの時間 - "
					+ (totalCount > 0 ? (now - time) / totalCount : "不明") + "ms");
		}
	}

	private class BMSFolder {
		private final Path path;
		private final String[] bmsroot;
		private boolean updateFolder = true;
		private boolean txt = false;
		private final List<Path> bmsfiles = new ArrayList<Path>();
		private final List<BMSFolder> dirs = new ArrayList<BMSFolder>();
		private String previewpath = null;

		private BMSFolder(Path path, String[] bmsroot) {
			this.path = path;
			this.bmsroot = bmsroot;
		}

		private void processDirectory(UpdaterState property) throws IOException {
			final String folderKey = SongUtils.crc32(path.toString(), bmsroot, root.toString());
			final Map<String, ExistingSongRecord> records = property.songsByFolder.getOrDefault(folderKey, Collections.<String, ExistingSongRecord>emptyMap());
			final Map<String, ExistingFolderRecord> folders = property.foldersByParent.getOrDefault(folderKey, Collections.<String, ExistingFolderRecord>emptyMap());
			try (DirectoryStream<Path> paths = Files.newDirectoryStream(path)) {
				for (Path current : paths) {
					if (Files.isDirectory(current)) {
						dirs.add(new BMSFolder(current, bmsroot));
					} else {
						String lower = current.getFileName().toString().toLowerCase(Locale.ROOT);
						if (!txt && lower.endsWith(".txt")) {
							txt = true;
						}
						if (previewpath == null && lower.startsWith("preview")
								&& (lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".mp3") || lower.endsWith(".flac"))) {
							previewpath = current.getFileName().toString();
						}
						if (lower.endsWith(".bms") || lower.endsWith(".bme") || lower.endsWith(".bml") || lower.endsWith(".pms") || lower.endsWith(".bmson")) {
							bmsfiles.add(current);
						}
					}
				}
			}
			final boolean containsBMS = !bmsfiles.isEmpty();
			collectFolderJobs(records, property);
			property.reportScannedFolder(property.songJobs.size());

			final Set<String> remainingFolders = new HashSet<String>(folders.keySet());
			for (BMSFolder dir : dirs) {
				String relative = (dir.path.startsWith(root) ? root.relativize(dir.path).toString() : dir.path.toString()) + File.separatorChar;
				ExistingFolderRecord record = folders.get(relative);
				if (record != null) {
					remainingFolders.remove(relative);
					if (record.date == Files.getLastModifiedTime(dir.path).toMillis() / 1000) {
						dir.updateFolder = false;
					}
				}
			}

			if (!containsBMS) {
				for (BMSFolder dir : dirs) {
					dir.processDirectory(property);
				}
			}

			if (updateFolder) {
				String folderPath = (path.startsWith(root) ? root.relativize(path).toString() : path.toString()) + File.separatorChar;
				Path parentPath = path.getParent() != null ? path.getParent() : path.toAbsolutePath().getParent();
				property.folderUpserts.add(new PersistedFolder(
						path.getFileName().toString(),
						"",
						"",
						folderPath,
						"",
						SongUtils.crc32(parentPath.toString(), bmsroot, root.toString()),
						0,
						(int) (Files.getLastModifiedTime(path).toMillis() / 1000),
						(int) property.updatetime,
						0));
			}
			for (String staleFolderPath : remainingFolders) {
				property.staleFolderPrefixes.add(staleFolderPath);
			}
		}

		private void collectFolderJobs(Map<String, ExistingSongRecord> recordsByPath, UpdaterState property) {
			Set<String> seen = new HashSet<String>(bmsfiles.size());
			for (Path chart : bmsfiles) {
				String pathname = (chart.startsWith(root) ? root.relativize(chart).toString() : chart.toString());
				seen.add(pathname);
				property.songJobs.add(new SongParseJob(chart, pathname, recordsByPath.get(pathname), txt, previewpath, bmsroot));
			}
			for (ExistingSongRecord record : recordsByPath.values()) {
				if (!seen.contains(record.path)) {
					property.staleSongPaths.add(record.path);
				}
			}
		}
	}

	private SongImportResult decodeSong(SongParseJob job, UpdaterState property) {
		try {
			long lastModifiedTime = -1;
			try {
				lastModifiedTime = Files.getLastModifiedTime(job.path).toMillis() / 1000;
			} catch (IOException ignored) {
			}

			if (job.record != null && job.record.date == lastModifiedTime) {
				String oldPreview = job.record.preview;
				String newPreview = job.previewpath == null ? "" : job.previewpath;
				return SongImportResult.skip(job.pathname, !oldPreview.equals(newPreview), newPreview);
			}

			BMSModel model = job.pathname.toLowerCase(Locale.ROOT).endsWith(".bmson")
					? new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE).decode(job.path)
					: new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(job.path);
			if (model == null) {
				return null;
			}

			SongData song = new SongData(model, job.txt, property.info != null);
			if (song.getNotes() == 0 && model.getWavList().length == 0) {
				return SongImportResult.delete(job.pathname);
			}

			populateSongData(song, model, job.path, job.pathname, lastModifiedTime, job.previewpath, job.bmsroot, property);
			return SongImportResult.upsert(job.pathname, song);
		} finally {
			property.reportParsedSong();
		}
	}

	private void populateSongData(SongData song, BMSModel model, Path path, String pathname, long lastModifiedTime,
			String previewpath, String[] bmsroot, UpdaterState property) {
		if (song.getDifficulty() == 0) {
			final String fulltitle = (song.getTitle() + song.getSubtitle()).toLowerCase(Locale.ROOT);
			final String diffname = song.getSubtitle().toLowerCase(Locale.ROOT);
			if (diffname.contains("beginner")) {
				song.setDifficulty(1);
			} else if (diffname.contains("normal")) {
				song.setDifficulty(2);
			} else if (diffname.contains("hyper")) {
				song.setDifficulty(3);
			} else if (diffname.contains("another")) {
				song.setDifficulty(4);
			} else if (diffname.contains("insane") || diffname.contains("leggendaria")) {
				song.setDifficulty(5);
			} else if (fulltitle.contains("beginner")) {
				song.setDifficulty(1);
			} else if (fulltitle.contains("normal")) {
				song.setDifficulty(2);
			} else if (fulltitle.contains("hyper")) {
				song.setDifficulty(3);
			} else if (fulltitle.contains("another")) {
				song.setDifficulty(4);
			} else if (fulltitle.contains("insane") || fulltitle.contains("leggendaria")) {
				song.setDifficulty(5);
			} else if (song.getNotes() < 250) {
				song.setDifficulty(1);
			} else if (song.getNotes() < 600) {
				song.setDifficulty(2);
			} else if (song.getNotes() < 1000) {
				song.setDifficulty(3);
			} else if (song.getNotes() < 2000) {
				song.setDifficulty(4);
			} else {
				song.setDifficulty(5);
			}
		}
		if ((song.getPreview() == null || song.getPreview().length() == 0) && previewpath != null) {
			song.setPreview(previewpath);
		}
		String tag = property.tags.get(song.getSha256());
		Integer favorite = property.favorites.get(song.getSha256());
		for (SQLiteSongDatabaseAccessor.SongDatabaseAccessorPlugin plugin : plugins) {
			plugin.update(model, song);
		}
		song.setTag(tag != null ? tag : "");
		song.setPath(pathname);
		song.setFolder(SongUtils.crc32(path.getParent().toString(), bmsroot, root.toString()));
		song.setParent(SongUtils.crc32(path.getParent().getParent().toString(), bmsroot, root.toString()));
		song.setDate((int) lastModifiedTime);
		song.setFavorite(favorite != null ? favorite.intValue() : 0);
		song.setAdddate((int) property.updatetime);
	}

	private void applyParsedSongs(UpdaterState property, List<SongImportResult> results) {
		for (SongImportResult result : results) {
			Map<String, PersistedSong> shard = property.songShards.get(shardForPath(result.pathname));
			if (result.skipUpdate) {
				if (result.previewChanged) {
					PersistedSong existing = shard.get(result.pathname);
					if (existing != null) {
						PersistedSong updated = existing.copy();
						updated.preview = result.previewPath;
						shard.put(result.pathname, updated);
					}
				}
				continue;
			}
			if (result.deleteOnly) {
				shard.remove(result.pathname);
				continue;
			}
			PersistedSong persisted = PersistedSong.fromSongData(result.song);
			shard.put(result.pathname, persisted);
			if (property.info != null && result.song.getInformation() != null) {
				property.info.update(result.song.getInformation());
			}
			property.count.increment();
		}
	}

	private void applyFinalChanges(UpdaterState property) {
		for (String staleFolderPrefix : property.staleFolderPrefixes) {
			for (Map<String, PersistedSong> shard : property.songShards) {
				shard.keySet().removeIf(path -> path.startsWith(staleFolderPrefix));
			}
			property.foldersByPath.keySet().removeIf(path -> path.startsWith(staleFolderPrefix));
		}
		for (String staleSongPath : property.staleSongPaths) {
			property.songShards.get(shardForPath(staleSongPath)).remove(staleSongPath);
		}
		for (PersistedFolder folder : property.folderUpserts) {
			property.foldersByPath.put(folder.path, folder);
		}
	}

	private int shardForPath(String path) {
		return Math.floorMod(path.hashCode(), SHARD_COUNT);
	}

	private static final class ExistingSongRecord {
		private final String path;
		private final int date;
		private final String preview;
		private final String sha256;
		private final String folder;

		private ExistingSongRecord(String path, int date, String preview, String sha256, String folder) {
			this.path = path;
			this.date = date;
			this.preview = preview;
			this.sha256 = sha256;
			this.folder = folder;
		}
	}

	private static final class ExistingFolderRecord {
		private final String path;
		private final String parent;
		private final int date;

		private ExistingFolderRecord(String path, String parent, int date) {
			this.path = path;
			this.parent = parent;
			this.date = date;
		}
	}

	private static final class SongParseJob {
		private final Path path;
		private final String pathname;
		private final ExistingSongRecord record;
		private final boolean txt;
		private final String previewpath;
		private final String[] bmsroot;

		private SongParseJob(Path path, String pathname, ExistingSongRecord record, boolean txt, String previewpath, String[] bmsroot) {
			this.path = path;
			this.pathname = pathname;
			this.record = record;
			this.txt = txt;
			this.previewpath = previewpath;
			this.bmsroot = bmsroot;
		}
	}

	private static final class SongImportResult {
		private final String pathname;
		private final SongData song;
		private final boolean skipUpdate;
		private final boolean previewChanged;
		private final String previewPath;
		private final boolean deleteOnly;

		private SongImportResult(String pathname, SongData song, boolean skipUpdate, boolean previewChanged,
				String previewPath, boolean deleteOnly) {
			this.pathname = pathname;
			this.song = song;
			this.skipUpdate = skipUpdate;
			this.previewChanged = previewChanged;
			this.previewPath = previewPath;
			this.deleteOnly = deleteOnly;
		}

		private static SongImportResult skip(String pathname, boolean previewChanged, String previewPath) {
			return new SongImportResult(pathname, null, true, previewChanged, previewPath, false);
		}

		private static SongImportResult delete(String pathname) {
			return new SongImportResult(pathname, null, false, false, null, true);
		}

		private static SongImportResult upsert(String pathname, SongData song) {
			return new SongImportResult(pathname, song, false, false, null, false);
		}
	}

	private final class UpdaterState {
		private final long updatetime = Instant.now().getEpochSecond();
		private final SongInformationAccessor info;
		private final SongDatabaseImportListener listener;
		private final LongAdder count = new LongAdder();
		private final LongAdder parsedSongs = new LongAdder();
		private final Map<String, String> tags = new HashMap<String, String>();
		private final Map<String, Integer> favorites = new HashMap<String, Integer>();
		private final List<Map<String, PersistedSong>> songShards = new ArrayList<Map<String, PersistedSong>>(SHARD_COUNT);
		private final Map<String, PersistedFolder> foldersByPath = new HashMap<String, PersistedFolder>();
		private final Map<String, Map<String, ExistingSongRecord>> songsByFolder = new HashMap<String, Map<String, ExistingSongRecord>>();
		private final Map<String, Map<String, ExistingFolderRecord>> foldersByParent = new HashMap<String, Map<String, ExistingFolderRecord>>();
		private final List<SongParseJob> songJobs = new ArrayList<SongParseJob>();
		private final Set<String> staleSongPaths = new LinkedHashSet<String>();
		private final Set<String> staleFolderPrefixes = new LinkedHashSet<String>();
		private final List<PersistedFolder> folderUpserts = new ArrayList<PersistedFolder>();
		private final LongAdder scannedFolders = new LongAdder();
		private final String[] roots;
		private volatile int totalSongs;

		private UpdaterState(Snapshot baseSnapshot, SongInformationAccessor info, SongDatabaseImportListener listener, String[] roots) {
			this.info = info;
			this.listener = listener;
			this.roots = roots;
			for (int i = 0; i < SHARD_COUNT; i++) {
				Map<String, PersistedSong> shard = new HashMap<String, PersistedSong>(baseSnapshot.songShards.get(i));
				for (PersistedSong song : baseSnapshot.songShards.get(i).values()) {
					if (song.tag != null && song.tag.length() > 0) {
						tags.put(song.sha256, song.tag);
					}
					if (song.favorite > 0) {
						favorites.put(song.sha256, song.favorite);
					}
				}
				songShards.add(shard);
			}
			foldersByPath.putAll(baseSnapshot.foldersByPath);
		}

		private void pruneOutsideRoots() {
			for (Map<String, PersistedSong> shard : songShards) {
				shard.entrySet().removeIf(entry -> !withinRoots(entry.getKey(), roots));
			}
			foldersByPath.entrySet().removeIf(entry -> !withinRoots(entry.getKey(), roots)
					&& !entry.getKey().startsWith("LR2files") && !entry.getKey().endsWith(".lr2folder"));
		}

		private void preloadExistingState() {
			for (PersistedSong song : allSongs()) {
				ExistingSongRecord record = new ExistingSongRecord(song.path, song.date, emptyIfNull(song.preview), song.sha256, song.folder);
				songsByFolder.computeIfAbsent(record.folder, ignored -> new HashMap<String, ExistingSongRecord>())
						.put(record.path, record);
			}
			for (PersistedFolder folder : foldersByPath.values()) {
				ExistingFolderRecord record = new ExistingFolderRecord(folder.path, folder.parent, folder.date);
				foldersByParent.computeIfAbsent(record.parent, ignored -> new HashMap<String, ExistingFolderRecord>())
						.put(record.path, record);
			}
		}

		private Iterable<PersistedSong> allSongs() {
			List<PersistedSong> songs = new ArrayList<PersistedSong>();
			for (Map<String, PersistedSong> shard : songShards) {
				songs.addAll(shard.values());
			}
			return songs;
		}

		private Snapshot freeze() {
			List<Map<String, PersistedSong>> shards = new ArrayList<Map<String, PersistedSong>>(SHARD_COUNT);
			for (Map<String, PersistedSong> shard : songShards) {
				shards.add(new HashMap<String, PersistedSong>(shard));
			}
			return Snapshot.fromData(shards, new HashMap<String, PersistedFolder>(foldersByPath));
		}

		private void reportProgress(SongDatabaseImportPhase phase, int processedSongs, int totalSongs, String message) {
			if (listener != null) {
				listener.onProgress(new SongDatabaseImportProgress(phase, processedSongs, totalSongs, message));
			}
		}

		private void reportParsedSong() {
			parsedSongs.increment();
			long parsed = parsedSongs.sum();
			if (totalSongs <= 0) {
				return;
			}
			if (parsed == 1 || parsed == totalSongs || parsed % 32 == 0) {
				reportProgress(SongDatabaseImportPhase.PARSING, (int) parsed, totalSongs,
						"Parsing songs (" + parsed + "/" + totalSongs + ") using virtual threads");
			}
		}

		private void reportScannedFolder(int queuedSongs) {
			scannedFolders.increment();
			long scanned = scannedFolders.sum();
			if (scanned == 1 || scanned % 32 == 0) {
				reportProgress(SongDatabaseImportPhase.SCANNING, (int) scanned, 0,
						"Scanning folders (" + scanned + " visited, " + queuedSongs + " songs found)");
			}
		}
	}

	@FunctionalInterface
	private interface SnapshotWriteListener {
		void onStep(int processedSteps, int totalSteps, String message);
	}

	private boolean withinRoots(String path, String[] roots) {
		for (String root : roots) {
			if (path.startsWith(root) || root.startsWith(path)) {
				return true;
			}
		}
		return false;
	}

	private String emptyIfNull(String value) {
		return value == null ? "" : value;
	}

	private static final class Snapshot {
		private final List<Map<String, PersistedSong>> songShards;
		private final Map<String, PersistedFolder> foldersByPath;
		private final Map<String, List<String>> md5Index;
		private final Map<String, List<String>> sha256Index;
		private final Map<String, List<String>> folderIndex;
		private final Map<String, List<String>> parentIndex;

		private Snapshot(List<Map<String, PersistedSong>> songShards, Map<String, PersistedFolder> foldersByPath,
				Map<String, List<String>> md5Index, Map<String, List<String>> sha256Index,
				Map<String, List<String>> folderIndex, Map<String, List<String>> parentIndex) {
			this.songShards = songShards;
			this.foldersByPath = foldersByPath;
			this.md5Index = md5Index;
			this.sha256Index = sha256Index;
			this.folderIndex = folderIndex;
			this.parentIndex = parentIndex;
		}

		private static Snapshot empty() {
			List<Map<String, PersistedSong>> shards = new ArrayList<Map<String, PersistedSong>>(SHARD_COUNT);
			for (int i = 0; i < SHARD_COUNT; i++) {
				shards.add(new HashMap<String, PersistedSong>());
			}
			return fromData(shards, new HashMap<String, PersistedFolder>());
		}

		private static Snapshot fromData(List<Map<String, PersistedSong>> songShards, Map<String, PersistedFolder> foldersByPath) {
			Map<String, List<String>> md5Index = new HashMap<String, List<String>>();
			Map<String, List<String>> sha256Index = new HashMap<String, List<String>>();
			Map<String, List<String>> folderIndex = new HashMap<String, List<String>>();
			Map<String, List<String>> parentIndex = new HashMap<String, List<String>>();
			for (Map<String, PersistedSong> shard : songShards) {
				for (PersistedSong song : shard.values()) {
					index(md5Index, song.md5, song.path);
					index(sha256Index, song.sha256, song.path);
					index(folderIndex, song.folder, song.path);
					index(parentIndex, song.parent, song.path);
				}
			}
			return new Snapshot(songShards, foldersByPath, md5Index, sha256Index, folderIndex, parentIndex);
		}

		private static void index(Map<String, List<String>> index, String key, String path) {
			if (key != null && key.length() > 0) {
				index.computeIfAbsent(key, ignored -> new ArrayList<String>()).add(path);
			}
		}

		private PersistedSong songByPath(String path) {
			return songShards.get(Math.floorMod(path.hashCode(), SHARD_COUNT)).get(path);
		}

		private List<PersistedSong> allSongs() {
			List<PersistedSong> songs = new ArrayList<PersistedSong>();
			for (Map<String, PersistedSong> shard : songShards) {
				songs.addAll(shard.values());
			}
			return songs;
		}

		private Collection<PersistedFolder> allFolders() {
			return foldersByPath.values();
		}

		private MutableSnapshot mutableCopy() {
			return new MutableSnapshot(this);
		}
	}

	private static final class MutableSnapshot {
		private final List<Map<String, PersistedSong>> songShards = new ArrayList<Map<String, PersistedSong>>(SHARD_COUNT);
		private final Map<String, PersistedFolder> foldersByPath = new HashMap<String, PersistedFolder>();

		private MutableSnapshot(Snapshot snapshot) {
			for (Map<String, PersistedSong> shard : snapshot.songShards) {
				songShards.add(new HashMap<String, PersistedSong>(shard));
			}
			foldersByPath.putAll(snapshot.foldersByPath);
		}

		private void putSong(PersistedSong song) {
			songShards.get(Math.floorMod(song.path.hashCode(), SHARD_COUNT)).put(song.path, song);
		}

		private Snapshot freeze() {
			return Snapshot.fromData(songShards, foldersByPath);
		}
	}

	private static final class PersistedSong implements Serializable {
		private static final long serialVersionUID = 1L;

		private String md5 = "";
		private String sha256 = "";
		private String title = "";
		private String subtitle = "";
		private String genre = "";
		private String artist = "";
		private String subartist = "";
		private String tag = "";
		private String path = "";
		private String folder = "";
		private String stagefile = "";
		private String banner = "";
		private String backbmp = "";
		private String preview = "";
		private String parent = "";
		private int level;
		private int difficulty;
		private int maxbpm;
		private int minbpm;
		private int length;
		private int mode;
		private int judge;
		private int feature;
		private int content;
		private int date;
		private int favorite;
		private int adddate;
		private int notes;
		private String charthash = "";
		private String url = "";
		private String appendurl = "";
		private String ipfs = "";
		private String appendipfs = "";

		private static PersistedSong fromSongData(SongData song) {
			PersistedSong persisted = new PersistedSong();
			persisted.md5 = song.getMd5();
			persisted.sha256 = song.getSha256();
			persisted.title = song.getTitle();
			persisted.subtitle = song.getSubtitle();
			persisted.genre = song.getGenre();
			persisted.artist = song.getArtist();
			persisted.subartist = song.getSubartist();
			persisted.tag = song.getTag();
			persisted.path = song.getPath();
			persisted.folder = song.getFolder();
			persisted.stagefile = song.getStagefile();
			persisted.banner = song.getBanner();
			persisted.backbmp = song.getBackbmp();
			persisted.preview = song.getPreview();
			persisted.parent = song.getParent();
			persisted.level = song.getLevel();
			persisted.difficulty = song.getDifficulty();
			persisted.maxbpm = song.getMaxbpm();
			persisted.minbpm = song.getMinbpm();
			persisted.length = song.getLength();
			persisted.mode = song.getMode();
			persisted.judge = song.getJudge();
			persisted.feature = song.getFeature();
			persisted.content = song.getContent();
			persisted.date = song.getDate();
			persisted.favorite = song.getFavorite();
			persisted.adddate = song.getAdddate();
			persisted.notes = song.getNotes();
			persisted.charthash = song.getCharthash();
			persisted.url = song.getUrl();
			persisted.appendurl = song.getAppendurl();
			persisted.ipfs = song.getIpfs();
			persisted.appendipfs = song.getAppendIpfs();
			return persisted;
		}

		private SongData toSongData() {
			SongData song = new SongData();
			song.setMd5(md5);
			song.setSha256(sha256);
			song.setTitle(title);
			song.setSubtitle(subtitle);
			song.setGenre(genre);
			song.setArtist(artist);
			song.setSubartist(subartist);
			song.setTag(tag);
			song.setPath(path);
			song.setFolder(folder);
			song.setStagefile(stagefile);
			song.setBanner(banner);
			song.setBackbmp(backbmp);
			song.setPreview(preview);
			song.setParent(parent);
			song.setLevel(level);
			song.setDifficulty(difficulty);
			song.setMaxbpm(maxbpm);
			song.setMinbpm(minbpm);
			song.setLength(length);
			song.setMode(mode);
			song.setJudge(judge);
			song.setFeature(feature);
			song.setContent(content);
			song.setDate(date);
			song.setFavorite(favorite);
			song.setAdddate(adddate);
			song.setNotes(notes);
			song.setCharthash(charthash);
			song.setUrl(url);
			song.setAppendurl(appendurl);
			song.setIpfs(ipfs);
			song.setAppendIpfs(appendipfs);
			return song;
		}

		private PersistedSong copy() {
			PersistedSong copy = new PersistedSong();
			copy.md5 = md5;
			copy.sha256 = sha256;
			copy.title = title;
			copy.subtitle = subtitle;
			copy.genre = genre;
			copy.artist = artist;
			copy.subartist = subartist;
			copy.tag = tag;
			copy.path = path;
			copy.folder = folder;
			copy.stagefile = stagefile;
			copy.banner = banner;
			copy.backbmp = backbmp;
			copy.preview = preview;
			copy.parent = parent;
			copy.level = level;
			copy.difficulty = difficulty;
			copy.maxbpm = maxbpm;
			copy.minbpm = minbpm;
			copy.length = length;
			copy.mode = mode;
			copy.judge = judge;
			copy.feature = feature;
			copy.content = content;
			copy.date = date;
			copy.favorite = favorite;
			copy.adddate = adddate;
			copy.notes = notes;
			copy.charthash = charthash;
			copy.url = url;
			copy.appendurl = appendurl;
			copy.ipfs = ipfs;
			copy.appendipfs = appendipfs;
			return copy;
		}

		private String get(String key) {
			if ("title".equals(key)) return title;
			if ("subtitle".equals(key)) return subtitle;
			if ("genre".equals(key)) return genre;
			if ("artist".equals(key)) return artist;
			if ("subartist".equals(key)) return subartist;
			if ("tag".equals(key)) return tag;
			if ("path".equals(key)) return path;
			if ("folder".equals(key)) return folder;
			if ("stagefile".equals(key)) return stagefile;
			if ("banner".equals(key)) return banner;
			if ("backbmp".equals(key)) return backbmp;
			if ("preview".equals(key)) return preview;
			if ("parent".equals(key)) return parent;
			if ("md5".equals(key)) return md5;
			if ("sha256".equals(key)) return sha256;
			if ("charthash".equals(key)) return charthash;
			return null;
		}

		private void bind(PreparedStatement stmt) throws SQLException {
			stmt.setString(1, md5);
			stmt.setString(2, sha256);
			stmt.setString(3, title);
			stmt.setString(4, subtitle);
			stmt.setString(5, genre);
			stmt.setString(6, artist);
			stmt.setString(7, subartist);
			stmt.setString(8, tag);
			stmt.setString(9, path);
			stmt.setString(10, folder);
			stmt.setString(11, stagefile);
			stmt.setString(12, banner);
			stmt.setString(13, backbmp);
			stmt.setString(14, preview);
			stmt.setString(15, parent);
			stmt.setInt(16, level);
			stmt.setInt(17, difficulty);
			stmt.setInt(18, maxbpm);
			stmt.setInt(19, minbpm);
			stmt.setInt(20, length);
			stmt.setInt(21, mode);
			stmt.setInt(22, judge);
			stmt.setInt(23, feature);
			stmt.setInt(24, content);
			stmt.setInt(25, date);
			stmt.setInt(26, favorite);
			stmt.setInt(27, adddate);
			stmt.setInt(28, notes);
			stmt.setString(29, charthash);
		}

		private void writeTo(DataOutputStream out) throws IOException {
			writeString(out, md5);
			writeString(out, sha256);
			writeString(out, title);
			writeString(out, subtitle);
			writeString(out, genre);
			writeString(out, artist);
			writeString(out, subartist);
			writeString(out, tag);
			writeString(out, path);
			writeString(out, folder);
			writeString(out, stagefile);
			writeString(out, banner);
			writeString(out, backbmp);
			writeString(out, preview);
			writeString(out, parent);
			out.writeInt(level);
			out.writeInt(difficulty);
			out.writeInt(maxbpm);
			out.writeInt(minbpm);
			out.writeInt(length);
			out.writeInt(mode);
			out.writeInt(judge);
			out.writeInt(feature);
			out.writeInt(content);
			out.writeInt(date);
			out.writeInt(favorite);
			out.writeInt(adddate);
			out.writeInt(notes);
			writeString(out, charthash);
			writeString(out, url);
			writeString(out, appendurl);
			writeString(out, ipfs);
			writeString(out, appendipfs);
		}

		private void readFrom(DataInputStream in) throws IOException {
			md5 = readString(in);
			sha256 = readString(in);
			title = readString(in);
			subtitle = readString(in);
			genre = readString(in);
			artist = readString(in);
			subartist = readString(in);
			tag = readString(in);
			path = readString(in);
			folder = readString(in);
			stagefile = readString(in);
			banner = readString(in);
			backbmp = readString(in);
			preview = readString(in);
			parent = readString(in);
			level = in.readInt();
			difficulty = in.readInt();
			maxbpm = in.readInt();
			minbpm = in.readInt();
			length = in.readInt();
			mode = in.readInt();
			judge = in.readInt();
			feature = in.readInt();
			content = in.readInt();
			date = in.readInt();
			favorite = in.readInt();
			adddate = in.readInt();
			notes = in.readInt();
			charthash = readString(in);
			url = readString(in);
			appendurl = readString(in);
			ipfs = readString(in);
			appendipfs = readString(in);
		}
	}

	private static final class PersistedFolder implements Serializable {
		private static final long serialVersionUID = 1L;

		private String title;
		private String subtitle;
		private String command;
		private String path;
		private String banner;
		private String parent;
		private int type;
		private int date;
		private int adddate;
		private int max;

		private PersistedFolder(String title, String subtitle, String command, String path, String banner, String parent,
				int type, int date, int adddate, int max) {
			this.title = title;
			this.subtitle = subtitle;
			this.command = command;
			this.path = path;
			this.banner = banner;
			this.parent = parent;
			this.type = type;
			this.date = date;
			this.adddate = adddate;
			this.max = max;
		}

		private FolderData toFolderData() {
			FolderData folder = new FolderData();
			folder.setTitle(title);
			folder.setSubtitle(subtitle);
			folder.setCommand(command);
			folder.setPath(path);
			folder.setBanner(banner);
			folder.setParent(parent);
			folder.setType(type);
			folder.setDate(date);
			folder.setAdddate(adddate);
			folder.setMax(max);
			return folder;
		}

		private PersistedFolder copy() {
			return new PersistedFolder(title, subtitle, command, path, banner, parent, type, date, adddate, max);
		}

		private String get(String key) {
			if ("title".equals(key)) return title;
			if ("subtitle".equals(key)) return subtitle;
			if ("command".equals(key)) return command;
			if ("path".equals(key)) return path;
			if ("banner".equals(key)) return banner;
			if ("parent".equals(key)) return parent;
			return null;
		}

		private void writeTo(DataOutputStream out) throws IOException {
			writeString(out, title);
			writeString(out, subtitle);
			writeString(out, command);
			writeString(out, path);
			writeString(out, banner);
			writeString(out, parent);
			out.writeInt(type);
			out.writeInt(date);
			out.writeInt(adddate);
			out.writeInt(max);
		}

		private static PersistedFolder readFrom(DataInputStream in) throws IOException {
			return new PersistedFolder(
					readString(in),
					readString(in),
					readString(in),
					readString(in),
					readString(in),
					readString(in),
					in.readInt(),
					in.readInt(),
					in.readInt(),
					in.readInt());
		}
	}

	private static final class IntRange {
		private final int start;
		private final int end;

		private IntRange(int start, int end) {
			this.start = start;
			this.end = end;
		}

		private Stream<Integer> parallelStream() {
			return java.util.stream.IntStream.range(start, end).boxed().parallel();
		}

		private static IntRange range(int start, int end) {
			return new IntRange(start, end);
		}
	}
}
