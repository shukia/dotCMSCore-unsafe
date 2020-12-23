package com.dotmarketing.image.filter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import com.dotcms.repackage.com_dotmarketing_jhlabs_images_filters.com.dotmarketing.jhlabs.image.ScaleFilter;
import com.dotmarketing.util.ImageResizeUtils;
import com.dotmarketing.util.Logger;

public class ResizeImageFilter extends ImageFilter {
	public String[] getAcceptedParameters(){
		return  new String[] {
				"w (int) specifies width",
				"h (int) specifies height",
		};
	}
	public File runFilter(File file,    Map<String, String[]> parameters) {
		double w = parameters.get(getPrefix() +"w") != null?Integer.parseInt(parameters.get(getPrefix() +"w")[0]):0;
		double h = parameters.get(getPrefix() +"h") != null?Integer.parseInt(parameters.get(getPrefix() +"h")[0]):0;
		
		File resultFile = getResultsFile(file, parameters);
		
		if(!overwrite(resultFile,parameters)){
			return resultFile;
		}
		resultFile.delete();
		
		try {
			
			BufferedImage src = ImageIO.read(file);
			if(w ==0 && h ==0){
				return file;
			}

			
			if(w ==0 && h >0){
				w = Math.round(h * src.getWidth() / src.getHeight());
			}
			if(w >0 && h ==0){
				h = Math.round(w * src.getHeight() / src.getWidth());
			}
			
			int width    =      (int) w;    
			int hieght     =     (int) h;

			ScaleFilter filter = new ScaleFilter(width,hieght);

			BufferedImage dst = new BufferedImage(width,hieght,
					BufferedImage.TYPE_INT_ARGB);

			 dst = filter.filter(src, dst);
			ImageIO.write(dst, "png", resultFile);
			return resultFile;
			
			//fos = new FileOutputStream(resultFile);
			//ImageResizeUtils.resizeImage(new FileInputStream(file), fos, FILE_EXT, width, height);
		} catch (FileNotFoundException e) {
			Logger.error(this.getClass(), e.getMessage());
		} catch (IOException e) {
			Logger.error(this.getClass(), e.getMessage());
		}
		
		return resultFile;
	}

}
