/**
 * 
 */
package org.thomaspengo.tslim;

import ij.ImageJ;

import org.thomaspengo.tslim.gui.RadialReconstructionPlugin;

/**
 * @author Thomas Pengo
 *
 */
public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ImageJ();
		
		ij.IJ.run("T1 Head (2.4M, 16-bits)");
		ij.IJ.run("Flip Vertically","stack");
		
		String title = ij.IJ.getImage().getTitle();
		
		new RadialReconstructionPlugin().run("input=["+title+"] spacing=2.79");
		
	}

}
