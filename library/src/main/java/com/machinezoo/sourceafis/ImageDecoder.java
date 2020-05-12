// Part of SourceAFIS for Java: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis;

/* src:
import static java.util.stream.Collectors.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.*;
import org.apache.sanselan.*;
import org.jnbis.api.*;
import org.jnbis.api.model.*;
import com.machinezoo.noexception.*;
 */

/*
 * We cannot just use ImageIO, because fingerprints often come in formats not supported by ImageIO.
 * We would also like SourceAFIS to work out of the box on Android, which doesn't have ImageIO at all.
 * For these reasons, we have several image decoders that are tried in order for every image.
 * 
 * This should really be a separate image decoding library, but AFAIK there is no such universal library.
 * Perhaps one should be created by forking this code off SourceAFIS and expanding it considerably.
 * Such library can be generalized to suit many applications by making supported formats
 * configurable via maven's provided dependencies.
 */
/* src:
abstract class ImageDecoder {
	static class DecodedImage {
		int width;
		int height;
		int[] pixels;
		DecodedImage(int width, int height, int[] pixels) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
		}
	}
	abstract boolean available();
	abstract String name();
	abstract DecodedImage decode(byte[] image);
	private static final List<ImageDecoder> all = Arrays.asList(
		new ImageIODecoder(),
		new SanselanDecoder(),
		new WsqDecoder(),
		new AndroidDecoder());
	static DecodedImage decodeAny(byte[] image) {
		Map<ImageDecoder, Throwable> exceptions = new HashMap<>();
		for (ImageDecoder decoder : all) {
			try {
				if (!decoder.available())
					throw new UnsupportedOperationException("Image decoder is not available.");
				return decoder.decode(image);
			} catch (Throwable ex) {
				exceptions.put(decoder, ex);
			}
		}
		throw new IllegalArgumentException(String.format("Unsupported image format [%s].", all.stream()
			.map(d -> String.format("%s = '%s'", d.name(), formatError(exceptions.get(d))))
			.collect(joining(", "))));
	}
	private static String formatError(Throwable exception) {
		List<Throwable> ancestors = new ArrayList<>();
		for (Throwable ancestor = exception; ancestor != null; ancestor = ancestor.getCause())
			ancestors.add(ancestor);
		return ancestors.stream()
			.map(ex -> ex.toString())
			.collect(joining(" -> "));
	}
	private static class ImageIODecoder extends ImageDecoder {
		@Override boolean available() {
			return PlatformCheck.hasClass("javax.imageio.ImageIO");
		}
		@Override String name() {
			return "ImageIO";
		}
		@Override DecodedImage decode(byte[] image) {
			return Exceptions.sneak().get(() -> {
				BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(image));
				if (buffered == null)
					throw new IllegalArgumentException("Unsupported image format.");
				int width = buffered.getWidth();
				int height = buffered.getHeight();
				int[] pixels = new int[width * height];
				buffered.getRGB(0, 0, width, height, pixels, 0, width);
				return new DecodedImage(width, height, pixels);
			});
		}
	}
	private static class SanselanDecoder extends ImageDecoder {
		@Override boolean available() {
			return PlatformCheck.hasClass("java.awt.image.BufferedImage");
		}
		@Override String name() {
			return "Sanselan";
		}
		@Override DecodedImage decode(byte[] image) {
			return Exceptions.sneak().get(() -> {
				BufferedImage buffered = Sanselan.getBufferedImage(new ByteArrayInputStream(image));
				int width = buffered.getWidth();
				int height = buffered.getHeight();
				int[] pixels = new int[width * height];
				buffered.getRGB(0, 0, width, height, pixels, 0, width);
				return new DecodedImage(width, height, pixels);
			});
		}
	}
	private static class WsqDecoder extends ImageDecoder {
		@Override boolean available() {
			return true;
		}
		@Override String name() {
			return "WSQ";
		}
		@Override DecodedImage decode(byte[] image) {
			if (image.length < 2 || image[0] != (byte)0xff || image[1] != (byte)0xa0)
				throw new IllegalArgumentException("This is not a WSQ image.");
			return Exceptions.sneak().get(() -> {
				Bitmap bitmap = Jnbis.wsq().decode(image).asBitmap();
				int width = bitmap.getWidth();
				int height = bitmap.getHeight();
				byte[] buffer = bitmap.getPixels();
				int[] pixels = new int[width * height];
				for (int y = 0; y < height; ++y) {
					for (int x = 0; x < width; ++x) {
						int gray = buffer[y * width + x] & 0xff;
						pixels[y * width + x] = 0xff00_0000 | (gray << 16) | (gray << 8) | gray;
					}
				}
				return new DecodedImage(width, height, pixels);
			});
		}
	}
	private static class AndroidDecoder extends ImageDecoder {
		@Override boolean available() {
			return PlatformCheck.hasClass("android.graphics.BitmapFactory");
		}
		@Override String name() {
			return "Android";
		}
		@Override DecodedImage decode(byte[] image) {
			AndroidBitmap bitmap = AndroidBitmapFactory.decodeByteArray(image, 0, image.length);
			if (bitmap.instance == null)
				throw new IllegalArgumentException("Unsupported image format.");
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			int[] pixels = new int[width * height];
			bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
			return new DecodedImage(width, height, pixels);
		}
		static class AndroidBitmapFactory {
			static Class<?> clazz = Exceptions.sneak().get(() -> Class.forName("android.graphics.BitmapFactory"));
			static Method decodeByteArray = Exceptions.sneak().get(() -> clazz.getMethod("decodeByteArray", byte[].class, int.class, int.class));
			static AndroidBitmap decodeByteArray(byte[] data, int offset, int length) {
				return new AndroidBitmap(Exceptions.sneak().get(() -> decodeByteArray.invoke(null, data, offset, length)));
			}
		}
		static class AndroidBitmap {
			static Class<?> clazz = Exceptions.sneak().get(() -> Class.forName("android.graphics.Bitmap"));
			static Method getWidth = Exceptions.sneak().get(() -> clazz.getMethod("getWidth"));
			static Method getHeight = Exceptions.sneak().get(() -> clazz.getMethod("getHeight"));
			static Method getPixels = Exceptions.sneak().get(() -> clazz.getMethod("getPixels", int[].class, int.class, int.class, int.class, int.class, int.class, int.class));
			final Object instance;
			AndroidBitmap(Object instance) {
				this.instance = instance;
			}
			int getWidth() {
				return Exceptions.sneak().getAsInt(() -> (int)getWidth.invoke(instance));
			}
			int getHeight() {
				return Exceptions.sneak().getAsInt(() -> (int)getHeight.invoke(instance));
			}
			void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
				Exceptions.sneak().run(() -> getPixels.invoke(instance, pixels, offset, stride, x, y, width, height));
			}
		}
	}
}
*/