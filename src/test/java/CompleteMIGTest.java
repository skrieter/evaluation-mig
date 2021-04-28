import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.spldev.evaluation.mig.IncrementalMIGBuilder;
import org.spldev.evaluation.mig.RegularMIGBuilder;
import org.spldev.evaluation.mig.BuildStatistic;
import org.spldev.evaluation.mig.XmlCNFFormat;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.CNFFormatManager;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.io.DIMACSFormat;
import org.spldev.formula.clause.mig.MIG;
import org.spldev.formula.clause.mig.Vertex;
import org.spldev.formula.clause.solver.Sat4JSolver;
import org.spldev.formula.clause.solver.SatSolver;
import org.spldev.formula.clause.solver.SatSolver.SatResult;
import org.spldev.util.io.FileHandler;
import org.spldev.util.job.DefaultMonitor;
import org.spldev.util.job.Executor;
import org.spldev.util.job.UpdateThread;
import org.spldev.util.logging.Logger;

public class CompleteMIGTest {

//	@Test
	public void test() {
		CNFFormatManager.getInstance().addExtension(new XmlCNFFormat());
		CNFFormatManager.getInstance().addExtension(new DIMACSFormat());
		Path root = Paths.get("/home/sebas/Documents/Coding/spldev/evaluation-mig/models");
		Path financialServices = root.resolve("FinancialServices01");
		Path busybox = root.resolve("Busybox");
		Path busyboxM = root.resolve("Busybox_monthlySnapshot");
		Path automotive = root.resolve("Automotive02");

//		Path model1 = financialServices.resolve("2017-05-22_obfuscated_model_2wVKAsCKmjQD51mx6wEnGD3cicO5VXpf.xml");
//		Path model2 = financialServices.resolve("2017-09-28_obfuscated_model_2wVKAsCKmjQD51mx6wEnGD3cicO5VXpf.xml");
		Path model1 = financialServices.resolve("2017-09-28_obfuscated_model_2wVKAsCKmjQD51mx6wEnGD3cicO5VXpf.xml");
		Path model2 = financialServices.resolve("2017-10-20_obfuscated_model_2wVKAsCKmjQD51mx6wEnGD3cicO5VXpf.xml");
//		Path model1 = busybox.resolve("2007-05-20_17-12-43/model.xml");
//		Path model2 = busybox.resolve("2007-05-23_00-32-25/model.xml");
//		Path model1 = busyboxM.resolve("2007-05-20_17-12-43/model.xml");
//		Path model2 = busyboxM.resolve("2007-06-01_14-40-03/model.xml");
//		Path model = busybox.resolve("2007-05-20_17-12-43/model.xml");
//		Path model = automotive.resolve("Automotive02_V1/model.xml");

		CNF cnf1 = FileHandler.parse(model1, CNFFormatManager.getInstance()).orElse(Logger::logProblems);
		CNF cnf2 = FileHandler.parse(model2, CNFFormatManager.getInstance()).orElse(Logger::logProblems);

		RegularMIGBuilder.statistic = new BuildStatistic();
		DefaultMonitor monitor = new DefaultMonitor();
		UpdateThread monitorLogger = Logger.startMonitorLogger(monitor);
		MIG mig1 = Executor.run(new RegularMIGBuilder(), cnf1, monitor).orElse(Logger::logProblems);
		monitorLogger.finish();

		BuildStatistic statistic = new BuildStatistic();

		RegularMIGBuilder.statistic = statistic;
		monitor = new DefaultMonitor();
		monitorLogger = Logger.startMonitorLogger(monitor);
		MIG mig2 = Executor.run(new RegularMIGBuilder(), cnf2, monitor).orElse(Logger::logProblems);
		monitorLogger.finish();

		Logger.logInfo(("Init:    " + (statistic.time[BuildStatistic.timeFinishRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Core:    " + (statistic.time[BuildStatistic.timeCoreRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Clean:   " + (statistic.time[BuildStatistic.timeCleanRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Add1:    " + (statistic.time[BuildStatistic.timeFirstAddRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Strong1: " + (statistic.time[BuildStatistic.timeFirstStrongBfsRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Weak:    " + (statistic.time[BuildStatistic.timeWeakBfsRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Add2:    " + (statistic.time[BuildStatistic.timeSecondAddRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Strong2: " + (statistic.time[BuildStatistic.timeSecondStrongBfsRegular] / 1_000_000) / 1000.0));
		Logger.logInfo(("Finish:  " + (statistic.time[BuildStatistic.timeFinishRegular] / 1_000_000) / 1000.0));

		long sum = statistic.time[BuildStatistic.timeFinishRegular] //
				+ statistic.time[BuildStatistic.timeCoreRegular] //
				+ statistic.time[BuildStatistic.timeCleanRegular] //
				+ statistic.time[BuildStatistic.timeFirstAddRegular] //
				+ statistic.time[BuildStatistic.timeFirstStrongBfsRegular] //
				+ statistic.time[BuildStatistic.timeWeakBfsRegular] //
				+ statistic.time[BuildStatistic.timeSecondAddRegular] //
				+ statistic.time[BuildStatistic.timeSecondStrongBfsRegular] //
				+ statistic.time[BuildStatistic.timeFinishRegular];

		Logger.logInfo("------");
		Logger.logInfo("Sum:     " + ((sum / 1_000_000) / 1000.0));

		IncrementalMIGBuilder.statistic = statistic;
		monitor = new DefaultMonitor();
		monitorLogger = Logger.startMonitorLogger(monitor);
		MIG mig3 = Executor.run(new IncrementalMIGBuilder(mig1), cnf2, monitor).orElse(Logger::logProblems);
		monitorLogger.finish();

		Logger.logInfo(("Init:    " + (statistic.time[BuildStatistic.timeFinishIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Core:    " + (statistic.time[BuildStatistic.timeCoreIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Clean:   " + (statistic.time[BuildStatistic.timeCleanIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Add1:    " + (statistic.time[BuildStatistic.timeFirstAddIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Strong1: " + (statistic.time[BuildStatistic.timeFirstStrongBfsIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Weak:    " + (statistic.time[BuildStatistic.timeWeakBfsIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Add2:    " + (statistic.time[BuildStatistic.timeSecondAddIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Strong2: " + (statistic.time[BuildStatistic.timeSecondStrongBfsIncremental] / 1_000_000) / 1000.0));
		Logger.logInfo(("Finish:  " + (statistic.time[BuildStatistic.timeFinishIncremental] / 1_000_000) / 1000.0));

		sum = statistic.time[BuildStatistic.timeFinishIncremental] //
				+ statistic.time[BuildStatistic.timeCoreIncremental] //
				+ statistic.time[BuildStatistic.timeCleanIncremental] //
				+ statistic.time[BuildStatistic.timeFirstAddIncremental] //
				+ statistic.time[BuildStatistic.timeFirstStrongBfsIncremental] //
				+ statistic.time[BuildStatistic.timeWeakBfsIncremental] //
				+ statistic.time[BuildStatistic.timeSecondAddIncremental] //
				+ statistic.time[BuildStatistic.timeSecondStrongBfsIncremental] //
				+ statistic.time[BuildStatistic.timeFinishIncremental];

		Logger.logInfo("------");
		Logger.logInfo("Sum:     " + ((sum / 1_000_000) / 1000.0));
		
		Logger.logInfo("------");
		final ListIterator<Vertex> it2 = mig2.getVertices().listIterator();
		final ListIterator<Vertex> it3 = mig3.getVertices().listIterator();
		
		while(it2.hasNext()) {
			final Vertex v2 = it2.next();
			final Vertex v3 = it3.next();
			if (v2.getVar() != v3.getVar()) {
				Logger.logDebug(v2.getVar() + " != " + v3.getVar());
			}
			if (v2.getStatus() != v3.getStatus()) {
				Logger.logDebug(v2.getVar() + ": " + v2.getStatus() + " != " + v3.getStatus());
			}
			if (!Objects.equals(v2.getStrongEdges(), v3.getStrongEdges())) {
				Logger.logDebug(v2.getVar() + ": Different Strong Edges!");
				Logger.logDebug("\t" + v2.getStrongEdges());
				Logger.logDebug("\t" + v3.getStrongEdges());
			}
		}

		Random random = new Random(1);
		SatSolver solver = new Sat4JSolver(cnf2);
		solver.rememberSolutionHistory(0);

		HashSet<Integer> coreSet = new HashSet<>();
		for (Vertex vertex : mig2.getVertices()) {
			if (vertex.isCore()) {
				coreSet.add(vertex.getVar());
//				Logger.logDebug(vertex.getVar());
			}
		}

		for (Vertex vertex : mig2.getVertices()) {
//			Logger.logDebug(vertex.getVar());
			solver.assignmentClear(0);
			switch (vertex.getStatus()) {
			case Core:
				solver.assignmentPush(-vertex.getVar());
				assertEquals(null, solver.findSolution());
//				Logger.logDebug("\tCore");
				break;
			case Dead:
				solver.assignmentPush(vertex.getVar());
				assertEquals(null, solver.findSolution());
//				Logger.logDebug("\tDead");
				break;
			case Normal:
				solver.assignmentPush(vertex.getVar());
				final int[] firstSolution = solver.findSolution();
				assertNotEquals(null, solver.findSolution());

				solver.setSelectionStrategy(firstSolution, true, true);
				LiteralList.resetConflicts(firstSolution, solver.findSolution());

				// find core/dead features
				for (int i = 0; i < firstSolution.length; i++) {
					final int varX = firstSolution[i];
					if (varX != 0) {
						solver.assignmentPush(-varX);
						final SatResult hasSolution = solver.hasSolution();
						switch (hasSolution) {
						case FALSE:
							solver.assignmentReplaceLast(varX);
							break;
						case TIMEOUT:
							solver.assignmentPop();
							break;
						case TRUE:
							solver.assignmentPop();
							LiteralList.resetConflicts(firstSolution, solver.getSolution());
							solver.shuffleOrder(random);
							break;
						}
					}
				}

				final List<Vertex> strongEdges = vertex.getStrongEdges();
				HashSet<Integer> strongSet = new HashSet<>();
				HashSet<Integer> impliesSet = new HashSet<>();
				for (Vertex strongVertex : strongEdges) {
					strongSet.add(strongVertex.getVar());
				}
				for (int implies : solver.getAssignmentArray()) {
					impliesSet.add(implies);
				}
				impliesSet.remove(vertex.getVar());
				impliesSet.removeAll(coreSet);
				if (!Objects.equals(impliesSet, strongSet)) {
					Logger.logDebug(vertex.getVar());
					Logger.logDebug("\tStrong:  " + strongSet.stream().sorted().collect(Collectors.toList()));
					Logger.logDebug("\tImplies: " + impliesSet.stream().sorted().collect(Collectors.toList()));
				}
				assertEquals(impliesSet, strongSet);
				break;
			default:
				fail();
			}

		}

	}
}
