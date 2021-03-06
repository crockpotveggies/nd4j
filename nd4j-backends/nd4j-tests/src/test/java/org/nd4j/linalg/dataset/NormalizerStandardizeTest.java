package org.nd4j.linalg.dataset;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.TestDataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.ops.transforms.Transforms;

import static org.junit.Assert.*;


/**
 * Created by susaneraly on 5/25/16.
 */
@RunWith(Parameterized.class)
public class NormalizerStandardizeTest extends BaseNd4jTest {
    public NormalizerStandardizeTest(Nd4jBackend backend) {
        super(backend);
    }
    @Test
    public void testBruteForce() {
        /* This test creates a dataset where feature values are multiples of consecutive natural numbers
           The obtained values are compared to the theoretical mean and std dev
         */
        double tolerancePerc = 0.01; // 0.01% of correct value
        int nSamples = 5120;
        int x = 1,y = 2, z = 3;

        INDArray featureX = Nd4j.linspace(1,nSamples,nSamples).reshape(nSamples,1).mul(x);
        INDArray featureY = featureX.mul(y);
        INDArray featureZ = featureX.mul(z);
        INDArray featureSet = Nd4j.concat(1,featureX,featureY,featureZ);
        INDArray labelSet = Nd4j.zeros(nSamples, 1);
        DataSet sampleDataSet = new DataSet(featureSet, labelSet);

        double meanNaturalNums = (nSamples + 1)/2.0;
        INDArray theoreticalMean = Nd4j.create(new double[] {meanNaturalNums*x,meanNaturalNums*y,meanNaturalNums*z});
        double stdNaturalNums = Math.sqrt((nSamples*nSamples - 1)/12.0);
        INDArray theoreticalStd = Nd4j.create(new double[] {stdNaturalNums*x,stdNaturalNums*y,stdNaturalNums*z});

        NormalizerStandardize myNormalizer = new NormalizerStandardize();
        myNormalizer.fit(sampleDataSet);

        INDArray meanDelta = Transforms.abs(theoreticalMean.sub(myNormalizer.getMean()));
        INDArray meanDeltaPerc = meanDelta.div(theoreticalMean).mul(100);
        double maxMeanDeltaPerc = meanDeltaPerc.max(1).getDouble(0,0);
        assertTrue(maxMeanDeltaPerc < tolerancePerc);

        INDArray stdDelta = Transforms.abs(theoreticalMean.sub(myNormalizer.getMean()));
        INDArray stdDeltaPerc = stdDelta.div(theoreticalStd).mul(100);
        double maxStdDeltaPerc = stdDeltaPerc.max(1).getDouble(0,0);
        assertTrue(maxStdDeltaPerc < tolerancePerc);

        // SAME TEST WITH THE ITERATOR
        int bSize = 10;
        tolerancePerc = 1; // 1% of correct value
        DataSetIterator sampleIter = new TestDataSetIterator(sampleDataSet,bSize);
        myNormalizer.fit(sampleIter);

        meanDelta = Transforms.abs(theoreticalMean.sub(myNormalizer.getMean()));
        meanDeltaPerc = meanDelta.div(theoreticalMean).mul(100);
        maxMeanDeltaPerc = meanDeltaPerc.max(1).getDouble(0,0);
        assertTrue(maxMeanDeltaPerc < tolerancePerc);

        stdDelta = Transforms.abs(theoreticalMean.sub(myNormalizer.getMean()));
        stdDeltaPerc = stdDelta.div(theoreticalStd).mul(100);
        maxStdDeltaPerc = stdDeltaPerc.max(1).getDouble(0,0);
        assertTrue(maxStdDeltaPerc < tolerancePerc);
    }

    @Test
    public void testTransform() {
        /*Random dataset is generated such that
            AX + B where X is from a normal distribution with mean 0 and std 1
            The mean of above will be B and std A
            Obtained mean and std dev are compared to theoretical
            Transformed values should be the same as X with the same seed.
         */
        long randSeed = 7139183;

        int nFeatures = 2;
        int nSamples = 6400;
        int bsize = 8;
        int a = 2;
        int b = 10;
        INDArray sampleMean, sampleStd, sampleMeanDelta, sampleStdDelta, delta, deltaPerc;
        double maxDeltaPerc, sampleMeanSEM;

        genRandomDataSet normData = new genRandomDataSet(nSamples, nFeatures, a, b, randSeed);
        genRandomDataSet expectedData = new genRandomDataSet(nSamples,nFeatures,1,0, randSeed);
        genRandomDataSet beforeTransformData = new genRandomDataSet(nSamples,nFeatures,a,b, randSeed);

        NormalizerStandardize myNormalizer = new NormalizerStandardize();
        DataSetIterator normIterator = normData.getIter(bsize);
        DataSetIterator expectedIterator = expectedData.getIter(bsize);
        DataSetIterator beforeTransformIterator = beforeTransformData.getIter(bsize);

        myNormalizer.fit(normIterator);

        double tolerancePerc = 5.0; //within 5%
        sampleMean = myNormalizer.getMean();
        sampleMeanDelta = Transforms.abs(sampleMean.sub(normData.theoreticalMean));
        assertTrue(sampleMeanDelta.mul(100).div(normData.theoreticalMean).max(1).getDouble(0,0) < tolerancePerc);
        //sanity check to see if it's within the theoretical standard error of mean
        sampleMeanSEM = sampleMeanDelta.div(normData.theoreticalSEM).max(1).getDouble(0,0);
        assertTrue(sampleMeanSEM < 2.6 ); //99% of the time it should be within this many SEMs

        tolerancePerc = 10.0; //within 10%
        sampleStd = myNormalizer.getStd();
        sampleStdDelta = Transforms.abs(sampleStd.sub(normData.theoreticalStd));
        assertTrue(sampleStdDelta.div(normData.theoreticalStd).max(1).mul(100).getDouble(0,0) < tolerancePerc);

        normIterator.setPreProcessor(myNormalizer);
        while (normIterator.hasNext()) {
           INDArray before = beforeTransformIterator.next().getFeatures();
           INDArray after = normIterator.next().getFeatures();
           INDArray expected = expectedIterator.next().getFeatures();
           delta = Transforms.abs(after.sub(expected));
           deltaPerc = delta.div(before.sub(expected));
           deltaPerc.muli(100);
           maxDeltaPerc = deltaPerc.max(0,1).getDouble(0,0);
           //System.out.println("=== BEFORE ===");
           //System.out.println(before);
           //System.out.println("=== AFTER ===");
           //System.out.println(after);
           //System.out.println("=== SHOULD BE ===");
           //System.out.println(expected);
           assertTrue(maxDeltaPerc < tolerancePerc);
        }
    }

    @Test
    public void testUnderOverflow() {
        // This dataset will be basically constant with a small std deviation
        // And the constant is large. Checking if algorithm can handle
        double tolerancePerc = 1; //Within 1 %
        double toleranceAbs = 0.0005;
        int nSamples = 1000;
        int bSize = 10;
        int x = -1000000,y = 1000000;
        double z = 1000000;

        INDArray featureX = Nd4j.rand(nSamples,1).mul(1).add(x);
        INDArray featureY = Nd4j.rand(nSamples,1).mul(2).add(y);
        INDArray featureZ = Nd4j.rand(nSamples,1).mul(3).add(z);
        INDArray featureSet = Nd4j.concat(1,featureX,featureY,featureZ);
        INDArray labelSet = Nd4j.zeros(nSamples, 1);
        DataSet sampleDataSet = new DataSet(featureSet, labelSet);
        DataSetIterator sampleIter = new TestDataSetIterator(sampleDataSet,bSize);

        INDArray theoreticalMean = Nd4j.create(new double[] {x,y,z});

        NormalizerStandardize myNormalizer = new NormalizerStandardize();
        myNormalizer.fit(sampleIter);

        INDArray meanDelta = Transforms.abs(theoreticalMean.sub(myNormalizer.getMean()));
        INDArray meanDeltaPerc = meanDelta.mul(100).div(theoreticalMean);
        assertTrue( meanDeltaPerc.max(1).getDouble(0,0) < tolerancePerc);

        //this just has to not barf
        //myNormalizer.transform(sampleIter);
        myNormalizer.transform(sampleDataSet);
    }

    @Test
    public void testRevert() {
        double tolerancePerc = 0.01; // 0.01% of correct value
        int nSamples = 500;
        int nFeatures = 3;

        INDArray featureSet = Nd4j.randn(nSamples,nFeatures);
        INDArray labelSet = Nd4j.zeros(nSamples, 1);
        DataSet sampleDataSet = new DataSet(featureSet, labelSet);

        NormalizerStandardize myNormalizer = new NormalizerStandardize();
        myNormalizer.fit(sampleDataSet);
        DataSet transformed = sampleDataSet.copy();
        myNormalizer.transform(transformed);
        //System.out.println(transformed.getFeatures());
        myNormalizer.revert(transformed);
        //System.out.println(transformed.getFeatures());
        INDArray delta = Transforms.abs(transformed.getFeatures().sub(sampleDataSet.getFeatures())).div(sampleDataSet.getFeatures());
        double maxdeltaPerc = delta.max(0,1).mul(100).getDouble(0,0);
        assertTrue(maxdeltaPerc < tolerancePerc);

    }

    @Test
    public void testConstant() {
        double tolerancePerc = 10.0; // 10% of correct value
        int nSamples = 500;
        int nFeatures = 3;
        int constant = 100;

        INDArray featureSet = Nd4j.zeros(nSamples,nFeatures).add(constant);
        INDArray labelSet = Nd4j.zeros(nSamples, 1);
        DataSet sampleDataSet = new DataSet(featureSet, labelSet);


        NormalizerStandardize myNormalizer = new NormalizerStandardize();
        myNormalizer.fit(sampleDataSet);
        //Checking if we gets nans
        assertFalse (Double.isNaN(myNormalizer.getStd().getDouble(0)));

        myNormalizer.transform(sampleDataSet);
        //Checking if we gets nans, because std dev is zero
        assertFalse (Double.isNaN(sampleDataSet.getFeatures().min(0,1).getDouble(0)));
        //Checking to see if transformed values are close enough to zero
        assertEquals(Transforms.abs(sampleDataSet.getFeatures()).max(0,1).getDouble(0,0),0,constant*tolerancePerc/100.0);

        myNormalizer.revert(sampleDataSet);
        //Checking if we gets nans, because std dev is zero
        assertFalse (Double.isNaN(sampleDataSet.getFeatures().min(0,1).getDouble(0)));
        assertEquals(Transforms.abs(sampleDataSet.getFeatures().sub(featureSet)).min(0,1).getDouble(0),0,constant*tolerancePerc/100.0);
    }

    public class genRandomDataSet {
        /* generate random dataset from normally distributed mean 0, std 1
        based on given seed and scaling constants
         */
        DataSet sampleDataSet;
        INDArray theoreticalMean;
        INDArray theoreticalStd;
        INDArray theoreticalSEM;
        public genRandomDataSet(int nSamples, int nFeatures, int a, int b , long randSeed) {
            int i = 0;
            // Randomly generate scaling constants and add offsets
            // to get aA and bB
            INDArray aA = Nd4j.rand(1, nFeatures, randSeed).add(a);
            INDArray bB = Nd4j.rand(1, nFeatures, randSeed).add(b);
            // transform ndarray as X = aA + bB * X
            INDArray randomFeatures = Nd4j.zeros(nSamples, nFeatures);
            while (i < nFeatures) {
                INDArray randomSlice = Nd4j.randn(nSamples, 1, randSeed);
                randomSlice.muli(aA.getScalar(0, i));
                randomSlice.addi(bB.getScalar(0, i));
                randomFeatures.putColumn(i, randomSlice);
                i++;
            }
            INDArray randomLabels = Nd4j.zeros(nSamples, 1);
            this.sampleDataSet = new DataSet(randomFeatures, randomLabels);
            this.theoreticalMean = bB;
            this.theoreticalStd = aA;
            this.theoreticalSEM = this.theoreticalStd.div(Math.sqrt(nSamples));
        }
        public DataSetIterator getIter(int bsize) {
            return new TestDataSetIterator(sampleDataSet,bsize);
        }
    }
    @Override
    public char ordering() {
        return 'c';
    }
}
