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

import org.spldev.formula.clauses.*;
import org.spldev.formula.solver.*;
import org.spldev.formula.solver.mig.*;
import org.spldev.formula.solver.mig.MIG.*;
import org.spldev.util.job.*;

/**
 * Adjacency matrix implementation for a feature graph.
 *
 * @author Sebastian Krieter
 */

public class RegularMIGBuilder extends MIGBuilder implements MonitorableFunction<CNF, MIG> {

	public static BuildStatistic statistic;
	private long start, end;

	@Override
	public MIG execute(CNF cnf, InternalMonitor monitor) throws Exception {
		monitor.setTotalWork(24 + (detectStrong ? 1020 : 0) + (checkRedundancy ? 100 : 10));

		start = System.nanoTime();
		init(cnf);
		monitor.step();
		end = System.nanoTime();
		statistic.time[BuildStatistic.timeInitRegular] = end - start;

		start = System.nanoTime();
		if (!satCheck(cnf)) {
			throw new RuntimeContradictionException("CNF is not satisfiable!");
		}
		monitor.step();
		findCoreFeatures(monitor.subTask(10));
		end = System.nanoTime();
		statistic.time[BuildStatistic.timeCoreRegular] = end - start;

		start = System.nanoTime();
		cleanClauses();
		monitor.step();
		end = System.nanoTime();
		statistic.time[BuildStatistic.timeCleanRegular] = end - start;

		if (detectStrong) {
			start = System.nanoTime();
			addClauses(cnf, false, monitor.subTask(10));
			end = System.nanoTime();
			statistic.time[BuildStatistic.timeFirstAddRegular] = end - start;

			start = System.nanoTime();
			bfsStrong(monitor.subTask(10));
			end = System.nanoTime();
			statistic.time[BuildStatistic.timeFirstStrongBfsRegular] = end - start;

			start = System.nanoTime();
			bfsWeak(null, monitor.subTask(1000));
			end = System.nanoTime();
			statistic.time[BuildStatistic.timeWeakBfsRegular] = end - start;
			mig.setStrongStatus(BuildStatus.Complete);
		} else {
			mig.setStrongStatus(BuildStatus.None);
		}

		start = System.nanoTime();
		final long added = addClauses(cnf, checkRedundancy, monitor.subTask(checkRedundancy ? 100 : 10));
		end = System.nanoTime();
		statistic.time[BuildStatistic.timeSecondAddRegular] = end - start;
		statistic.data[BuildStatistic.redundantRegular] = (int) (cleanedClausesList.size() - added);

		start = System.nanoTime();
		bfsStrong(monitor.subTask(10));
		end = System.nanoTime();
		statistic.time[BuildStatistic.timeSecondStrongBfsRegular] = end - start;

		start = System.nanoTime();
		finish();
		monitor.step();
		end = System.nanoTime();
		statistic.time[BuildStatistic.timeFinishRegular] = end - start;
		statistic.data[BuildStatistic.coreRegular] = (int) mig.getVertices().stream().filter(v -> !v.isNormal())
			.count();
		statistic.data[BuildStatistic.strongRegular] = (int) mig.getVertices().stream().filter(Vertex::isNormal)
			.flatMap(v -> v.getStrongEdges().stream()).count();
		statistic.data[BuildStatistic.weakRegular] = mig.getVertices().stream()
			.flatMap(v -> v.getComplexClauses().stream()).mapToInt(c -> c.size() - 1).sum();

		return mig;
	}

}
