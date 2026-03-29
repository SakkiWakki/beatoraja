package bms.player.beatoraja.play;

import bms.model.Mode;

public class LaneProperty {

	/**
	 * 入力キーからレーンへの対応
	 */
	private final int[] keyToLane;

	/**
	 * レーンから入力キー（複数）への対応
	 */
	private final int[][] laneToKey;

	/**
	 * レーンが何個目のスクラッチか
	 */
	private final int[] laneToScratch;

	/**
	 * レーンからスキンに使用する番号への対応
	 */
	private final int[] laneToSkinOffset;

	/**
	 * レーンからプレイヤー番号への対応
	 */
	private final int[] laneToPlayer;

	/**
	 * 各スクラッチを処理する入力キー（2個ずつ）
	 */
	private final int[][] scratchToKey;

	public LaneProperty(Mode mode) {
		final int lanes = mode.key;
		final int lanesPerPlayer = lanes / mode.player;
		final int scratches = mode.scratchesPerPlayer;
		final int regularPerPlayer = lanesPerPlayer - scratches;

		if (scratches > 0) {
			// BEAT-style: N regular keys + scratch lanes per player side.
			// Each scratch lane maps 2 physical inputs to 1 lane.
			int totalInputs = lanes + scratches * mode.player; // extra input per scratch
			keyToLane = new int[totalInputs];
			laneToKey = new int[lanes][];
			laneToScratch = new int[lanes];
			laneToSkinOffset = new int[lanes];
			scratchToKey = new int[scratches * mode.player][2];

			int inputIdx = 0;
			int scratchIdx = 0;
			for (int p = 0; p < mode.player; p++) {
				int laneBase = p * lanesPerPlayer;
				// Regular keys: 1 input -> 1 lane, skin offset 1..N
				for (int i = 0; i < regularPerPlayer; i++) {
					int lane = laneBase + i;
					keyToLane[inputIdx] = lane;
					laneToKey[lane] = new int[] { inputIdx };
					laneToScratch[lane] = -1;
					laneToSkinOffset[lane] = i + 1;
					inputIdx++;
				}
				// Scratch lanes: 2 inputs -> 1 lane, skin offset 0
				for (int s = 0; s < scratches; s++) {
					int lane = laneBase + regularPerPlayer + s;
					keyToLane[inputIdx] = lane;
					keyToLane[inputIdx + 1] = lane;
					laneToKey[lane] = new int[] { inputIdx, inputIdx + 1 };
					laneToScratch[lane] = scratchIdx;
					laneToSkinOffset[lane] = 0;
					scratchToKey[scratchIdx] = new int[] { inputIdx, inputIdx + 1 };
					scratchIdx++;
					inputIdx += 2;
				}
			}
		} else {
			// POPN / KEYBOARD style: 1:1 input-to-lane mapping, no scratch.
			keyToLane = new int[lanes];
			laneToKey = new int[lanes][1];
			laneToScratch = new int[lanes];
			laneToSkinOffset = new int[lanes];
			scratchToKey = new int[][] {};

			for (int i = 0; i < lanes; i++) {
				keyToLane[i] = i;
				laneToKey[i][0] = i;
				laneToScratch[i] = -1;
				laneToSkinOffset[i] = (i % lanesPerPlayer) + 1;
			}
		}

		laneToPlayer = new int[lanes];
		for (int i = 0; i < lanes; i++) {
			laneToPlayer[i] = i / lanesPerPlayer;
		}
	}

	public int[] getKeyLaneAssign() {
		return keyToLane;
	}

	public int[][] getLaneKeyAssign() {
		return laneToKey;
	}

	public int[] getLaneScratchAssign() {
		return laneToScratch;
	}

	public int[] getLaneSkinOffset() {
		return laneToSkinOffset;
	}

	public int[] getLanePlayer() {
		return laneToPlayer;
	}

	public int[][] getScratchKeyAssign() {
		return scratchToKey;
	}
}
