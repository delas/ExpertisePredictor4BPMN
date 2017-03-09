package moderare.expertise.classifier;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import moderare.expertise.exceptions.WrongValueType;
import moderare.expertise.model.Dataset;
import moderare.expertise.model.EXPERTISE;
import moderare.expertise.model.ModelSample;
import moderare.expertise.model.ModelingSession;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.core.Instance;
import weka.core.SerializationHelper;

public abstract class Classifier {

	private AbstractClassifier classifier;
	
	public Classifier() {
		classifier = construct();
	}
	
	protected abstract AbstractClassifier construct();
	
	public void save(String file) throws Exception {
		SerializationHelper.write(file, classifier);
	}
	
	public void load(String file) throws Exception {
		classifier = (AbstractClassifier) SerializationHelper.read(file);
	}
	
	public void train(Dataset trainingDataset) {
		try {
			classifier.buildClassifier(trainingDataset.getWekaInstances());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public Evaluation evaluate(Dataset trainingDataset, Dataset testDataset) throws Exception {
		Evaluation eval = new Evaluation(trainingDataset.getWekaInstances());
		eval.evaluateModel(classifier, testDataset.getWekaInstances());
		return eval;
	}
	
	public String printEvaluation(Dataset trainingDataset, Dataset testDataset) throws Exception {
		Evaluation eval = evaluate(trainingDataset, testDataset);
		return eval.toSummaryString("Evaluation statistics:", false) + "\n" + eval.toMatrixString("Confusion matrix:");
	}
	
	public EXPERTISE classifyInstance(ModelSample sample) throws Exception {
		Instance i = sample.getWekaInstance();
		int classification = (int) classifier.classifyInstance(i);
		return EXPERTISE.fromString(EXPERTISE.names().get(classification));
	}
	
	public List<EXPERTISE> classifyInstance(Dataset session) {
		List<EXPERTISE> classifications = new ArrayList<EXPERTISE>();
		for(ModelSample sample : session) {
			EXPERTISE expertise = null;
			try {
				expertise = classifyInstance(sample);
			} catch (Exception e) {
				e.printStackTrace();
			}
			classifications.add(expertise);
		}
		return classifications;
	}
	
	public double computeAccuracy(ModelingSession session, int windowSize, double minSupport) {
		List<EXPERTISE> classifications = classifyInstance(session);
		double totalClassfication = 0.0;
		double correctClassification = 0.0;
		for (int i = 0; i < session.size(); i++) {
			ModelSample sample = session.get(i);
			int correctInWindow = 0;
			if (i > windowSize) {
				for (int j = i; j > i - windowSize; j--) {
					if (classifications.get(j) == sample.getSampleClass()) {
						correctInWindow++;
					}
				}
				if (((double) correctInWindow / (double) windowSize) >= minSupport) {
					correctClassification++;
				}
				totalClassfication++;
			}
		}
		return correctClassification / totalClassfication;
	}
	
	public void exportAccuracyChart(ModelingSession session, int[] windowSizes, String fileName) {
		List<EXPERTISE> classifications = classifyInstance(session);
		EXPERTISE expectedExpertise = null;
		
		XYSeriesCollection dataset = new XYSeriesCollection();
		for (int windowSize : windowSizes) {
			XYSeries series = new XYSeries("Window size " + windowSize, true);
			for (int i = 0; i < session.size(); i++) {
				ModelSample sample = session.get(i);
				if (expectedExpertise == null) {
					expectedExpertise = sample.getSampleClass();
				}
				if (i > windowSize) {
					int correctInWindow = 0;
					for (int j = i; j > i - windowSize; j--) {
						if (classifications.get(j) == expectedExpertise) {
							correctInWindow++;
						}
					}
					try {
						Double time = sample.getNumeric("relative_modeling_time");
						Double score = correctInWindow / (double) windowSize;
						series.add(time, score);
					} catch (WrongValueType e) {
						e.printStackTrace();
					}
				}
			}
			dataset.addSeries(series);
		}
		
		JFreeChart chart = ChartFactory.createScatterPlot(session.getModelId() + " (" + session.getSampleClass() + ", " + session.getTaskName() + ")",
				"Relative time (% modeling session)", // x axis label
				"Correctness ratio", // y axis label
				dataset, // data
				PlotOrientation.VERTICAL, //
				true, // include legend
				true, // tooltips
				false // urls
				);
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		for (int i = 0; i < windowSizes.length; i++) {
			renderer.setSeriesLinesVisible(i, true);
			renderer.setSeriesShapesVisible(i, false);
			renderer.setSeriesStroke(i, new BasicStroke(3f));
		}
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setRenderer(renderer);
		plot.getRangeAxis().setRange(0.0, 1.0);
		plot.getDomainAxis().setRange(0.0, 1.0);

		try {
			ChartUtilities.saveChartAsPNG(new File(fileName), chart, 800, 400);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void exportCorrectClassificationChart(ModelingSession session, int[] windowSizes, String fileName, double minSupport) {
		List<EXPERTISE> classifications = classifyInstance(session);
		EXPERTISE expectedExpertise = null;
		
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		
		List<Boolean> correctlyClassifiedInterval = new ArrayList<Boolean>();
		List<Integer> seriesToWindowSizes = new ArrayList<Integer>();
		for (int ws = 0; ws < windowSizes.length; ws++) {
			int windowSize = windowSizes[ws];
			Double previousTime = null;
			Boolean previousCorrectness = false;
			for (int i = 0; i < session.size(); i++) {
				ModelSample sample = session.get(i);
				if (expectedExpertise == null) {
					expectedExpertise = sample.getSampleClass();
				}
				if (i > windowSize) {
					int correctInWindow = 0;
					for (int j = i; j > i - windowSize; j--) {
						if (classifications.get(j) == expectedExpertise) {
							correctInWindow++;
						}
					}
					try {
						Double time = sample.getNumeric("relative_modeling_time");
						Boolean correct = false;
						if (((double) correctInWindow / (double) windowSize) >= minSupport) {
							correct = true;
						}
							if (previousCorrectness != correct || i == session.size() - 1) {
								if (i == session.size() - 1) {
									correct = !previousCorrectness;
								}
								System.out.println((previousTime == null)? time : time - previousTime + "\t" + correct);
								dataset.addValue(
									(previousTime == null)? time : time - previousTime,
									windowSize + "-" + i,
									"ws = " + windowSize);
								correctlyClassifiedInterval.add(!correct);
								seriesToWindowSizes.add(ws);
								previousTime = time;
								previousCorrectness = correct;
						}
					} catch (WrongValueType e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		StackedBarRenderer renderer = new StackedBarRenderer();
		renderer.setBarPainter(new StandardBarPainter());
		renderer.setShadowVisible(false);
		for (int i = 0; i < correctlyClassifiedInterval.size(); i++) {
			renderer.setSeriesVisibleInLegend(i, false);
			if (correctlyClassifiedInterval.get(i)) {
				renderer.setSeriesPaint(i, DefaultDrawingSupplier.DEFAULT_PAINT_SEQUENCE[seriesToWindowSizes.get(i) % DefaultDrawingSupplier.DEFAULT_PAINT_SEQUENCE.length]);
			} else {
				renderer.setSeriesPaint(i, new Color(0, 0, 255, 0));
			}
		}
		
		JFreeChart chart = ChartFactory.createStackedBarChart(
				session.getModelId() + " (" + session.getSampleClass() + ", " + session.getTaskName() + ", min support = " + minSupport + ")",
				null, // domain axis label
				"Relative time (% modeling session)", // x axis label
				dataset, // data
				PlotOrientation.HORIZONTAL, // the plot orientation
				true, // include legend
				true, // tooltips
				false // urls
				);
		
		CategoryPlot plot = (CategoryPlot) chart.getPlot();
		plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
		plot.setRenderer(renderer);
		plot.setDomainGridlinesVisible(true);
		
		plot.getRangeAxis().setRange(0.0, 1.0);
		
		try {
			ChartUtilities.saveChartAsPNG(new File(fileName), chart, 800, 400);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
