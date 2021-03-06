package ch.unibas.cs.dbis.cineast.core.features;

import java.sql.ResultSet;
import java.util.List;

import ch.unibas.cs.dbis.cineast.core.config.Config;
import ch.unibas.cs.dbis.cineast.core.data.FloatVector;
import ch.unibas.cs.dbis.cineast.core.data.FrameContainer;
import ch.unibas.cs.dbis.cineast.core.data.LongDoublePair;
import ch.unibas.cs.dbis.cineast.core.features.abstracts.AbstractFeatureModule;
import ch.unibas.cs.dbis.cineast.core.util.ColorLayoutDescriptor;
import ch.unibas.cs.dbis.cineast.core.util.ImageHistogramEqualizer;

public class CLDNormalized extends AbstractFeatureModule {

	public CLDNormalized(){
		super("features.CLDNormalized", "cld", 1960f / 4f);
	}
	
	@Override
	public void processShot(FrameContainer shot) {
		if(!phandler.check("SELECT * FROM features.CLDNormalized WHERE shotid = " + shot.getId())){
			FloatVector fv = ColorLayoutDescriptor.calculateCLD(ImageHistogramEqualizer.getEqualized(shot.getMostRepresentativeFrame().getImage()));
			addToDB(shot.getId(), fv);
		}
	}

	@Override
	public List<LongDoublePair> getSimilar(FrameContainer qc) {
		FloatVector query = ColorLayoutDescriptor.calculateCLD(ImageHistogramEqualizer.getEqualized(qc.getMostRepresentativeFrame().getImage()));
		int limit = Config.getRetrieverConfig().getMaxResultsPerModule();
		
		ResultSet rset = this.selector.select("SELECT * FROM features.CLDNormalized USING DISTANCE MINKOWSKI(1)(\'" + query.toFeatureString() + "\', cld) ORDER USING DISTANCE LIMIT " + limit);
		return manageResultSet(rset);
	}

	@Override
	public List<LongDoublePair> getSimilar(FrameContainer qc, String resultCacheName) {
		FloatVector query = ColorLayoutDescriptor.calculateCLD(ImageHistogramEqualizer.getEqualized(qc.getMostRepresentativeFrame().getImage()));
		int limit = Config.getRetrieverConfig().getMaxResultsPerModule();
		
		ResultSet rset = this.selector.select(getResultCacheLimitSQL(resultCacheName) + " SELECT * FROM features.CLDNormalized, c WHERE shotid = c.filter USING DISTANCE MINKOWSKI(1)(\'" + query.toFeatureString() + "\', cld) ORDER USING DISTANCE LIMIT " + limit);
		return manageResultSet(rset);
	}

}
