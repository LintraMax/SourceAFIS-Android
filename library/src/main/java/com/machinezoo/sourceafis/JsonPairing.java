// Part of SourceAFIS: https://sourceafis.machinezoo.com
package com.machinezoo.sourceafis;

// src: import static java.util.stream.Collectors.*;
// src: import java.util.*;
import java.util.List;

import java8.util.J8Arrays;
import java8.util.stream.Collectors;

class JsonPairing {
	JsonPair root;
	List<JsonEdge> tree;
	List<JsonEdge> support;
	JsonPairing(int count, MinutiaPair[] pairs, List<JsonEdge> supporting) {
		root = new JsonPair(pairs[0].probe, pairs[0].candidate);
		// src: tree = Arrays.stream(pairs).limit(count).skip(1).map(JsonEdge::new).collect(toList());
		tree = J8Arrays.stream(pairs).limit(count).skip(1).map(JsonEdge::new).collect(Collectors.toList());
		support = supporting;
	}
}
