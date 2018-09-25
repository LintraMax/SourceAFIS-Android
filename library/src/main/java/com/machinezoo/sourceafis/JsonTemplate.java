// Part of SourceAFIS: https://sourceafis.machinezoo.com
package com.machinezoo.sourceafis;

import java.util.List;

import java8.util.J8Arrays;
import java8.util.function.Function;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

class JsonTemplate {
	int width;
	int height;
	List<JsonMinutia> minutiae;
	JsonTemplate(Cell size, Minutia[] minutiae) {
		width = size.x;
		height = size.y;
		this.minutiae = J8Arrays.stream(minutiae).map(JsonMinutia::new).collect(Collectors.toList());
	}
	Cell size() {
		return new Cell(width, height);
	}
	Minutia[] minutiae() {
		return StreamSupport.stream(minutiae).map((Function<JsonMinutia, Object>) Minutia::new).toArray(Minutia[]::new);
	}
}
