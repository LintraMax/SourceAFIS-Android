// Part of SourceAFIS: https://sourceafis.machinezoo.com
package com.machinezoo.sourceafis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.Stream;
import java8.util.stream.StreamSupport;

class JsonSkeleton {
	int width;
	int height;
	List<Cell> minutiae;
	List<JsonSkeletonRidge> ridges;
	JsonSkeleton(Skeleton skeleton) {
		width = skeleton.size.x;
		height = skeleton.size.y;
		final Map<SkeletonMinutia, Integer> offsets = new HashMap<>();
		for (int i = 0; i < skeleton.minutiae.size(); ++i)
			offsets.put(skeleton.minutiae.get(i), i);
		this.minutiae = StreamSupport.stream(skeleton.minutiae).map(m -> m.position).collect(Collectors.toList());
		ridges = StreamSupport.stream(skeleton.minutiae)
				.flatMap((Function<SkeletonMinutia, Stream<JsonSkeletonRidge>>) m -> StreamSupport.stream(m.ridges)
						.filter(r -> r.points instanceof CircularList)
						.map(r -> {
							JsonSkeletonRidge jr = new JsonSkeletonRidge();
							jr.start = offsets.get(r.start());
							jr.end = offsets.get(r.end());
							jr.length = r.points.size();
							return jr;
						}))
				.collect(Collectors.toList());
	}
}
