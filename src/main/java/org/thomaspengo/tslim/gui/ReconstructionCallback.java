package org.thomaspengo.tslim.gui;

import net.imglib2.img.Img;

public interface ReconstructionCallback <T> extends ReconstructionProgress {

	/**
	 * As soon as the reconstruction will have finished, the reconstruction plugin will call 
	 * this method, passing the reconstruction as a parameter.
	 * 
	 * @param reconstruction the reconstructed image
	 * @param success true if the reconstruction was successful
	 * @param e the exception if the reconstruction failed
	 */
	public void reconstructed(boolean success, Img<T> reconstruction, Exception e);
}
