package org.vitrivr.cineast.core.segmenter.audio;

import org.vitrivr.cineast.core.data.SegmentContainer;
import org.vitrivr.cineast.core.data.audio.AudioFrame;
import org.vitrivr.cineast.core.data.segments.AudioSegment;
import org.vitrivr.cineast.core.decode.general.Decoder;
import org.vitrivr.cineast.core.segmenter.general.Segmenter;

import java.util.ArrayDeque;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Merges multiple AudioFrames into a single AudioSegment using a constant number of frames per AudioSegment. The length
 * of an AudioSegment in frames AND the overlap between two subsequent AudioSegments can be defined upon onstruction of the
 * ConstantLengthAudioSegmenter.
 *
 * @see AudioSegment
 * @see AudioFrame
 *
 * @author rgasser
 * @version 1.0
 * @created 31.01.17
 */
public class ConstantLengthAudioSegmenter implements Segmenter<AudioFrame> {


    private static final int SEGMENT_QUEUE_LENGTH = 10;
    private static final int SEGMENT_POLLING_TIMEOUT = 1000;

    /** Decoder<AudioFrame> used for file decoding. */
    private Decoder<AudioFrame> decoder;

    /** A LinkedBlockingQueue used that holds the resulting AudioSegments. */
    private final LinkedBlockingQueue<SegmentContainer> outputQueue = new LinkedBlockingQueue<SegmentContainer>(SEGMENT_QUEUE_LENGTH);

    /** ArrayDeque containing AudioFrames waiting for segmentation. */
    private final ArrayDeque<AudioFrame> enqueuedFrames;

    /** The length in AudioFrames of a resulting AudioSegment. */
    private final int length;

    /** The number of AudioFrames that two subsequent SegmentContainer's have in common. */
    private final int overlap;

    /** A flag indicating whether or not the segmenter has completed its work. */
    private AtomicBoolean complete = new AtomicBoolean(false);

    /**
     * Default constructor.
     *
     * @param length
     * @param overlap
     */
    public ConstantLengthAudioSegmenter(int length, int overlap) {
        this.length = length;
        this.overlap = overlap;
        this.enqueuedFrames = new ArrayDeque<>(length);
    }

    /**
     * Method used to initialize the Segmenter. A class implementing the Decoder interface
     * with the same type must be provided.
     *
     * @param decoder Decoder used for audio-decoding.
     */
    @Override
    public void init(Decoder<AudioFrame> decoder) {
        this.decoder = decoder;
        this.complete.set(false);
    }

    /**
     * Returns the next SegmentContainer from the source OR null if there are no more segments in the outputQueue. As
     * generation of SegmentContainers can take some time (depending on the media-type), a null return does not
     * necessarily mean that the Segmenter is done segmenting. Use the complete() method to check this.
     *
     * Important: This method should be designed to block and wait for an appropriate amount of time if the Segmenter
     * is not yet ready to deliver another segment! It's up to the Segmenter how long that timeout should last.
     *
     * @return
     */
    @Override
    public SegmentContainer getNext() throws InterruptedException {
       SegmentContainer segment = this.outputQueue.poll(SEGMENT_POLLING_TIMEOUT, TimeUnit.MILLISECONDS);
       if (segment == null) {
           this.complete.set(this.decoder.complete());
       }
       return segment;
    }

    /**
     * Indicates whether the Segmenter is complete i.e. no new segments are to be expected.
     *
     * @return true if work is complete, false otherwise.
     */
    @Override
    public boolean complete() {
        return this.complete.get();
    }

    /**
     * Closes the Segmenter. This method should cleanup and relinquish all resources. Especially,
     * calling this method should also close the Decoder associated with this Segmenter instance.
     * <p>
     * Note: It is unsafe to re-use a Segmenter after it has been closed.
     */
    @Override
    public void close() {
        if (this.complete.get()) {
            this.decoder.close();
        }
    }

    /**
     * This methods pulls AudioFrames as they become available from the Decoder and adds them
     * the the Deque of AudioFrames. Once that queue holds enough AudioFrames it is drained and
     * a AudioSegment is emmited.
     *
     * The overlap defines how many frames re-enter the queue once they have been added to the
     * new Segment.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        while (!this.decoder.complete()) {
            AudioFrame newFrame = this.decoder.getNext();
            if (newFrame != null) {
                this.enqueuedFrames.offer(newFrame);
                if (this.enqueuedFrames.size() == this.length) {
                    this.drainQueue(false);
                }
            } else if (this.decoder.complete()) {
                this.drainQueue(true);
            }
        }
    }


    /**
     * Drains the Deque and emits a new AudioSegment.
     */
    private void drainQueue(boolean last) {
        AudioSegment segment = new AudioSegment();
        for (int i = 1; i <= this.length; i++) {
            AudioFrame frame = this.enqueuedFrames.poll();
            segment.addFrame(frame);
            if (i >= (this.length - this.overlap) && !last) this.enqueuedFrames.offer(frame);
        }
        try {
            this.outputQueue.put(segment);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
