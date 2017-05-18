package org.thomaspengo.tslim;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.imglib2.Cursor;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.thomaspengo.tslim.gui.ReconstructionCallback;
import org.thomaspengo.tslim.gui.ReconstructionProgress;


/**
 * Plugin to reconstruct a cylindrical stack 
 * 
 * @author Thomas Pengo
 */
public class ReconstructFromRadialSlices {
	
	private static final int RADIAL_ANGLE_SPACING_DEFAULT = 1;

	private int n_chunks = 1;
	
	private double radialStackAngleSpacing = RADIAL_ANGLE_SPACING_DEFAULT; //degrees
	
	int[] h_r_theta_i = {0,1,2};
	int[] x_y_z_i = {0,1,2};

	public enum RHT_order {
			H_R_Theta, H_Theta_R,
			R_H_Theta, R_Theta_H,
			Theta_R_H, Theta_H_R;
			
			public void toIndexArray(RHT_order arrOrder, int[] destIndexArray) {
				String[] fromL = this.toString().split("_");
				List<String> toL = Arrays.asList(arrOrder.toString().split("_"));
				
				for (int i=0; i<destIndexArray.length; i++)
					destIndexArray[i] = toL.indexOf(fromL[i]); 
			}
	};
	
	private RHT_order sourceOrder;	
	public void setSourceOrder(RHT_order newOrder) {
		sourceOrder = newOrder;
		sourceOrder.toIndexArray(RHT_order.H_R_Theta, h_r_theta_i);
	}
	public RHT_order getSourceOrder() {
		return sourceOrder;
	}
	
	private RHT_order destOrder;	
	public void setDestOrder(RHT_order newOrder) {
		destOrder = newOrder;
		destOrder.toIndexArray(RHT_order.R_Theta_H, x_y_z_i);
	}
	public RHT_order getDestOrder() {
		return sourceOrder;
	}
	
	
	/**
	 * Set the interval between two successive slices in degrees.
	 * 
	 * E.g. if the radial stack is of interval 2 degree, this parameter should be set to 2. 
	 * 
	 * @param spacing_degrees
	 */
	public void setRadialStackAngleSpacing(double spacing_degrees) {
		this.radialStackAngleSpacing = spacing_degrees;
	}

	/**
	 * Get the interval between two successive slices in degrees.
	 * 
	 * @return the interval between two successive slices in degrees.
	 */
	public double getRadialStackAngleSpacing() {
		return radialStackAngleSpacing;
	}

	private Img<FloatType> source;
	
	/**
	 * Set the number of workers. Default is the number of cores as reported by Runtime.getRuntime().availableProcessors()
	 * 
	 * @param n_workers
	 */
	public void setNWorkers(int n_workers) {
		this.n_chunks = n_workers;
	}
	
	public ReconstructFromRadialSlices() {
		 setSourceOrder(RHT_order.H_R_Theta);
		 setDestOrder(RHT_order.R_Theta_H);
		 setNWorkers(Runtime.getRuntime().availableProcessors());
	}
	
	/**
	 * Set the input stack. The stack is assumed to be a rotational stack. 
	 * 
	 * @param in
	 */
	public void setInputStack(Img<FloatType> in) {
		// Check if not null
		if (in == null)
			throw new RuntimeException("The image stack was null");
		
		this.source = in;
	}
	
	ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	
	public void startReconstruction(ReconstructionCallback<FloatType> callback) {
		executor.execute(() -> {
			try {				
				Img<FloatType> res = createReconstruction(callback);
			
				callback.reconstructed(true, res, null);
			} catch (Exception e) {
				callback.reconstructed(false, null, e);
			}
		});
	}
	
	public void killAllReconstructions() {
		executor.shutdownNow();
	}

    public Img< FloatType > createReconstruction(ReconstructionProgress callback) {

       	// Loop the output image and fetch the corresponding angle image stack
        long R = source.dimension(h_r_theta_i[1]);
        long H = source.dimension(h_r_theta_i[0]);
        long[] outputDimensions = {0,0,0};
        
        // The order of dimensions of the output needs not be XYZ. They are determined by x_y_z_i.
        outputDimensions[x_y_z_i[0]] = 2*R;
        outputDimensions[x_y_z_i[1]] = 2*R;
        outputDimensions[x_y_z_i[2]] = H;
        
        RealRandomAccessible< FloatType > interpolant1 = Views.interpolate(
                Views.extendZero( source ), new NLinearInterpolatorFactory< FloatType >() );

        ImgFactory<FloatType> outputImageFactory = new ArrayImgFactory< FloatType >();
    	Img<FloatType> output = outputImageFactory.create(outputDimensions, interpolant1.realRandomAccess().get());
    	
    	// Note that the origin_XYZ dimension order are always X_Y (plane of rotation) Z (axial)
    	final double[] origin_XYZ = new double[] {
    			output.dimension(x_y_z_i[0])/2,
    			output.dimension(x_y_z_i[1])/2,
    			0
    		};
    	
    	// Get the dimension along which to chunk the image
    	long maxDim_max = Arrays.stream(outputDimensions).max().getAsLong();
    	int maxDim_i=0;
    	for (maxDim_i=0;maxDim_i<outputDimensions.length;maxDim_i++)
    		if (outputDimensions[maxDim_i]==maxDim_max)
    			break;

    	// Create a list of ChunkProcessors
    	List<ChunkProcessor> processors = new Vector<ChunkProcessor>(n_chunks);

    	for (long maxDim_min=0; maxDim_min<maxDim_max; maxDim_min+=maxDim_max/n_chunks) {
    		long[] maxDim_minChunk = new long[outputDimensions.length];
    		long[] maxDim_maxChunk = Arrays.stream(outputDimensions.clone()).map((a) -> a-1).toArray();
    		
    		maxDim_minChunk[maxDim_i]=maxDim_min;
    		maxDim_maxChunk[maxDim_i]=maxDim_min+(maxDim_max/n_chunks)-1;
    		
    		if (maxDim_maxChunk[maxDim_i]>maxDim_max) {
    			maxDim_maxChunk[maxDim_i] = maxDim_max-1;
    		}
    		
    		processors.add(new ChunkProcessor(
    				Views.interval(output, maxDim_minChunk, maxDim_maxChunk), 
    				origin_XYZ.clone(), 
    				interpolant1.realRandomAccess()));
    	}
    	
    	// Start all threads
    	List<Thread> threads = new Vector<Thread>(n_chunks);
    	for (ChunkProcessor c : processors) {
    		Thread t = new Thread(c);
    		threads.add(t);
    		t.start();
    	}
    	
    	// Update progress every 500ms
    	Timer progressUpdater = new java.util.Timer();
    	progressUpdater.scheduleAtFixedRate(new TimerTask() {
    		@Override
    		public void run() {
    			double progressT = 0;
    			for (ChunkProcessor c : processors)
    				progressT += c.getProgress();
    			
    			callback.progressUpdate(progressT/n_chunks);
    		}}, 0, 500);
    	
    	// Wait for each thread to finish execution
    	for (Thread t : threads)
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				progressUpdater.cancel();
			}
    	
		return output;
    }
    
    class ChunkProcessor implements Runnable {
    	IntervalView<FloatType> output;
    	RealRandomAccess<FloatType> input;
    	double[] origin_XYZ;
    	double progress;
    	
    	public double getProgress() {
			return progress;
		}
    	
    	ChunkProcessor(IntervalView<FloatType> output, double[] origin_XYZ, RealRandomAccess<FloatType> input) {
    		this.output = output;
    		this.input = input;
    		this.origin_XYZ=origin_XYZ;
    		this.progress = 0;
    	}
    	
	    public void run() {
    		long todo = 1;
    		for (int idim=0; idim<output.numDimensions(); idim++)
    			todo *= output.dimension(idim);
    		long done = 0;
    		
	    	double[] x_y_z = {0,0,0};
			double[] h_r_theta = {0,0,0};
	    	Cursor<FloatType> cursor = output.localizingCursor();
	    	while (cursor.hasNext()) {
	    		// Have to make assumption that it is 3D (xyz)
	    		// Origin of the final stack (x,y,z) is the center of the first slice (X/2,Y/2,0)
	    		// Origin of the angle stack (h,r,theta) is the top left corner of the first slice (0,0,0)
	    		cursor.fwd();
	    		
	    		x_y_z[0] = cursor.getDoublePosition(x_y_z_i[0]);
	    		x_y_z[1] = cursor.getDoublePosition(x_y_z_i[1]);
	    		x_y_z[2] = cursor.getDoublePosition(x_y_z_i[2]);
	    		
	    		// At this point the coordinate order is in the standard order HRT and XYZ
	    		final double[] h_r_theta_pre = fromCubicCoordinates(x_y_z, origin_XYZ, radialStackAngleSpacing);
	    		
	    		h_r_theta[h_r_theta_i[0]]=h_r_theta_pre[0];
	    		h_r_theta[h_r_theta_i[1]]=h_r_theta_pre[1];
	    		h_r_theta[h_r_theta_i[2]]=h_r_theta_pre[2];
	    		
	    		input.setPosition(h_r_theta);
	    		cursor.get().set(input.get());
	    		
	    		progress = (double)++done/todo;
	    	}
	    }
    }
    
    /**
     * Returns the converted coordinates from x,y,z to h, rho, theta. 
     * 
     * - rho is the distance from the z-axis passing through originFinal
     * - theta is between 0 and 360
     * - h is the same as z
     *  
     * @param x_y_z a double array of size 3 with the x,y and z coordinates with origin in upper left corner
     * @param originFinal where rho=0,h=0 is in the xyz space
     * @param spacing is the spacing (degrees) between thetas(=slices) in the h,rho,theta stack
     * 
     * @return an array of size 3 with the coordinates in cylindrical form
     */
	private static final double[] fromCubicCoordinates(final double[] x_y_z, final double[] originFinal, final double spacing) {
		double[] h_r_theta = new double[]  {0,0,0};
		
		final double dx = x_y_z[0]-originFinal[0];
		final double dy = x_y_z[1]-originFinal[1];
		
		h_r_theta[0] = x_y_z[2];
		h_r_theta[1] = Math.sqrt(dx*dx+dy*dy);
		
		if(dx==0 && dy==0)
			h_r_theta[2]=0;
		else
			h_r_theta[2] = Math.atan2(dy, dx)/Math.PI*180+180;
		
		h_r_theta[2] /= spacing;
		
		return h_r_theta;
	}

	private static final double[] toCubicCoordinates(final double[] h_r_theta, final double[] origin) {
		double[] x_y_z = new double[] {0,0,0};
		
		x_y_z[0] = origin[0] + h_r_theta[1] * Math.cos(h_r_theta[2]/180*Math.PI);
		x_y_z[1] = origin[1] + h_r_theta[1] * Math.sin(h_r_theta[2]/180*Math.PI);
		x_y_z[3] = origin[2] + h_r_theta[2];
		
		return x_y_z;
	}
}

