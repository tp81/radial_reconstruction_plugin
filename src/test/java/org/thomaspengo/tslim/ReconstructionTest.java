package org.thomaspengo.tslim;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.thomaspengo.tslim.ReconstructFromRadialSlices;


/**
 * A test class for the reconstruction plugin
 *   
 * @author Thomas Pengo
 */
@RunWith(Enclosed.class) 
public class ReconstructionTest {
	static ImageJ ij;
	
	@BeforeClass
	public static void setUp() {
		ij = new ImageJ();
	}
	
	@AfterClass
	public static void tearDown() {
		ij.dispose();
	}
	
	static Img<FloatType> createParallelogram(long X, long Y, long Z) {
		Img<FloatType> img = new ArrayImgFactory<FloatType>().create(new long[]{X,Y,Z},new FloatType());
		
		Cursor<FloatType> cursor = img.localizingCursor();
		while(cursor.hasNext()) {
			cursor.fwd();
			
			double x = cursor.getDoublePosition(0);
			double y = cursor.getDoublePosition(1);
			double z = cursor.getDoublePosition(2);
			
			if ( Math.abs(x-X/2)< X/4 &&
					Math.abs(y-Y/2)<Y/4 &&
					Math.abs(z-Z/2)<Z/4 )
				cursor.get().set(1);
		}
		
		return img;
	}

	static Img<FloatType> createOval(long X, long Y, long Z) {
		Img<FloatType> img = new ArrayImgFactory<FloatType>().create(new long[]{X,Y,Z},new FloatType());
		
		Cursor<FloatType> cursor = img.localizingCursor();
		while(cursor.hasNext()) {
			cursor.fwd();
			
			double x = cursor.getDoublePosition(0);
			double y = cursor.getDoublePosition(1);
			double z = cursor.getDoublePosition(2);
			
			double dx = x-X/2; dx = dx/X;
			double dy = y-Y/2; dy = dy/Y;
			double dz = z-Z/2; dz = dz/Z;
			
			if ( dx*dx+dy*dy+dz*dz < 0.125 ) 
				cursor.get().set(1);
		}
		
		return img;
	}

	public static class BasicTester {
		@Test
		public void testCreateCube1() {
			long[] dims = new long[] {100,100,10};
			
			Img<FloatType> out = createParallelogram(dims[0],dims[1],dims[2]);
			
			int d = out.numDimensions();
			for (d=0;d<3;d++)
				assertEquals(out.dimension(d),dims[d]);
		}
		
		@Test
		public void testCreateCube2() {
			Img<FloatType> out = createParallelogram(100, 100, 10);
			
			RandomAccess<FloatType> c = out.randomAccess();
			c.setPosition(new long[] {50,50,5});
			assertEquals(1.0, c.get().getRealDouble(), .01);
			
			c.setPosition(new long[] {99,50,5});
			assertEquals(0.0, c.get().getRealDouble(), .01);
			
			c.setPosition(new long[] {26,26,5});
			assertEquals(1.0, c.get().getRealDouble(), .01);
		}
		
		@Test
		public void testCreateOval1() {
			Img<FloatType> out = createOval(100, 100, 50);
						
			RandomAccess<FloatType> c = out.randomAccess();
			c.setPosition(new long[] {50,50,25});
			assertEquals(1.0, c.get().getRealDouble(), .01);
			
			c.setPosition(new long[] {25,25,25});
			assertEquals(0.0, c.get().getRealDouble(), .01);
			
			c.setPosition(new long[] {25,25,30});
			assertEquals(0.0, c.get().getRealDouble(), .01);
			
			c.setPosition(new long[] {26,26,25});
			assertEquals(1.0, c.get().getRealDouble(), .01);
		}
	}
	
	public static class CubeReconstructionTester {
		@Test
		public void testReconstruct1() {
			Img<FloatType> in = createParallelogram(300, 150, 300);

			ReconstructFromRadialSlices r = new ReconstructFromRadialSlices();
			
			r.setInputStack(in);

			Img<FloatType> out = r.createReconstruction();
		}
	}
	
	private static void pause(int secs) {
		try {
			TimeUnit.SECONDS.sleep(20);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
}