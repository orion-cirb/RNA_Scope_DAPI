package RNA_Scope_DAPI_Tools;

import java.util.HashMap;
import mcib3d.geom2.Object3DInt;

/**
 * @author hm
 */
public class Nucleus {
    
    public Object3DInt nucleus;
    public HashMap<String, Double> params;
    
    
    public Nucleus(Object3DInt nucleus) {
        this.nucleus = nucleus;
        this.params = new HashMap<>();
    }
    
    public void setParams(double nucLabel, double nucVol, double dotsX, double volDotsX, double dotsY, double volDotsY) {
        params.put("nucIndex", nucLabel);
        params.put("nucVol", nucVol);
        params.put("dotsX", dotsX);
        params.put("volDotsX", volDotsX);
        params.put("dotsY", dotsY);
        params.put("volDotsY", volDotsY);
    }
}
