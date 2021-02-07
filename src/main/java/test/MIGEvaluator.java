package test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.spldev.evaluation.Evaluator;
import org.spldev.evaluation.properties.ListProperty;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.CNFFormatManager;
import org.spldev.formula.clause.io.DIMACSFormat;
import org.spldev.formula.clause.mig.MIG;
import org.spldev.util.io.FileHandler;
import org.spldev.util.io.csv.CSVWriter;
import org.spldev.util.job.Executor;
import org.spldev.util.logging.Logger;

public class MIGEvaluator extends Evaluator {

	protected static final ListProperty<Double> oldCoreThresholdProperty = new ListProperty<>("oldCoreThreshold",
			Double::parseDouble);
	protected static final ListProperty<Double> newCoreThresholdProperty = new ListProperty<>("newCoreThreshold",
			Double::parseDouble);
	protected static final ListProperty<Boolean> checkRedundancyProperty = new ListProperty<>("checkRedundancy",
			Boolean::parseBoolean, Boolean.FALSE);

	private static Path root = Paths.get("models");

	private CSVWriter csvWriter;
	private Statistic[][] statistics;
	private double oldCoreThreshold;
	private double newCoreThreshold;
	private boolean checkRedundancy;
	private int algorithmID;

	public MIGEvaluator(String configPath, String configName) throws Exception {
		super(configPath, configName);
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("Configuration path not specified!");
			return;
		}
		CNFFormatManager.getInstance().addExtension(new XmlCNFFormat());
		CNFFormatManager.getInstance().addExtension(new DIMACSFormat());

		final MIGEvaluator evaluator = new MIGEvaluator(args[0], "config");
		evaluator.init();
		evaluator.run();
		evaluator.dispose();
	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		csvWriter = addCSVWriter("statistics.csv", Arrays.asList( //
				"System", "Algorithm",
				"OldCoreThreshold", "NewCoreThreshold", "CheckRedundancy",
				"ID", //
				"Version1", "Version2", //
				"TimeNewInit", "TimeNewCore", "TimeNewAdd", "TimeNewBFS", "TimeNewFinish", //
				"TimeOldInit", "TimeOldCoreSat", "TimeOldCoreCheck", //
				"TimeOldCoreFind", "TimeOldAdd", "TimeOldBFS", //
				"TimeOldFinish", "VariablesAdded", "VariablesRemoved", "VariablesShared", //
				"ClausesAdded", "ClausesRemoved", "ClausesShared", //
				"CoreAdded", "CoreRemoved", "CoreShared", //
				"RedundantAdded", "RedundantRemoved", "RedundantShared"));
	}

	public void run() {
		super.run();

		tabFormatter.setTabLevel(0);
		if (config.systemIterations.getValue() > 0) {
			Logger.logInfo("Start");
			tabFormatter.setTabLevel(1);

			int systemIndexEnd = config.systemNames.size();

			for (systemID = 0; systemID < systemIndexEnd; systemID++) {
				final String systemName = config.systemNames.get(systemID);
				logSystem();
				tabFormatter.setTabLevel(2);
				for (systemIteration = 1; systemIteration <= config.systemIterations.getValue(); systemIteration++) {
					algorithmID = 0;
					for (Double oldCoreThresholdValue : oldCoreThresholdProperty.getValue()) {
						oldCoreThreshold = oldCoreThresholdValue;
						for (Double newCoreThresholdValue : newCoreThresholdProperty.getValue()) {
							newCoreThreshold = newCoreThresholdValue;
							for (Boolean checkRedundancyValue : checkRedundancyProperty.getValue()) {
								checkRedundancy = checkRedundancyValue;
								algorithmID++;
								tabFormatter.setTabLevel(3);
								logSettings();
								tabFormatter.setTabLevel(4);
								try {
									statistics = run(root.resolve(systemName));
									if (statistics != null) {
										writeStatistics();
									}
								} catch (IOException e) {
									Logger.logError(e);
								}
							}
						}
					}
				}
			}
			tabFormatter.setTabLevel(0);
			Logger.logInfo("Finished");
		} else {
			Logger.logInfo("Nothing to do");
		}
	}

	protected void logSettings() {
		StringBuilder sb = new StringBuilder();
		sb.append("Using Settings: OldCoreThreshold = ");
		sb.append(oldCoreThreshold);
		sb.append(", NewCoreThreshold = ");
		sb.append(newCoreThreshold);
		sb.append(", CheckRedundancy = ");
		sb.append(checkRedundancy);
		sb.append(" (");
		sb.append(algorithmID);
		sb.append("/");
		sb.append(oldCoreThresholdProperty.getValue().size() 
				* newCoreThresholdProperty.getValue().size() 
				* checkRedundancyProperty.getValue().size());
		sb.append(")");
		Logger.logInfo(sb.toString());
	}

	private Statistic[][] run(Path modelHistoryPath) throws IOException {
		if (checkRedundancy && modelHistoryPath.getFileName().toString().equals("Linux")) {
			return null;
		}

		List<Path> models = Files.walk(modelHistoryPath).filter(Files::isRegularFile)
				.sorted(Comparator.comparing(Path::toString)).limit(200).collect(Collectors.toList());

		IncrementalMIGBuilder.oldCoreThreshold = oldCoreThreshold;
		IncrementalMIGBuilder.newCoreThreshold = newCoreThreshold;

		Statistic[][] statistics = new Statistic[models.size()][models.size()];
		List<CNF> cnfList = new ArrayList<>(models.size());
		List<MIG> migList = new ArrayList<>(models.size());

		// JVM warm up
		{
			MIGBuilder.statistic = new Statistic();
			MIGBuilder migBuilder1 = new MIGBuilder();
			migBuilder1.setCheckRedundancy(checkRedundancy);
			final MIG oldMig = Executor
					.run(migBuilder1, FileHandler.parse(models.get(0), CNFFormatManager.getInstance()).get()).get();

			IncrementalMIGBuilder.statistic = new Statistic();
			IncrementalMIGBuilder migBuilder2 = new IncrementalMIGBuilder(oldMig);
			migBuilder2.setCheckRedundancy(checkRedundancy);
			Executor.run(migBuilder2, FileHandler.parse(models.get(1), CNFFormatManager.getInstance()).get());
		}

		int i = 0;
		for (Path path1 : models) {
			Logger.logInfo("Load " + (i + 1) + "/" + models.size() + ": " + root.relativize(path1).toString());
			System.gc();

			CNF cnf = FileHandler.parse(path1, CNFFormatManager.getInstance()).get();
			cnfList.add(cnf);

			Statistic statistic = new Statistic();
			MIGBuilder.statistic = statistic;

			MIGBuilder migBuilder = new MIGBuilder();
			migBuilder.setCheckRedundancy(checkRedundancy);
			MIG mig = Executor.run(migBuilder, cnf).get();
			migList.add(mig);
			for (int j = 0; j < statistics.length; j++) {
				statistics[j][i] = new Statistic(statistic);
			}
			i++;
		}

		final int total = models.size() * (models.size() - 1);
		int count = 0;
		i = 0;
		for (Path path1 : models) {
			int j = 0;
			MIG oldMig = migList.get(i);
			for (Path path2 : models) {
				if (path1 != path2) {
					Logger.logInfo("Build " + (++count) + "/" + total + ": " + root.relativize(path1).toString());
					System.gc();

					CNF cnf = cnfList.get(j);
					Statistic statistic = statistics[i][j];
					statistic.name1 = path1.getParent().getFileName().toString();
					statistic.name2 = path2.getParent().getFileName().toString();
					IncrementalMIGBuilder.statistic = statistic;

					IncrementalMIGBuilder migBuilder = new IncrementalMIGBuilder(oldMig);
					migBuilder.setCheckRedundancy(checkRedundancy);
					Executor.run(migBuilder, cnf);
				}
				j++;
			}
			i++;
		}
		return statistics;
	}

	private void writeStatistics() {
		final String modelName = config.systemNames.get(systemID);
		final String algorithmName = "Incremental_" + oldCoreThreshold + "_" + newCoreThreshold + "_" + checkRedundancy;
		int id = 0;
		for (int j = 0; j < statistics.length; j++) {
			Statistic[] statistics2 = statistics[j];
			for (int k = 0; k < statistics2.length; k++) {
				Statistic statistic = statistics2[k];
				if (j != k) {
					final int curId = id++;
					writeCSV(csvWriter, w -> writeStatistic(w, statistic, modelName, algorithmName, curId));
				}
			}
		}
	}

	private void writeStatistic(CSVWriter csvWriter, Statistic statistic, String modelName, String algorithmName, int id) {
		csvWriter.addValue(modelName);
		csvWriter.addValue(algorithmName);
		csvWriter.addValue(oldCoreThreshold);
		csvWriter.addValue(newCoreThreshold);
		csvWriter.addValue(checkRedundancy);
		csvWriter.addValue(id);
		csvWriter.addValue(statistic.name1);
		csvWriter.addValue(statistic.name2);
		csvWriter.addValue(statistic.timeInitNew);
		csvWriter.addValue(statistic.timeCoreDeadNew);
		csvWriter.addValue(statistic.timeAddNew);
		csvWriter.addValue(statistic.timeBfsNew);
		csvWriter.addValue(statistic.timeFinishNew);
		csvWriter.addValue(statistic.timeInitIt);
		csvWriter.addValue(statistic.timeSatCoreDeadIt);
		csvWriter.addValue(statistic.timeOldCoreDeadIt);
		csvWriter.addValue(statistic.timeNewCoreDeadIt);
		csvWriter.addValue(statistic.timeAddIt);
		csvWriter.addValue(statistic.timeBfsIt);
		csvWriter.addValue(statistic.timeFinishIt);
		csvWriter.addValue(statistic.addedVar);
		csvWriter.addValue(statistic.removedVar);
		csvWriter.addValue(statistic.sharedVar);
		csvWriter.addValue(statistic.addedClauses);
		csvWriter.addValue(statistic.removedClauses);
		csvWriter.addValue(statistic.sharedClauses);
		csvWriter.addValue(statistic.addedCore);
		csvWriter.addValue(statistic.removedCore);
		csvWriter.addValue(statistic.sharedCore);
		csvWriter.addValue(statistic.addedRedundant);
		csvWriter.addValue(statistic.removedRedundant);
		csvWriter.addValue(statistic.sharedRedundant);
	}

}
