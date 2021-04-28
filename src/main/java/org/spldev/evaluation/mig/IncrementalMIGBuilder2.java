///* -----------------------------------------------------------------------------
// * Evaluation-MIG - Program for the evalaution of building incremetnal MIGs.
// * Copyright (C) 2021  Sebastian Krieter
// * 
// * This file is part of Evaluation-MIG.
// * 
// * Evaluation-MIG is free software: you can redistribute it and/or modify it
// * under the terms of the GNU Lesser General Public License as published by
// * the Free Software Foundation, either version 3 of the License,
// * or (at your option) any later version.
// * 
// * Evaluation-MIG is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// * See the GNU Lesser General Public License for more details.
// * 
// * You should have received a copy of the GNU Lesser General Public License
// * along with Evaluation-MIG.  If not, see <https://www.gnu.org/licenses/>.
// * 
// * See <https://github.com/skrieter/evaluation-mig> for further information.
// * -----------------------------------------------------------------------------
// */
//package org.spldev.evaluation.mig;
//
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.HashSet;
//import java.util.Objects;
//import java.util.Random;
//import java.util.Set;
//import java.util.stream.Collectors;
//import java.util.stream.IntStream;
//import java.util.stream.Stream;
//
//import org.sat4j.core.VecInt;
//import org.spldev.formula.VariableMap;
//import org.spldev.formula.clause.CNF;
//import org.spldev.formula.clause.Clauses;
//import org.spldev.formula.clause.LiteralList;
//import org.spldev.formula.clause.LiteralList.Order;
//import org.spldev.formula.clause.mig.MIG;
//import org.spldev.formula.clause.mig.MIG.BuildStatus;
//import org.spldev.formula.clause.mig.MIGBuilder2;
//import org.spldev.formula.clause.mig.Vertex.Status;
//import org.spldev.formula.clause.solver.RuntimeContradictionException;
//import org.spldev.formula.clause.solver.Sat4JSolver;
//import org.spldev.util.job.InternalMonitor;
//import org.spldev.util.job.MonitorableFunction;
//
//public class IncrementalMIGBuilder2 extends MIGBuilder2 implements MonitorableFunction<CNF, MIG> {
//
//	private static final int MIN_NEWCORE_SIZE = 8;
//	private static final int MIN_OLDCORE_SIZE = 8;
//	private static final int MAX_NEWCORE_DEPTH = 20;
//	private static final int MAX_OLDCORE_DEPTH = 20;
//
//	public static double newCoreThreshold = 0;
//	public static double oldCoreThreshold = 0;
//	public static Statistic statistic;
//
//	private static enum Changes {
//		UNCHANGED, ADDED, REMOVED, REPLACED
//	}
//
//	private final Random random = new Random(112358);
////	private final CNF oldCNF;
//	private final MIG oldMig;
//
//	private Changes changes;
//	private int[] coreDead;
//	private HashSet<LiteralList> addedClauses;
//	private VariableMap variables;
//
//	private long start, end;
//	private double changeRatio;
//
//	public IncrementalMIGBuilder2(MIG oldMig) {
//		this.oldMig = oldMig;
//	}
//
//	@Override
//	public MIG execute(CNF cnf, InternalMonitor monitor) throws Exception {
//		Objects.requireNonNull(cnf);
//		Objects.requireNonNull(oldMig);
//
//		start = System.nanoTime();
//		collect(cnf);
//		monitor.step();
//		end = System.nanoTime();
//		statistic.time[Statistic.timeInitIncremental] = end - start;
//
//		start = System.nanoTime();
//		if (!satCheck(cnf)) {
//			throw new RuntimeContradictionException("CNF is not satisfiable!");
//		}
//		monitor.step();
//		core(cnf, monitor);
//		monitor.step();
//		end = System.nanoTime();
//		statistic.time[Statistic.timeCoreIncremental] = end - start;
//
//		start = System.nanoTime();
//		cleanClauses();
//		monitor.step();
//		end = System.nanoTime();
//		statistic.time[Statistic.timeCleanIncremental] = end - start;
//
//		if (detectStrong) {
//			start = System.nanoTime();
//			checkOldStrong();
//			end = System.nanoTime();
//			statistic.time[Statistic.timeWeakBfsIncremental] = end - start;
//
//			start = System.nanoTime();
//			addClauses(cnf, true, monitor.subTask(10));
//			end = System.nanoTime();
//			statistic.time[Statistic.timeFirstAddIncremental] = end - start;
//
//			start = System.nanoTime();
//			bfsStrong(monitor.subTask(10));
//			end = System.nanoTime();
//			statistic.time[Statistic.timeFirstStrongBfsIncremental] = end - start;
//
//			start = System.nanoTime();
//			final LiteralList affectedVariables = new LiteralList(
//					addedClauses.stream() //
//						.map(c -> c.adapt(variables, cnf.getVariableMap()).get()) //
//						.flatMapToInt(c -> IntStream.of(c.getLiterals())) //
//						.map(Math::abs) //
//						.distinct() //
//						.toArray(), //
//					Order.NATURAL);
//			end = System.nanoTime();
//			statistic.time[Statistic.timeWeakBfsIncremental] += end - start;
//			System.out.println(affectedVariables.size());
//			System.out.println(mig.getCnf().getVariableMap().size());
//			bfsWeak(affectedVariables, monitor.subTask(1000));
//			mig.setStrongStatus(BuildStatus.Incremental);
//		} else {
//			mig.setStrongStatus(BuildStatus.None);
//		}
//
//		start = System.nanoTime();
//		add(cnf, checkRedundancy, addedClauses);
//		end = System.nanoTime();
//		statistic.time[Statistic.timeSecondAddIncremental] = end - start;
//
//		start = System.nanoTime();
//		bfsStrong(monitor);
//		monitor.step();
//		end = System.nanoTime();
//		statistic.time[Statistic.timeSecondStrongBfsIncremental] = end - start;
//
//		start = System.nanoTime();
//		finish();
//		monitor.step();
//		end = System.nanoTime();
//		statistic.time[Statistic.timeFinishIncremental] = end - start;
//
//		return mig;
//	}
//
//	public static double getChangeRatio(CNF cnf1, CNF cnf2) {
//		final Set<String> allVariables = new HashSet<>(cnf2.getVariableMap().getNames());
//		allVariables.addAll(cnf1.getVariableMap().getNames());
//		VariableMap variables = new VariableMap(allVariables);
//
//		final HashSet<LiteralList> adaptedNewClauses = cnf1.getClauses().stream()
//				.map(c -> c.adapt(cnf1.getVariableMap(), variables).get()) //
//				.peek(c -> c.setOrder(Order.NATURAL)).collect(Collectors.toCollection(HashSet::new));
//
//		final HashSet<LiteralList> adaptedOldClauses = cnf2.getClauses().stream() //
//				.map(c -> c.adapt(cnf2.getVariableMap(), variables).get()) //
//				.peek(c -> c.setOrder(Order.NATURAL)) //
//				.collect(Collectors.toCollection(HashSet::new));
//
//		final HashSet<LiteralList> addedClauses = adaptedNewClauses.stream() //
//				.filter(c -> !adaptedOldClauses.contains(c)) //
//				.collect(Collectors.toCollection(HashSet::new));
//		final HashSet<LiteralList> removedClauses = adaptedOldClauses.stream() //
//				.filter(c -> !adaptedNewClauses.contains(c)) //
//				.collect(Collectors.toCollection(HashSet::new));
//
//		HashSet<LiteralList> allClauses = new HashSet<>(adaptedNewClauses);
//		allClauses.addAll(adaptedOldClauses);
//		return (addedClauses.size() + removedClauses.size()) / (double) allClauses.size();
//	}
//
//	private void collect(CNF cnf) {
//		init(cnf);
//
//		final CNF oldCnf = oldMig.getCnf();
//		final Set<String> allVariables = new HashSet<>(oldCnf.getVariableMap().getNames());
//		allVariables.addAll(cnf.getVariableMap().getNames());
//		variables = new VariableMap(allVariables);
//
//		final HashSet<LiteralList> adaptedNewClauses = cnf.getClauses().stream()
//				.map(c -> c.adapt(cnf.getVariableMap(), variables).get()) //
//				.peek(c -> c.setOrder(Order.NATURAL)).collect(Collectors.toCollection(HashSet::new));
//
//		final HashSet<LiteralList> adaptedOldClauses = oldCnf.getClauses().stream() //
//				.map(c -> c.adapt(oldCnf.getVariableMap(), variables).get()) //
//				.peek(c -> c.setOrder(Order.NATURAL)) //
//				.collect(Collectors.toCollection(HashSet::new));
//
//		addedClauses = adaptedNewClauses.stream() //
//				.filter(c -> !adaptedOldClauses.contains(c)) //
//				.collect(Collectors.toCollection(HashSet::new));
//		final HashSet<LiteralList> removedClauses = adaptedOldClauses.stream() //
//				.filter(c -> !adaptedNewClauses.contains(c)) //
//				.collect(Collectors.toCollection(HashSet::new));
//
//		changes = addedClauses.isEmpty() ? removedClauses.isEmpty() ? Changes.UNCHANGED : Changes.REMOVED
//				: removedClauses.isEmpty() ? Changes.ADDED : Changes.REPLACED;
//
//		HashSet<LiteralList> allClauses = new HashSet<>(adaptedNewClauses);
//		allClauses.addAll(adaptedOldClauses);
//		changeRatio = (addedClauses.size() + removedClauses.size()) / (double) allClauses.size();
//
//		statistic.data[Statistic.addedVar] = variables.size() - oldCnf.getVariableMap().size();
//		statistic.data[Statistic.removedVar] = variables.size() - cnf.getVariableMap().size();
//		statistic.data[Statistic.sharedVar] = variables.size()
//				- (statistic.data[Statistic.addedVar] + statistic.data[Statistic.removedVar]);
//
//		statistic.data[Statistic.addedClauses] = addedClauses.size();
//		statistic.data[Statistic.removedClauses] = removedClauses.size();
//		statistic.data[Statistic.sharedClauses] = allClauses.size() - (addedClauses.size() + removedClauses.size());
//	}
//
//	private void core(CNF cnf, InternalMonitor monitor) {
//		coreDead = getOldCoreLiterals(cnf);
//		statistic.data[Statistic.sharedCore] = coreDead.length;
//		switch (changes) {
//		case ADDED:
//			for (int literal : coreDead) {
//				solver.assignmentPush(literal);
//				fixedFeatures[Math.abs(literal) - 1] = 0;
//			}
//			findNewCoreLiterals(monitor);
//			break;
//		case REMOVED:
//			checkOldCoreLiterals(coreDead);
//
//			for (int literal : coreDead) {
//				if (fixedFeatures[Math.abs(literal) - 1] == 0) {
//					statistic.data[Statistic.removedCore]++;
//				}
//			}
//			statistic.data[Statistic.sharedCore] = coreDead.length - statistic.data[Statistic.removedCore];
//			break;
//		case REPLACED:
//			checkOldCoreLiterals(coreDead);
//
//			for (int literal : coreDead) {
//				if (fixedFeatures[Math.abs(literal) - 1] == 0) {
//					statistic.data[Statistic.removedCore]++;
//				}
//			}
//			statistic.data[Statistic.sharedCore] = coreDead.length - statistic.data[Statistic.removedCore];
//
//			for (int literal : coreDead) {
//				fixedFeatures[Math.abs(literal) - 1] = 0;
//			}
//			findNewCoreLiterals(monitor);
//			break;
//		case UNCHANGED:
//			break;
//		default:
//			throw new IllegalStateException(String.valueOf(changes));
//		}
//	}
//
//	private void add(CNF cnf, boolean checkRedundancy, Collection<LiteralList> addedClauses) {
//		Stream<LiteralList> cnfStream = cleanedClausesList.stream();
//		if (checkRedundancy) {
//			final Set<LiteralList> oldMigClauses = oldMig.getVertices().stream()
//					.flatMap(v -> v.getComplexClauses().stream()).collect(Collectors.toCollection(HashSet::new));
//			final HashSet<LiteralList> redundantClauses = oldMig.getCnf().getClauses().stream()
//					.map(this::cleanClause) // 
//					.filter(Objects::nonNull) //
//					.filter(c -> c.size() > 2) //
//					.filter(c -> !oldMigClauses.contains(c)) //
//					.map(c -> c.adapt(oldMig.getCnf().getVariableMap(), variables).get()) //
//					.peek(c -> c.setOrder(Order.NATURAL)) //
//					.collect(Collectors.toCollection(HashSet::new));
//
//			statistic.data[Statistic.addedRedundant] = 0;
//			statistic.data[Statistic.removedRedundant] = 0;
//			statistic.data[Statistic.sharedRedundant] = redundantClauses.size();
//
//			cnfStream = cnfStream.map(c -> c.adapt(cnf.getVariableMap(), variables).get())
//					.peek(c -> c.setOrder(Order.NATURAL)).distinct();
//
//			switch (changes) {
//			case ADDED: {
//				final Sat4JSolver redundancySolver = new Sat4JSolver(new CNF(variables));
//				final int[] affectedVariables = addedClauses.stream().flatMapToInt(c -> IntStream.of(c.getLiterals()))
//						.map(Math::abs).distinct().toArray();
//				cnfStream = cnfStream.sorted(lengthComparator).filter(c -> {
//					if (c.size() < 3) {
//						return true;
//					}
//					if (!redundantClauses.contains(c)) {
//						if (c.containsAnyVariable(affectedVariables)) {
//							final boolean r = isRedundant(redundancySolver, c);
//							if (r) {
//								statistic.data[Statistic.addedRedundant]++;
//							}
//							return !r;
//						}
//					}
//					return true;
//				}).peek(redundancySolver::addClause);
//				mig.setRedundancyStatus(BuildStatus.Incremental);
//				break;
//			}
//			case REMOVED: {
//				final Sat4JSolver redundancySolver = new Sat4JSolver(new CNF(variables));
//				cnfStream = cnfStream.sorted(lengthComparator).filter(c -> {
//					if (c.size() < 3) {
//						return true;
//					}
//					if (redundantClauses.contains(c)) {
//						final boolean r = isRedundant(redundancySolver, c);
//						if (!r) {
//							statistic.data[Statistic.removedRedundant]++;
//							statistic.data[Statistic.sharedRedundant]--;
//						}
//						return !r;
//					}
//					return true;
//				}).peek(redundancySolver::addClause);
//				mig.setRedundancyStatus(mig.getRedundancyStatus());
//				break;
//			}
//			case REPLACED: {
//				final Sat4JSolver redundancySolver = new Sat4JSolver(new CNF(variables));
//				final int[] affectedLiterals = addedClauses.stream().flatMapToInt(c -> IntStream.of(c.getLiterals()))
//						.map(Math::abs).distinct().toArray();
//				cnfStream = cnfStream.sorted(lengthComparator).filter(c -> {
//					if (c.size() < 3) {
//						return true;
//					}
//					if (redundantClauses.contains(c)) {
//						final boolean r = isRedundant(redundancySolver, c);
//						if (!r) {
//							statistic.data[Statistic.removedRedundant]++;
//							statistic.data[Statistic.sharedRedundant]--;
//						}
//						return !r;
//					} else {
//						if (c.containsAnyVariable(affectedLiterals)) {
//							final boolean r = isRedundant(redundancySolver, c);
//							if (r) {
//								statistic.data[Statistic.addedRedundant]++;
//							}
//							return !r;
//						}
//					}
//					return true;
//				}).peek(redundancySolver::addClause);
//				mig.setRedundancyStatus(BuildStatus.Incremental);
//				break;
//			}
//			case UNCHANGED: {
//				cnfStream = cnfStream.filter(c -> c.size() <= 2 || !redundantClauses.contains(c));
//				mig.setRedundancyStatus(mig.getRedundancyStatus());
//				break;
//			}
//			default:
//				throw new IllegalStateException(String.valueOf(changes));
//			}
//			cnfStream = cnfStream.map(c -> c.adapt(variables, cnf.getVariableMap()).get())
//					.peek(c -> c.setOrder(Order.NATURAL)).distinct();
//		} else {
//			mig.setRedundancyStatus(BuildStatus.None);
//		}
//		cnfStream.forEach(mig::addClause);
//	}
//
//	private int[] getOldCoreLiterals(CNF cnf) {
//		int[] array = oldMig.getVertices().stream() //
//				.filter(v -> v.getStatus() == Status.Core) //
//				.mapToInt(v -> v.getVar()) //
//				.map(l -> Clauses.adapt(l, oldMig.getCnf().getVariableMap(), cnf.getVariableMap())) //
//				.filter(l -> l != 0) //
//				.peek(l -> {
//					mig.getVertex(l).setStatus(Status.Core);
//					mig.getVertex(-l).setStatus(Status.Dead);
//				}).toArray();
//		return array;
//	}
//
//	protected void checkOldStrong() {
//		switch (changes) {
//		case REMOVED:
//		case REPLACED:
//			loop: for (LiteralList strongEdge : oldMig.getDetectedStrong()) {
//				final LiteralList adaptClause = strongEdge
//						.adapt(oldMig.getCnf().getVariableMap(), mig.getCnf().getVariableMap()).get();
//				if (adaptClause != null) {
//					final int[] literals = adaptClause.getLiterals();
//					final int l1 = -literals[0];
//					final int l2 = -literals[1];
//					for (LiteralList solution : solver.getSolutionHistory()) {
//						if (solution.containsAllLiterals(l1, l2)) {
//							continue loop;
//						}
//					}
//					solver.assignmentPush(l1);
//					solver.assignmentPush(l2);
//					switch (solver.hasSolution()) {
//					case FALSE:
//						cleanedClausesList.add(adaptClause);
//						mig.getDetectedStrong().add(adaptClause);
//					case TIMEOUT:
//					case TRUE:
//						break;
//					}
//					solver.assignmentPop();
//					solver.assignmentPop();
//				}
//			}
//			break;
//		case ADDED:
//		case UNCHANGED:
//			for (LiteralList strongEdge : oldMig.getDetectedStrong()) {
//				final LiteralList adaptClause = strongEdge
//						.adapt(oldMig.getCnf().getVariableMap(), mig.getCnf().getVariableMap()).get();
//				if (adaptClause != null) {
//					cleanedClausesList.add(adaptClause);
//					mig.getDetectedStrong().add(adaptClause);
//				}
//			}
//			break;
//		default:
//			throw new IllegalStateException(String.valueOf(changes));
//		}
//	}
//
//	// For removed clauses
//	protected void checkOldCoreLiterals(int[] coreDead) {
//		if (changeRatio < oldCoreThreshold) {
//			checkOldCoreLiterals2(coreDead);
//		} else {
//			checkOldCoreLiterals1(coreDead);
//		}
//	}
//
//	// For added clauses
//	protected void findNewCoreLiterals(InternalMonitor monitor) {
//		if (changeRatio < newCoreThreshold) {
//			findNewCoreLiterals2();
//		} else {
//			findCoreFeatures(monitor);
//		}
//	}
//
//	private void findNewCoreLiterals2() {
//		solver.setSelectionStrategy(fixedFeatures, true, true);
//		split(fixedFeatures, 0, fixedFeatures.length, 0);
//	}
//
//	private void split(int[] model, int start, int end, int depth) {
//		VecInt vars = new VecInt(end - start);
//		for (int j = start; j < end; j++) {
//			final int var = model[j];
//			if (var != 0) {
//				vars.push(-var);
//			}
//		}
//		if (vars.size() <= MIN_NEWCORE_SIZE || depth > MAX_NEWCORE_DEPTH) {
//			for (int j = start; j < end; j++) {
//				final int varX = model[j];
//				if (varX != 0) {
//					solver.assignmentPush(-varX);
//					switch (solver.hasSolution()) {
//					case FALSE:
//						solver.assignmentReplaceLast(varX);
//						mig.getVertex(-varX).setStatus(Status.Dead);
//						mig.getVertex(varX).setStatus(Status.Core);
//						statistic.data[Statistic.addedCore]++;
//						break;
//					case TIMEOUT:
//						solver.assignmentPop();
//						break;
//					case TRUE:
//						solver.assignmentPop();
//						LiteralList.resetConflicts(model, solver.getSolution());
//						solver.shuffleOrder(random);
//						break;
//					}
//				}
//			}
//		} else {
//			LiteralList mainClause = new LiteralList(Arrays.copyOf(vars.toArray(), vars.size()), Order.UNORDERED);
//			switch (solver.hasSolution(mainClause)) {
//			case FALSE:
//				final int halfLength = (end - start) >> 1;
//				split(model, start + halfLength, end, depth + 1);
//				split(model, start, start + halfLength, depth + 1);
//				break;
//			case TIMEOUT:
//				break;
//			case TRUE:
//				LiteralList.resetConflicts(model, solver.getSolution());
//				solver.shuffleOrder(random);
//				break;
//			}
//		}
//	}
//
//	private void checkOldCoreLiterals1(int[] coreDead) {
//		solver.setSelectionStrategy(fixedFeatures, true, true);
//		for (int literal : coreDead) {
//			final int varX = fixedFeatures[Math.abs(literal) - 1];
//			if (varX == -literal) {
//				fixedFeatures[Math.abs(literal) - 1] = 0;
//				mig.getVertex(-varX).setStatus(Status.Normal);
//				mig.getVertex(varX).setStatus(Status.Normal);
//			} else if (varX == 0) {
//				mig.getVertex(-literal).setStatus(Status.Normal);
//				mig.getVertex(literal).setStatus(Status.Normal);
//			} else {
//				solver.assignmentPush(-varX);
//				switch (solver.hasSolution()) {
//				case FALSE:
//					solver.assignmentReplaceLast(varX);
//					break;
//				case TIMEOUT:
//					solver.assignmentPop();
//					fixedFeatures[Math.abs(literal) - 1] = 0;
//					mig.getVertex(-varX).setStatus(Status.Normal);
//					mig.getVertex(varX).setStatus(Status.Normal);
//					break;
//				case TRUE:
//					solver.assignmentPop();
//					mig.getVertex(-varX).setStatus(Status.Normal);
//					mig.getVertex(varX).setStatus(Status.Normal);
//					LiteralList.resetConflicts(fixedFeatures, solver.getSolution());
//					solver.shuffleOrder(random);
//					break;
//				}
//			}
//		}
//	}
//
//	protected void checkOldCoreLiterals2(int[] coreDead) {
//		solver.setSelectionStrategy(fixedFeatures, true, false);
//		int[] negCoreDead = new int[coreDead.length];
//		for (int i = 0; i < coreDead.length; i++) {
//			negCoreDead[i] = -coreDead[i];
//		}
//		checkOldCoreLiteralsRec(negCoreDead, 0);
//	}
//
//	protected void checkOldCoreLiteralsRec(int[] coreDead, int depth) {
//		if (coreDead.length <= MIN_OLDCORE_SIZE || depth > MAX_OLDCORE_DEPTH) {
//			for (int literal : coreDead) {
//				final int varX = fixedFeatures[Math.abs(literal) - 1];
//				if (varX == literal) {
//					fixedFeatures[Math.abs(literal) - 1] = 0;
//					mig.getVertex(-literal).setStatus(Status.Normal);
//					mig.getVertex(literal).setStatus(Status.Normal);
//				} else if (varX == 0) {
//					mig.getVertex(-literal).setStatus(Status.Normal);
//					mig.getVertex(literal).setStatus(Status.Normal);
//				} else {
//					solver.assignmentPush(-varX);
//					switch (solver.hasSolution()) {
//					case FALSE:
//						solver.assignmentReplaceLast(varX);
//						break;
//					case TIMEOUT:
//						solver.assignmentPop();
//						fixedFeatures[Math.abs(literal) - 1] = 0;
//						mig.getVertex(-varX).setStatus(Status.Normal);
//						mig.getVertex(varX).setStatus(Status.Normal);
//						break;
//					case TRUE:
//						solver.assignmentPop();
//						mig.getVertex(-varX).setStatus(Status.Normal);
//						mig.getVertex(varX).setStatus(Status.Normal);
//						LiteralList.resetConflicts(fixedFeatures, solver.getSolution());
//						solver.shuffleOrder(random);
//						break;
//					}
//				}
//			}
//		} else {
//			try {
//				solver.addClause(new LiteralList(coreDead, Order.UNORDERED));
//				switch (solver.hasSolution()) {
//				case FALSE:
//					solver.removeLastClause();
//					for (int literal : coreDead) {
//						solver.assignmentPush(-literal);
//					}
//					break;
//				case TIMEOUT:
//					solver.removeLastClause();
//					break;
//				case TRUE:
//					solver.removeLastClause();
//					final int half = coreDead.length >> 1;
//					checkOldCoreLiteralsRec(Arrays.copyOfRange(coreDead, 0, half), depth + 1);
//					checkOldCoreLiteralsRec(Arrays.copyOfRange(coreDead, half, coreDead.length), depth + 1);
//					break;
//				}
//			} catch (RuntimeContradictionException e) {
//				for (int literal : coreDead) {
//					solver.assignmentPush(-literal);
//				}
//			}
//		}
//	}
//
//}
