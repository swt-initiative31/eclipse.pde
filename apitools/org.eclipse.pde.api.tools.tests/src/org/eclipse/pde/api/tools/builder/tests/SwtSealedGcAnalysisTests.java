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

import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.pde.api.tools.internal.ApiDescriptionXmlCreator;
import org.eclipse.pde.api.tools.internal.builder.BaseApiAnalyzer;
import org.eclipse.pde.api.tools.internal.builder.BuildContext;
import org.eclipse.pde.api.tools.internal.model.ApiModelFactory;
import org.eclipse.pde.api.tools.internal.provisional.IApiAnnotations;
import org.eclipse.pde.api.tools.internal.provisional.IApiDescription;
import org.eclipse.pde.api.tools.internal.provisional.model.ApiTypeContainerVisitor;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiBaseline;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiType;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiTypeContainer;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiTypeRoot;
import org.eclipse.pde.api.tools.internal.provisional.problems.IApiProblem;
import org.eclipse.pde.api.tools.internal.util.Util;
import org.eclipse.pde.api.tools.model.tests.TestSuiteHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Reproduces the API tools error seen in the SWT skia-canvas PR:
 *
 * "The field org.eclipse.swt.graphics.GC.handle has been added to a class"
 *
 * when GC is changed from {@code public final class} to
 * {@code public sealed class}.
 *
 * Prerequisites: set these system properties (e.g. in the Eclipse run
 * configuration): swt.baseline.jar = path to org.eclipse.swt.gtk.linux.x86_64
 * JAR from master_skija_canvas swt.sealed.jar = path to
 * org.eclipse.swt.gtk.linux.x86_64 JAR from sealed_skija_canvas
 * swt.nosealed.jar = path to org.eclipse.swt.gtk.linux.x86_64 JAR from
 * no_sealed_skija_canvas
 *
 * Default paths (adjust if needed): swt.baseline.jar =
 * C:/.../master_skija_canvas/binaries/org.eclipse.swt.gtk.linux.x86_64/target/org.eclipse.swt.gtk.linux.x86_64-3.135.0-SNAPSHOT.jar
 * swt.sealed.jar =
 * C:/.../sealed_skija_canvas/binaries/org.eclipse.swt.gtk.linux.x86_64/target/org.eclipse.swt.gtk.linux.x86_64-3.135.0-SNAPSHOT.jar
 * swt.nosealed.jar =
 * C:/.../no_sealed_skija_canvas/binaries/org.eclipse.swt.gtk.linux.x86_64/target/org.eclipse.swt.gtk.linux.x86_64-3.135.0-SNAPSHOT.jar
 */
public class SwtSealedGcAnalysisTests {

	private static final String NO_SEALED = "no-sealed"; //$NON-NLS-1$
	private static final String SEALED = "sealed"; //$NON-NLS-1$
	private static final String BASELINE = "baseline"; //$NON-NLS-1$
	private static final String PROP_BASELINE = "swt.baseline.jar"; //$NON-NLS-1$
	private static final String PROP_SEALED = "swt.sealed.jar"; //$NON-NLS-1$
	private static final String PROP_NOSEALED = "swt.nosealed.jar"; //$NON-NLS-1$

	private static final String BASE = "C:/SAPDevelop/ai_playground/git/"; //$NON-NLS-1$
	private static final String FRAG = "binaries/org.eclipse.swt.gtk.linux.x86_64/target/org.eclipse.swt.gtk.linux.x86_64-3.135.0-SNAPSHOT.jar"; //$NON-NLS-1$

	private static final String DEFAULT_BASELINE = BASE + "master_skija_canvas/" + FRAG; //$NON-NLS-1$
	private static final String DEFAULT_SEALED = BASE + "sealed_skija_canvas/" + FRAG; //$NON-NLS-1$
	private static final String DEFAULT_NOSEALED = BASE + "no_sealed_skija_canvas/" + FRAG; //$NON-NLS-1$

	private static final String SWT_FRAGMENT = "org.eclipse.swt.gtk.linux.x86_64"; //$NON-NLS-1$

	private IApiBaseline baselineBaseline;
	private IApiBaseline sealedBaseline;
	private IApiBaseline noSealedBaseline;

	@Before
	public void setUp() throws CoreException {
		String baselineJar = System.getProperty(PROP_BASELINE, DEFAULT_BASELINE);
		String sealedJar = System.getProperty(PROP_SEALED, DEFAULT_SEALED);
		String noSealedJar = System.getProperty(PROP_NOSEALED, DEFAULT_NOSEALED);

		baselineBaseline = createBaseline(BASELINE, baselineJar);
		sealedBaseline = createBaseline(SEALED, sealedJar);
		noSealedBaseline = createBaseline("nosealed", noSealedJar); //$NON-NLS-1$
	}

	@After
	public void tearDown() {
		ApiTestingEnvironment.dispose(baselineBaseline);
		ApiTestingEnvironment.dispose(sealedBaseline);
		ApiTestingEnvironment.dispose(noSealedBaseline);
	}

	/**
	 * sealed GC vs. final GC baseline: must reproduce the "GC.handle has been
	 * added to a class" error.
	 */
	@Test
	public void testSealedGcProducesApiError() {

		System.out.println("============ Start testSealedGcProducesApiError() ============"); //$NON-NLS-1$

		IApiComponent component = sealedBaseline.getApiComponent(SWT_FRAGMENT);
		assertNotNull("org.eclipse.swt.gtk.linux.x86_64 not found in sealed baseline", component); //$NON-NLS-1$

		printComponentInfo(BASELINE, baselineBaseline.getApiComponent(SWT_FRAGMENT));
		printComponentInfo(SEALED, component);

		IApiProblem[] problems = analyze(baselineBaseline, component);

		System.out.println("=== Problems for sealed GC ==="); //$NON-NLS-1$
		for (IApiProblem p : problems) {
			System.out.println("  id=" + p.getId() + " msg=" + p.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		}

		boolean hasHandleAdded = Arrays.stream(problems)
				.anyMatch(p -> p.getMessage() != null && p.getMessage().contains("GC.handle")); //$NON-NLS-1$

		fail("show"); //$NON-NLS-1$

		if (!hasHandleAdded) {
			fail("Expected 'GC.handle has been added to a class' problem, but got: " //$NON-NLS-1$
					+ Arrays.stream(problems).map(IApiProblem::getMessage).collect(Collectors.joining(", "))); //$NON-NLS-1$
		}
	}

	/**
	 * non-sealed (plain) GC vs. final GC baseline: must produce no GC.handle
	 * error.
	 */
	@Test
	public void testNoSealedGcProducesNoHandleError() {

		System.out.println("============ Start testNoSealedGcProducesNoHandleError() ============"); //$NON-NLS-1$

		IApiComponent component = noSealedBaseline.getApiComponent(SWT_FRAGMENT);
		assertNotNull("org.eclipse.swt.gtk.linux.x86_64 not found in no-sealed baseline", component); //$NON-NLS-1$

		printComponentInfo(BASELINE, baselineBaseline.getApiComponent(SWT_FRAGMENT));
		printComponentInfo(NO_SEALED, component);

		IApiProblem[] problems = analyze(baselineBaseline, component);

		System.out.println("=== Problems for no-sealed GC ==="); //$NON-NLS-1$
		for (IApiProblem p : problems) {
			System.out.println("  id=" + p.getId() + " msg=" + p.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		}

		boolean hasHandleAdded = Arrays.stream(problems)
				.anyMatch(p -> p.getMessage() != null && p.getMessage().contains("GC.handle")); //$NON-NLS-1$

		fail("show"); //$NON-NLS-1$

		assertEquals("No 'GC.handle has been added to a class' problem expected for no-sealed GC, but got: " //$NON-NLS-1$
				+ Arrays.stream(problems).map(IApiProblem::getMessage).collect(Collectors.joining(", ")), //$NON-NLS-1$
				false, hasHandleAdded);
	}

	// --- helpers ---

	/**
	 * Verifies that the .api_description of the no-sealed component contains
	 * GC.handle with @noreference, and that the sealed component does NOT —
	 * which is the root cause of the false "GC.handle has been added" error.
	 */
	@Test
	public void testApiDescriptionContainsGcHandleOnlyForNoSealed() throws CoreException {

		System.out.println("============ Start testApiDescriptionContainsGcHandleOnlyForNoSealed() ============"); //$NON-NLS-1$

		IApiComponent sealedComp   = sealedBaseline.getApiComponent(SWT_FRAGMENT);
		IApiComponent noSealedComp = noSealedBaseline.getApiComponent(SWT_FRAGMENT);
		assertNotNull("sealed component not found", sealedComp); //$NON-NLS-1$
		assertNotNull("no-sealed component not found", noSealedComp); //$NON-NLS-1$

		IApiAnnotations sealedHandleAnnotations   = resolveHandleAnnotations(sealedComp);
		IApiAnnotations noSealedHandleAnnotations = resolveHandleAnnotations(noSealedComp);

		System.out.println("sealedHandleAnnotations: " + sealedHandleAnnotations); //$NON-NLS-1$
		System.out.println("noSealedHandleAnnotations: " + noSealedHandleAnnotations); //$NON-NLS-1$

		System.out.println("=== API Description: GC.handle annotations ==="); //$NON-NLS-1$
		System.out.println("  sealed:    " + (sealedHandleAnnotations == null ? "null (missing!)" : "restrictions=0x" + Integer.toHexString(sealedHandleAnnotations.getRestrictions()))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		System.out.println("  no-sealed: " + (noSealedHandleAnnotations == null ? "null" : "restrictions=0x" + Integer.toHexString(noSealedHandleAnnotations.getRestrictions()))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertNotNull("sealed: GC.handle annotations should not be null", sealedHandleAnnotations); //$NON-NLS-1$
		assertEquals("sealed: GC.handle should have @noreference (restrictions=0x8) but has 0x" //$NON-NLS-1$
				+ Integer.toHexString(sealedHandleAnnotations == null ? -1 : sealedHandleAnnotations.getRestrictions())
				+ " (bug: sealed class suppresses @noreference)", 0x8, sealedHandleAnnotations == null ? -1 : sealedHandleAnnotations.getRestrictions()); //$NON-NLS-1$
		assertNotNull("no-sealed: GC.handle should have @noreference annotations", noSealedHandleAnnotations); //$NON-NLS-1$
		assertEquals("no-sealed: GC.handle should have @noreference (restrictions=0x8)", 0x8, noSealedHandleAnnotations.getRestrictions()); //$NON-NLS-1$
	}

	/**
	 * Resolves the IApiAnnotations for the field GC.handle in the given component,
	 * or null if not found.
	 */
	private static IApiAnnotations resolveHandleAnnotations(IApiComponent component) throws CoreException {
		IApiDescription apiDesc = component.getApiDescription();
		IApiTypeRoot gcRoot = component.findTypeRoot("org.eclipse.swt.graphics.GC", component.getSymbolicName()); //$NON-NLS-1$
		if (gcRoot == null) {
			return null;
		}
		IApiType gcType = gcRoot.getStructure();
		if (gcType == null) {
			return null;
		}
		org.eclipse.pde.api.tools.internal.provisional.model.IApiField handleField = gcType.getField("handle"); //$NON-NLS-1$
		if (handleField == null) {
			return null;
		}
		return apiDesc.resolveAnnotations(handleField.getHandle());
	}

	private static IApiBaseline createBaseline(String name, String jarPath) throws CoreException {
		IApiBaseline baseline = ApiModelFactory.newApiBaseline(name, TestSuiteHelper.getEEDescription(), null);
		IApiComponent component = ApiModelFactory.newApiComponent(baseline, jarPath);
		assertNotNull("Could not create API component from: " + jarPath, component); //$NON-NLS-1$
		baseline.addApiComponents(new IApiComponent[] { component });
		return baseline;
	}

	private static IApiProblem[] analyze(IApiBaseline baseline, IApiComponent component) {
		BaseApiAnalyzer analyzer = new BaseApiAnalyzer();
		analyzer.analyzeComponent(null, null, null, baseline, component, new BuildContext(), new NullProgressMonitor());
		return analyzer.getProblems();
	}

	private static void printComponentInfo(String label, IApiComponent component) {

		if (true) {
			return;
		}

		System.out.println("=== Component info: " + label + " (" + component.getSymbolicName() + " " + component.getVersion() + ") ==="); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		// API Description XML
		try {
			ApiDescriptionXmlCreator visitor = new ApiDescriptionXmlCreator(component);
			component.getApiDescription().accept(visitor, null);
			System.out.println("  -- API Description --"); //$NON-NLS-1$
			System.out.println(Util.serializeDocument(visitor.getXML()));
		} catch (CoreException e) {
			System.err.println("  Failed to serialize API description: " + e.getMessage()); //$NON-NLS-1$
		}
		// Class flags for GC and related types
		System.out.println("  -- Type flags (GC*) --"); //$NON-NLS-1$
		try {
			for (IApiTypeContainer container : component.getApiTypeContainers()) {
				container.accept(new ApiTypeContainerVisitor() {
					@Override
					public boolean visit(IApiTypeContainer container2) {
						return true;
					}
					@Override
					public void visit(String subPackageName, IApiTypeRoot typeRoot) {
						if (!typeRoot.getTypeName().contains("GC")) { //$NON-NLS-1$
							return;
						}
						try {
							IApiType type = typeRoot.getStructure();
							if (type == null) {
								return;
							}
							int flags = type.getModifiers();
							System.out.println("  type=" + type.getName() //$NON-NLS-1$
									+ " modifiers=0x" + Integer.toHexString(flags) //$NON-NLS-1$
									+ " final=" + java.lang.reflect.Modifier.isFinal(flags) //$NON-NLS-1$
									+ " abstract=" + java.lang.reflect.Modifier.isAbstract(flags) //$NON-NLS-1$
									+ " superclass=" + type.getSuperclassName()); //$NON-NLS-1$
							// sealed is encoded via PermittedSubclasses attribute, not a modifier bit
							// check superinterfaces / permitted subtypes indirectly via member types
							String[] superInterfaces = type.getSuperInterfaceNames();
							System.out.println("    superInterfaces=" + java.util.Arrays.toString(superInterfaces)); //$NON-NLS-1$
						} catch (CoreException e) {
							System.err.println("  Failed to read type: " + typeRoot.getTypeName() + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
						}
					}
					@Override
					public void end(String subPackageName, IApiTypeRoot typeRoot) { /* nothing */ }
					@Override
					public void end(IApiTypeContainer container2) { /* nothing */ }
				});
			}
		} catch (CoreException e) {
			System.err.println("  Failed to iterate types: " + e.getMessage()); //$NON-NLS-1$
		}
	}
}
