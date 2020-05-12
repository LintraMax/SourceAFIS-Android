// Part of SourceAFIS for Java: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis;

class DoubleAngle {
	static final double PI2 = 2 * Math.PI;
	static final double INV_PI2 = 1.0 / PI2;
	static final double HALF_PI = 0.5 * Math.PI;
	static DoublePoint toVector(double angle) {
		return new DoublePoint(Math.cos(angle), Math.sin(angle));
	}
	static double atan(DoublePoint vector) {
		double angle = Math.atan2(vector.y, vector.x);
		return angle >= 0 ? angle : angle + PI2;
	}
	static double atan(IntPoint vector) {
		return atan(vector.toPoint());
	}
	static double atan(IntPoint center, IntPoint point) {
		return atan(point.minus(center));
	}
	static double toOrientation(double angle) {
		return angle < Math.PI ? 2 * angle : 2 * (angle - Math.PI);
	}
	static double fromOrientation(double angle) {
		return 0.5 * angle;
	}
	static double add(double start, double delta) {
		double angle = start + delta;
		return angle < PI2 ? angle : angle - PI2;
	}
	static double bucketCenter(int bucket, int resolution) {
		return PI2 * (2 * bucket + 1) / (2 * resolution);
	}
	static int quantize(double angle, int resolution) {
		int result = (int)(angle * INV_PI2 * resolution);
		if (result < 0)
			return 0;
		else if (result >= resolution)
			return resolution - 1;
		else
			return result;
	}
	static double opposite(double angle) {
		return angle < Math.PI ? angle + Math.PI : angle - Math.PI;
	}
	static double distance(double first, double second) {
		double delta = Math.abs(first - second);
		return delta <= Math.PI ? delta : PI2 - delta;
	}
	static double difference(double first, double second) {
		double angle = first - second;
		return angle >= 0 ? angle : angle + PI2;
	}
	static double complementary(double angle) {
		double complement = PI2 - angle;
		return complement < PI2 ? complement : complement - PI2;
	}
	static boolean normalized(double angle) {
		return angle >= 0 && angle < PI2;
	}
}
