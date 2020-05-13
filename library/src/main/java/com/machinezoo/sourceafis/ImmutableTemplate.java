// Part of SourceAFIS for Java: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis;

import java.util.Comparator;
import java8.util.Comparators;
import java8.util.J8Arrays;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

class ImmutableTemplate {
	static final ImmutableTemplate EMPTY = new ImmutableTemplate();
	final IntPoint size;
	final ImmutableMinutia[] minutiae;
	final NeighborEdge[][] edges;
	private ImmutableTemplate() {
		size = new IntPoint(1, 1);
		minutiae = new ImmutableMinutia[0];
		edges = new NeighborEdge[0][];
	}
	private static final int PRIME = 1610612741;
	ImmutableTemplate(MutableTemplate mutable) {
		size = mutable.size;
		/* src:
		minutiae = mutable.minutiae.stream()
			.map(ImmutableMinutia::new)
			.sorted(Comparator
				.comparingInt((ImmutableMinutia m) -> ((m.position.x * PRIME) + m.position.y) * PRIME)
				.thenComparing(m -> m.position.x)
				.thenComparing(m -> m.position.y)
				.thenComparing(m -> m.direction)
				.thenComparing(m -> m.type))
			.toArray(ImmutableMinutia[]::new);
		 */
		Comparator<ImmutableMinutia> comparator;
		comparator = Comparators.comparingInt((ImmutableMinutia m) -> ((m.position.x * PRIME) + m.position.y) * PRIME);
		comparator = Comparators.thenComparing(comparator, m -> m.position.x);
		comparator = Comparators.thenComparing(comparator, m -> m.position.y);
		comparator = Comparators.thenComparing(comparator, m -> m.direction);
		comparator = Comparators.thenComparing(comparator, m -> m.type);
		minutiae = StreamSupport.stream(mutable.minutiae)
				.map(ImmutableMinutia::new)
				.sorted(comparator)
				.toArray(ImmutableMinutia[]::new);
		// https://sourceafis.machinezoo.com/transparency/shuffled-minutiae
		FingerprintTransparency.current().log("shuffled-minutiae", this::mutable);
		edges = NeighborEdge.buildTable(minutiae);
	}
	MutableTemplate mutable() {
		MutableTemplate mutable = new MutableTemplate();
		mutable.size = size;
		mutable.minutiae = J8Arrays.stream(minutiae).map(ImmutableMinutia::mutable).collect(Collectors.toList());
		return mutable;
	}
}
