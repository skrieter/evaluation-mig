package test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.sat4j.core.VecInt;
import org.spldev.formula.VariableMap;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.Clauses;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.LiteralList.Order;
import org.spldev.formula.clause.mig.MIG;
import org.spldev.formula.clause.mig.MIGBuilder2;
import org.spldev.formula.clause.mig.Vertex.Status;
import org.spldev.formula.clause.solver.RuntimeContradictionException;
import org.spldev.formula.clause.solver.Sat4JSolver;
import org.spldev.formula.clause.solver.SatSolver;
import org.spldev.util.job.InternalMonitor;
import org.spldev.util.job.MonitorableFunction;

public class IncrementalMIGBuilder extends MIGBuilder2 implements MonitorableFunction<CNF, MIG> {

	private static final int MIN_NEWCORE_SIZE = 8;
	private static final int MIN_OLDCORE_SIZE = 8;
	private static final int MAX_NEWCORE_DEPTH = 5;
	private static final int MAX_OLDCORE_DEPTH = 5;

	public static double newCoreThreshold = 0;
	public static double oldCoreThreshold = 0;
	public static Statistic statistic;

	private static enum Changes {
		UNCHANGED, ADDED, REMOVED, REPLACED
	}

	private final Random random = new Random(112358);
	private final CNF oldCNF;
	private final MIG oldMig;

	private Changes changes;
	private SatSolver solver;
	private int[] fixedFeatures;
	private int[] coreDead;
	private HashSet<LiteralList> addedClauses;
	private VariableMap variables;

	private long start, end;
	private double changeRatio;

	public IncrementalMIGBuilder(MIG oldMig) {
		this.oldMig = oldMig;
		this.oldCNF = oldMig.getCnf();
	}

	@Override
	public MIG execute(CNF cnf, InternalMonitor monitor) throws Exception {
		Objects.requireNonNull(cnf);
		Objects.requireNonNull(oldMig);

		start = System.nanoTime();
		collect(cnf);
		monitor.step();
		end = System.nanoTime();
		statistic.timeInitIt = end - start;

		start = System.nanoTime();
		if (!satCheck(cnf)) {
			return null;
		}
		monitor.step();
		end = System.nanoTime();
		statistic.timeSatCoreDeadIt = end - start;

		core(cnf);
		monitor.step();

		start = System.nanoTime();
		add(cnf, addedClauses);
		end = System.nanoTime();
		statistic.timeAddIt = end - start;

		start = System.nanoTime();
		bfsStrong();
		monitor.step();
		end = System.nanoTime();
		statistic.timeBfsIt = end - start;

		start = System.nanoTime();
		finish();
		monitor.step();
		end = System.nanoTime();
		statistic.timeFinishIt = end - start;

		return mig;
	}

	private void collect(CNF cnf) {
		init(cnf);

		final Set<String> allVariables = new HashSet<>(oldCNF.getVariableMap().getNames());
		allVariables.addAll(cnf.getVariableMap().getNames());
		variables = new VariableMap(allVariables);

		statistic.addedVar = variables.size() - oldCNF.getVariableMap().size();
		statistic.removedVar = variables.size() - cnf.getVariableMap().size();
		statistic.sharedVar = variables.size() - (statistic.addedVar + statistic.removedVar);

		final HashSet<LiteralList> adaptedNewClauses = cnf.getClauses().stream()
				.map(c -> c.adapt(cnf.getVariableMap(), variables)) //
				.peek(c -> c.setOrder(Order.NATURAL))
				.collect(Collectors.toCollection(HashSet::new));

		final HashSet<LiteralList> adaptedOldClauses = oldCNF.getClauses().stream() //
				.map(c -> c.adapt(oldCNF.getVariableMap(), variables)) //
				.peek(c -> c.setOrder(Order.NATURAL)) //
				.collect(Collectors.toCollection(HashSet::new));

		addedClauses = adaptedNewClauses.stream() //
				.filter(c -> !adaptedOldClauses.contains(c)) //
				.collect(Collectors.toCollection(HashSet::new));
		final HashSet<LiteralList> removedClauses = adaptedOldClauses.stream() //
				.filter(c -> !adaptedNewClauses.contains(c)) //
				.collect(Collectors.toCollection(HashSet::new));

		changes = addedClauses.isEmpty() ? removedClauses.isEmpty() ? Changes.UNCHANGED : Changes.REMOVED
				: removedClauses.isEmpty() ? Changes.ADDED : Changes.REPLACED;

		HashSet<LiteralList> allClauses = new HashSet<>(adaptedNewClauses);
		allClauses.addAll(adaptedOldClauses);
		changeRatio = (addedClauses.size() + removedClauses.size()) / (double) allClauses.size();

		statistic.addedClauses = addedClauses.size();
		statistic.removedClauses = removedClauses.size();
		statistic.sharedClauses = allClauses.size() - (addedClauses.size() + removedClauses.size());
	}

	private void core(CNF cnf) {
		switch (changes) {
		case ADDED:
			start = System.nanoTime();
			for (int literal : coreDead) {
				solver.assignmentPush(literal);
				fixedFeatures[Math.abs(literal) - 1] = 0;
			}
			findNewCoreLiterals();
			end = System.nanoTime();
			statistic.timeNewCoreDeadIt = end - start;
			break;
		case REMOVED:
			start = System.nanoTime();
			checkOldCoreLiterals(coreDead);
			end = System.nanoTime();
			statistic.timeOldCoreDeadIt = end - start;

			for (int literal : coreDead) {
				if (fixedFeatures[Math.abs(literal) - 1] == 0) {
					statistic.removedCore++;
				}
			}
			statistic.sharedCore = coreDead.length - statistic.removedCore;
			break;
		case REPLACED:
			start = System.nanoTime();
			checkOldCoreLiterals(coreDead);
			end = System.nanoTime();
			statistic.timeOldCoreDeadIt = end - start;

			for (int literal : coreDead) {
				if (fixedFeatures[Math.abs(literal) - 1] == 0) {
					statistic.removedCore++;
				}
			}
			statistic.sharedCore = coreDead.length - statistic.removedCore;

			start = System.nanoTime();
			for (int literal : coreDead) {
				fixedFeatures[Math.abs(literal) - 1] = 0;
			}
			findNewCoreLiterals();
			end = System.nanoTime();
			statistic.timeNewCoreDeadIt = end - start;
			break;
		case UNCHANGED:
			break;
		default:
			throw new IllegalStateException(String.valueOf(changes));
		}
	}

	private void add(CNF cnf, Collection<LiteralList> addedClauses) {
		Stream<LiteralList> cnfStream = cnf.getClauses().stream().map(c -> cleanClause(c, mig)).filter(Objects::nonNull)
				.distinct();
		if (checkRedundancy) {
			final HashSet<LiteralList> redundantClauses = getOldRedundantClauses(variables);
			statistic.addedRedundant = 0;
			statistic.removedRedundant = 0;
			statistic.sharedRedundant = redundantClauses.size();
			cnfStream = cnfStream.map(c -> c.adapt(cnf.getVariableMap(), variables))
					.peek(c -> c.setOrder(Order.NATURAL));
			switch (changes) {
			case ADDED: {
				final Sat4JSolver redundancySolver = new Sat4JSolver(new CNF(variables));
				final int[] affectedLiterals = addedClauses.stream().flatMapToInt(c -> IntStream.of(c.getLiterals()))
						.distinct().toArray();
				cnfStream = cnfStream.sorted(lengthComparator).filter(c -> {
					if (c.size() < 3) {
						return true;
					}
					if (!redundantClauses.contains(c)) {
						if (c.containsLiteral(affectedLiterals)) {
							final boolean r = isRedundant(redundancySolver, c);
							if (r) {
								statistic.addedRedundant++;
							}
							return !r;
						}
					}
					return true;
				}).peek(redundancySolver::addClause);
				break;
			}
			case REMOVED: {
				final Sat4JSolver redundancySolver = new Sat4JSolver(new CNF(variables));
				cnfStream = cnfStream.sorted(lengthComparator).filter(c -> {
					if (c.size() < 3) {
						return true;
					}
					if (redundantClauses.contains(c)) {
						final boolean r = isRedundant(redundancySolver, c);
						if (!r) {
							statistic.removedRedundant++;
							statistic.sharedRedundant--;
						}
						return !r;
					}
					return true;
				}).peek(redundancySolver::addClause);
				break;
			}
			case REPLACED: {
				final Sat4JSolver redundancySolver = new Sat4JSolver(new CNF(variables));
				final int[] affectedLiterals = addedClauses.stream().flatMapToInt(c -> IntStream.of(c.getLiterals()))
						.distinct().toArray();
				cnfStream = cnfStream.sorted(lengthComparator).filter(c -> {
					if (c.size() < 3) {
						return true;
					}
					if (redundantClauses.contains(c)) {
						final boolean r = isRedundant(redundancySolver, c);
						if (!r) {
							statistic.removedRedundant++;
							statistic.sharedRedundant--;
						}
						return !r;
					} else {
						if (c.containsLiteral(affectedLiterals)) {
							final boolean r = isRedundant(redundancySolver, c);
							if (r) {
								statistic.addedRedundant++;
							}
							return !r;
						}
					}
					return true;
				}).peek(redundancySolver::addClause);
				break;
			}
			case UNCHANGED: {
				cnfStream = cnfStream.filter(c -> c.size() <= 2 || !redundantClauses.contains(c));
				break;
			}
			default:
				throw new IllegalStateException(String.valueOf(changes));
			}
			cnfStream = cnfStream.map(c -> c.adapt(variables, cnf.getVariableMap()));
		}
		cnfStream.forEach(mig::addClause);
	}

	private int[] getOldCoreLiterals(CNF cnf) {
		int[] array = oldMig.getVertices().stream() //
				.filter(v -> v.getStatus() == Status.Core) //
				.mapToInt(v -> v.getVar()) //
				.map(l -> Clauses.adapt(l, oldCNF.getVariableMap(), cnf.getVariableMap())) //
				.filter(l -> l != 0) //
				.peek(l -> {
					mig.getVertex(l).setStatus(Status.Core);
					mig.getVertex(-l).setStatus(Status.Dead);
				}).toArray();
		return array;
	}

	private HashSet<LiteralList> getOldRedundantClauses(VariableMap variables) {
		final Set<LiteralList> oldMigClauses = oldMig.getVertices().stream()
				.flatMap(v -> v.getComplexClauses().stream()).collect(Collectors.toCollection(HashSet::new));
		return oldCNF.getClauses().stream().map(c -> cleanClause(c, oldMig)).filter(Objects::nonNull)
				.filter(c -> c.size() > 2).filter(c -> !oldMigClauses.contains(c))
				.map(c -> c.adapt(oldCNF.getVariableMap(), variables)).peek(c -> c.setOrder(Order.NATURAL))
				.collect(Collectors.toCollection(HashSet::new));
	}

	protected boolean satCheck(CNF cnf) {
		solver = new Sat4JSolver(cnf);
		solver.rememberSolutionHistory(0);
		fixedFeatures = solver.findSolution();
		coreDead = getOldCoreLiterals(cnf);

		statistic.sharedClauses = coreDead.length;

		return fixedFeatures != null;
	}

	// For removed clauses
	protected void checkOldCoreLiterals(int[] coreDead) {
		if (changeRatio < oldCoreThreshold) {
			checkOldCoreLiterals1(coreDead);
		} else {
			checkOldCoreLiterals2(coreDead);
		}
	}

	// For added clauses
	protected void findNewCoreLiterals() {
		if (changeRatio < newCoreThreshold) {
			findNewCoreLiterals1();
		} else {
			findNewCoreLiterals2();
		}
	}

	protected void findNewCoreLiterals1() {
		int count = 0;
		solver.setSelectionStrategy(fixedFeatures, true, true);
		for (int varX : fixedFeatures) {
			if (varX != 0) {
				solver.assignmentPush(-varX);
				switch (solver.hasSolution()) {
				case FALSE:
					solver.assignmentReplaceLast(varX);
					mig.getVertex(-varX).setStatus(Status.Dead);
					mig.getVertex(varX).setStatus(Status.Core);
					count++;
					break;
				case TIMEOUT:
					solver.assignmentPop();
					break;
				case TRUE:
					solver.assignmentPop();
					LiteralList.resetConflicts(fixedFeatures, solver.getSolution());
					solver.shuffleOrder(random);
					break;
				}
			}
		}
		statistic.addedCore = count;
	}

	private void findNewCoreLiterals2() {
		solver.setSelectionStrategy(fixedFeatures, true, true);
		split(fixedFeatures, 0, fixedFeatures.length, 0);
	}

	private void split(int[] model, int start, int end, int depth) {
		VecInt vars = new VecInt(end - start);
		for (int j = start; j < end; j++) {
			final int var = model[j];
			if (var != 0) {
				vars.push(-var);
			}
		}
		if (vars.size() <= MIN_NEWCORE_SIZE || depth > MAX_NEWCORE_DEPTH) {
			for (int j = start; j < end; j++) {
				final int varX = model[j];
				if (varX != 0) {
					solver.assignmentPush(-varX);
					switch (solver.hasSolution()) {
					case FALSE:
						solver.assignmentReplaceLast(varX);
						mig.getVertex(-varX).setStatus(Status.Dead);
						mig.getVertex(varX).setStatus(Status.Core);
						break;
					case TIMEOUT:
						solver.assignmentPop();
						break;
					case TRUE:
						solver.assignmentPop();
						LiteralList.resetConflicts(model, solver.getSolution());
						solver.shuffleOrder(random);
						break;
					}
				}
			}
		} else {
			LiteralList mainClause = new LiteralList(Arrays.copyOf(vars.toArray(), vars.size()), Order.UNORDERED);
			switch (solver.hasSolution(mainClause)) {
			case FALSE:
				final int halfLength = (end - start) >> 1;
				split(model, start + halfLength, end, depth + 1);
				split(model, start, start + halfLength, depth + 1);
				break;
			case TIMEOUT:
				break;
			case TRUE:
				LiteralList.resetConflicts(model, solver.getSolution());
				solver.shuffleOrder(random);
				break;
			}
		}
	}

	private void checkOldCoreLiterals1(int[] coreDead) {
		solver.setSelectionStrategy(fixedFeatures, true, true);
		for (int literal : coreDead) {
			final int varX = fixedFeatures[Math.abs(literal) - 1];
			if (varX == -literal) {
				fixedFeatures[Math.abs(literal) - 1] = 0;
				mig.getVertex(-varX).setStatus(Status.Normal);
				mig.getVertex(varX).setStatus(Status.Normal);
			} else if (varX == 0) {
				mig.getVertex(-literal).setStatus(Status.Normal);
				mig.getVertex(literal).setStatus(Status.Normal);
			} else {
				solver.assignmentPush(-varX);
				switch (solver.hasSolution()) {
				case FALSE:
					solver.assignmentReplaceLast(varX);
					break;
				case TIMEOUT:
					solver.assignmentPop();
					fixedFeatures[Math.abs(literal) - 1] = 0;
					mig.getVertex(-varX).setStatus(Status.Normal);
					mig.getVertex(varX).setStatus(Status.Normal);
					break;
				case TRUE:
					solver.assignmentPop();
					mig.getVertex(-varX).setStatus(Status.Normal);
					mig.getVertex(varX).setStatus(Status.Normal);
					LiteralList.resetConflicts(fixedFeatures, solver.getSolution());
					solver.shuffleOrder(random);
					break;
				}
			}
		}
	}

	protected void checkOldCoreLiterals2(int[] coreDead) {
		solver.setSelectionStrategy(fixedFeatures, true, false);
		int[] negCoreDead = new int[coreDead.length];
		for (int i = 0; i < coreDead.length; i++) {
			negCoreDead[i] = -coreDead[i];
		}
		checkOldCoreLiteralsRec(negCoreDead, 0);
	}

	protected void checkOldCoreLiteralsRec(int[] coreDead, int depth) {
		if (coreDead.length <= MIN_OLDCORE_SIZE || depth > MAX_OLDCORE_DEPTH) {
			for (int literal : coreDead) {
				final int varX = fixedFeatures[Math.abs(literal) - 1];
				if (varX == literal) {
					fixedFeatures[Math.abs(literal) - 1] = 0;
					mig.getVertex(-literal).setStatus(Status.Normal);
					mig.getVertex(literal).setStatus(Status.Normal);
				} else if (varX == 0) {
					mig.getVertex(-literal).setStatus(Status.Normal);
					mig.getVertex(literal).setStatus(Status.Normal);
				} else {
					solver.assignmentPush(-varX);
					switch (solver.hasSolution()) {
					case FALSE:
						solver.assignmentReplaceLast(varX);
						break;
					case TIMEOUT:
						solver.assignmentPop();
						fixedFeatures[Math.abs(literal) - 1] = 0;
						mig.getVertex(-varX).setStatus(Status.Normal);
						mig.getVertex(varX).setStatus(Status.Normal);
						break;
					case TRUE:
						solver.assignmentPop();
						mig.getVertex(-varX).setStatus(Status.Normal);
						mig.getVertex(varX).setStatus(Status.Normal);
						LiteralList.resetConflicts(fixedFeatures, solver.getSolution());
						solver.shuffleOrder(random);
						break;
					}
				}
			}
		} else {
			try {
				solver.addClause(new LiteralList(coreDead, Order.UNORDERED));
				switch (solver.hasSolution()) {
				case FALSE:
					solver.removeLastClause();
					for (int literal : coreDead) {
						solver.assignmentPush(-literal);
					}
					break;
				case TIMEOUT:
					solver.removeLastClause();
					break;
				case TRUE:
					solver.removeLastClause();
					final int half = coreDead.length >> 1;
					checkOldCoreLiteralsRec(Arrays.copyOfRange(coreDead, 0, half), depth + 1);
					checkOldCoreLiteralsRec(Arrays.copyOfRange(coreDead, half, coreDead.length), depth + 1);
					break;
				}
			} catch (RuntimeContradictionException e) {
				for (int literal : coreDead) {
					solver.assignmentPush(-literal);
				}
			}
		}
	}

}
