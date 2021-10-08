/* -----------------------------------------------------------------------------
 * Evaluation-MIG - Program for the evaluation of building incremental MIGs.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-MIG.
 * 
 * Evaluation-MIG is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-MIG is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-MIG.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-mig> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.mig;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.spldev.evaluation.*;
import org.spldev.evaluation.properties.*;
import org.spldev.formula.analysis.mig.*;
import org.spldev.formula.analysis.sat4j.*;
import org.spldev.formula.clauses.*;
import org.spldev.formula.expression.io.*;
import org.spldev.formula.solver.mig.*;
import org.spldev.util.io.*;
import org.spldev.util.io.csv.*;
import org.spldev.util.job.*;
import org.spldev.util.logging.*;

public class MIGEvaluator extends Evaluator {

	protected static final ListProperty<String> settingsProperty = new ListProperty<>("settings",
		Property.StringConverter);
	protected static final Property<Integer> randomConfigsProperty = new Property<>("random_configs",
		Property.IntegerConverter);
	protected static final Property<Integer> randomConfigSplitsProperty = new Property<>("random_config_splits",
		Property.IntegerConverter);
	protected static final Property<Integer> randomLiteralsProperty = new Property<>("random_literals",
		Property.IntegerConverter, 100);

	private static Path root = Paths.get("models");

	private CSVWriter csvWriter, csvWriter2, modelCSVWriter, algorithmCSVWriter, versionCSVWriter;
	private String settings;
	private int randomConfigsValue;
	private int randomConfigSplitsValue;
	private int randomLiteralsValue;
	private boolean checkRedundancy;
	private boolean detectStrong;
	private boolean detectAnomalies;
	private boolean accumulative;
	private int algorithmID;
	private List<Path> modelPaths;
	private List<CNF> cnfs;

	@Override
	public String getName() {
		return "eval-mig-builder";
	}

	@Override
	public void evaluate() {
		randomConfigsValue = randomConfigsProperty.getValue();
		randomConfigSplitsValue = randomConfigSplitsProperty.getValue();
		randomLiteralsValue = randomLiteralsProperty.getValue();

		algorithmID = 0;
		for (final String settingsValue : settingsProperty.getValue()) {
			settings = settingsValue;
			checkRedundancy = "T".equals(settings.split("_")[0]);
			detectStrong = "T".equals(settings.split("_")[1]);
			detectAnomalies = "T".equals(settings.split("_")[2]);
			accumulative = true;
			algorithmID++;
			writeCSV(algorithmCSVWriter, this::writeAlgorithmStatistic);
			accumulative = false;
			algorithmID++;
			writeCSV(algorithmCSVWriter, this::writeAlgorithmStatistic);
		}

		tabFormatter.setTabLevel(0);
		if (config.systemIterations.getValue() > 0) {
			Logger.logInfo("Start");
			tabFormatter.setTabLevel(1);

			final int systemIndexEnd = config.systemNames.size();

			for (systemIndex = 0; systemIndex < systemIndexEnd; systemIndex++) {
				final String systemName = config.systemNames.get(systemIndex);
				logSystem();
				try {
					final Path modelHistoryPath = root.resolve(systemName);
					loadModel(modelHistoryPath);
					writeCSV(modelCSVWriter, this::writeModelStatistic);
					for (int i = 0; i < modelPaths.size(); i++) {
						final int versionID = i + 1;
						final String versionName = modelHistoryPath.relativize(modelPaths.get(i)).toString();
						writeCSV(versionCSVWriter, w -> writeVersionStatistic(w, versionID, versionName));
					}
					tabFormatter.setTabLevel(2);
					for (systemIteration = 1; systemIteration <= config.systemIterations
						.getValue(); systemIteration++) {
						algorithmID = 0;
						logSystemRun();
						for (final String settingsValue : settingsProperty.getValue()) {
							settings = settingsValue;
							checkRedundancy = "T".equals(settings.split("_")[0]);
							detectStrong = "T".equals(settings.split("_")[1]);
							detectAnomalies = "T".equals(settings.split("_")[2]);
							algorithmID++;
							tabFormatter.setTabLevel(3);
							logSettings();
							tabFormatter.setTabLevel(4);
							build();
						}
					}
				} catch (final IOException e) {
					Logger.logError(e);
				}
			}
			tabFormatter.setTabLevel(0);
			Logger.logInfo("Finished");
		} else {
			Logger.logInfo("Nothing to do");
		}
	}

	protected void logSystemRun() {
		final StringBuilder sb = new StringBuilder();
		sb.append("System Iteration: ");
		sb.append(systemIteration);
		sb.append("/");
		sb.append(config.systemIterations.getValue());
		Logger.logInfo(sb.toString());
	}

	protected void logSettings() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Using Settings: ");
		sb.append(settings);
		sb.append(" (");
		sb.append((algorithmID + 1) / 2);
		sb.append("/");
		sb.append(settingsProperty.getValue().size());
		sb.append(")");
		Logger.logInfo(sb.toString());
	}

	private void build() {
//		BuildStatistic[][] crossStatistics = new BuildStatistic[modelPaths.size()][modelPaths.size()];
		final BuildStatistic[] accStatistics = new BuildStatistic[modelPaths.size() - 1];
		final BuildStatistic[] seqStatistics = new BuildStatistic[modelPaths.size() - 1];
		final BuildStatistic[] conStatistics = new BuildStatistic[modelPaths.size() - 1];
		for (int i = 0; i < (modelPaths.size() - 1); i++) {
			accStatistics[i] = new BuildStatistic();
			seqStatistics[i] = new BuildStatistic();
			conStatistics[i] = new BuildStatistic();
//			BuildStatistic[] statistics2 = crossStatistics[i];
//			for (int j = i + 1; j < statistics2.length; j++) {
//				statistics2[j] = new BuildStatistic();
//			}
		}

		{
			// JVM warm up
			RegularMIGBuilder.statistic = new BuildStatistic();
			final MIG regMig = computeRegMig(cnfs.get(0));

			// JVM warm up
			IncrementalMIGBuilder.statistic = new BuildStatistic();
			computeIncMig(cnfs.get(1), regMig);

			accumulative = true;
			MIG lastMig = regMig;
			for (int i = 1; i < modelPaths.size(); i++) {
				final Path path2 = modelPaths.get(i);
				final CNF cnf2 = cnfs.get(i);
				Logger.logInfo("Build accumulative " + (i + 1) + "/" + modelPaths.size() + ": "
					+ root.relativize(path2).toString());

				final BuildStatistic statistic = accStatistics[i - 1];
				IncrementalMIGBuilder.statistic = statistic;

				lastMig = computeIncMig(cnf2, lastMig);
				useMig(lastMig, cnf2, i, i + 1);
			}
		}
		algorithmID++;
		accumulative = false;

//		final int total = (modelPaths.size() * (modelPaths.size() - 1)) / 2;
//		int count = 0;
//		for (int i = 0; i < modelPaths.size(); i++) {
//			final Path path1 = modelPaths.get(i);
//			final CNF cnf1 = cnfs.get(i);
//
//			Logger.logInfo("Build MIG " + (i + 1) + "/" + modelPaths.size() + ": " + root.relativize(path1).toString());
//
//			BuildStatistic curBuildStatistic = new BuildStatistic();
//			RegularMIGBuilder.statistic = curBuildStatistic;
//
//			final MIG regMig = computeRegMig(cnf1);
//			useMig(regMig, cnf1, 0, i + 1);
//
//			for (int j = 0; j < i; j++) {
//				final BuildStatistic statistic = crossStatistics[j][i];
//				transferStatisticData(curBuildStatistic, statistic);
//			}
//			transferStatisticData(curBuildStatistic, accStatistics[i]);
//
//			for (int j = i + 1; j < modelPaths.size(); j++) {
//				final CNF cnf2 = cnfs.get(j);
//				Logger.logInfo("Build " + (++count) + "/" + total);
//
//				BuildStatistic statistic = crossStatistics[i][j];
//				IncrementalMIGBuilder.statistic = statistic;
//
//				final MIG incMig = computeIncMig(cnf2, regMig);
//				useMig(incMig, cnf2, i + 1, j + 1);
//			}
//		}
		{
			final Path path1 = modelPaths.get(0);
			final CNF cnf1 = cnfs.get(0);

			Logger.logInfo("Build Reg MIG 1/" + modelPaths.size() + ": " + root.relativize(path1).toString());

//			BuildStatistic curBuildStatistic = new BuildStatistic();
//			RegularMIGBuilder.statistic = curBuildStatistic;
			RegularMIGBuilder.statistic = new BuildStatistic();

			final MIG regMig = computeRegMig(cnf1);

//			transferStatisticData(curBuildStatistic, accStatistics[0]);
//			transferStatisticData(curBuildStatistic, seqStatistics[0]);
//			transferStatisticData(curBuildStatistic, conStatistics[0]);

			for (int i = 1; i < modelPaths.size(); i++) {
				final CNF cnf2 = cnfs.get(i);
				Logger.logInfo("Build Inc MIG " + i + "/" + modelPaths.size());

				final BuildStatistic statistic = seqStatistics[i - 1];
				IncrementalMIGBuilder.statistic = statistic;

				final MIG incMig = computeIncMig(cnf2, regMig);
				useMig(incMig, cnf2, 1, i + 1);
			}
		}
		for (int i = 1; i < modelPaths.size(); i++) {
			final Path path1 = modelPaths.get(i);
			final CNF cnf1 = cnfs.get(i);

			Logger.logInfo(
				"Build Reg MIG " + (i + 1) + "/" + modelPaths.size() + ": " + root.relativize(path1).toString());

			final BuildStatistic curBuildStatistic = new BuildStatistic();
			RegularMIGBuilder.statistic = curBuildStatistic;

			final MIG regMig = computeRegMig(cnf1);
			useMig(regMig, cnf1, 0, i + 1);
//			useNoMig(cnf1, -1, i + 1);

			transferStatisticData(curBuildStatistic, accStatistics[i - 1]);
			transferStatisticData(curBuildStatistic, seqStatistics[i - 1]);
			transferStatisticData(curBuildStatistic, conStatistics[i - 1]);

			if ((i + 1) < modelPaths.size()) {
				final CNF cnf2 = cnfs.get(i + 1);
				Logger.logInfo("Build Inc MIG " + (i + 2) + "/" + modelPaths.size());

				final BuildStatistic statistic = conStatistics[i];
				IncrementalMIGBuilder.statistic = statistic;

				final MIG incMig = computeIncMig(cnf2, regMig);
				useMig(incMig, cnf2, i + 1, i + 2);
			}
		}

		algorithmID--;
		for (int i = 0; i < accStatistics.length; i++) {
			final BuildStatistic statistic = accStatistics[i];
			final int versionID1 = 1;
			final int versionID2 = i + 2;
			writeCSV(csvWriter, w -> writeStatistic(w, statistic, versionID1, versionID2));
		}
		algorithmID++;
		for (int i = 0; i < seqStatistics.length; i++) {
			final BuildStatistic statistic = seqStatistics[i];
			final int versionID1 = 1;
			final int versionID2 = i + 2;
			writeCSV(csvWriter, w -> writeStatistic(w, statistic, versionID1, versionID2));
		}
		for (int i = 1; i < conStatistics.length; i++) {
			final BuildStatistic statistic = conStatistics[i];
			final int versionID1 = i + 1;
			final int versionID2 = i + 2;
			writeCSV(csvWriter, w -> writeStatistic(w, statistic, versionID1, versionID2));
		}
//		for (int i = 0; i < crossStatistics.length; i++) {
//			final BuildStatistic[] statistics2 = crossStatistics[i];
//			for (int j = i + 1; j < statistics2.length; j++) {
//				final BuildStatistic statistic = statistics2[j];
//				final int versionID1 = i + 1;
//				final int versionID2 = j + 1;
//				writeCSV(csvWriter, w -> writeStatistic(w, statistic, versionID1, versionID2));
//			}
//		}
	}

	private void loadModel(Path modelHistoryPath) throws IOException {
		modelPaths = new ArrayList<>();
		cnfs = new ArrayList<>();
		Files.walk(modelHistoryPath).filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString))
			.peek(p -> Logger.logInfo("Load " + root.relativize(p).toString())).peek(modelPaths::add)
			.map(p -> FileHandler.load(p, FormulaFormatManager.getInstance()).map(Clauses::convertToCNF)
				.orElse(Logger::logProblems))
			.filter(cnf2 -> {
				if (cnf2 == null) {
					modelPaths.remove(modelPaths.size() - 1);
					return false;
				}
				return true;
			}).peek(cnfs::add).filter(cnf2 -> {
				if (cnfs.size() > 1) {
					final CNF cnf1 = cnfs.get(cnfs.size() - 2);
					if (IncrementalMIGBuilder.getChangeRatio(cnf1, cnf2) == 0) {
						cnfs.remove(cnfs.size() - 1);
						modelPaths.remove(modelPaths.size() - 1);
						return false;
					}
				}
				return true;
			}).count();
	}

	private MIG computeRegMig(final CNF cnf) {
		System.gc();
		final RegularMIGBuilder migBuilder = new RegularMIGBuilder();
		migBuilder.setCheckRedundancy(checkRedundancy);
		migBuilder.setDetectStrong(detectStrong);
		return Executor.run(migBuilder, cnf).orElse(Logger::logProblems);
	}

	private MIG computeIncMig(final CNF cnf, MIG oldMig) {
		System.gc();
		final IncrementalMIGBuilder migBuilder = new IncrementalMIGBuilder(oldMig);
		migBuilder.setCheckRedundancy(checkRedundancy);
		migBuilder.setDetectStrong(detectStrong);
		migBuilder.setAdd(detectAnomalies);
		return Executor.run(migBuilder, cnf).orElse(Logger::logProblems);
	}

	private void transferStatisticData(BuildStatistic curBuildStatistic, final BuildStatistic statistic) {
		statistic.time[BuildStatistic.timeInitRegular] = curBuildStatistic.time[BuildStatistic.timeInitRegular];
		statistic.time[BuildStatistic.timeCoreRegular] = curBuildStatistic.time[BuildStatistic.timeCoreRegular];
		statistic.time[BuildStatistic.timeCleanRegular] = curBuildStatistic.time[BuildStatistic.timeCleanRegular];
		statistic.time[BuildStatistic.timeFirstAddRegular] = curBuildStatistic.time[BuildStatistic.timeFirstAddRegular];
		statistic.time[BuildStatistic.timeFirstStrongBfsRegular] = curBuildStatistic.time[BuildStatistic.timeFirstStrongBfsRegular];
		statistic.time[BuildStatistic.timeWeakBfsRegular] = curBuildStatistic.time[BuildStatistic.timeWeakBfsRegular];
		statistic.time[BuildStatistic.timeSecondAddRegular] = curBuildStatistic.time[BuildStatistic.timeSecondAddRegular];
		statistic.time[BuildStatistic.timeSecondStrongBfsRegular] = curBuildStatistic.time[BuildStatistic.timeSecondStrongBfsRegular];
		statistic.time[BuildStatistic.timeFinishRegular] = curBuildStatistic.time[BuildStatistic.timeFinishRegular];
		statistic.data[BuildStatistic.redundantRegular] = curBuildStatistic.data[BuildStatistic.redundantRegular];
		statistic.data[BuildStatistic.strongRegular] = curBuildStatistic.data[BuildStatistic.strongRegular];
		statistic.data[BuildStatistic.weakRegular] = curBuildStatistic.data[BuildStatistic.weakRegular];
		statistic.data[BuildStatistic.redundantRegular] = curBuildStatistic.data[BuildStatistic.redundantRegular];
		statistic.data[BuildStatistic.strongRegular] = curBuildStatistic.data[BuildStatistic.strongRegular];
		statistic.data[BuildStatistic.weakRegular] = curBuildStatistic.data[BuildStatistic.weakRegular];
		statistic.data[BuildStatistic.coreRegular] = curBuildStatistic.data[BuildStatistic.coreRegular];
	}

	private void useMig(MIG mig, final CNF cnf, int versionID1, int versionID2) {
		useMig1(mig, cnf, versionID1, versionID2);
	}

	private void useMig1(MIG mig, final CNF cnf, int versionID1, int versionID2) {
		int breakCount = 0;
		for (final Vertex vertex : mig.getVertices()) {
			if (vertex.isNormal()) {
				final ConditionallyCoreDeadAnalysisMIG incAnalysis = new ConditionallyCoreDeadAnalysisMIG();
				incAnalysis.setSolver(new Sat4JMIGSolver(mig));
				incAnalysis.setFixedFeatures(new int[] { vertex.getVar() }, 1);
				Executor.run(incAnalysis::execute);
				if (++breakCount == 10) {
					break;
				}
			}
		}
		long start, end;
		final List<Integer> indexList = getIndexList(cnf);
		for (final Integer literal : indexList) {
			if (mig.getVertex(literal).isNormal()) {
				System.gc();
				start = System.nanoTime();
				final ConditionallyCoreDeadAnalysisMIG incAnalysis = new ConditionallyCoreDeadAnalysisMIG();
				incAnalysis.setSolver(new Sat4JMIGSolver(mig));
				incAnalysis.setFixedFeatures(new int[] { literal }, 1);
				Executor.run(incAnalysis::execute);
				end = System.nanoTime();
				final long diff = end - start;
				writeCSV(csvWriter2, w -> writeUsageStatistic(w, 0, literal, diff, versionID1, versionID2));
			}
		}
	}

	private void useNoMig(final CNF cnf, int versionID1, int versionID2) {
		int breakCount = 0;
		for (int i = 2; i <= cnf.getVariableMap().size(); i++) {
			final CoreDeadAnalysis incAnalysis1 = new CoreDeadAnalysis();
			incAnalysis1.getAssumptions().set(i, true);
			Executor.run(incAnalysis1::execute, cnf);
			if (++breakCount == 10) {
				break;
			}
		}
		long start, end;
		final List<Integer> indexList = getIndexList(cnf);
		for (final Integer literal : indexList) {
			System.gc();
			start = System.nanoTime();
			final CoreDeadAnalysis incAnalysis = new CoreDeadAnalysis();
			incAnalysis.getAssumptions().set(Math.abs(literal), literal > 0);
			Executor.run(incAnalysis::execute, cnf);
			end = System.nanoTime();
			final long diff = end - start;
			writeCSV(csvWriter2, w -> writeUsageStatistic(w, 0, literal, diff, versionID1, versionID2));
		}
	}

	private List<Integer> getIndexList(final CNF cnf) {
		final List<Integer> indexList = new ArrayList<>();
		IntStream.range(1, cnf.getVariables().size() + 1).forEach(l -> {
			indexList.add(-l);
			indexList.add(l);
		});
		Collections.shuffle(indexList, new Random(0));
		return indexList.subList(0, Math.min(indexList.size(), randomLiteralsValue));
	}

//	private void useMig2(MIG mig, final CNF cnf, int versionID1, int versionID2) {
//		RandomConfigurationGenerator gen = new RandomConfigurationGenerator();
//		gen.setRandom(new Random(1));
//		gen.setLimit(randomConfigsValue);
//		final List<LiteralList> randomConfigs = Executor.run(gen, cnf).orElse(Logger::logProblems);
//		if (randomConfigs != null) {
//			for (Vertex vertex : mig.getVertices()) {
//				if (vertex.isNormal()) {
//					ConditionallyCoreDeadAnalysisMIG incAnalysis = new ConditionallyCoreDeadAnalysisMIG(mig);
//					incAnalysis.setFixedFeatures(new int[] { vertex.getVar() }, 1);
//					Executor.run(incAnalysis, cnf);
//					break;
//				}
//			}
//			final Random seedRandom = new Random(1);
//			int countIndex = 0;
//			for (LiteralList config : randomConfigs) {
//				final Random random = new Random(seedRandom.nextLong());
//				final int[] literals = config.getLiterals();
//				for (int i = literals.length - 1; i >= 0; i--) {
//					final int randomIndex = random.nextInt(literals.length);
//					final int temp = literals[i];
//					literals[i] = literals[randomIndex];
//					literals[randomIndex] = temp;
//				}
//				final int numLiterals = literals.length / randomConfigSplitsValue;
//				System.gc();
//				final long start = System.nanoTime();
//				if (numLiterals > 0) {
//					int startIndex = 0;
//					for (int i = 0; i < randomConfigSplitsValue; i++) {
//						final int[] fixedFeatures = Arrays.copyOfRange(literals, startIndex, startIndex + numLiterals);
//						startIndex += numLiterals;
//						ConditionallyCoreDeadAnalysisMIG incAnalysis = new ConditionallyCoreDeadAnalysisMIG(mig);
//						incAnalysis.setFixedFeatures(fixedFeatures, fixedFeatures.length);
//						Executor.run(incAnalysis, cnf);
////						final int index = countIndex++;
//					}
//				} else {
//					for (int i = 0; i < literals.length; i++) {
//						System.gc();
//						ConditionallyCoreDeadAnalysisMIG incAnalysis = new ConditionallyCoreDeadAnalysisMIG(mig);
//						incAnalysis.setFixedFeatures(new int[] { literals[i] }, 1);
////						final long start = System.nanoTime();
//						Executor.run(incAnalysis, cnf);
////						final long end = System.nanoTime();
////						final int index = countIndex++;
////						writeCSV(csvWriter2, w -> writeUsageStatistic(w, index, end - start, versionID1, versionID2));
//					}
//				}
//				final long end = System.nanoTime();
//				final int index = countIndex++;
//				writeCSV(csvWriter2, w -> writeUsageStatistic(w, index, end - start, versionID1, versionID2));
//			}
//		}
//	}

	@Override
	protected void addCSVWriters() {
		super.addCSVWriters();
		csvWriter = addCSVWriter("statistics.csv", Arrays.asList( //
			"SystemID", "SystemIteration", "AlgorithmID", //
			"VersionID1", "VersionID2", //
			"timeInitRegular", "timeCoreRegular", "timeCleanRegular", //
			"timeFirstAddRegular", "timeFirstStrongBfsRegular", "timeWeakBfsRegular", //
			"timeSecondAddRegular", "timeSecondStrongBfsRegular", "timeFinishRegular", //
			"timeInitIncremental", "timeCoreIncremental", "timeCleanIncremental", //
			"timeFirstAddIncremental", "timeFirstStrongBfsIncremental", "timeWeakBfsIncremental", //
			"timeSecondAddIncremental", "timeSecondStrongBfsIncremental", "timeFinishIncremental", //
			"VariablesAdded", "VariablesRemoved", "VariablesShared", //
			"ClausesAdded", "ClausesRemoved", "ClausesShared", //
			"RedundantRegular", "StrongRegular", "WeakRegular", //
			"RedundantIncremental", "StrongIncremental", "WeakIncremental", //
			"coreIncremental", "coreRegular" //
		));
		csvWriter2 = addCSVWriter("usage.csv", Arrays.asList( //
			"SystemID", "SystemIteration", "AlgorithmID", //
			"VersionID1", "VersionID2", //
			"Index", "Literal", "Time"));
		modelCSVWriter = addCSVWriter("models.csv", Arrays.asList( //
			"SystemID", "System"));
		versionCSVWriter = addCSVWriter("versions.csv", Arrays.asList( //
			"SystemID", "VersionID", "VersionName"));
		algorithmCSVWriter = addCSVWriter("algorithms.csv", Arrays.asList( //
			"AlgorithmID", "Algorithm", //
			"CheckRedundancy", "DetectStrong", "Anomalies", "Accumulative"));
	}

	private void writeModelStatistic(CSVWriter csvWriter) {
		csvWriter.addValue(config.systemIDs.get(systemIndex));
		csvWriter.addValue(config.systemNames.get(systemIndex));
	}

	private void writeAlgorithmStatistic(CSVWriter csvWriter) {
		csvWriter.addValue(algorithmID);
		csvWriter.addValue(//
			(checkRedundancy ? "T" : "F") + "_" //
				+ (detectStrong ? "T" : "F") + "_" //
				+ (detectAnomalies ? "T" : "F") + "_" //
				+ (accumulative ? "T" : "F" //
				));
		csvWriter.addValue(checkRedundancy);
		csvWriter.addValue(detectStrong);
		csvWriter.addValue(detectAnomalies);
		csvWriter.addValue(accumulative);
	}

	private void writeVersionStatistic(CSVWriter csvWriter, int versionID, String name) {
		csvWriter.addValue(config.systemIDs.get(systemIndex));
		csvWriter.addValue(versionID);
		csvWriter.addValue(name);
	}

	private void writeUsageStatistic(CSVWriter csvWriter, int index, int literal, long time, int versionID1,
		int versionID2) {
		csvWriter.addValue(config.systemIDs.get(systemIndex));
		csvWriter.addValue(systemIteration);
		csvWriter.addValue(algorithmID);
		csvWriter.addValue(versionID1);
		csvWriter.addValue(versionID2);
		csvWriter.addValue(index);
		csvWriter.addValue(literal);
		csvWriter.addValue(time);
	}

	private void writeStatistic(CSVWriter csvWriter, BuildStatistic statistic, int versionID1, int versionID2) {
		csvWriter.addValue(config.systemIDs.get(systemIndex));
		csvWriter.addValue(systemIteration);
		csvWriter.addValue(algorithmID);
		csvWriter.addValue(versionID1);
		csvWriter.addValue(versionID2);
		for (final long time : statistic.time) {
			csvWriter.addValue(time);
		}
		for (final long data : statistic.data) {
			csvWriter.addValue(data);
		}
	}

}
