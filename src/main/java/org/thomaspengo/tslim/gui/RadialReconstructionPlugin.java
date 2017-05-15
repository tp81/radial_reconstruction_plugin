package org.thomaspengo.tslim.gui;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;

import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;

import org.thomaspengo.tslim.ReconstructFromRadialSlices;
import org.thomaspengo.tslim.ReconstructFromRadialSlices.RHT_order;
import org.thomaspengo.tslim.util.Utils;

public class RadialReconstructionPlugin extends JDialog implements PlugIn {
	private static final String 	CURRENT_VERSION = "0.1";
	
	ImagePlus inputImage = null;
	ReconstructFromRadialSlices reconstructor = new ReconstructFromRadialSlices();
	
	/** 
	 * Utility function to make this plugin scriptable
	 *  
	 * @param arguments
	 */
	public static void start(String arguments) {
		new RadialReconstructionPlugin().run(arguments);
	}
	
	@Override
	public void run(String arg0) {
		if (arg0==null | "".equals(arg0))
			startGUI();
		else {
			Map<String,String> map = Utils.parseParameters(arg0);
			
			// Get the input image
			if (!map.containsKey("input")) {
				inputImage = ij.IJ.getImage();				
			} else {
				inputImage = ij.WindowManager.getImage(map.get("input"));
			}
			reconstructor.setInputStack(ImageJFunctions.convertFloat(inputImage));
			
			if (inputImage==null) {
				ij.IJ.error("You need to either have an image open or specify the title in the 'input' parameter");
			}
			
			if (map.containsKey("spacing")) {
				try {
					reconstructor.setRadialStackAngleSpacing(Double.valueOf(map.get("spacing")));
				} catch(NumberFormatException e) {
					ij.IJ.error("Could not parse spacing value '"+map.get("spacing")+"', using default value of "+reconstructor.getRadialStackAngleSpacing());
				}
			}
			
			Img<FloatType> res = reconstructor.createReconstruction( (progress) -> IJ.showProgress(progress) );
			
			ImageJFunctions.show(res);
		}
	}
	
	private void startGUI() {
		setLocationRelativeTo(null);
		setModal(false);

		JPanel jp = new JPanel();
		jp.setLayout(new GridBagLayout());
		
		// --- DESIGN BEGIN
		int row = 0;
		JLabel tslim = new JLabel("TSLIM Reconstruction v"+getVersion()); 	addTo(jp,tslim,0,row, 3,1);

		row++;
		addTo(jp,new JLabel("Radial stack"),0,row, 2,1);
		JComboBox<ImagePointer> jcbImages = new JComboBox<>();				addTo(jp,jcbImages,2,row, 1,1);
		JButton jbRefresh = new JButton("Refresh");							addTo(jp,jbRefresh,3,row, 1,1);
		
		row++;
		addTo(jp,new JLabel("Radial stack angle(z) spacing (deg)"),0,row, 2,1);
		JSpinner jtSpacing = new JSpinner();								addTo(jp,jtSpacing,2,row, 1,1); 

		row++;
		addTo(jp,new JLabel("Dimension order of source"),0,row, 2,1);
		JComboBox<ReconstructFromRadialSlices.RHT_order> jcOrder = new JComboBox<ReconstructFromRadialSlices.RHT_order>();
		addTo(jp,jcOrder,2,row, 1,1); 

		row++;
		addTo(jp,new JLabel("Dimension order of destination"),0,row, 2,1);
		JComboBox<ReconstructFromRadialSlices.RHT_order> jcDestOrder = new JComboBox<ReconstructFromRadialSlices.RHT_order>();
		addTo(jp,jcDestOrder,2,row, 1,1); 

		row++;
		JButton jbReconstruct = new JButton("Start"); 				addTo(jp,jbReconstruct,0,row, 2,1);
		JButton jbCancel = new JButton("Cancel"); 							addTo(jp,jbCancel,3,row, 1,1);
		// --- DESIGN END
		
		getContentPane().add(jp);
		
		// Setup image list and listeners
		
		// IMAGE LIST
		refreshImageList(jcbImages);
		jcbImages.addItemListener(e -> {
			if (e==null) {
				return;
			}
			ImagePointer imgpt = (ImagePointer)e.getItem();
			ImagePlus imgp = ij.WindowManager.getImage(imgpt.getID());
			if (imgp==null) {
				refreshImageList(jcbImages);
			} else {
				Img<FloatType> imgf = ImageJFunctions.convertFloat(imgp);
				reconstructor.setInputStack(imgf);
				jbReconstruct.setEnabled(true);				
			}
		});
		
		// SOURCE DIMENSION ORDER LIST
		jcOrder.removeAllItems();
		for (RHT_order o : RHT_order.values())
			jcOrder.addItem(o);
		jcOrder.addItemListener(e -> reconstructor.setSourceOrder((RHT_order)e.getItem()));
		jcOrder.setSelectedItem(RHT_order.H_R_Theta);
		
		// DEST DIMENSION ORDER LIST
		jcDestOrder.removeAllItems();
		for (RHT_order o : RHT_order.values())
			jcDestOrder.addItem(o);
		jcDestOrder.addItemListener(e -> reconstructor.setDestOrder((RHT_order)e.getItem()));
		jcDestOrder.setSelectedItem(RHT_order.R_Theta_H);
		
		// REFRESH BUTTON
		jbRefresh.addActionListener(e -> {
			refreshImageList(jcbImages);
			pack();
		});
		
		// RECONSTRUCT BUTTON
		jbReconstruct.setEnabled(false);
		jbReconstruct.addActionListener(e -> {
			reconstructor.startReconstruction(new ReconstructionActivity(this));
			if (Recorder.record) {
				String command = "call('"+RadialReconstructionPlugin.class.getCanonicalName()+".start','input=["+((ImagePointer)jcbImages.getSelectedItem()).getTitle()+"] spacing="+jtSpacing.getValue()+"');";
				Recorder.recordString(command);
			}});
		
		// CANCEL BUTTON
		jbCancel.addActionListener(e -> reconstructor.killAllReconstructions());
		
		// TITLE LABEL
		tslim.setFont(new Font("Monospace",Font.PLAIN,20));
		
		// SPACING SPINNER
		SpinnerNumberModel snm = new SpinnerNumberModel(1,0,360,0.1); 
		jtSpacing.setModel(snm);
		jtSpacing.addChangeListener(e -> {
			double val = snm.getNumber().doubleValue();
			reconstructor.setRadialStackAngleSpacing(val);
		});
		
		pack();
		setVisible(true);
		
		ij.WindowManager.addWindow(this);
	}
	
	class ReconstructionActivity implements ReconstructionCallback<FloatType> {
		RadialReconstructionPlugin parent;
		
		public ReconstructionActivity(RadialReconstructionPlugin radialReconstructionPlugin) {
			this.parent = radialReconstructionPlugin;
		}
		
		@Override
		public void reconstructed(boolean success,
				Img<FloatType> reconstruction, Exception e) {
			if (success) {
				ImageJFunctions.show(reconstruction);
				IJ.showProgress(1);
			} else {
				JOptionPane.showMessageDialog(parent, "Exception thrown :"+e.getLocalizedMessage());
			}
			
		}
		
		@Override
		public void progressUpdate(double progress) {
			IJ.showProgress(progress);
		}
	}
	
	private String getVersion() {
		return CURRENT_VERSION;
	}

	private void refreshImageList(JComboBox<ImagePointer> jcbImages) {
		int[] idList = ij.WindowManager.getIDList();
		
		if (idList!=null) {
			jcbImages.removeAllItems();
			for(int id : idList) {
				jcbImages.addItem(
						new ImagePointer(
								id,
								ij.WindowManager.getImage(id).getTitle()));
			}
		}
	}

	private void addTo(JPanel jp, Component o, int gridx, int gridy, int gridwidth, int gridheight) {
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = gridx;
		c.gridy = gridy;
		c.gridwidth = gridwidth;
		c.gridheight = gridheight;
		c.ipadx = 4;
		c.ipady = 4;
		
		jp.add(o, c);
	}
	
	// Small class to represent each item in the list
	class ImagePointer {
		int ID;
		String title;
		
		ImagePointer(int ID, String title) {
			this.ID = ID;
			this.title = title;
		}
		
		public int getID() {
			return ID;
		}
		
		public String getTitle() {
			return title;
		}
		
		@Override
		public String toString() {
			return String.format("%s (ID: %d)", title, ID);
		}
	}
	
	public static void main(String[] args) {
		new ImageJ();
		
		IJ.openImage("http://imagej.nih.gov/ij/images/blobs.gif").show();
		
		new RadialReconstructionPlugin().run("");
	}
}
