package RNA_Scope_DAPI_Tools;

import Cellpose.CellposeSegmentImgPlusAdvanced;
import Cellpose.CellposeTaskSettings;
import StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.ImageIcon;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.Objects3DIntPopulationComputation;
import mcib3d.geom2.VoxelInt;
import mcib3d.geom2.measurements.MeasureCentroid;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import org.apache.commons.io.FilenameUtils;


/**
 * @author phm
 */
public class Tools {
    public final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
    public Calibration cal = new Calibration();
    public float pixVol = 0;
    String[] chNames = {"Nuclei", "Foci X", "Foci Y"};
    
    // Cellpose
    public int cellPoseDiameter = 40;
    public String cellPoseModel = "cyto2";
    public String cellPoseEnvDirPath = (IJ.isWindows()) ? System.getProperty("user.home")+"\\miniconda3\\envs\\CellPose" : "/opt/miniconda3/envs/cellpose";
    public double stitchTh = 0.5;
    public double minNucVol= 100;
    public double maxNucVol = 1000;  

    // Stardist
    public Object syncObject = new Object();
    public File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    protected String stardistFociModel = "fociRNA-1.2.zip";
    public String stardistOutput = "Label Image"; 
    public final double stardistPercentileBottom = 0.2;
    public final double stardistPercentileTop = 99.8;
    public final double stardistProbThresh = 0.2;
    public final double stardistOverlayThresh = 0.35;
    public double minFociVol= 0.2;
    public double maxFociVol = 10;
 
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.showMessage("Error", "3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Find images in folder
     */
    public ArrayList findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in " + imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt) && !f.startsWith("."))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
     /**
     * Find channels name
     * @param imageName
     * @return 
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        ArrayList<String> channels = new ArrayList<>();
        int chs = reader.getSizeC();
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelName(0, n).toString());
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelFluor(0, n).toString());
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break; 
            case "ics2" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels.add(Integer.toString(n));
                    else 
                        channels.add(meta.getChannelExcitationWavelength(0, n).value().toString());
                break;        
            default :
                for (int n = 0; n < chs; n++)
                    channels.add(Integer.toString(n));

        }
        return(channels.toArray(new String[channels.size()]));         
    }
    
    
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] channels) {
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 80, 0);
        gd.addImage(icon);
          
        gd.addMessage("Channels", new Font(Font.MONOSPACED , Font.BOLD, 12), Color.blue);
        int index = 0;
        for (String chName: chNames) {
            gd.addChoice(chName + ": ", channels, channels[index]);
            index++;
        }
        
        gd.addMessage("Nuclei detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min nucleus volume (µm3):", minNucVol);
        gd.addNumericField("Max nucleus volume (µm3):", maxNucVol); 
        
        gd.addMessage("Foci detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min foci volume (µm3):", minFociVol);
        gd.addNumericField("Max foci volume (µm3):", maxFociVol); 
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY calibration (µm):", cal.pixelWidth);
        gd.addNumericField("Z calibration (µm):", cal.pixelDepth);
        gd.showDialog();
        
        String[] ch = new String[chNames.length];
        for (int i = 0; i < chNames.length; i++)
            ch[i] = gd.getNextChoice();

        minNucVol = (float) gd.getNextNumber();
        maxNucVol = (float) gd.getNextNumber();
        minFociVol = (float) gd.getNextNumber();
        maxFociVol = (float) gd.getNextNumber();
        
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = (float) (cal.pixelWidth*cal.pixelHeight*cal.pixelDepth);
        
        if(gd.wasCanceled())
            ch = null;
                
        return(ch);
    }
    
    
    /**
     * Flush and close an image
     */
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Look for all 3D cells in a Z-stack: 
     * - apply CellPose in 2D slice by slice 
     * - let CellPose reconstruct cells in 3D using the stitch threshold parameters
     */
    public ArrayList<Nucleus> cellposeDetection(ImagePlus img) throws IOException{
        float resizeFactor = 0.5f;
        ImagePlus imgResized = img.resize((int)(img.getWidth()*resizeFactor), (int)(img.getHeight()*resizeFactor), 1, "none");

        // Define CellPose settings
        CellposeTaskSettings settings = new CellposeTaskSettings(cellPoseModel, 1, cellPoseDiameter, cellPoseEnvDirPath);
        settings.setStitchThreshold(stitchTh);
        settings.useGpu(true);
       
        // Run CellPose
        CellposeSegmentImgPlusAdvanced cellpose = new CellposeSegmentImgPlusAdvanced(settings, imgResized);
        ImagePlus imgOut = cellpose.run();
        imgOut = imgOut.resize(img.getWidth(), img.getHeight(), "none");
        imgOut.setCalibration(cal);
        
        // Get cells as a population of objects
        Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgOut));
        System.out.println(pop.getNbObjects() + " CellPose detections");
       
        // Filter cells
        pop = zFilterPop(pop);
        pop = new Objects3DIntPopulationComputation(pop).getExcludeBorders(ImageHandler.wrap(img), false);
        pop = new Objects3DIntPopulationComputation​(pop).getFilterSize​(minNucVol/pixVol, maxNucVol/pixVol);
        pop.resetLabels();
        System.out.println(pop.getNbObjects() + " detections remaining after size filtering");
        
        ArrayList<Nucleus> nuclei = new ArrayList<Nucleus>();
        for (Object3DInt obj : pop.getObjects3DInt())
            nuclei.add(new Nucleus(obj));
                
        flush_close(imgResized);
        flush_close(imgOut);
        return(nuclei);
    }
    
    
    /*
     * Remove objects present in only one z slice from population 
     */
    public Objects3DIntPopulation zFilterPop (Objects3DIntPopulation pop) {
        Objects3DIntPopulation popZ = new Objects3DIntPopulation();
        for (Object3DInt obj : pop.getObjects3DInt()) {
            int zmin = obj.getBoundingBox().zmin;
            int zmax = obj.getBoundingBox().zmax;
            if (zmax != zmin)
                popZ.addObject(obj);
        }
        return popZ;
    } 
    
    
    /**
     * Apply StarDist 2D slice by slice
     * Label detections in 3D
     */
   public Objects3DIntPopulation stardistDetection(ImagePlus img) throws IOException{
       ImagePlus imgIn = new Duplicator().run(img);
       
       // StarDist
       File starDistModelFile = new File(modelsPath+File.separator+stardistFociModel);
       StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
       star.loadInput(imgIn);
       star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThresh, stardistOverlayThresh, stardistOutput);
       star.run();
       
       // Label detections in 3D
       ImagePlus imgLabels = star.associateLabels();
       imgLabels.setCalibration(cal); 
       
       // Get objects as a population of objects
       Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels));  
       System.out.println(pop.getNbObjects()+" Stardist detections");
       
       // Filter objects
       pop = new Objects3DIntPopulationComputation​(pop).getFilterSize​(minFociVol/pixVol, maxFociVol/pixVol);
       pop.resetLabels();
       System.out.println(pop.getNbObjects()+ " detections remaining after size filtering");
       
       flush_close(img);
       flush_close(imgLabels);
       return(pop);
    }
    

    /**
     * Tag cell with parameters
     */
    public void tagCells(Objects3DIntPopulation fociXPop, Objects3DIntPopulation fociYPop, ArrayList<Nucleus> nuclei, ImagePlus imgNuc, String outDir, String imgName) {
        Objects3DIntPopulation allFociXPop = new Objects3DIntPopulation();
        Objects3DIntPopulation allFociYPop = new Objects3DIntPopulation();
        
        for (Nucleus nucleus: nuclei) {
            Object3DInt nucObj = nucleus.nucleus;
            
            // Get nucleus parameters
            double nucVol = new MeasureVolume(nucObj).getVolumeUnit();
            
            // Get foci X parameters
            Objects3DIntPopulation fociXNucPop = findFociInNucleus(nucObj, fociXPop);
            addPops(fociXNucPop, allFociXPop);
            int fociX = fociXNucPop.getNbObjects();
            double fociXVol = findPopVolume(fociXNucPop);
            
            // Get foci Y parameters
            Objects3DIntPopulation fociYNucPop = findFociInNucleus(nucObj, fociYPop);
            addPops(fociYNucPop, allFociYPop);
            int fociY = fociYNucPop.getNbObjects();
            double fociYVol = findPopVolume(fociYNucPop);
            
            // Add all parameters to cell
            nucleus.setParams(nucObj.getLabel(), nucVol, fociX, fociXVol, fociY, fociYVol);
        }
        
        drawPop(nuclei, allFociXPop, allFociYPop, imgNuc, outDir, imgName);
    }

    
    /**
     * Find foci in nucleus
     */
    public Objects3DIntPopulation findFociInNucleus(Object3DInt nucObj, Objects3DIntPopulation fociPop) {
        Objects3DIntPopulation fociNucPop = new Objects3DIntPopulation();
        if (fociPop.getNbObjects() != 0) {
            for (Object3DInt foci : fociPop.getObjects3DInt()) {
                VoxelInt fociCenter = new MeasureCentroid(foci).getCentroidRoundedAsVoxelInt();
                if (nucObj.contains(fociCenter)){
                   fociNucPop.addObject(foci); 
                }
            }
        }
        return(fociNucPop);
    }
    
   
    /**
     * Add two populations of objects
     */
    private void addPops(Objects3DIntPopulation pop1, Objects3DIntPopulation pop2) {
        int index = (pop2.getNbObjects() == 0) ? 1 : pop2.getNbObjects()+1;
        for (Object3DInt obj : pop1.getObjects3DInt()) {
            obj.setLabel(index);
            pop2.addObject(obj);
            index++;
        }
    }
    
     
    /**
     * Find total volume of objects 
     */
    public double findPopVolume(Objects3DIntPopulation pop) {
        double sumVol = 0;
        for(Object3DInt obj: pop.getObjects3DInt()) {
            sumVol += new MeasureVolume(obj).getVolumeUnit();
        }
        return(sumVol);
    }
    
    /**
     * Draw results in image
     */
    public void drawPop(ArrayList<Nucleus> nuclei, Objects3DIntPopulation fociXPop, Objects3DIntPopulation fociYPop, ImagePlus img, String outDir, String imgName) {
        ImageHandler imgNuc = ImageHandler.wrap(img).createSameDimensions();
        for (Nucleus nucleus: nuclei) 
           nucleus.nucleus.drawObject(imgNuc);
        
        ImageHandler imgFociX = ImageHandler.wrap(img).createSameDimensions();
        for (Object3DInt foci: fociXPop.getObjects3DInt())
           foci.drawObject(imgFociX, 255);
        
        ImageHandler imgFociY = ImageHandler.wrap(img).createSameDimensions();
        for (Object3DInt foci: fociYPop.getObjects3DInt())
           foci.drawObject(imgFociY, 255);
        
        ImagePlus[] imgColors = {imgFociX.getImagePlus(), imgFociY.getImagePlus(), imgNuc.getImagePlus(), img};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir + imgName + ".tif");
    }
    
}