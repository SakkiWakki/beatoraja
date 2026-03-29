package bms.player.beatoraja.play;

import java.util.HashMap;
import java.util.Map;

import bms.model.BMSModel;
import bms.model.Mode;

/**
 * プレイヤールール
 *
 * @author exch
 */
public enum BMSPlayerRule {

	Beatoraja_5(GaugeProperty.FIVEKEYS, JudgeProperty.FIVEKEYS, "beat-5"),
	Beatoraja_7(GaugeProperty.SEVENKEYS, JudgeProperty.SEVENKEYS, "beat-7"),
	Beatoraja_9(GaugeProperty.PMS, JudgeProperty.PMS, "popn"),
	Beatoraja_24(GaugeProperty.KEYBOARD, JudgeProperty.KEYBOARD, "keyboard"),
	Beatoraja_Other(GaugeProperty.SEVENKEYS, JudgeProperty.SEVENKEYS, (String) null),

	LR2(GaugeProperty.LR2, JudgeProperty.SEVENKEYS, (String) null),

	Default(GaugeProperty.SEVENKEYS, JudgeProperty.SEVENKEYS, (String) null),
;

	/**
	 * ゲージ仕様
	 */
    public final GaugeProperty gauge;
	/**
	 * 判定仕様
	 */
    public final JudgeProperty judge;
	/**
	 * 対象ルールカテゴリ。nullの場合はフォールバック
	 */
	public final String ruleCategory;

	private static final Map<String, BMSPlayerRule> categoryMap = new HashMap<>();

	static {
		for (BMSPlayerRule rule : BMSPlayerRuleSet.Beatoraja.ruleset) {
			if (rule.ruleCategory != null) {
				categoryMap.put(rule.ruleCategory, rule);
			}
		}
	}

    private BMSPlayerRule(GaugeProperty gauge, JudgeProperty judge, String ruleCategory) {
        this.gauge = gauge;
        this.judge = judge;
        this.ruleCategory = ruleCategory;
    }

    public static BMSPlayerRule getBMSPlayerRule(Mode mode) {
        BMSPlayerRule rule = categoryMap.get(mode.ruleCategory);
        if (rule != null) {
            return rule;
        }
        // Fallback: find first rule with null category (Beatoraja_Other)
        for (BMSPlayerRule r : BMSPlayerRuleSet.Beatoraja.ruleset) {
            if (r.ruleCategory == null) {
                return r;
            }
        }
        return Default;
    }

    public static void validate(BMSModel model) {
    	BMSPlayerRule rule = getBMSPlayerRule(model.getMode());
    	final int judgerank = model.getJudgerank();
    	switch(model.getJudgerankType()) {
    	case BMS_RANK:
			model.setJudgerank(judgerank >= 0 && model.getJudgerank() < 5 ? rule.judge.windowrule.judgerank[judgerank] : rule.judge.windowrule.judgerank[2]);
    		break;
    	case BMS_DEFEXRANK:
			model.setJudgerank(judgerank > 0 ? judgerank * rule.judge.windowrule.judgerank[2] / 100 : rule.judge.windowrule.judgerank[2]);
    		break;
    	case BMSON_JUDGERANK:
			model.setJudgerank(judgerank > 0 ? judgerank : 100);
    		break;
    	}
    	model.setJudgerankType(BMSModel.JudgeRankType.BMSON_JUDGERANK);

    	switch(model.getTotalType()) {
    	case BMS:
			// TOTAL未定義の場合
			if (model.getTotal() <= 0.0) {
				model.setTotal(calculateDefaultTotal(model.getMode(), model.getTotalNotes()));
			}
    		break;
    	case BMSON:
    		final double total = calculateDefaultTotal(model.getMode(), model.getTotalNotes());
			model.setTotal(model.getTotal() > 0 ? model.getTotal() / 100.0 * total : total);
    		break;
    	}
    	model.setTotalType(BMSModel.TotalType.BMS);
    }

	private static double calculateDefaultTotal(Mode mode, int totalnotes) {
		if ("keyboard".equals(mode.ruleCategory)) {
			return Math.max(300.0, 7.605 * (totalnotes + 100) / (0.01 * totalnotes + 6.5));
		}
		return Math.max(260.0, 7.605 * totalnotes / (0.01 * totalnotes + 6.5));
	}
}

enum BMSPlayerRuleSet {

	Beatoraja(BMSPlayerRule.Beatoraja_5, BMSPlayerRule.Beatoraja_7, BMSPlayerRule.Beatoraja_9, BMSPlayerRule.Beatoraja_24,  BMSPlayerRule.Beatoraja_Other),
	LR2(BMSPlayerRule.LR2);

	public final BMSPlayerRule[] ruleset;

    private BMSPlayerRuleSet(BMSPlayerRule... ruleset) {
    	this.ruleset = ruleset;
    }
}
