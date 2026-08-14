/*******************************************************************************
 * Copyright (c) 2026 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     SAP SE - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.builder.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.pde.api.tools.internal.APIFileGenerator;
import org.eclipse.pde.api.tools.internal.ApiDescription;
import org.eclipse.pde.api.tools.internal.ApiDescriptionXmlCreator;
import org.eclipse.pde.api.tools.internal.CompilationUnit;
import org.eclipse.pde.api.tools.internal.model.ArchiveApiTypeContainer;
import org.eclipse.pde.api.tools.internal.provisional.Factory;
import org.eclipse.pde.api.tools.internal.provisional.IApiAnnotations;
import org.eclipse.pde.api.tools.internal.provisional.RestrictionModifiers;
import org.eclipse.pde.api.tools.internal.provisional.descriptors.IElementDescriptor;
import org.eclipse.pde.api.tools.internal.provisional.descriptors.IReferenceTypeDescriptor;
import org.eclipse.pde.api.tools.internal.provisional.scanner.TagScanner;
import org.eclipse.pde.api.tools.internal.util.Util;
import org.junit.Test;

/**
 * Tests that TagScanner correctly reads @noreference from GC.handle
 * for both sealed and non-sealed GC classes.
 *
 * Reproduces the bug in APIFileGenerator.resolveCompliance() which returns
 * VERSION_1_3 for JavaSE-17, causing the JDT parser to fail on sealed class
 * syntax and not write GC.handle's @noreference into the .api_description.
 */
public class TagScannerSealedGcTest {

	private static final String BASE = "C:/SAPDevelop/ai_playground/git/"; //$NON-NLS-1$
	private static final String GC_JAVA = "bundles/org.eclipse.swt/Eclipse SWT/gtk/org/eclipse/swt/graphics/GC.java"; //$NON-NLS-1$
	private static final String SEALED_GC   = BASE + "sealed_skija_canvas/" + GC_JAVA; //$NON-NLS-1$
	private static final String NOSEALED_GC = BASE + "no_sealed_skija_canvas/" + GC_JAVA; //$NON-NLS-1$

	private static final String FRAG = "binaries/org.eclipse.swt.gtk.linux.x86_64/target/org.eclipse.swt.gtk.linux.x86_64-3.135.0-SNAPSHOT.jar"; //$NON-NLS-1$
	private static final String SEALED_JAR   = BASE + "sealed_skija_canvas/" + FRAG; //$NON-NLS-1$
	private static final String NOSEALED_JAR = BASE + "no_sealed_skija_canvas/" + FRAG; //$NON-NLS-1$

	/**
	 * Bug reproducer: Java 1.3 compliance + sealed class → @noreference missing.
	 */
	@Test
	public void testSealedGcWithJava13ComplianceMissesNoreference() throws Exception {
		ApiDescription description = buildDescription(SEALED_GC, SEALED_JAR, JavaCore.VERSION_1_3);
		printXml("sealed + java1.3 (bug)", description); //$NON-NLS-1$
		IApiAnnotations annotations = resolveHandleAnnotations(description);
		assertEquals("sealed + java1.3: handle should NOT have @noreference (bug reproducer)", //$NON-NLS-1$
				RestrictionModifiers.NO_RESTRICTIONS,
				annotations == null ? RestrictionModifiers.NO_RESTRICTIONS : annotations.getRestrictions());
		fail("show"); //$NON-NLS-1$
	}

	/**
	 * Fix verifier: Java 17 compliance + sealed class → @noreference present.
	 */
	@Test
	public void testSealedGcWithJava17ComplianceFindsNoreference() throws Exception {
		ApiDescription description = buildDescription(SEALED_GC, SEALED_JAR, JavaCore.VERSION_17);
		printXml("sealed + java17 (fix)", description); //$NON-NLS-1$
		IApiAnnotations annotations = resolveHandleAnnotations(description);
		assertNotNull("sealed + java17: handle annotations should not be null", annotations); //$NON-NLS-1$
		assertEquals("sealed + java17: handle should have @noreference (restrictions=0x8)", //$NON-NLS-1$
				RestrictionModifiers.NO_REFERENCE, annotations.getRestrictions());
		fail("show"); //$NON-NLS-1$
	}

	/**
	 * Control: non-sealed GC always has @noreference regardless of compliance.
	 */
	@Test
	public void testNoSealedGcAlwaysFindsNoreference() throws Exception {
		ApiDescription description = buildDescription(NOSEALED_GC, NOSEALED_JAR, JavaCore.VERSION_1_3);
		printXml("no-sealed + java1.3", description); //$NON-NLS-1$
		IApiAnnotations annotations = resolveHandleAnnotations(description);
		assertNotNull("no-sealed: handle annotations should not be null", annotations); //$NON-NLS-1$
		assertEquals("no-sealed: handle should have @noreference (restrictions=0x8)", //$NON-NLS-1$
				RestrictionModifiers.NO_REFERENCE, annotations.getRestrictions());
		fail("show"); //$NON-NLS-1$
	}

	/**
	 * Verifies that resolveCompliance returns the correct Java version for
	 * modern JavaSE EE names. Before the fix it returned VERSION_1_3 for
	 * anything after JavaSE-1.8.
	 */
	@Test
	public void testResolveComplianceForModernJavaSE() throws Exception {
		APIFileGenerator generator = new APIFileGenerator();
		// use reflection to call the private method
		java.lang.reflect.Method m = APIFileGenerator.class.getDeclaredMethod("resolveCompliance", java.util.Map.class); //$NON-NLS-1$
		m.setAccessible(true);

		java.util.Map<String, String> manifest9  = java.util.Map.of("Bundle-RequiredExecutionEnvironment", "JavaSE-9"); //$NON-NLS-1$ //$NON-NLS-2$
		java.util.Map<String, String> manifest17 = java.util.Map.of("Bundle-RequiredExecutionEnvironment", "JavaSE-17"); //$NON-NLS-1$ //$NON-NLS-2$
		java.util.Map<String, String> manifest21 = java.util.Map.of("Bundle-RequiredExecutionEnvironment", "JavaSE-21"); //$NON-NLS-1$ //$NON-NLS-2$
		java.util.Map<String, String> manifest18 = java.util.Map.of("Bundle-RequiredExecutionEnvironment", "JavaSE-1.8"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals("JavaSE-9 should map to 9",   "9",  m.invoke(generator, manifest9)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("JavaSE-17 should map to 17", "17", m.invoke(generator, manifest17)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("JavaSE-21 should map to 21", "21", m.invoke(generator, manifest21)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("JavaSE-1.8 should map to 1.8", JavaCore.VERSION_1_8, m.invoke(generator, manifest18)); //$NON-NLS-1$
	}

	/**
	 * End-to-end: simulates what APIFileGenerator does for a JavaSE-21 bundle.
	 * Uses resolveCompliance to derive the compliance level, then scans GC.java.
	 */
	@Test
	public void testEndToEndWithResolveCompliance() throws Exception {
		APIFileGenerator generator = new APIFileGenerator();
		java.lang.reflect.Method m = APIFileGenerator.class.getDeclaredMethod("resolveCompliance", java.util.Map.class); //$NON-NLS-1$
		m.setAccessible(true);

		java.util.Map<String, String> manifest = java.util.Map.of("Bundle-RequiredExecutionEnvironment", "JavaSE-21"); //$NON-NLS-1$ //$NON-NLS-2$
		String compliance = (String) m.invoke(generator, manifest);
		System.out.println("resolveCompliance(JavaSE-21) = " + compliance); //$NON-NLS-1$

		ApiDescription description = buildDescription(SEALED_GC, SEALED_JAR, compliance);
		printXml("sealed + resolveCompliance(JavaSE-21)", description); //$NON-NLS-1$
		IApiAnnotations annotations = resolveHandleAnnotations(description);
		assertNotNull("handle annotations should not be null after fix", annotations); //$NON-NLS-1$
		assertEquals("handle should have @noreference after fix", //$NON-NLS-1$
				RestrictionModifiers.NO_REFERENCE, annotations.getRestrictions());
	}

	private static final String TESTS_DELTAS = "C:/SAPDevelop/ai_playground/git/ini31.eclipse.pde/apitools/org.eclipse.pde.api.tools.tests/tests-deltas/class_sealed/"; //$NON-NLS-1$

	/**
	 * test901 before: public final class GC with @noreference handle.
	 * With java1.3 compliance: works (final class is valid Java 1.3 syntax).
	 */
	@Test
	public void test901BeforeFinalGcFindsNoreference() throws Exception {
		ApiDescription description = buildDescriptionFromSource(
				TESTS_DELTAS + "test901/before/GC.java", JavaCore.VERSION_1_3); //$NON-NLS-1$
		printXml("test901/before (final class, java1.3)", description); //$NON-NLS-1$
		IApiAnnotations annotations = resolveHandleAnnotationsForGC(description);
		assertNotNull("test901/before: handle annotations should not be null", annotations); //$NON-NLS-1$
		assertEquals("test901/before: handle should have @noreference", //$NON-NLS-1$
				RestrictionModifiers.NO_REFERENCE, annotations.getRestrictions());
		fail("show"); //$NON-NLS-1$
	}

	/**
	 * test901 after: public sealed class GC with @noreference handle.
	 * With java1.3 compliance (bug): @noreference is NOT found.
	 */
	@Test
	public void test901AfterSealedGcWithJava13MissesNoreference() throws Exception {
		ApiDescription description = buildDescriptionFromSource(
				TESTS_DELTAS + "test901/after/GC.java", JavaCore.VERSION_1_3); //$NON-NLS-1$
		printXml("test901/after (sealed class, java1.3 - bug)", description); //$NON-NLS-1$
		IApiAnnotations annotations = resolveHandleAnnotationsForGC(description);
		assertEquals("test901/after java1.3: handle should NOT have @noreference (bug)", //$NON-NLS-1$
				RestrictionModifiers.NO_RESTRICTIONS,
				annotations == null ? RestrictionModifiers.NO_RESTRICTIONS : annotations.getRestrictions());
		fail("show"); //$NON-NLS-1$
	}

	/**
	 * test901 after: public sealed class GC with @noreference handle.
	 * With java17 compliance (fix): @noreference IS found.
	 */
	@Test
	public void test901AfterSealedGcWithJava17FindsNoreference() throws Exception {
		ApiDescription description = buildDescriptionFromSource(
				TESTS_DELTAS + "test901/after/GC.java", JavaCore.VERSION_17); //$NON-NLS-1$
		printXml("test901/after (sealed class, java17 - fix)", description); //$NON-NLS-1$
		IApiAnnotations annotations = resolveHandleAnnotationsForGC(description);
		assertNotNull("test901/after java17: handle annotations should not be null", annotations); //$NON-NLS-1$
		assertEquals("test901/after java17: handle should have @noreference", //$NON-NLS-1$
				RestrictionModifiers.NO_REFERENCE, annotations.getRestrictions());
		fail("show"); //$NON-NLS-1$
	}

	private static ApiDescription buildDescriptionFromSource(String javaPath, String compliance) throws Exception {
		ApiDescription description = new ApiDescription("deltatest"); //$NON-NLS-1$
		TagScanner scanner = TagScanner.newScanner();
		CompilationUnit unit = new CompilationUnit(javaPath, "UTF-8"); //$NON-NLS-1$
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, compliance);
		options.put(JavaCore.COMPILER_SOURCE, compliance);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, compliance);
		scanner.scan(unit, description, null, options, null);
		return description;
	}

	private static IApiAnnotations resolveHandleAnnotationsForGC(ApiDescription description) {
		IReferenceTypeDescriptor gcType = Factory.packageDescriptor("") //$NON-NLS-1$
				.getType("GC"); //$NON-NLS-1$
		IElementDescriptor handleField = gcType.getField("handle"); //$NON-NLS-1$
		return description.resolveAnnotations(handleField);
	}

	private static ApiDescription buildDescription(String gcJavaPath, String jarPath, String compliance) throws Exception {
		ApiDescription description = new ApiDescription("org.eclipse.swt.graphics"); //$NON-NLS-1$
		TagScanner scanner = TagScanner.newScanner();
		CompilationUnit unit = new CompilationUnit(gcJavaPath, "UTF-8"); //$NON-NLS-1$
		ArchiveApiTypeContainer container = new ArchiveApiTypeContainer(null, jarPath);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, compliance);
		options.put(JavaCore.COMPILER_SOURCE, compliance);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, compliance);
		scanner.scan(unit, description, container, options, null);
		return description;
	}

	private static void printXml(String label, ApiDescription description) {
		try {
			ApiDescriptionXmlCreator visitor = new ApiDescriptionXmlCreator("org.eclipse.swt.graphics", "org.eclipse.swt.graphics"); //$NON-NLS-1$ //$NON-NLS-2$
			description.accept(visitor, null);
			System.out.println("=== API Description XML (" + label + ") ==="); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println(Util.serializeDocument(visitor.getXML()));
		} catch (CoreException e) {
			System.err.println("Failed to serialize: " + e.getMessage()); //$NON-NLS-1$
		}
	}

	private static IApiAnnotations resolveHandleAnnotations(ApiDescription description) {
		IReferenceTypeDescriptor gcType = Factory.packageDescriptor("org.eclipse.swt.graphics") //$NON-NLS-1$
				.getType("GC"); //$NON-NLS-1$
		IElementDescriptor handleField = gcType.getField("handle"); //$NON-NLS-1$
		return description.resolveAnnotations(handleField);
	}
}
