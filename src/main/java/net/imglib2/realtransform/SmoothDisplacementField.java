package net.imglib2.realtransform;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.DfieldIoHelper;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.FieldOfView;

@Command(version = "0.2.0-SNAPSHOT")
public class SmoothDisplacementField implements Callable<Void> {

    @Option(
        names = { "-i", "--input" },
        required = true,
        description = "Input displacement field."
    )
    private String input;

    @Option(
        names = { "-s", "--sigma" },
        required = false,
        description = "Smoothing parameter."
    )
    private double sigma = 1.0;

    @Option(
        names = { "-o", "--output" },
        required = true,
        description = "Output file for transformed image"
    )
    private String outputFile;

    @Option(
        names = { "-q", "--num-threads" },
        required = false,
        description = "Number of threads."
    )
    private int nThreads = 1;

    private RandomAccessibleInterval<FloatType> displacementField;

    private FieldOfView fov;

    final Logger logger = LoggerFactory.getLogger(
        SmoothDisplacementField.class
    );

    public static void main(String[] args) {
        new CommandLine(new SmoothDisplacementField()).execute(args);
    }

    public void setup() throws Exception {
        // use size / resolution if the only input transform is a dfield
        // ( and size / resolution is not given )
        Optional<ValuePair<long[], double[]>> sizeAndRes = Optional.empty();
        final String transformFile = input;
        if (
            transformFile.contains(".nrrd") ||
            transformFile.contains(".nii") ||
            transformFile.contains(".h5") ||
            transformFile.contains(".hdf5") ||
            transformFile.contains(".n5") ||
            transformFile.contains(".zarr")
        ) {
            try {
                sizeAndRes = Optional.of(
                    TransformReader.transformSpatialSizeAndRes(transformFile)
                );

                // TODO deal with offset
                fov = FieldOfView.fromSpacingSize(
                    sizeAndRes.get().getB(),
                    new FinalInterval(sizeAndRes.get().getA())
                );

                fov.updatePixelToPhysicalTransform();
            } catch (final Exception e) {}
        } else {
            System.err.println(
                "Can not infer resolution from this displacement field format"
            );
            return;
        }

        displacementField = new DfieldIoHelper().readAsRai(
            input,
            new FloatType()
        );
    }

    @Override
    public Void call() throws Exception {
        setup();
        process(displacementField);
        return null;
    }

    public <T extends RealType<T> & NativeType<T>> void process( RandomAccessibleInterval<T> dfield) throws Exception {

		final RandomAccessibleInterval<T> dfPerm = DfieldIoHelper.vectorAxisPermute(dfield, 3, 3);
 
    	
    	logger.info("allocating");
        final ImagePlusImgFactory<T> factory = new ImagePlusImgFactory<>(
            dfield.getType()
        );
        final ImagePlusImg<T, ?> dfieldOut = factory.create(dfPerm);
        
        System.out.println("dfield   : " + Intervals.toString(dfPerm));
        System.out.println("dfieldOut: " + Intervals.toString(dfieldOut));

        logger.info("processing with " + nThreads + " threads.");
        final double[] sigs = new double[3];
        Arrays.fill(sigs, sigma);
        for (int d = 0; d < 3; d++) {
        	IntervalView<T> aa = Views.hyperSlice(dfPerm, 3, d);
        	IntervalView<T> bb = Views.hyperSlice(dfieldOut, 3, d);
            Gauss3.gauss(
                sigs,
                Views.extendBorder(aa),
                bb,
                nThreads
            );
        }

        logger.info("writing");
        final DfieldIoHelper dfieldIo = new DfieldIoHelper();

        dfieldIo.spacing = fov.getSpacing();
        if (dfieldIo.origin == null) dfieldIo.origin = new double[] { 0, 0, 0 };

        try {
            dfieldIo.write(dfieldOut, outputFile);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        logger.info("done");
    }

    public void setNumThreads(int nThreads) {
        this.nThreads = nThreads;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }
}
