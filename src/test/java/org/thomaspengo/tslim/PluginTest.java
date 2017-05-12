package org.thomaspengo.tslim;

import static org.junit.Assert.*;
import ij.ImageJ;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.thomaspengo.tslim.util.Utils;

@RunWith(Enclosed.class) 
public class PluginTest {

	public static class ImageJTests {
		static ImageJ ij;
		
		@BeforeClass
		public static void setUp() {
			ij = new ImageJ();
		}
		
		@AfterClass
		public static void tearDown() {
			ij.dispose();
		}
		
		@Test
		public void test1() {
			
		}
	}
	
	public static class UtilsTests {
		@Test
		public void testParameterParsing() {
			assertEquals(
					Utils.parseParameters("input=Tom a122").get("input"),
					"Tom");
			assertEquals(
					Utils.parseParameters("   input=Tom in=ok").get("input"),
					"Tom");
			assertEquals(
					Utils.parseParameters("input=[Tom a]122").get("input"),
					"Tom a");
			assertEquals(
					Utils.parseParameters("input=[Tom a]122=33").get("122"),
					"33");
			assertEquals(
					Utils.parseParameters("input=[Tom a] not=33").get("not"),
					"33");
			
		}
	}
	
}
