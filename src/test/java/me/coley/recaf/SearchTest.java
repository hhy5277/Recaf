package me.coley.recaf;

import me.coley.recaf.search.*;
import me.coley.recaf.workspace.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static me.coley.recaf.search.StringMatchMode.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the Search api.
 *
 * @author Matt
 */
public class SearchTest extends Base {
	private static JavaResource base;
	private static Workspace workspace;

	@BeforeAll
	public static void setup() {
		try {
			base = new JarResource(getClasspathFile("calc.jar"));
			workspace = new Workspace(base);
		} catch(IOException ex) {
			fail(ex);
		}
	}

	@Test
	public void testStringResultContext() {
		// Setup search - String "EVAL: " in Calculator.evaluate(int, String)
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug()
				.query(new StringQuery("EVAL", STARTS_WITH)).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(1, results.size());
		StringResult res =  (StringResult)results.get(0);
		assertEquals("EVAL: ",res.getText());
		// Assert context shows the string is in the expected method
		// - res context is of the LDC insn
		// - parent is of the method containing the String
		contextEquals(res.getContext().getParent(), "calc/Calculator", "evaluate", "(ILjava/lang/String;)D");
	}

	@Test
	public void testMemberDefAnyInClass() {
		// Setup search - Any member in "Expression"
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipDebug()
				.query(new MemberDefinitionQuery("calc/Expression", null, null, EQUALS)).build();
		// Show results - should be given four (field + 3 methods)
		Set<String> results = collector.getAllResults().stream()
				.map(Object::toString)
				.collect(Collectors.toSet());
		assertEquals(4, results.size());
		assertTrue(results.contains("calc/Expression.i I"));
		assertTrue(results.contains("calc/Expression.<init>(I)V"));
		assertTrue(results.contains("calc/Expression.accept(Ljava/lang/String;)D"));
		assertTrue(results.contains("calc/Expression.evaluate(Ljava/lang/String;)D"));
	}

	@Test
	public void testMemberDefAnyIntField() {
		// Setup search - Any int member in any class
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipDebug()
				.query(new MemberDefinitionQuery(null, null, "I", EQUALS)).build();
		// Show results - should be the given three
		Set<String> results = collector.getAllResults().stream()
				.map(Object::toString)
				.collect(Collectors.toSet());
		assertEquals(3, results.size());
		assertTrue(results.contains("calc/Parenthesis.LEVEL_UNSET I"));
		assertTrue(results.contains("calc/Calculator.MAX_DEPTH I"));
		assertTrue(results.contains("calc/Expression.i I"));
	}

	@Test
	public void testClassReference() {
		// Setup search - References to the "Exponent" class
		// - Should be 3 references in "Calculator" and three self references in "Exponent"
		SearchCollector collector = SearchBuilder.in(workspace)
				.query(new ClassReferenceQuery("calc/Exponent")).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(6, results.size());
		int calc = 0, exp = 0;
		for (SearchResult res : results) {
			Context.InsnContext insnContext = (Context.InsnContext) res.getContext();
			String owner = insnContext.getParent().getParent().getName();
			switch(owner) {
				case "calc/Calculator": calc++; break;
				case "calc/Exponent": exp++; break;
				default: fail("Unexpected result in: " + owner);
			}
		}
		// Three self-references (same method, 3 times)
		// - INVOKE evaluate(String)
		// - INVOKE evaluate(String)
		// - INVOKE evaluate(String)
		assertEquals(3, exp);
		// Three references in Calculator
		// - NEW Exponent
		// - INVOKE Exponent.<init>
		// - INVOKE Exponent.accept(String)
		assertEquals(3, calc);
	}

	@Test
	public void testMemberReference() {
		// Setup search - References to the "Calculator.log(int, String)" method
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug()
				.query(new MemberReferenceQuery("calc/Calculator", "log", null, EQUALS)).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(2, results.size());
		for (SearchResult res : results) {
			Context.InsnContext insnContext = (Context.InsnContext) res.getContext();
			String owner = insnContext.getParent().getParent().getName();
			if (!owner.equals("calc/Calculator")) {
				fail("Unexpected result in: " + owner);
			}
		}
	}

	@Test
	public void testNoMemberReferenceWhenCodeSkipped() {
		// Setup search - References to the "Calculator.log(int, String)" method
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipCode()
				.query(new MemberReferenceQuery("calc/Calculator", "log", null, EQUALS)).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(0, results.size());
	}

	@Test
	public void testClassNameEquals() {
		// Setup search - Equality for "Start"
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipCode()
				.query(new ClassNameQuery("Start", EQUALS)).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(1, results.size());
		assertEquals("Start", ((ClassResult)results.get(0)).getName());
	}

	@Test
	public void testClassNameStartsWith() {
		// Setup search - Starts with for "Start"
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipCode()
				.query(new ClassNameQuery("S", STARTS_WITH)).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(1, results.size());
		assertEquals("Start", ((ClassResult)results.get(0)).getName());
	}

	@Test
	public void testClassNameEndsWith() {
		// Setup search - Ends with for "ParenTHESIS"
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipCode()
				.query(new ClassNameQuery("thesis", ENDS_WITH)).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(1, results.size());
		assertEquals("calc/Parenthesis", ((ClassResult)results.get(0)).getName());
	}

	@Test
	public void testClassNameRegex() {
		// Setup search - Regex for "Start" by matching only word characters (no package splits)
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipCode()
				.query(new ClassNameQuery("^\\w+$", REGEX)).build();
		// Show results
		List<SearchResult> results = collector.getAllResults();
		assertEquals(1, results.size());
		assertEquals("Start", ((ClassResult)results.get(0)).getName());
	}

	@Test
	public void testClassInheritance() {
		// Setup search - All implementations of "Expression"
		SearchCollector collector = SearchBuilder.in(workspace).skipDebug().skipCode()
				.query(new ClassInheritanceQuery(workspace, "calc/Expression")).build();
		// Show results
		Set<String> results = collector.getAllResults().stream()
				.map(res -> ((ClassResult)res).getName())
				.collect(Collectors.toSet());
		assertEquals(5, results.size());
		assertTrue(results.contains("calc/Parenthesis"));
		assertTrue(results.contains("calc/Exponent"));
		assertTrue(results.contains("calc/MultAndDiv"));
		assertTrue(results.contains("calc/AddAndSub"));
		assertTrue(results.contains("calc/Constant"));
	}

	private static void contextEquals(Context<?> context, String owner, String name, String desc) {
		assertTrue(context instanceof Context.MemberContext);
		Context.MemberContext member = (Context.MemberContext) context;
		assertEquals(owner, member.getParent().getName());
		assertEquals(name, member.getName());
		assertEquals(desc, member.getDesc());
	}
}
