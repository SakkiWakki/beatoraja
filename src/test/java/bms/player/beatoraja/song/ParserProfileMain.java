package bms.player.beatoraja.song;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.BMSONDecoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ParserProfileMain {

	public static void main(String[] args) throws Exception {
		String mode = args.length > 0 ? args[0] : "bms";
		int chartCount = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
		int repeats = args.length > 2 ? Integer.parseInt(args[2]) : 3;
		long startupDelayMillis = args.length > 3 ? Long.parseLong(args[3]) : 0L;
		Logger.getGlobal().setLevel(Level.SEVERE);

		Path root = Files.createTempDirectory("parser-profile");
		try {
			Path charts = root.resolve("charts");
			Files.createDirectories(charts);
			for (int i = 0; i < chartCount; i++) {
				if ("bmson".equalsIgnoreCase(mode)) {
					writeBmson(charts.resolve(String.format("chart-%05d.bmson", i)), i);
				} else {
					writeBms(charts.resolve(String.format("chart-%05d.bms", i)), i);
				}
			}
			if (startupDelayMillis > 0L) {
				Thread.sleep(startupDelayMillis);
			}

			long started = System.nanoTime();
			int parsed = 0;
			for (int repeat = 0; repeat < repeats; repeat++) {
				for (int i = 0; i < chartCount; i++) {
					Path path = "bmson".equalsIgnoreCase(mode)
							? charts.resolve(String.format("chart-%05d.bmson", i))
							: charts.resolve(String.format("chart-%05d.bms", i));
					BMSModel model = "bmson".equalsIgnoreCase(mode)
							? new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE).decode(path)
							: new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(path);
					if (model == null) {
						throw new IllegalStateException("Failed to parse " + path);
					}
					new SongData(model, false, false);
					parsed++;
				}
			}

			long elapsedNanos = System.nanoTime() - started;
			double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
			System.out.println("mode=" + mode + " charts=" + chartCount + " repeats=" + repeats
					+ " parsed=" + parsed + " elapsedSeconds=" + elapsedSeconds);
		} finally {
			deleteRecursively(root);
		}
	}

	private static void writeBms(Path path, int index) throws IOException {
		StringBuilder chart = new StringBuilder(16_384);
		chart.append("#PLAYER 1\n");
		chart.append("#GENRE TEST\n");
		chart.append("#TITLE Profile ").append(index).append('\n');
		chart.append("#ARTIST Perf\n");
		chart.append("#BPM 160\n");
		chart.append("#PLAYLEVEL 12\n");
		chart.append("#RANK 2\n");
		chart.append("#TOTAL 380\n");
		chart.append("#WAV01 sample.wav\n");
		for (int measure = 1; measure <= 64; measure++) {
			chart.append(String.format("#%03d11:0100010001000100\n", measure));
			chart.append(String.format("#%03d14:0001000000010000\n", measure));
			chart.append(String.format("#%03d15:0100000100000100\n", measure));
		}
		Files.writeString(path, chart.toString());
	}

	private static void writeBmson(Path path, int index) throws IOException {
		StringBuilder notes = new StringBuilder(32_768);
		for (int i = 0; i < 256; i++) {
			if (i > 0) {
				notes.append(",\n");
			}
			notes.append("        { \"x\": ").append((i % 7) + 1)
					.append(", \"y\": ").append(i * 120)
					.append(", \"l\": 0, \"c\": false }");
		}
		String chart = "{\n"
				+ "  \"version\": \"1.0.0\",\n"
				+ "  \"info\": {\n"
				+ "    \"title\": \"Profile " + index + "\",\n"
				+ "    \"artist\": \"Perf\",\n"
				+ "    \"genre\": \"TEST\",\n"
				+ "    \"mode_hint\": \"beat-7k\",\n"
				+ "    \"chart_name\": \"HYPER\",\n"
				+ "    \"level\": 12,\n"
				+ "    \"init_bpm\": 160,\n"
				+ "    \"judge_rank\": 100,\n"
				+ "    \"total\": 380,\n"
				+ "    \"resolution\": 240\n"
				+ "  },\n"
				+ "  \"lines\": [{ \"y\": 30720 }],\n"
				+ "  \"sound_channels\": [\n"
				+ "    {\n"
				+ "      \"name\": \"sample.wav\",\n"
				+ "      \"notes\": [\n"
				+ notes
				+ "\n      ]\n"
				+ "    }\n"
				+ "  ],\n"
				+ "  \"bga\": {\n"
				+ "    \"bga_header\": [],\n"
				+ "    \"bga_events\": [],\n"
				+ "    \"layer_events\": [],\n"
				+ "    \"poor_events\": []\n"
				+ "  }\n"
				+ "}\n";
		Files.writeString(path, chart);
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (java.util.stream.Stream<Path> files = Files.walk(path)) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(current -> {
				try {
					Files.deleteIfExists(current);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}
}
