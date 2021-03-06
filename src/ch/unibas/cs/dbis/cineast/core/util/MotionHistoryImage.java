package ch.unibas.cs.dbis.cineast.core.util;

import java.util.ArrayList;

import ch.unibas.cs.dbis.cineast.core.color.ReadableRGBContainer;
import ch.unibas.cs.dbis.cineast.core.data.Frame;
import ch.unibas.cs.dbis.cineast.core.data.FrameContainer;
import ch.unibas.cs.dbis.cineast.core.data.MultiImage;

public class MotionHistoryImage {

	private MotionHistoryImage(int width, int height){
		this.width = width;
		this.height = height;
		this.intensities = new byte[width * height];
	}
	
	private final int width, height;
	private final byte[] intensities;
	
	public int getWidth(){
		return this.width;
	}
	
	public int getHeight(){
		return this.height;
	}
	
	public byte[] getIntensities(){
		return this.intensities;
	}
	
	public static MotionHistoryImage motionHistoryImage(FrameContainer container, int lifeTime, int threshold){
		return motionHistoryImage(container, lifeTime, threshold, true);
	}
	/**
	 * 
	 * @param container
	 * @param lifeTime number of frames to consider for image
	 * @param threshold threshold distance [0, 255]
	 * @param useThumbnails produce image based on thumbnails to entire frame
	 * @return
	 */
	public static MotionHistoryImage motionHistoryImage(FrameContainer container, int lifeTime, int threshold, boolean useThumbnails){
		if(container.getFrames().isEmpty()){
			return null;
		}
		
		ArrayList<int[]> images = new ArrayList<int[]>(container.getFrames().size());
		for(Frame f : container.getFrames()){
			if(useThumbnails){
				images.add(f.getImage().getThumbnailColors());
			}else{
				images.add(f.getImage().getColors());
			}
		}
		
		MultiImage first = container.getFrames().get(0).getImage();
		
		MotionHistoryImage _return = new MotionHistoryImage(
				useThumbnails ? first.getThumbnailImage().getWidth() : first.getWidth(),
				useThumbnails ? first.getThumbnailImage().getHeight() : first.getHeight());
		
		if(container.getFrames().size() == 1){
			return _return;
		}
		
		float sub = 1f / lifeTime;
		
		float[] tmp = new float[images.get(0).length];
		for(int i = 1; i < images.size(); ++i){
			int[] current = images.get(i);
			int[] last = images.get(i - 1);
			
			for(int j = 0; j < current.length; ++j){
				int dist = dist(last[j], current[j]);
				if(dist > threshold){
					tmp[j] = 1f;
				}else{
					tmp[j] = Math.max(0f, tmp[j] - sub);
				}
			}
			
		}
		
		for(int i = 0; i < tmp.length; ++i){
			_return.intensities[i] = (byte)Math.round(127f * tmp[i]);
		}
		
		return _return;
	}
	
	private static int dist(int color1, int color2){
		
		float l1 = 0.2126f * ReadableRGBContainer.getRed(color1) + 0.7152f * ReadableRGBContainer.getGreen(color1) + 0.0722f * ReadableRGBContainer.getBlue(color1);
		float l2 = 0.2126f * ReadableRGBContainer.getRed(color2) + 0.7152f * ReadableRGBContainer.getGreen(color2) + 0.0722f * ReadableRGBContainer.getBlue(color2);
		
		return Math.round(Math.abs(l1 - l2));
	}
	
}
