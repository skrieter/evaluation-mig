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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.spldev.formula.clauses.*;
import org.spldev.formula.expression.io.*;
import org.spldev.formula.solver.SatSolver.*;
import org.spldev.formula.solver.mig.*;
import org.spldev.formula.solver.sat4j.*;
import org.spldev.util.extension.*;
import org.spldev.util.io.*;
import org.spldev.util.job.*;
import org.spldev.util.logging.*;

public class CompleteMIGTest {
	static {
		ExtensionLoader.load();
	}

//	@Test
	public void test() {
		final Path root = Paths.get("models");
		final Path financialServices = root.resolve("FinancialServices01");
		final Path model1 = financialServices
			.resolve("2017-09-28_obfuscated_model_2wVKAsCKmjQD51mx6wEnGD3cicO5VXpf.xml");
		final Path model2 = financialServices
			.resolve("2017-10-20_obfuscated_model_2wVKAsCKmjQD51mx6wEnGD3cicO5VXpf.xml");
		final CNF cnf1 = FileHandler.load(model1, FormulaFormatManager.getInstance()).map(Clauses::convertToCNF)
			.orElse(Logger::logProblems);
		final CNF cnf2 = FileHandler.load(model2, FormulaFormatManager.getInstance()).map(Clauses::convertToCNF)
			.orElse(Logger::logProblems);

		RegularMIGBuilder.statistic = new BuildStatistic();
		DefaultMonitor monitor = new DefaultMonitor();
		UpdateThread monitorLogger = Logger.startMonitorLogger(monitor);
		final MIG mig1 = Executor.run(new RegularMIGBuilder(), cnf1, monitor).orElse(Logger::logProblems);
		monitorLogger.finish();

		final BuildStatistic statistic = new BuildStatistic();

		RegularMIGBuilder.statistic = statistic;
		monitor = new DefaultMonitor();
		monitorLogger = Logger.startMonitorLogger(monitor);
		final MIG mig2 = Executor.run(new RegularMIGBuilder(), cnf2, monitor).orElse(Logger::logProblems);
		monitorLogger.finish();

		Logger.logInfo(("Init:    " + ((statistic.time[BuildStatistic.timeFinishRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Core:    " + ((statistic.time[BuildStatistic.timeCoreRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Clean:   " + ((statistic.time[BuildStatistic.timeCleanRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Add1:    " + ((statistic.time[BuildStatistic.timeFirstAddRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(
			("Strong1: " + ((statistic.time[BuildStatistic.timeFirstStrongBfsRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Weak:    " + ((statistic.time[BuildStatistic.timeWeakBfsRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Add2:    " + ((statistic.time[BuildStatistic.timeSecondAddRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(
			("Strong2: " + ((statistic.time[BuildStatistic.timeSecondStrongBfsRegular] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Finish:  " + ((statistic.time[BuildStatistic.timeFinishRegular] / 1_000_000) / 1000.0)));

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
		final MIG mig3 = Executor.run(new IncrementalMIGBuilder(mig1), cnf2, monitor).orElse(Logger::logProblems);
		monitorLogger.finish();

		Logger.logInfo(("Init:    " + ((statistic.time[BuildStatistic.timeFinishIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Core:    " + ((statistic.time[BuildStatistic.timeCoreIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Clean:   " + ((statistic.time[BuildStatistic.timeCleanIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Add1:    " + ((statistic.time[BuildStatistic.timeFirstAddIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(
			("Strong1: " + ((statistic.time[BuildStatistic.timeFirstStrongBfsIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Weak:    " + ((statistic.time[BuildStatistic.timeWeakBfsIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(
			("Add2:    " + ((statistic.time[BuildStatistic.timeSecondAddIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(
			("Strong2: " + ((statistic.time[BuildStatistic.timeSecondStrongBfsIncremental] / 1_000_000) / 1000.0)));
		Logger.logInfo(("Finish:  " + ((statistic.time[BuildStatistic.timeFinishIncremental] / 1_000_000) / 1000.0)));

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

		while (it2.hasNext()) {
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

		final Random random = new Random(1);
		final Sat4JSolver solver = new Sat4JSolver(cnf2);
		solver.rememberSolutionHistory(0);

		final HashSet<Integer> coreSet = new HashSet<>();
		for (final Vertex vertex : mig2.getVertices()) {
			if (vertex.isCore()) {
				coreSet.add(vertex.getVar());
//				Logger.logDebug(vertex.getVar());
			}
		}

		for (final Vertex vertex : mig2.getVertices()) {
//			Logger.logDebug(vertex.getVar());
			solver.getAssumptions().clear(0);
			switch (vertex.getStatus()) {
			case Core:
				solver.getAssumptions().push(-vertex.getVar());
				assertEquals(null, solver.findSolution());
//				Logger.logDebug("\tCore");
				break;
			case Dead:
				solver.getAssumptions().push(vertex.getVar());
				assertEquals(null, solver.findSolution());
//				Logger.logDebug("\tDead");
				break;
			case Normal:
				solver.getAssumptions().push(vertex.getVar());
				final LiteralList solution = solver.findSolution();
				assertNotNull(solution);
				final int[] firstSolution = solution.getLiterals();

				solver.setSelectionStrategy(SStrategy.inverse(firstSolution));
				solver.hasSolution();
				LiteralList.resetConflicts(firstSolution, solver.getInternalSolution());

				// find core/dead features
				for (int i = 0; i < firstSolution.length; i++) {
					final int varX = firstSolution[i];
					if (varX != 0) {
						solver.getAssumptions().push(-varX);
						final SatResult hasSolution = solver.hasSolution();
						switch (hasSolution) {
						case FALSE:
							solver.getAssumptions().replaceLast(varX);
							break;
						case TIMEOUT:
							solver.getAssumptions().pop();
							break;
						case TRUE:
							solver.getAssumptions().pop();
							LiteralList.resetConflicts(firstSolution, solver.getInternalSolution());
							solver.shuffleOrder(random);
							break;
						}
					}
				}

				final List<Vertex> strongEdges = vertex.getStrongEdges();
				final HashSet<Integer> strongSet = new HashSet<>();
				final HashSet<Integer> impliesSet = new HashSet<>();
				for (final Vertex strongVertex : strongEdges) {
					strongSet.add(strongVertex.getVar());
				}
				for (final int implies : solver.getAssumptions().asArray()) {
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
