package org.thomaspengo.tslim.gui;

public interface ReconstructionProgress {

	/**
	 * Gives some progress update. No guarantee as how many times this is called, but don't 
	 * run any expensive task here if you don't want your reconstruction to slow down.
	 * 
	 * @param progress a value between 0 and 1 
	 */
	public void progressUpdate(double progress);

}
