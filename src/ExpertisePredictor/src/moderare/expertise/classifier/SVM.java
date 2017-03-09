package moderare.expertise.classifier;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.supportVector.PolyKernel;
import weka.core.SelectedTag;

public class SVM extends Classifier {

	@Override
	protected AbstractClassifier construct() {
		PolyKernel kernel = new PolyKernel();
		kernel.setExponent(1);
		
		SMO svm = new SMO();
		svm.setDebug(false);
		svm.setC(1.0);
		svm.setFilterType(new SelectedTag(SMO.FILTER_NORMALIZE, SMO.TAGS_FILTER));
		svm.setToleranceParameter(0.001);
		svm.setEpsilon(1.0e-12);
		svm.setBuildCalibrationModels(false);
		svm.setNumFolds(-1);
		svm.setRandomSeed(1);
		svm.setKernel(kernel);
		
		return svm;
	}
}
