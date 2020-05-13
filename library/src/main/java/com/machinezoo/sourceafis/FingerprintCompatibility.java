// Part of SourceAFIS for Java: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis;

import static java8.util.stream.Collectors.*;
import java.nio.charset.*;
import java.util.Arrays;
import java.util.List;

import org.slf4j.*;
import com.machinezoo.fingerprintio.*;

import java8.util.*;
import java8.util.stream.*;

/**
 * Collection of methods for export and import of foreign fingerprint template formats.
 * Only <a href="https://templates.machinezoo.com/">publicly documented formats</a> are supported.
 * Three versions of ANSI 378 can be both imported and exported.
 * Limited support for import of ISO 19794-2 is also provided.
 * <p>
 * ANSI and ISO template specs prescribe specific ways to calculate minutia position and angle.
 * They even vary these calculations between versions of the same spec.
 * SourceAFIS has its own algorithms to determine minutia positions and angles.
 * Conversion routines currently don't attempt to compensate for this difference.
 * They just copy minutia positions and angles to the target template without any adjustments.
 * This may result in some loss of accuracy when matching against templates from other sources.
 * <p>
 * Note that the use of these so-called "standard" templates for fingerprint exchange is
 * <a href="https://templates.machinezoo.com/discouraged">strongly discouraged</a>
 * in favor of plain fingerprint images.
 * 
 * @see <a href="https://templates.machinezoo.com/">Fingerprint template formats</a>
 * @see <a href="https://templates.machinezoo.com/discouraged">Why "standard" templates are a bad idea</a>
 */
public class FingerprintCompatibility {
	static {
		PlatformCheck.run();
	}
	private static final Logger logger = LoggerFactory.getLogger(FingerprintCompatibility.class);
	// src: private static String version = new String(PlatformCheck.resource("version.txt"), StandardCharsets.UTF_8).trim();
	private static String version = BuildConfig.VERSION_NAME;
	/**
	 * Gets version of the currently running SourceAFIS.
	 * This is useful during upgrades when the application has to deal
	 * with possible template incompatibility between versions.
	 * 
	 * @return SourceAFIS version in a three-part 1.2.3 format
	 */
	public static String version() {
		return version;
	}
	private FingerprintCompatibility() {
	}
	/**
	 * Converts foreign fingerprint template to native SourceAFIS template.
	 * This is a convenience wrapper around {@link #convertAll(byte[])}
	 * that returns the first fingerprint in the template or throws if there are none.
	 * <p>
	 * This method accepts all template formats documented on the
	 * <a href="https://templates.machinezoo.com/">template formats website</a>,
	 * specifically all versions of ANSI 378 and the initial version of ISO 19794-2.
	 * Foreign template can come from feature extractor other than SourceAFIS.
	 * 
	 * @param template
	 *            imported foreign template in one of the supported formats
	 * @return converted native template
	 * @throws NullPointerException
	 *             if {@code template} is {@code null}
	 * @throws IllegalArgumentException
	 *             if {@code template} contains no fingerprints
	 * @throws RuntimeException
	 *             if {@code template} is in an unsupported format or it is corrupted
	 *
	 * @see #convertAll(byte[])
	 * @see #toAnsiIncits378v2004(FingerprintTemplate...)
	 * @see <a href="https://templates.machinezoo.com/">Supported fingerprint template formats</a>
	 */
	public static FingerprintTemplate convert(byte[] template) {
		Objects.requireNonNull(template);
		/* src:
		return convertAll(template).stream()
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No fingerprints found in the template"));
		 */
		return StreamSupport.stream(convertAll(template))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No fingerprints found in the template"));
	}
	/**
	 * Converts foreign fingerprint template to a list of native SourceAFIS templates.
	 * This method accepts all template formats documented on the
	 * <a href="https://templates.machinezoo.com/">template formats website</a>,
	 * specifically all versions of ANSI 378 and the initial version of ISO 19794-2.
	 * Foreign template can come from feature extractor other than SourceAFIS.
	 * Several native templates can be returned, because many foreign template formats can contain multiple fingerprints.
	 * 
	 * @param template
	 *            imported foreign template in one of the supported formats
	 * @return list of native templates containing fingerprints from the foreign template
	 * @throws NullPointerException
	 *             if {@code template} is {@code null}
	 * @throws RuntimeException
	 *             if {@code template} is in an unsupported format or it is corrupted
	 *
	 * @see #convert(byte[])
	 * @see #toAnsiIncits378v2004(FingerprintTemplate...)
	 * @see <a href="https://templates.machinezoo.com/">Supported fingerprint template formats</a>
	 */
	public static List<FingerprintTemplate> convertAll(byte[] template) {
		Objects.requireNonNull(template);
		try {
			TemplateFormat format = TemplateFormat.detect(template);
			if (format == null || !TemplateCodec.ALL.containsKey(format))
				throw new IllegalArgumentException("Unsupported template format.");
			/* src:
			return TemplateCodec.ALL.get(format).decode(template).stream()
				.map(fp -> new FingerprintTemplate(new ImmutableTemplate(fp)))
				.collect(toList());
			 */
			return StreamSupport.stream(TemplateCodec.ALL.get(format).decode(template))
				.map(fp -> new FingerprintTemplate(new ImmutableTemplate(fp)))
				.collect(toList());
		} catch (Throwable ex) {
			/*
			 * If it's none of the known foreign formats, try our own native format
			 * in case native template gets here by accident.
			 */
			try {
				/*
				 * Pass false to FingerprintTemplate constructor to prevent infinite recursion
				 * of mutual fallbacks between FingerprintTemplate and FingerprintCompatibility.
				 */
				List<FingerprintTemplate> deserialized = Arrays.asList(new FingerprintTemplate(template, false));
				/*
				 * It is an error to pass native template here, so at least log a warning.
				 */
				logger.warn("Native SourceAFIS template was passed to convert() or convertAll() in FingerprintCompatibility. " +
					"It was accepted, but FingerprintTemplate constructor should be used instead.");
				return deserialized;
			} catch (Throwable ex2) {
				/*
				 * Throw the original exception. We don't want to hide it with exception from this fallback.
				 */
				throw ex;
			}
		}
	}
	private static byte[] encode(TemplateFormat format, FingerprintTemplate[] templates) {
		// src: return TemplateCodec.ALL.get(format).encode(Arrays.stream(templates).map(t -> t.immutable.mutable()).collect(toList()));
		return TemplateCodec.ALL.get(format).encode(J8Arrays.stream(templates).map(t -> t.immutable.mutable()).collect(toList()));
	}
	/**
	 * Converts native fingerprint template to ANSI 378-2004 template.
	 * This method produces fingerprint template conforming to
	 * <a href="https://templates.machinezoo.com/ansi-incits-378-2004">ANSI INCITS 378-2004</a>.
	 * The template can then be used by fingerprint matcher other than SourceAFIS.
	 * Several native templates can be supplied, because ANSI 378 template can contain multiple fingerprints.
	 * 
	 * @param templates
	 *            list of native SourceAFIS templates to export
	 * @return ANSI 378-2004 template
	 * @throws NullPointerException
	 *             if {@code templates} or any of its items is {@code null}
	 * 
	 * @see #toAnsiIncits378v2009(FingerprintTemplate...)
	 * @see #toAnsiIncits378v2009AM1(FingerprintTemplate...)
	 * @see #convertAll(byte[])
	 * @see <a href="https://templates.machinezoo.com/ansi-incits-378-2004">ANSI INCITS 378-2004</a>
	 */
	public static byte[] toAnsiIncits378v2004(FingerprintTemplate... templates) {
		return encode(TemplateFormat.ANSI_378, templates);
	}
	/**
	 * Converts native fingerprint template to ANSI 378-2009 template.
	 * This method produces fingerprint template conforming to
	 * <a href="https://templates.machinezoo.com/ansi-incits-378-2009-r2014">ANSI INCITS 378-2009[R2014]</a>.
	 * The template can then be used by fingerprint matcher other than SourceAFIS.
	 * Several native templates can be supplied, because ANSI 378 template can contain multiple fingerprints.
	 * 
	 * @param templates
	 *            list of native SourceAFIS templates to export
	 * @return ANSI 378-2009 template
	 * @throws NullPointerException
	 *             if {@code templates} or any of its items is {@code null}
	 * 
	 * @see #toAnsiIncits378v2004(FingerprintTemplate...)
	 * @see #toAnsiIncits378v2009AM1(FingerprintTemplate...)
	 * @see #convertAll(byte[])
	 * @see <a href="https://templates.machinezoo.com/ansi-incits-378-2009-r2014">ANSI INCITS 378-2009[R2014]</a>
	 */
	public static byte[] toAnsiIncits378v2009(FingerprintTemplate... templates) {
		return encode(TemplateFormat.ANSI_378_2009, templates);
	}
	/**
	 * Converts native fingerprint template to ANSI 378-2009/AM1 template.
	 * This method produces fingerprint template conforming to
	 * <a href="https://templates.machinezoo.com/ansi-incits-378-2009-am1-2010-r2015">ANSI INCITS 378:2009/AM 1:2010[R2015]</a>.
	 * The template can then be used by fingerprint matcher other than SourceAFIS.
	 * Several native templates can be supplied, because ANSI 378 template can contain multiple fingerprints.
	 * 
	 * @param templates
	 *            list of native SourceAFIS templates to export
	 * @return ANSI 378-2009/AM1 template
	 * @throws NullPointerException
	 *             if {@code templates} or any of its items is {@code null}
	 * 
	 * @see #toAnsiIncits378v2004(FingerprintTemplate...)
	 * @see #toAnsiIncits378v2009(FingerprintTemplate...)
	 * @see #convertAll(byte[])
	 * @see <a href="https://templates.machinezoo.com/ansi-incits-378-2009-am1-2010-r2015">ANSI INCITS 378:2009/AM 1:2010[R2015]</a>
	 */
	public static byte[] toAnsiIncits378v2009AM1(FingerprintTemplate... templates) {
		return encode(TemplateFormat.ANSI_378_2009_AM1, templates);
	}
}
