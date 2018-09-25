// Part of SourceAFIS: https://sourceafis.machinezoo.com
package com.machinezoo.sourceafis;

import java.util.List;

import java8.util.J8Arrays;
import java8.util.stream.Collectors;

class JsonPair {
	int probe;
	int candidate;
	JsonPair(int probe, int candidate) {
		this.probe = probe;
		this.candidate = candidate;
	}
	static List<JsonPair> roots(int count, MinutiaPair[] roots) {
		return J8Arrays.stream(roots).limit(count).map(p -> new JsonPair(p.probe, p.candidate)).collect(Collectors.toList());
	}
}
