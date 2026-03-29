package bms.model;

import java.util.Map;
import java.util.TreeMap;

final class IntervalCoverage {

	private final TreeMap<Double, Double> intervals = new TreeMap<Double, Double>();

	void add(double start, double end) {
		if (end < start) {
			double tmp = start;
			start = end;
			end = tmp;
		}

		Map.Entry<Double, Double> current = intervals.floorEntry(start);
		if (current != null && current.getValue() >= start) {
			start = current.getKey();
			end = Math.max(end, current.getValue());
			intervals.remove(current.getKey());
		}

		current = intervals.ceilingEntry(start);
		while (current != null && current.getKey() <= end) {
			end = Math.max(end, current.getValue());
			intervals.remove(current.getKey());
			current = intervals.ceilingEntry(start);
		}

		intervals.put(start, end);
	}

	boolean contains(double point) {
		Map.Entry<Double, Double> interval = intervals.floorEntry(point);
		return interval != null && interval.getValue() >= point;
	}

	boolean containsAfterStart(double point) {
		Map.Entry<Double, Double> interval = intervals.floorEntry(point);
		return interval != null && interval.getKey() < point && interval.getValue() >= point;
	}
}
