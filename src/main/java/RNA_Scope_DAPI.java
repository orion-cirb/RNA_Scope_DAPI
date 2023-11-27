import RNA_Scope_DAPI_Tools.Tools;
import RNA_Scope_DAPI_Tools.Nucleus;
import ij.IJ;
import ij.ImagePlus;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.util.ImageProcessorReader;
import ij.plugin.PlugIn;
import java.io.FileWriter;
import java.util.ArrayList;
import loci.plugins.in.ImporterOptions;
import mcib3d.geom2.Objects3DIntPopulation;
import org.apache.commons.io.FilenameUtils;
import org.scijava.util.ArrayUtils;


/**
 * Find nuclei and their respective number of foci X and foci Y
 * 
 * @author phm
 */
public class RNA_Scope_DAPI implements PlugIn {
    
    Tools tools = new Tools();
    private String imageDir = "";
    public  String outDirResults = "";
    public  String rootName = "";
    public BufferedWriter results;
    
    public void run(String arg) {
        try {
            if ((!tools.checkInstalledModules())) {
                return;
            } 
         
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }
            
            // Find images with nd extension
            ArrayList<String> imageFile = tools.findImages(imageDir, "nd");
            if (imageFile == null) {
                IJ.showMessage("Error", "No images found with nd extension");
                return;
            }
            
            // Create output folder
            outDirResults = imageDir + File.separator + "Results" + File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write header for results file
            String header = "Image name\tNucleus ID\tNucleus volume (µm3)\tNumber foci X\tVolume foci X (µm3)\tNumber foci Y\tVolume foci Y (µm)\n";
            FileWriter fwResults = new FileWriter(outDirResults + "results.xls", false);
            results = new BufferedWriter(fwResults);
            results.write(header);
            results.flush();
           
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFile.get(0));
            
            // Find image calibration
            tools.cal = tools.findImageCalib(meta);
            
            // Find channel names
            String[] channels = tools.findChannels(imageFile.get(0), meta, reader);

            // Channels dialog
            String[] chs = tools.dialog(channels);
            if (chs == null) {
                IJ.showStatus("Plugin canceled");
                return;
            }
            
            for (String f : imageFile) {
                rootName = FilenameUtils.getBaseName(f);
                System.out.println("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setCrop(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);

                // Open nuclei channel
                System.out.println("- Analyzing " + chs[0] + " channel -");
                int indexCh = ArrayUtils.indexOf(channels, chs[0]);
                ImagePlus imgNucleus = BF.openImagePlus(options)[indexCh];
                // Find nuclei
                System.out.println("Finding " + chs[0] + " nuclei...");
                ArrayList<Nucleus> nuclei = tools.cellposeDetection(imgNucleus);
                
                // Open foci X channel
                System.out.println("- Analyzing " + chs[1] + " channel -");
                indexCh = ArrayUtils.indexOf(channels, chs[1]);
                ImagePlus imgFociX = BF.openImagePlus(options)[indexCh];
                // Find foci X
                System.out.println("Finding " + chs[1] + " foci X...");
                Objects3DIntPopulation fociXPop = tools.stardistDetection(imgFociX);
                
                // Open foci Y channel
                System.out.println("- Analyzing " + chs[2] + " channel -");
                indexCh = ArrayUtils.indexOf(channels, chs[2]);
                ImagePlus imgFociY = BF.openImagePlus(options)[indexCh];
                // Find foci Y
                System.out.println("Finding " + chs[2] + " foci Y...");
                Objects3DIntPopulation fociYPop = tools.stardistDetection(imgFociY);
                
                // Tag nuclei with parameters
                System.out.println("- Measuring cells parameters -");
                tools.tagCells(fociXPop, fociYPop, nuclei, imgNucleus, outDirResults, rootName);
                
                // Write nucleus parameters results
                for (Nucleus nucleus: nuclei) {
                    HashMap<String, Double> params = nucleus.params;
                    results.write(rootName+"\t"+params.get("nucIndex")+"\t"+params.get("nucVol")+"\t"+params.get("dotsX")+
                            "\t"+params.get("volDotsX")+"\t"+params.get("dotsY")+"\t"+params.get("volDotsY")+"\n");
                    results.flush();
                }
                tools.flush_close(imgNucleus);
                tools.flush_close(imgFociX);
                tools.flush_close(imgFociY);
            }            
            results.close();
        } catch (IOException | DependencyException | ServiceException | FormatException  ex) {
            Logger.getLogger(RNA_Scope_DAPI.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        System.out.println("--- All done! ---");
    }
}

           