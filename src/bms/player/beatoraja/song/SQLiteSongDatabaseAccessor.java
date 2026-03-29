package bms.player.beatoraja.song;

import java.beans.IntrospectionException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bms.player.beatoraja.SQLiteDatabaseAccessor;
import bms.player.beatoraja.Validatable;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteDataSource;

import bms.model.*;

/**
 * 楽曲データベースへのアクセスクラス
 * 
 * @author exch
 */
public class SQLiteSongDatabaseAccessor extends SQLiteDatabaseAccessor implements SongDatabaseAccessor {

	private static final int PARSE_BATCH_SIZE = 256;

	private SQLiteDataSource ds;

	private final Path root;

	private final ResultSetHandler<List<SongData>> songhandler = new BeanListHandler<SongData>(SongData.class);
	private final ResultSetHandler<List<FolderData>> folderhandler = new BeanListHandler<FolderData>(FolderData.class);

	private final QueryRunner qr;
	
	private List<SongDatabaseAccessorPlugin> plugins = new ArrayList();
	
	public SQLiteSongDatabaseAccessor(String filepath, String[] bmsroot) throws ClassNotFoundException {
		super(new Table("folder", 
				new Column("title", "TEXT"),
				new Column("subtitle", "TEXT"),
				new Column("command", "TEXT"),
				new Column("path", "TEXT", 0, 1),
				new Column("banner", "TEXT"),
				new Column("parent", "TEXT"),
				new Column("type", "INTEGER"),
				new Column("date", "INTEGER"),
				new Column("adddate", "INTEGER"),
				new Column("max", "INTEGER")
				),
				new Table("song",
						new Column("md5", "TEXT", 1, 0),
						new Column("sha256", "TEXT", 1, 0),
						new Column("title", "TEXT"),
						new Column("subtitle", "TEXT"),
						new Column("genre", "TEXT"),
						new Column("artist", "TEXT"),
						new Column("subartist", "TEXT"),
						new Column("tag", "TEXT"),
						new Column("path", "TEXT", 0, 1),
						new Column("folder", "TEXT"),
						new Column("stagefile", "TEXT"),
						new Column("banner", "TEXT"),
						new Column("backbmp", "TEXT"),
						new Column("preview", "TEXT"),
						new Column("parent", "TEXT"),
						new Column("level", "INTEGER"),
						new Column("difficulty", "INTEGER"),
						new Column("maxbpm", "INTEGER"),
						new Column("minbpm", "INTEGER"),
						new Column("length", "INTEGER"),
						new Column("mode", "INTEGER"),
						new Column("judge", "INTEGER"),
						new Column("feature", "INTEGER"),
						new Column("content", "INTEGER"),
						new Column("date", "INTEGER"),
						new Column("favorite", "INTEGER"),
						new Column("adddate", "INTEGER"),
						new Column("notes", "INTEGER"),
						new Column("charthash", "TEXT")
						));
		
		Class.forName("org.sqlite.JDBC");
		SQLiteConfig conf = new SQLiteConfig();
		conf.setSharedCache(true);
		conf.setSynchronous(SynchronousMode.OFF);
		// conf.setJournalMode(JournalMode.MEMORY);
		ds = new SQLiteDataSource(conf);
		ds.setUrl("jdbc:sqlite:" + filepath);
		qr = new QueryRunner(ds);
		root = Paths.get(".");
		createTable();
	}
		
	public void addPlugin(SongDatabaseAccessorPlugin plugin) {
		plugins.add(plugin);
	}
	
	/**
	 * 楽曲データベースを初期テーブルを作成する。 すでに初期テーブルを作成している場合は何もしない。
	 */
	private void createTable() {
		try {
			// songテーブル作成(存在しない場合)
			validate(qr);
			
			if(qr.query("PRAGMA TABLE_INFO(song)", new MapListHandler()).stream().anyMatch(m -> m.get("name").equals("sha256") && (int)(m.get("pk")) == 1)) {
				qr.update("ALTER TABLE [song] RENAME TO [old_song]");
				validate(qr);
				qr.update("INSERT INTO song SELECT "
						+ "md5, sha256, title, subtitle, genre, artist, subartist, tag, path,"
						+ "folder, stagefile, banner, backbmp, preview, parent, level, difficulty,"
						+ "maxbpm, minbpm, length, mode, judge, feature, content,"
						+ "date, favorite, notes, adddate, charthash "
						+ "FROM old_song GROUP BY path HAVING MAX(adddate)");
				qr.update("DROP TABLE old_song");
			}
		} catch (SQLException e) {
			Logger.getGlobal().severe("楽曲データベース初期化中の例外:" + e.getMessage());
		}
	}

	
	/**
	 * 楽曲を取得する
	 * 
	 * @param key
	 *            属性
	 * @param value
	 *            属性値
	 * @return 検索結果
	 */
	public SongData[] getSongDatas(String key, String value) {
		try {
			final List<SongData> m = qr.query("SELECT * FROM song WHERE " + key + " = ?", songhandler, value);
			return Validatable.removeInvalidElements(m).toArray(new SongData[m.size()]);
		} catch (Exception e) {
			e.printStackTrace();
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
		}
		return SongData.EMPTY;
	}

	/**
	 * MD5/SHA256で指定した楽曲をまとめて取得する
	 * 
	 * @param hashes
	 *            楽曲のMD5/SHA256
	 * @return 取得した楽曲
	 */
	public SongData[] getSongDatas(String[] hashes) {
		try {
			StringBuilder md5str = new StringBuilder();
			StringBuilder sha256str = new StringBuilder();
			for (String hash : hashes) {
				if (hash.length() > 32) {
					if (sha256str.length() > 0) {
						sha256str.append(',');
					}
					sha256str.append('\'').append(hash).append('\'');
				} else {
					if (md5str.length() > 0) {
						md5str.append(',');
					}
					md5str.append('\'').append(hash).append('\'');
				}
			}
			List<SongData> m = qr.query("SELECT * FROM song WHERE md5 IN (" + md5str.toString() + ") OR sha256 IN ("
					+ sha256str.toString() + ")", songhandler);
			
			// 検索並び順保持
			List<SongData> sorted = m.stream().sorted((a, b) -> {
				int aIndexSha256 = -1,aIndexMd5 = -1,bIndexSha256 = -1,bIndexMd5 = -1;
				for(int i = 0;i < hashes.length;i++) {
					if(hashes[i].equals(a.getSha256())) aIndexSha256 = i;
					if(hashes[i].equals(a.getMd5())) aIndexMd5 = i;
					if(hashes[i].equals(b.getSha256())) bIndexSha256 = i;
					if(hashes[i].equals(b.getMd5())) bIndexMd5 = i;
				}
			    int aIndex = Math.min((aIndexSha256 == -1 ? Integer.MAX_VALUE : aIndexSha256), (aIndexMd5 == -1 ? Integer.MAX_VALUE : aIndexMd5));
			    int bIndex = Math.min((bIndexSha256 == -1 ? Integer.MAX_VALUE : bIndexSha256), (bIndexMd5 == -1 ? Integer.MAX_VALUE : bIndexMd5));
			    return aIndex - bIndex;
            }).collect(Collectors.toList());

			SongData[] validated = Validatable.removeInvalidElements(sorted).toArray(new SongData[m.size()]);
			return validated;
		} catch (Exception e) {
			e.printStackTrace();
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
		}

		return SongData.EMPTY;
	}

	public SongData[] getSongDatas(String sql, String score, String scorelog, String info) {
		try (Statement stmt = qr.getDataSource().getConnection().createStatement()) {
			stmt.execute("ATTACH DATABASE '" + score + "' as scoredb");
			stmt.execute("ATTACH DATABASE '" + scorelog + "' as scorelogdb");
			List<SongData> m;

			if(info != null) {
				stmt.execute("ATTACH DATABASE '" + info + "' as infodb");
				String s = "SELECT DISTINCT md5, song.sha256 AS sha256, title, subtitle, genre, artist, subartist,path,folder,stagefile,banner,backbmp,parent,level,difficulty,"
						+ "maxbpm,minbpm,song.mode AS mode, judge, feature, content, song.date AS date, favorite, song.notes AS notes, adddate, preview, length, charthash"
						+ " FROM song INNER JOIN (information LEFT OUTER JOIN (score LEFT OUTER JOIN scorelog ON score.sha256 = scorelog.sha256) ON information.sha256 = score.sha256) "
						+ "ON song.sha256 = information.sha256 WHERE " + sql;
				ResultSet rs = stmt.executeQuery(s);
				m = songhandler.handle(rs);
//				System.out.println(s + " -> result : " + m.size());
				stmt.execute("DETACH DATABASE infodb");
			} else {
				String s = "SELECT DISTINCT md5, song.sha256 AS sha256, title, subtitle, genre, artist, subartist,path,folder,stagefile,banner,backbmp,parent,level,difficulty,"
						+ "maxbpm,minbpm,song.mode AS mode, judge, feature, content, song.date AS date, favorite, song.notes AS notes, adddate, preview, length, charthash"
						+ " FROM song LEFT OUTER JOIN (score LEFT OUTER JOIN scorelog ON score.sha256 = scorelog.sha256) ON song.sha256 = score.sha256 WHERE " + sql;
				ResultSet rs = stmt.executeQuery(s);
				m = songhandler.handle(rs);
			}
			stmt.execute("DETACH DATABASE scorelogdb");				
			stmt.execute("DETACH DATABASE scoredb");
			return Validatable.removeInvalidElements(m).toArray(new SongData[m.size()]);
		} catch(Throwable e) {
			e.printStackTrace();			
		}

		return SongData.EMPTY;

	}

	public SongData[] getSongDatasByText(String text) {
		try {
			List<SongData> m = qr.query(
					"SELECT * FROM song WHERE rtrim(title||' '||subtitle||' '||artist||' '||subartist||' '||genre) LIKE ?"
							+ " GROUP BY sha256",songhandler, "%" + text + "%");
			return Validatable.removeInvalidElements(m).toArray(new SongData[m.size()]);
		} catch (Exception e) {
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
		}

		return SongData.EMPTY;
	}
	
	/**
	 * 楽曲を取得する
	 * 
	 * @param key
	 *            属性
	 * @param value
	 *            属性値
	 * @return 検索結果
	 */
	public FolderData[] getFolderDatas(String key, String value) {
		try {
			final List<FolderData> m = qr.query("SELECT * FROM folder WHERE " + key + " = ?", folderhandler, value);
			return m.toArray(new FolderData[m.size()]);
		} catch (Exception e) {
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
		}

		return FolderData.EMPTY;
	}

	/**
	 * 楽曲を更新する
	 * 
	 * @param songs 更新する楽曲
	 */
	public void setSongDatas(SongData[] songs) {
		try (Connection conn = qr.getDataSource().getConnection()){
			conn.setAutoCommit(false);

			for (SongData sd : songs) {
				this.insert(qr, conn, "song", sd);
			}
			conn.commit();
			conn.close();
		} catch (Exception e) {
			Logger.getGlobal().severe("song.db更新時の例外:" + e.getMessage());
		}
	}

	/**
	 * データベースを更新する
	 * 
	 * @param path
	 *            LR2のルートパス
	 */
	public void updateSongDatas(String path, String[] bmsroot, boolean updateAll, SongInformationAccessor info) {
		updateSongDatas(path, bmsroot, updateAll, info, null);
	}

	@Override
	public void updateSongDatas(String path, String[] bmsroot, boolean updateAll, SongInformationAccessor info,
			SongDatabaseImportListener listener) {
		if(bmsroot == null || bmsroot.length == 0) {
			Logger.getGlobal().warning("楽曲ルートフォルダが登録されていません");
			if (listener != null) {
				listener.onProgress(new SongDatabaseImportProgress(SongDatabaseImportPhase.FAILED, 0, 0,
						"No BMS root folders are configured."));
			}
			return;
		}
		SongDatabaseUpdater updater = new SongDatabaseUpdater(updateAll, bmsroot, info, listener);
		updater.updateSongDatas(path == null ? Stream.of(bmsroot).map(p -> Paths.get(p)) : Stream.of(Paths.get(path)));
	}
	
	/**
	 * song database更新用クラス
	 * 
	 * @author exch
	 */
	class SongDatabaseUpdater {

		private final boolean updateAll;
		private final String[] bmsroot;

		private SongInformationAccessor info;

		private final SongDatabaseImportListener listener;

		public SongDatabaseUpdater(boolean updateAll, String[] bmsroot, SongInformationAccessor info,
				SongDatabaseImportListener listener) {
			this.updateAll = updateAll;
			this.bmsroot = bmsroot;
			this.info = info;
			this.listener = listener;
		}

		/**
		 * データベースを更新する
		 * 
		 * @param paths
		 *            更新するディレクトリ(ルートディレクトリでなくても可)
		 */
		public void updateSongDatas(Stream<Path> paths) {
			long time = System.currentTimeMillis();
			SongDatabaseUpdaterProperty property = new SongDatabaseUpdaterProperty(
					Calendar.getInstance().getTimeInMillis() / 1000, info, listener);
			property.count.set(0);
			property.reportProgress(SongDatabaseImportPhase.SCANNING, 0, 0, "Scanning song folders...");
			boolean success = false;
			if(info != null) {
				info.startUpdate();
			}
			Connection conn = null;
			try {
				conn = ds.getConnection();
				property.conn = conn;
				conn.setAutoCommit(false);
				preloadPreservedSongMetadata(conn, property);
				if(updateAll) {
					qr.update(conn, "DELETE FROM folder");					
					qr.update(conn, "DELETE FROM song");
				} else {
					pruneDatabaseOutsideRoots(conn, bmsroot);
				}

				if (!updateAll) {
					preloadExistingState(conn, property);
				}

				paths.forEach((p) -> {
					try {
						BMSFolder folder = new BMSFolder(p, bmsroot);
						folder.processDirectory(property);
					} catch (IOException | SQLException | IllegalArgumentException | ReflectiveOperationException | IntrospectionException e) {
						Logger.getGlobal().severe("楽曲データベース更新時の例外:" + e.getMessage());
					}
				});

				property.totalSongs = property.songJobs.size();
				property.reportProgress(SongDatabaseImportPhase.PARSING, 0, property.totalSongs,
						"Parsing songs (0/" + property.totalSongs + ")");

				try (ImportStatements statements = new ImportStatements(conn)) {
					applyInitialCleanup(property, statements);
					for (int start = 0; start < property.songJobs.size(); start += PARSE_BATCH_SIZE) {
						final int end = Math.min(start + PARSE_BATCH_SIZE, property.songJobs.size());
						final List<SongImportResult> parsedSongs = property.songJobs.subList(start, end).parallelStream()
								.map(job -> decodeSong(job, property))
								.filter(Objects::nonNull)
								.collect(Collectors.toList());
						property.reportProgress(SongDatabaseImportPhase.WRITING, end, property.totalSongs,
								"Writing database changes...");
						applyParsedSongs(property, parsedSongs, statements);
					}
					applyFinalChanges(property, statements);
				}
				conn.commit();
				success = true;
			} catch (Exception e) {
				if (conn != null) {
					try {
						conn.rollback();
					} catch (SQLException rollbackException) {
						Logger.getGlobal().severe("楽曲データベース更新時のロールバック例外:" + rollbackException.getMessage());
					}
				}
				property.reportProgress(SongDatabaseImportPhase.FAILED, property.parsedSongs.get(), property.totalSongs,
						"Import failed. See log for details.");
				Logger.getGlobal().severe("楽曲データベース更新時の例外:" + e.getMessage());
				e.printStackTrace();
			} finally {
				if (conn != null) {
					try {
						conn.close();
					} catch (SQLException closeException) {
						Logger.getGlobal().severe("楽曲データベース更新時のクローズ例外:" + closeException.getMessage());
					}
				}
			}

			if(info != null) {
				if (success) {
					info.endUpdate();
				} else {
					info.rollbackUpdate();
				}
			}
			if (success) {
				property.reportProgress(SongDatabaseImportPhase.COMPLETE, property.totalSongs, property.totalSongs,
						"Import complete (" + property.count.get() + " songs updated)");
			}
			long nowtime = System.currentTimeMillis();
			Logger.getGlobal().info("楽曲更新完了 : Time - " + (nowtime - time) + " 1曲あたりの時間 - "
					+ (property.count.get() > 0 ? (nowtime - time) / property.count.get() : "不明"));
		}

	}
	
	private class BMSFolder {
		
		public final Path path;
		public boolean updateFolder = true;
		private boolean txt = false;
		private final List<Path> bmsfiles = new ArrayList<Path>();
		private final List<BMSFolder> dirs = new ArrayList<BMSFolder>();
		private String previewpath = null;
		private final String[] bmsroot;

		public BMSFolder(Path path, String[] bmsroot) {
			this.path = path;
			this.bmsroot = bmsroot;
		}
		
		private void processDirectory(SongDatabaseUpdaterProperty property)
					throws IOException, SQLException, ReflectiveOperationException, IllegalArgumentException, InvocationTargetException, IntrospectionException {
			final String folderKey = SongUtils.crc32(path.toString(), bmsroot, root.toString());
			final Map<String, ExistingSongRecord> records = property.songsByFolder.getOrDefault(folderKey, Collections.emptyMap());
			final Map<String, ExistingFolderRecord> folders = property.foldersByParent.getOrDefault(folderKey, Collections.emptyMap());
			try (DirectoryStream<Path> paths = Files.newDirectoryStream(path)) {
				for (Path p : paths) {
					if(Files.isDirectory(p)) {
						dirs.add(new BMSFolder(p, bmsroot));
					} else {
						final String s = p.getFileName().toString().toLowerCase();
						if (!txt && s.endsWith(".txt")) {
							txt = true;
						}
						if (previewpath == null) {
							if(s.startsWith("preview") && (s.endsWith(".wav") ||
															s.endsWith(".ogg") ||
															s.endsWith(".mp3") ||
															s.endsWith(".flac"))) {
								previewpath = p.getFileName().toString();
							}
						}
						if (s.endsWith(".bms") || s.endsWith(".bme") || s.endsWith(".bml") || s.endsWith(".pms")
								|| s.endsWith(".bmson")) {
							bmsfiles.add(p);
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			final boolean containsBMS = bmsfiles.size() > 0;
			this.collectBMSFolderJobs(records, property);

			final Set<String> remainingFolders = new HashSet<String>(folders.keySet());
			dirs.forEach(bf -> {
				final String s = (bf.path.startsWith(root) ? root.relativize(bf.path).toString() : bf.path.toString())
						+ File.separatorChar;
				final ExistingFolderRecord record = folders.get(s);
				if (record != null) {
					remainingFolders.remove(s);
					try {
						if (record.date == Files.getLastModifiedTime(bf.path).toMillis() / 1000) {
							bf.updateFolder = false;
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});

			if(!containsBMS) {
				dirs.forEach(bf -> {
					try {
						bf.processDirectory(property);
					} catch (IOException | SQLException | IllegalArgumentException | ReflectiveOperationException | IntrospectionException e) {
						Logger.getGlobal().severe("楽曲データベース更新時の例外:" + e.getMessage());
					}
				});
			}

			if (updateFolder) {
				final String folderPath = (path.startsWith(root) ? root.relativize(path).toString() : path.toString())
						+ File.separatorChar;
				Path parentpath = path.getParent();
				if(parentpath == null) {
					parentpath = path.toAbsolutePath().getParent();
				}
				property.folderUpserts.add(new FolderMutation(
						path.getFileName().toString(),
						folderPath,
						SongUtils.crc32(parentpath.toString() , bmsroot, root.toString()),
						(int) (Files.getLastModifiedTime(path).toMillis() / 1000),
						(int) property.updatetime));			
			}
			for (String staleFolderPath : remainingFolders) {
				property.staleFolderPrefixes.add(staleFolderPath + "%");
			}
		}
		
		private void collectBMSFolderJobs(Map<String, ExistingSongRecord> recordsByPath, SongDatabaseUpdaterProperty property) {
			final Set<String> seenPaths = new HashSet<String>(bmsfiles.size());
			for (Path path : bmsfiles) {
				final String pathname = (path.startsWith(root) ? root.relativize(path).toString() : path.toString());
				seenPaths.add(pathname);
				property.songJobs.add(new SongParseJob(path, pathname, recordsByPath.get(pathname), txt, previewpath, bmsroot));
			}
			for (ExistingSongRecord record : recordsByPath.values()) {
				if (!seenPaths.contains(record.path)) {
					property.staleSongPaths.add(record.path);
				}
			}
		}
	}

		private void preloadPreservedSongMetadata(Connection conn, SongDatabaseUpdaterProperty property) throws SQLException {
			try (PreparedStatement stmt = conn.prepareStatement("SELECT sha256, tag, favorite FROM song");
				 ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					final String sha256 = rs.getString("sha256");
					final String tag = rs.getString("tag");
					final int favorite = rs.getInt("favorite");
					if (tag != null && tag.length() > 0) {
						property.tags.put(sha256, tag);
					}
					if (favorite > 0) {
						property.favorites.put(sha256, favorite);
					}
				}
			}
		}

		private void pruneDatabaseOutsideRoots(Connection conn, String[] bmsroot) throws SQLException {
			StringBuilder dsql = new StringBuilder();
			Object[] param = new String[bmsroot.length];
			for (int i = 0; i < bmsroot.length; i++) {
				dsql.append("path NOT LIKE ?");
				param[i] = bmsroot[i] + "%";
				if (i < bmsroot.length - 1) {
					dsql.append(" AND ");
				}
			}

			qr.update(conn,
					"DELETE FROM folder WHERE path NOT LIKE 'LR2files%' AND path NOT LIKE '%.lr2folder' AND "
							+ dsql.toString(), param);
			qr.update(conn, "DELETE FROM song WHERE " + dsql.toString(), param);
		}

		private void preloadExistingState(Connection conn, SongDatabaseUpdaterProperty property) throws SQLException {
			try (PreparedStatement stmt = conn.prepareStatement(
					"SELECT path, date, preview, sha256, folder FROM song");
				 ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					ExistingSongRecord record = new ExistingSongRecord(
							rs.getString("path"),
							rs.getInt("date"),
							emptyIfNull(rs.getString("preview")),
							rs.getString("sha256"),
							rs.getString("folder"));
					property.songsByFolder
							.computeIfAbsent(record.folder, key -> new HashMap<String, ExistingSongRecord>())
							.put(record.path, record);
				}
			}
			try (PreparedStatement stmt = conn.prepareStatement("SELECT path, parent, date FROM folder");
				 ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					ExistingFolderRecord record = new ExistingFolderRecord(
							rs.getString("path"),
							rs.getString("parent"),
							rs.getInt("date"));
					property.foldersByParent
							.computeIfAbsent(record.parent, key -> new HashMap<String, ExistingFolderRecord>())
							.put(record.path, record);
				}
			}
		}

		private void applyInitialCleanup(SongDatabaseUpdaterProperty property, ImportStatements statements) throws SQLException {
			for (String staleFolderPrefix : property.staleFolderPrefixes) {
				statements.deleteFolder.setString(1, staleFolderPrefix);
				statements.deleteFolder.addBatch();
				statements.deleteSongByPrefix.setString(1, staleFolderPrefix);
				statements.deleteSongByPrefix.addBatch();
			}
			statements.deleteFolder.executeBatch();
			statements.deleteSongByPrefix.executeBatch();
		}

		private void applyParsedSongs(SongDatabaseUpdaterProperty property, List<SongImportResult> parsedSongs,
				ImportStatements statements) throws SQLException {
			for (SongImportResult result : parsedSongs) {
				if (result.skipUpdate) {
					if (result.previewChanged) {
						statements.updatePreview.setString(1, result.previewPath);
						statements.updatePreview.setString(2, result.pathname);
						statements.updatePreview.addBatch();
					}
					continue;
				}
				if (result.deleteOnly) {
					statements.deleteSong.setString(1, result.pathname);
					statements.deleteSong.addBatch();
					continue;
				}
				bindSong(statements.insertSong, result.song);
				statements.insertSong.addBatch();
				if (property.info != null && result.song.getInformation() != null) {
					property.info.update(result.song.getInformation());
				}
				property.count.incrementAndGet();
			}
			statements.updatePreview.executeBatch();
			statements.deleteSong.executeBatch();
			statements.insertSong.executeBatch();
		}

		private void applyFinalChanges(SongDatabaseUpdaterProperty property, ImportStatements statements) throws SQLException {
			for (String staleSongPath : property.staleSongPaths) {
				statements.deleteSong.setString(1, staleSongPath);
				statements.deleteSong.addBatch();
			}
			for (FolderMutation folder : property.folderUpserts) {
				bindFolder(statements.insertFolder, folder);
				statements.insertFolder.addBatch();
			}
			statements.deleteSong.executeBatch();
			statements.insertFolder.executeBatch();
		}

	private static class ImportStatements implements AutoCloseable {
		private final PreparedStatement updatePreview;
		private final PreparedStatement deleteSong;
		private final PreparedStatement deleteFolder;
		private final PreparedStatement deleteSongByPrefix;
		private final PreparedStatement insertSong;
		private final PreparedStatement insertFolder;

		private ImportStatements(Connection conn) throws SQLException {
			updatePreview = conn.prepareStatement("UPDATE song SET preview=? WHERE path=?");
			deleteSong = conn.prepareStatement("DELETE FROM song WHERE path = ?");
			deleteFolder = conn.prepareStatement("DELETE FROM folder WHERE path LIKE ?");
			deleteSongByPrefix = conn.prepareStatement("DELETE FROM song WHERE path LIKE ?");
			insertSong = conn.prepareStatement(INSERT_SONG_SQL);
			insertFolder = conn.prepareStatement(INSERT_FOLDER_SQL);
		}

		@Override
		public void close() throws SQLException {
			updatePreview.close();
			deleteSong.close();
			deleteFolder.close();
			deleteSongByPrefix.close();
			insertSong.close();
			insertFolder.close();
		}
	}

	private static class SongDatabaseUpdaterProperty {
		private final Map<String, String> tags = new HashMap<String, String>();
		private final Map<String, Integer> favorites = new HashMap<String, Integer>();
		private final SongInformationAccessor info;
		private final SongDatabaseImportListener listener;
		private final long updatetime;
		private final AtomicInteger count = new AtomicInteger();
		private final AtomicInteger parsedSongs = new AtomicInteger();
		private final Map<String, Map<String, ExistingSongRecord>> songsByFolder = new HashMap<String, Map<String, ExistingSongRecord>>();
		private final Map<String, Map<String, ExistingFolderRecord>> foldersByParent = new HashMap<String, Map<String, ExistingFolderRecord>>();
		private final List<SongParseJob> songJobs = new ArrayList<SongParseJob>();
		private final Set<String> staleSongPaths = new LinkedHashSet<String>();
		private final Set<String> staleFolderPrefixes = new LinkedHashSet<String>();
		private final List<FolderMutation> folderUpserts = new ArrayList<FolderMutation>();
		private volatile int totalSongs;
		private Connection conn;
		
		public SongDatabaseUpdaterProperty(long updatetime, SongInformationAccessor info,
				SongDatabaseImportListener listener) {
			this.updatetime = updatetime;
			this.info = info;
			this.listener = listener;
		}

		private void reportProgress(SongDatabaseImportPhase phase, int processedSongs, int totalSongs, String message) {
			if (listener != null) {
				listener.onProgress(new SongDatabaseImportProgress(phase, processedSongs, totalSongs, message));
			}
		}

		private void reportParsedSong() {
			final int parsed = parsedSongs.incrementAndGet();
			if (totalSongs <= 0) {
				return;
			}
			if (parsed == 1 || parsed == totalSongs || parsed % 32 == 0) {
				reportProgress(SongDatabaseImportPhase.PARSING, parsed, totalSongs,
						"Parsing songs (" + parsed + "/" + totalSongs + ")");
			}
		}

	}

	private static class ExistingSongRecord {
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

	private static class ExistingFolderRecord {
		private final String path;
		private final String parent;
		private final int date;

		private ExistingFolderRecord(String path, String parent, int date) {
			this.path = path;
			this.parent = parent;
			this.date = date;
		}
	}

	private static class FolderMutation {
		private final String title;
		private final String path;
		private final String parent;
		private final int date;
		private final int adddate;

		private FolderMutation(String title, String path, String parent, int date, int adddate) {
			this.title = title;
			this.path = path;
			this.parent = parent;
			this.date = date;
			this.adddate = adddate;
		}
	}

	private static class SongParseJob {
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

	private static class SongImportResult {
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

	private SongImportResult decodeSong(SongParseJob job, SongDatabaseUpdaterProperty property) {
		try {
			long lastModifiedTime = -1;
			try {
				lastModifiedTime = Files.getLastModifiedTime(job.path).toMillis() / 1000;
			} catch (IOException e) {
			}

			final ExistingSongRecord record = job.record;
			final String pathname = job.pathname;
			if (record != null && record.date == lastModifiedTime) {
				String oldpp = record.preview;
				String newpp = job.previewpath == null ? "" : job.previewpath;
				return SongImportResult.skip(pathname, !oldpp.equals(newpp), newpp);
			}

			BMSModel model = null;
			if (pathname.toLowerCase().endsWith(".bmson")) {
				try {
					model = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE).decode(job.path);
				} catch (Exception e) {
					Logger.getGlobal().severe("Error while decoding bmson at path: " + pathname + e.getMessage());
				}
			} else {
				try {
					model = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(job.path);
				} catch (Exception e) {
					Logger.getGlobal().severe("Error while decoding bms at path: " + pathname + e.getMessage());
				}
			}

			if (model == null) {
				return null;
			}

			final SongData sd = new SongData(model, job.txt, property.info != null);
			if (sd.getNotes() == 0 && model.getWavList().length == 0) {
				return SongImportResult.delete(pathname);
			}

			populateSongData(sd, model, job.path, pathname, lastModifiedTime, job.previewpath, job.bmsroot, property);
			return SongImportResult.upsert(pathname, sd);
		} finally {
			property.reportParsedSong();
		}
	}

	private void populateSongData(SongData sd, BMSModel model, Path path, String pathname, long lastModifiedTime,
			String previewpath, String[] bmsroot, SongDatabaseUpdaterProperty property) {
		if (sd.getDifficulty() == 0) {
			final String fulltitle = (sd.getTitle() + sd.getSubtitle()).toLowerCase();
			final String diffname = (sd.getSubtitle()).toLowerCase();
			if (diffname.contains("beginner")) {
				sd.setDifficulty(1);
			} else if (diffname.contains("normal")) {
				sd.setDifficulty(2);
			} else if (diffname.contains("hyper")) {
				sd.setDifficulty(3);
			} else if (diffname.contains("another")) {
				sd.setDifficulty(4);
			} else if (diffname.contains("insane") || diffname.contains("leggendaria")) {
				sd.setDifficulty(5);
			} else if (fulltitle.contains("beginner")) {
				sd.setDifficulty(1);
			} else if (fulltitle.contains("normal")) {
				sd.setDifficulty(2);
			} else if (fulltitle.contains("hyper")) {
				sd.setDifficulty(3);
			} else if (fulltitle.contains("another")) {
				sd.setDifficulty(4);
			} else if (fulltitle.contains("insane") || fulltitle.contains("leggendaria")) {
				sd.setDifficulty(5);
			} else if (sd.getNotes() < 250) {
				sd.setDifficulty(1);
			} else if (sd.getNotes() < 600) {
				sd.setDifficulty(2);
			} else if (sd.getNotes() < 1000) {
				sd.setDifficulty(3);
			} else if (sd.getNotes() < 2000) {
				sd.setDifficulty(4);
			} else {
				sd.setDifficulty(5);
			}
		}
		if ((sd.getPreview() == null || sd.getPreview().length() == 0) && previewpath != null) {
			sd.setPreview(previewpath);
		}
		final String tag = property.tags.get(sd.getSha256());
		final Integer favorite = property.favorites.get(sd.getSha256());

		for (SongDatabaseAccessorPlugin plugin : plugins) {
			plugin.update(model, sd);
		}

		sd.setTag(tag != null ? tag : "");
		sd.setPath(pathname);
		sd.setFolder(SongUtils.crc32(path.getParent().toString(), bmsroot, root.toString()));
		sd.setParent(SongUtils.crc32(path.getParent().getParent().toString(), bmsroot, root.toString()));
		sd.setDate((int) lastModifiedTime);
		sd.setFavorite(favorite != null ? favorite.intValue() : 0);
		sd.setAdddate((int) property.updatetime);
	}
	
	private static final String INSERT_SONG_SQL =
			"INSERT OR REPLACE INTO song (md5, sha256, title, subtitle, genre, artist, subartist, tag, path, folder, stagefile, banner, backbmp, preview, parent, level, difficulty, maxbpm, minbpm, length, mode, judge, feature, content, date, favorite, adddate, notes, charthash) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private static final String INSERT_FOLDER_SQL =
			"INSERT OR REPLACE INTO folder (title, subtitle, command, path, banner, parent, type, date, adddate, max) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private void bindSong(PreparedStatement stmt, SongData song) throws SQLException {
		stmt.setString(1, song.getMd5());
		stmt.setString(2, song.getSha256());
		stmt.setString(3, song.getTitle());
		stmt.setString(4, song.getSubtitle());
		stmt.setString(5, song.getGenre());
		stmt.setString(6, song.getArtist());
		stmt.setString(7, song.getSubartist());
		stmt.setString(8, song.getTag());
		stmt.setString(9, song.getPath());
		stmt.setString(10, song.getFolder());
		stmt.setString(11, song.getStagefile());
		stmt.setString(12, song.getBanner());
		stmt.setString(13, song.getBackbmp());
		stmt.setString(14, song.getPreview());
		stmt.setString(15, song.getParent());
		stmt.setInt(16, song.getLevel());
		stmt.setInt(17, song.getDifficulty());
		stmt.setInt(18, song.getMaxbpm());
		stmt.setInt(19, song.getMinbpm());
		stmt.setInt(20, song.getLength());
		stmt.setInt(21, song.getMode());
		stmt.setInt(22, song.getJudge());
		stmt.setInt(23, song.getFeature());
		stmt.setInt(24, song.getContent());
		stmt.setInt(25, song.getDate());
		stmt.setInt(26, song.getFavorite());
		stmt.setInt(27, song.getAdddate());
		stmt.setInt(28, song.getNotes());
		stmt.setString(29, song.getCharthash());
	}

	private void bindFolder(PreparedStatement stmt, FolderMutation folder) throws SQLException {
		stmt.setString(1, folder.title);
		stmt.setString(2, null);
		stmt.setString(3, null);
		stmt.setString(4, folder.path);
		stmt.setString(5, null);
		stmt.setString(6, folder.parent);
		stmt.setInt(7, 0);
		stmt.setInt(8, folder.date);
		stmt.setInt(9, folder.adddate);
		stmt.setInt(10, 0);
	}

	private String emptyIfNull(String value) {
		return value == null ? "" : value;
	}

	public static interface SongDatabaseAccessorPlugin {
		
		public void update(BMSModel model, SongData song);
	}
}
