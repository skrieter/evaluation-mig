package test;

import org.spldev.formula.clause.*;
import org.spldev.formula.clause.mig.*;
import org.spldev.util.job.*;

/**
 * Adjacency matrix implementation for a feature graph.
 *
 * @author Sebastian Krieter
 */

public class MIGBuilder extends MIGBuilder2 implements MonitorableFunction<CNF, MIG> {

	public static Statistic statistic;
	private long start, end;

	@Override
	public MIG execute(CNF cnf, InternalMonitor monitor) throws Exception {
		monitor.setTotalWork(113);

		start = System.nanoTime();
		init(cnf);
		monitor.step();
		end = System.nanoTime();
		statistic.timeInitNew = end - start;

		start = System.nanoTime();
		if (!getCoreFeatures(cnf, monitor.subTask(10))) {
			return null;
		}
		end = System.nanoTime();
		statistic.timeCoreDeadNew = end - start;

		start = System.nanoTime();
		addClauses(cnf, monitor.subTask(100));
		end = System.nanoTime();
		statistic.timeAddNew = end - start;

		start = System.nanoTime();
		bfsStrong();
		monitor.step();
		end = System.nanoTime();
		statistic.timeBfsNew = end - start;

		start = System.nanoTime();
		finish();
		monitor.step();
		end = System.nanoTime();
		statistic.timeFinishNew = end - start;

		return mig;
	}

}
