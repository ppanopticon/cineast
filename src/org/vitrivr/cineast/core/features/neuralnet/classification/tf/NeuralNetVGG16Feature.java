package org.vitrivr.cineast.core.features.neuralnet.classification.tf;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Position;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.vitrivr.adam.grpc.AdamGrpc;
import org.vitrivr.cineast.core.config.Config;
import org.vitrivr.cineast.core.config.NeuralNetConfig;
import org.vitrivr.cineast.core.config.QueryConfig;
import org.vitrivr.cineast.core.data.FloatVectorImpl;
import org.vitrivr.cineast.core.data.SegmentContainer;
import org.vitrivr.cineast.core.data.StringDoublePair;
import org.vitrivr.cineast.core.data.providers.primitive.PrimitiveTypeProvider;
import org.vitrivr.cineast.core.db.*;
import org.vitrivr.cineast.core.features.neuralnet.NeuralNetFeature;
import org.vitrivr.cineast.core.features.neuralnet.classification.NeuralNet;
import org.vitrivr.cineast.core.features.neuralnet.classification.NeuralNetFactory;
import org.vitrivr.cineast.core.setup.EntityCreator;
import org.vitrivr.cineast.core.util.MaxPool;
import org.vitrivr.cineast.core.util.NeuralNetUtil;
import org.vitrivr.cineast.core.util.TimeHelper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * VGG16-Feature
 * <p>
 * Created by silvan on 09.09.16.
 */
public class NeuralNetVGG16Feature extends NeuralNetFeature {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String fullVectorTableName = "features_neuralnet_vgg16_fullvector";
    private static final String generatedLabelsTableName = "features_neuralnet_vgg16_classifiedlabels";

    private VGG16Net cachedNet = null;
    private VGG16Net net(){
        if(cachedNet == null){
            cachedNet = (VGG16Net) factory.get();
        }
        return cachedNet;
    }
    private NeuralNetFactory factory;
    private float cutoff = 0.2f;

    private DBSelector classificationSelector;
    private PersistencyWriter<?> classificationWriter;

    /**
     * Needs to be public so the extraction runner has access with a config-object
     */
    @SuppressWarnings("unused")
    public NeuralNetVGG16Feature(com.eclipsesource.json.JsonObject config) {
        super(fullVectorTableName);
        NeuralNetConfig parsedConfig = NeuralNetConfig.parse(config);
        this.cutoff = parsedConfig.getCutoff();
        this.factory = parsedConfig.getNeuralNetFactory();
    }

    /**
     * Also needs to be public since the retriever config needs access.
     * Passes the current NN-Config to the next constructor since we need a net to init and we get that net from the config
     */
    @SuppressWarnings("unused")
    public NeuralNetVGG16Feature() {
        this(Config.getNeuralNetConfig());
    }

    public NeuralNetVGG16Feature(NeuralNetConfig neuralNetConfig) {
        super(fullVectorTableName);
        this.cutoff = neuralNetConfig.getCutoff();
        this.factory = neuralNetConfig.getNeuralNetFactory();
    }

    /**
     * Does not call super.fillLabels() since we do not want that to happen for every NN-Feature
     */
    @Override
    public void fillLabels() {
        LOGGER.debug("filling labels");
        List<PersistentTuple> tuples = new ArrayList<>(1000);
        for (int i = 0; i < net().getSynSetLabels().length; i++) {
            String[] labels = net().getLabels(net().getSynSetLabels()[i]);
            for (String label : labels) {
                PersistentTuple tuple = getClassWriter().generateTuple(UUID.randomUUID().toString(), net().getSynSetLabels()[i], label);
                tuples.add(tuple);
            }
        }
        getClassWriter().persist(tuples);
    }

    @Override
    public String getClassificationTable() {
        return generatedLabelsTableName;
    }

    /**
     * Stores best classification hit if it's above 10%
     */
    @Override
    public void processShot(SegmentContainer shot) {
        LOGGER.entry();
        TimeHelper.tic();
        if (!phandler.idExists(shot.getId())) {
            BufferedImage keyframe = shot.getMostRepresentativeFrame().getImage().getBufferedImage();
            float[] probabilities = classifyImage(keyframe);
            int maxIdx = -1;
            float max = -1;
            for (int i = 0; i < probabilities.length; i++) {
                if(max<probabilities[i]){
                    maxIdx = i;
                    max = probabilities[i];
                }
            }
            LOGGER.info("Best Match for shot {}: {} with probability {}", shot.getId(), String.join(", ", net().getLabels(net().getSynSetLabels()[maxIdx])), probabilities[maxIdx]);
            if(probabilities[maxIdx]>0.1){
                LOGGER.info("Actually persisting result");
                String id = UUID.randomUUID().toString();
                PersistentTuple tuple = classificationWriter.generateTuple(id, shot.getId(), net().getSynSetLabels()[maxIdx], probabilities[maxIdx]);
                classificationWriter.persist(tuple);
            }

            persist(shot.getId(), new FloatVectorImpl(probabilities));
            LOGGER.trace("NeuralNetFeature.processShot() done in {}",
                    TimeHelper.toc());
        }
        LOGGER.exit();
    }

    /**
     * Checks if labels have been specified. If no labels have been specified, takes the queryimage.
     * Might perform knn on the 1k-vector in the future.
     * It's also not clear yet if we could combine labels and input image??
     */
    @Override
    public List<StringDoublePair> getSimilar(SegmentContainer sc, QueryConfig qc) {
        LOGGER.entry();
        TimeHelper.tic();
        List<StringDoublePair> _return = new ArrayList<>();
        if (!sc.getTags().isEmpty()) {
            Set<String> wnLabels = new HashSet<>();
            wnLabels.addAll(getClassSelector().getRows(getHumanLabelColName(), sc.getTags().toArray(new String[sc.getTags().size()])).stream().map(row -> row.get(getWnLabelColName()).getString()).collect(Collectors.toList()));

            LOGGER.debug("Looking for labels: {}", String.join(", ",wnLabels.toArray(new String[wnLabels.size()])));
            for (Map<String, PrimitiveTypeProvider> row :
                    classificationSelector.getRows(getWnLabelColName(), wnLabels.toArray(new String[wnLabels.size()]))) {
                LOGGER.debug("Found hit for query {}: {} {} ", row.get("segmentid").getString(), row.get("probability").getDouble(), row.get(getWnLabelColName()).toString());
                _return.add(new StringDoublePair(row.get("segmentid").getString(), row.get("probability").getDouble()));
            }
        } else {
            NeuralNet _net = null;
            if (qc.getNet().isPresent()) {
                _net = qc.getNet().get();
            }
            if (_net == null) {
                _net = net();
            }
            float[] res = _net.classify(sc.getMostRepresentativeFrame().getImage().getBufferedImage());
            List<String> hits = new ArrayList<>();
            for (int i = 0; i < res.length; i++) {
                if (res[i] > qc.getCutoff().orElse(cutoff)) {
                    hits.add(_net.getSynSetLabels()[i]);
                }
            }
            for (Map<String, PrimitiveTypeProvider> row : classificationSelector.getRows(getWnLabelColName(), hits.toArray(new String[hits.size()]))) {
                LOGGER.debug("Found hit for query {}: {} {} ", row.get("segmentid").getString(), row.get("probability").getDouble(), row.get(getWnLabelColName()).toString());
                _return.add(new StringDoublePair(row.get("segmentid").getString(), row.get("probability").getDouble()));
            }
        }
        _return = MaxPool.maxPoolStringId(_return);
        LOGGER.trace("NeuralNetFeature.getSimilar() done in {}",
                TimeHelper.toc());
        return LOGGER.exit(_return);
    }

    /**
     * Classifies an Image with the given neural net.
     * Performs 3 Classifications with different croppings, maxpools the vectors on each dimension to get hits
     */
    private float[] classifyImage(BufferedImage img) {
        float[] probs = new float[1000];
        Arrays.fill(probs, 0f);
        Position[] positions = new Position[3];
        positions[0] = Positions.CENTER;
        if (img.getHeight() > img.getWidth()) {
            positions[1] = Positions.TOP_RIGHT;
            positions[2] = Positions.BOTTOM_RIGHT;
        } else {
            positions[1] = Positions.CENTER_RIGHT;
            positions[2] = Positions.CENTER_LEFT;
        }

        float[] curr;
        for (Position pos : positions) {
            try {
                curr = net().classify(Thumbnails.of(img).size(224, 224).crop(pos).asBufferedImage());
                probs = NeuralNetUtil.maxpool(curr, probs);
            } catch (IOException e) {
                LOGGER.error(e);
            }
        }
        return probs;
    }

    @Override
    public void init(DBSelectorSupplier selectorSupplier) {
        super.init(selectorSupplier);
        this.classificationSelector = selectorSupplier.get();
        this.classificationSelector.open(generatedLabelsTableName);
    }

    @Override
    public void init(PersistencyWriterSupplier phandlerSupply) {
        super.init(phandlerSupply);
        classificationWriter = phandlerSupply.get();
        classificationWriter.open(generatedLabelsTableName);
        classificationWriter.setFieldNames("id", "segmentid", getWnLabelColName(), "probability");
    }

    @Override
    public void finish() {
        super.finish();
        if (this.classificationWriter != null) {
            this.classificationWriter.close();
            this.classificationWriter = null;
        }
        if (this.classificationSelector != null) {
            this.classificationSelector.close();
            this.classificationSelector = null;
        }
    }

    /**
     * Table 1: segmentid | wnLabel | confidence (ex. 4014 | n203843 | 0.4) - generated labels
     */
    @Override
    public void initalizePersistentLayer(Supplier<EntityCreator> supply) {
        super.initalizePersistentLayer(supply);
        EntityCreator ec = supply.get();
        //TODO Set pk / Create idx -> Logic in the ecCreator
        ec.createIdEntity(generatedLabelsTableName, new EntityCreator.AttributeDefinition("segmentid", AdamGrpc.AttributeType.STRING), new EntityCreator.AttributeDefinition(getWnLabelColName(), AdamGrpc.AttributeType.STRING), new EntityCreator.AttributeDefinition("probability", AdamGrpc.AttributeType.FLOAT));
        ec.close();
    }
}
