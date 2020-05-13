// Part of SourceAFIS for Java: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import java8.util.Comparators;
import it.unimi.dsi.fastutil.ints.*;

class MatcherThread {
	private static final int DEFAULT_INITIAL_CAPACITY = 11;

	private static final ThreadLocal<MatcherThread> threads = new ThreadLocal<MatcherThread>() {
		/*
		 * ThreadLocal has method withInitial() that is more convenient,
		 * but that method alone would force whole SourceAFIS to require Android API level 26 instead of 24.
		 */
		@Override protected MatcherThread initialValue() {
			return new MatcherThread();
		}
	};
	private FingerprintTransparency transparency;
	ImmutableTemplate probe;
	private Int2ObjectMap<List<IndexedEdge>> edgeHash;
	ImmutableTemplate candidate;
	private MinutiaPair[] pool = new MinutiaPair[1];
	private int pooled;
	private PriorityQueue<MinutiaPair> queue = new PriorityQueue<>(DEFAULT_INITIAL_CAPACITY, Comparators.comparing(p -> p.distance));
	int count;
	MinutiaPair[] tree = new MinutiaPair[1];
	private MinutiaPair[] byProbe = new MinutiaPair[1];
	private MinutiaPair[] byCandidate = new MinutiaPair[1];
	private MinutiaPair[] roots = new MinutiaPair[1];
	private final IntSet duplicates = new IntOpenHashSet();
	private Score score = new Score();
	private final List<MinutiaPair> support = new ArrayList<>();
	private boolean reportSupport;
	static MatcherThread current() {
		return threads.get();
	}
	void selectMatcher(ImmutableMatcher matcher) {
		probe = matcher.template;
		if (probe.minutiae.length > tree.length) {
			tree = new MinutiaPair[probe.minutiae.length];
			byProbe = new MinutiaPair[probe.minutiae.length];
		}
		edgeHash = matcher.edgeHash;
	}
	void selectCandidate(ImmutableTemplate template) {
		candidate = template;
		if (byCandidate.length < candidate.minutiae.length)
			byCandidate = new MinutiaPair[candidate.minutiae.length];
	}
	double match() {
		try {
			/*
			 * Thread-local storage is fairly fast, but it's still a hash lookup,
			 * so do not access FingerprintTransparency.current() repeatedly in tight loops.
			 */
			transparency = FingerprintTransparency.current();
			/*
			 * Collection of support edges is very slow. It must be disabled on matcher level for it to have no performance impact.
			 */
			reportSupport = transparency.acceptsPairing();
			int totalRoots = enumerateRoots();
			// https://sourceafis.machinezoo.com/transparency/root-pairs
			transparency.logRootPairs(totalRoots, roots);
			double high = 0;
			int best = -1;
			for (int i = 0; i < totalRoots; ++i) {
				double partial = tryRoot(roots[i]);
				if (best < 0 || partial > high) {
					high = partial;
					best = i;
				}
				clearPairing();
			}
			// https://sourceafis.machinezoo.com/transparency/best-match
			transparency.logBestMatch(best);
			return high;
		} catch (Throwable e) {
			threads.remove();
			throw e;
		} finally {
			transparency = null;
		}
	}
	private int enumerateRoots() {
		if (roots.length < Parameters.MAX_TRIED_ROOTS)
			roots = new MinutiaPair[Parameters.MAX_TRIED_ROOTS];
		int totalLookups = 0;
		int totalRoots = 0;
		int triedRoots = 0;
		duplicates.clear();
		for (boolean shortEdges : new boolean[] { false, true }) {
			for (int period = 1; period < candidate.minutiae.length; ++period) {
				for (int phase = 0; phase <= period; ++phase) {
					for (int candidateReference = phase; candidateReference < candidate.minutiae.length; candidateReference += period + 1) {
						int candidateNeighbor = (candidateReference + period) % candidate.minutiae.length;
						EdgeShape candidateEdge = new EdgeShape(candidate.minutiae[candidateReference], candidate.minutiae[candidateNeighbor]);
						if ((candidateEdge.length >= Parameters.MIN_ROOT_EDGE_LENGTH) ^ shortEdges) {
							List<IndexedEdge> matches = edgeHash.get(hashShape(candidateEdge));
							if (matches != null) {
								for (IndexedEdge match : matches) {
									if (matchingShapes(match, candidateEdge)) {
										int duplicateKey = (match.reference << 16) | candidateReference;
										if (duplicates.add(duplicateKey)) {
											MinutiaPair pair = allocate();
											pair.probe = match.reference;
											pair.candidate = candidateReference;
											roots[totalRoots] = pair;
											++totalRoots;
										}
										++triedRoots;
										if (triedRoots >= Parameters.MAX_TRIED_ROOTS)
											return totalRoots;
									}
								}
							}
							++totalLookups;
							if (totalLookups >= Parameters.MAX_ROOT_EDGE_LOOKUPS)
								return totalRoots;
						}
					}
				}
			}
		}
		return totalRoots;
	}
	private int hashShape(EdgeShape edge) {
		int lengthBin = edge.length / Parameters.MAX_DISTANCE_ERROR;
		int referenceAngleBin = (int)(edge.referenceAngle / Parameters.MAX_ANGLE_ERROR);
		int neighborAngleBin = (int)(edge.neighborAngle / Parameters.MAX_ANGLE_ERROR);
		return (referenceAngleBin << 24) + (neighborAngleBin << 16) + lengthBin;
	}
	private boolean matchingShapes(EdgeShape probe, EdgeShape candidate) {
		int lengthDelta = probe.length - candidate.length;
		if (lengthDelta >= -Parameters.MAX_DISTANCE_ERROR && lengthDelta <= Parameters.MAX_DISTANCE_ERROR) {
			double complementaryAngleError = DoubleAngle.complementary(Parameters.MAX_ANGLE_ERROR);
			double referenceDelta = DoubleAngle.difference(probe.referenceAngle, candidate.referenceAngle);
			if (referenceDelta <= Parameters.MAX_ANGLE_ERROR || referenceDelta >= complementaryAngleError) {
				double neighborDelta = DoubleAngle.difference(probe.neighborAngle, candidate.neighborAngle);
				if (neighborDelta <= Parameters.MAX_ANGLE_ERROR || neighborDelta >= complementaryAngleError)
					return true;
			}
		}
		return false;
	}
	private double tryRoot(MinutiaPair root) {
		queue.add(root);
		do {
			addPair(queue.remove());
			collectEdges();
			skipPaired();
		} while (!queue.isEmpty());
		// https://sourceafis.machinezoo.com/transparency/pairing
		transparency.logPairing(count, tree, support);
		score.compute(this);
		// https://sourceafis.machinezoo.com/transparency/score
		transparency.logScore(score);
		return score.shapedScore;
	}
	private void clearPairing() {
		for (int i = 0; i < count; ++i) {
			byProbe[tree[i].probe] = null;
			byCandidate[tree[i].candidate] = null;
			release(tree[i]);
			tree[i] = null;
		}
		count = 0;
		if (reportSupport) {
			for (MinutiaPair pair : support)
				release(pair);
			support.clear();
		}
	}
	private void collectEdges() {
		MinutiaPair reference = tree[count - 1];
		NeighborEdge[] probeNeighbors = probe.edges[reference.probe];
		NeighborEdge[] candidateNeigbors = candidate.edges[reference.candidate];
		for (MinutiaPair pair : matchPairs(probeNeighbors, candidateNeigbors)) {
			pair.probeRef = reference.probe;
			pair.candidateRef = reference.candidate;
			if (byCandidate[pair.candidate] == null && byProbe[pair.probe] == null)
				queue.add(pair);
			else
				support(pair);
		}
	}
	private List<MinutiaPair> matchPairs(NeighborEdge[] probeStar, NeighborEdge[] candidateStar) {
		double complementaryAngleError = DoubleAngle.complementary(Parameters.MAX_ANGLE_ERROR);
		List<MinutiaPair> results = new ArrayList<>();
		int start = 0;
		int end = 0;
		for (int candidateIndex = 0; candidateIndex < candidateStar.length; ++candidateIndex) {
			NeighborEdge candidateEdge = candidateStar[candidateIndex];
			while (start < probeStar.length && probeStar[start].length < candidateEdge.length - Parameters.MAX_DISTANCE_ERROR)
				++start;
			if (end < start)
				end = start;
			while (end < probeStar.length && probeStar[end].length <= candidateEdge.length + Parameters.MAX_DISTANCE_ERROR)
				++end;
			for (int probeIndex = start; probeIndex < end; ++probeIndex) {
				NeighborEdge probeEdge = probeStar[probeIndex];
				double referenceDiff = DoubleAngle.difference(probeEdge.referenceAngle, candidateEdge.referenceAngle);
				if (referenceDiff <= Parameters.MAX_ANGLE_ERROR || referenceDiff >= complementaryAngleError) {
					double neighborDiff = DoubleAngle.difference(probeEdge.neighborAngle, candidateEdge.neighborAngle);
					if (neighborDiff <= Parameters.MAX_ANGLE_ERROR || neighborDiff >= complementaryAngleError) {
						MinutiaPair pair = allocate();
						pair.probe = probeEdge.neighbor;
						pair.candidate = candidateEdge.neighbor;
						pair.distance = candidateEdge.length;
						results.add(pair);
					}
				}
			}
		}
		return results;
	}
	private void skipPaired() {
		while (!queue.isEmpty() && (byProbe[queue.peek().probe] != null || byCandidate[queue.peek().candidate] != null))
			support(queue.remove());
	}
	private void addPair(MinutiaPair pair) {
		tree[count] = pair;
		byProbe[pair.probe] = pair;
		byCandidate[pair.candidate] = pair;
		++count;
	}
	private void support(MinutiaPair pair) {
		if (byProbe[pair.probe] != null && byProbe[pair.probe].candidate == pair.candidate) {
			++byProbe[pair.probe].supportingEdges;
			++byProbe[pair.probeRef].supportingEdges;
			if (reportSupport)
				support.add(pair);
			else
				release(pair);
		} else
			release(pair);
	}
	private MinutiaPair allocate() {
		if (pooled > 0) {
			--pooled;
			MinutiaPair pair = pool[pooled];
			pool[pooled] = null;
			return pair;
		} else
			return new MinutiaPair();
	}
	private void release(MinutiaPair pair) {
		if (pooled >= pool.length)
			pool = Arrays.copyOf(pool, 2 * pool.length);
		pair.probe = 0;
		pair.candidate = 0;
		pair.probeRef = 0;
		pair.candidateRef = 0;
		pair.distance = 0;
		pair.supportingEdges = 0;
		pool[pooled] = pair;
	}
}
