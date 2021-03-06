package ch.unibas.cs.dbis.cineast.api;

import java.util.List;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import ch.unibas.cs.dbis.cineast.core.data.LongDoublePair;
import ch.unibas.cs.dbis.cineast.core.db.ShotLookup.ShotDescriptor;
import ch.unibas.cs.dbis.cineast.core.db.VideoLookup.VideoDescriptor;

public final class JSONEncoder {

	private JSONEncoder(){}
	
	public static final JsonObject encodeResult(long shotId, double score, String category, int containerId, boolean includeType){
		JsonObject _return = new JsonObject();
		if(includeType){
			_return.add("type", "result");
		}
		_return.add("shotid", shotId);
		_return.add("score", score);
		_return.add("category", category);
		_return.add("containerid", containerId);
		return _return;
	}
	
	public static final JsonObject encodeResult(long shotId, double score, String category, int containerId){
		return encodeResult(shotId, score, category, containerId, true);
	}
	
	public static final JsonObject encodeResultBatched(List<LongDoublePair> ldpList, String category, int containerId){
		JsonObject _return = new JsonObject();
		_return.add("type", "batch");
		_return.add("inner", "result");
		JsonArray array = new JsonArray();
		for(LongDoublePair ldp : ldpList){
			array.add(encodeResult(ldp.key, ldp.value, category, containerId, false));
		}
		_return.add("array", array);
		return _return;
	}
	
	public static final JsonObject encodeShot(long shotId, long videoId, long startFrame, long endFrame, boolean includeType){
		JsonObject _return = new JsonObject();
		if(includeType){
			_return.add("type", "shot");
		}
		_return.add("shotid", shotId);
		_return.add("videoid", videoId);
		_return.add("start",  startFrame);
		_return.add("end", endFrame);
		return _return;
	}
	
	public static JsonObject encodeShot(ShotDescriptor sd, boolean includeType){
		return encodeShot(sd.getShotId(), sd.getVideoId(), sd.getStartFrame(), sd.getEndFrame(), includeType);
	}
	
	public static final JsonObject encodeShot(long shotId, long videoId, long startFrame, long endFrame){
		return encodeShot(shotId, videoId, startFrame, endFrame, true);
	}
	
	public static final JsonObject encodeShot(ShotDescriptor sd){
		return encodeShot(sd, true);
	}
	
	public static final JsonObject encodeShotBatch(List<ShotDescriptor> sdList){
		JsonObject _return = new JsonObject();
		_return.add("type", "batch");
		_return.add("inner", "shot");
		JsonArray array = new JsonArray();
		for(ShotDescriptor sd : sdList){
			array.add(encodeShot(sd, false));
		}
		_return.add("array", array);
		return _return;
	}
	
	public static final JsonObject encodeVideo(String name, long videoId, String path, int width, int height, long frames, double seconds, boolean includeType){
		JsonObject _return = new JsonObject();
		if(includeType){
			_return.add("type", "video");
		}
		_return.add("name", name);
		_return.add("videoid", videoId);
		_return.add("path", path);
		_return.add("width", width);
		_return.add("height", height);
		_return.add("frames", frames);
		_return.add("seconds", Double.isNaN(seconds) ? 1 : seconds);
		return _return;
	}
	
	public static final JsonObject encodeVideo(ShotDescriptor sd, boolean includeType){
		return encodeVideo(sd.getName(), sd.getVideoId(), sd.getPath(), sd.getWidth(), sd.getHeight(), sd.getFramecount(), sd.getSeconds(), includeType);
	}
	
	public static final JsonObject encodeVideo(String name, long videoId, String path, int width, int height, long frames, double seconds){
		return encodeVideo(name, videoId, path, width, height, frames, seconds, true);
	}
	
	public static final JsonObject encodeVideo(ShotDescriptor sd){
		return encodeVideo(sd, true);
	}
	
	public static final JsonObject encodeVideo(VideoDescriptor vd, boolean includeType){
		return encodeVideo(vd.getName(), vd.getVideoId(), vd.getPath(), vd.getWidth(), vd.getHeight(), vd.getFramecount(), vd.getSeconds(), includeType);
	}
	
	public static final JsonObject encodeVideo(VideoDescriptor vd){
		return encodeVideo(vd, true);
	}
	
	public static final JsonObject encodeVideoBatch(List<ShotDescriptor> sdList){
		JsonObject _return = new JsonObject();
		_return.add("type", "batch");
		_return.add("inner", "video");
		JsonArray array = new JsonArray();
		for(ShotDescriptor sd : sdList){
			array.add(encodeVideo(sd, false));
		}
		_return.add("array", array);
		return _return;
	}
	
	public static final JsonObject encodeResultName(String resultName){
		JsonObject _return = new JsonObject();
		_return.add("type", "resultname");
		_return.add("name", resultName);
		return _return;
	}
	
}
