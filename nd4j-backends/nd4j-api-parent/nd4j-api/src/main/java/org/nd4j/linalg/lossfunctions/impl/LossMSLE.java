package org.nd4j.linalg.lossfunctions.impl;

import lombok.EqualsAndHashCode;
import org.apache.commons.math3.util.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.LossUtil;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Created by susaneraly on 8/15/16.
 */
@EqualsAndHashCode
public class LossMSLE implements ILossFunction {
    public INDArray scoreArray(INDArray labels, INDArray preOutput, String activationFn, INDArray mask) {
        INDArray scoreArr;
        INDArray output = Nd4j.getExecutioner().execAndReturn(Nd4j.getOpFactory().createTransform(activationFn, preOutput.dup()));
        scoreArr = Transforms.log(output.addi(1.0).divi(labels.add(1.0)), false);
        scoreArr = scoreArr.muli(scoreArr).divi(labels.size(1));
        if (mask != null) scoreArr.muliColumnVector(mask);
        return scoreArr;
    }

    @Override
    public double computeScore(INDArray labels, INDArray preOutput, String activationFn, INDArray mask, boolean average) {
        INDArray scoreArr = scoreArray(labels, preOutput, activationFn, mask);

        double score = scoreArr.sumNumber().doubleValue();

        if (average) score /= scoreArr.size(0);

        return score;
    }

    @Override
    public INDArray computeScoreArray(INDArray labels, INDArray preOutput, String activationFn, INDArray mask) {
        INDArray scoreArr = scoreArray(labels, preOutput, activationFn, mask);
        return scoreArr.sum(1);
    }

    @Override
    public INDArray computeGradient(INDArray labels, INDArray preOutput, String activationFn, INDArray mask) {
        INDArray output = Nd4j.getExecutioner().execAndReturn(Nd4j.getOpFactory().createTransform(activationFn, preOutput.dup()));

        INDArray gradients;
        if ("softmax".equals(activationFn)) {
            INDArray p1 = output.add(1.0);
            INDArray dlda = p1.rdiv(2.0 / labels.size(1));
            INDArray logRatio = Transforms.log(p1.divi(labels.add(1.0)), false);
            dlda.muli(logRatio);
            gradients = LossUtil.dLdZsoftmaxi(dlda, output);
        } else {
            INDArray p1 = output.addi(1.0);
            INDArray sigmaPrimeZ = Nd4j.getExecutioner().execAndReturn(Nd4j.getOpFactory().createTransform(activationFn, preOutput.dup()).derivative());
            gradients = sigmaPrimeZ.divi(p1).muli(2.0 / labels.size(1));
            INDArray logRatio = Transforms.log(p1.divi(labels.add(1.0)), false);
            gradients.muli(logRatio);
        }

        if (mask != null) {
            gradients.muliColumnVector(mask);
        }

        return gradients;
    }

    @Override
    public org.apache.commons.math3.util.Pair<Double, INDArray> computeGradientAndScore(INDArray labels, INDArray preOutput, String activationFn, INDArray mask, boolean average) {
        //TODO: probably a more efficient way to do this...
        //Yes - will implement in round two. Just want to get done now.

        return new Pair<>(
                computeScore(labels, preOutput, activationFn, mask, average),
                computeGradient(labels, preOutput, activationFn, mask));
    }

    @Override
    public String toString() {
        return "LossMSLE()";
    }

}
