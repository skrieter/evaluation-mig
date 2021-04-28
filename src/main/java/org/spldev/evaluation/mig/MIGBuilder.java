package org.spldev.evaluation.mig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.LiteralList.Order;
import org.spldev.formula.clause.mig.MIG;
import org.spldev.formula.clause.mig.Vertex;
import org.spldev.formula.clause.mig.Vertex.Status;
import org.spldev.formula.clause.solver.Sat4JSolver;
import org.spldev.formula.clause.solver.SatSolver;
import org.spldev.formula.clause.solver.SatSolver.SatResult;
import org.spldev.formula.clause.solver.SatSolver.SelectionStrategy;
import org.spldev.util.job.InternalMonitor;

/**
 * Adjacency matrix implementation for a feature graph.
 *
 * @author Sebastian Krieter
 */

public class MIGBuilder {

	/**
	 * For sorting clauses by length. Starting with the longest.
	 */
	protected static final Comparator<LiteralList> lengthComparator = Comparator.comparing(o -> o.getLiterals().length);

	protected final Random random = new Random(112358);

	protected boolean checkRedundancy = true;
	protected boolean detectStrong = true;

	protected SatSolver solver;
	protected List<LiteralList> cleanedClausesList;
	protected int[] fixedFeatures;

	protected MIG mig;

	protected void init(CNF cnf) {
		mig = new MIG(cnf);
	}

	protected boolean satCheck(CNF cnf) {
		solver = new Sat4JSolver(cnf);
		solver.rememberSolutionHistory(1000);
		fixedFeatures = solver.findSolution();
		return fixedFeatures != null;
	}

	protected void findCoreFeatures(InternalMonitor monitor) {
		monitor.setTotalWork(fixedFeatures.length);

		solver.setSelectionStrategy(fixedFeatures, true, true);

		// find core/dead features
		for (int i = 0; i < fixedFeatures.length; i++) {
			final int varX = fixedFeatures[i];
			if (varX != 0) {
				solver.assignmentPush(-varX);
				final SatResult hasSolution = solver.hasSolution();
				switch (hasSolution) {
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
					LiteralList.resetConflicts(fixedFeatures, solver.getSolution());
					solver.shuffleOrder(random);
					break;
				}
			}
			monitor.step();
		}
		monitor.done();
	}

	protected long addClauses(CNF cnf, boolean checkRedundancy, InternalMonitor monitor) {
		monitor.setTotalWork(cleanedClausesList.size());
		Stream<LiteralList> stream = cleanedClausesList.stream();
		if (checkRedundancy) {
			final Sat4JSolver newSolver = new Sat4JSolver(new CNF(cnf.getVariableMap()));
			stream = stream.sorted(lengthComparator).distinct().peek(c -> monitor.step()).filter(clause -> //
			(clause.getLiterals().length < 3) //
					|| !isRedundant(newSolver, clause)) //
					.peek(newSolver::addClause); //
		} else {
			stream = stream.distinct().peek(c -> monitor.step());
		}
		final long count = stream.peek(mig::addClause).count();
		monitor.done();
		return count;
	}

//	protected void addStrongEdges() {
//		cleanedClausesList.stream().filter(c -> c.size() == 2).distinct().forEach(mig::addClause);
//	}

//	protected void addWeakEdges(boolean checkRedundancy, InternalMonitor monitor) {
//		monitor.setTotalWork(cleanedClausesList.size());
//		Stream<LiteralList> stream = cleanedClausesList.stream().filter(c -> c.size() > 2);
//		if (checkRedundancy) {
////			int[] dsa = new int[mig.size() + 1];
//			final Sat4JSolver newSolver = new Sat4JSolver(new CNF(mig.getCnf().getVariableMap()));
//			stream = stream //
//					.sorted(lengthComparator) //
//					.distinct() //
//					.peek(c -> monitor.step()) //
////				.filter(c -> {
////					for (int literal : c.getLiterals()) {
////						for (Vertex strong : mig.getVertex(-literal).getStrongEdges()) {
////							for (int literal2 : c.getLiterals()) {
////								if (strong.getVar() == literal2) {
////									return false;
////								}
////							}
////						}
////					}
////					return true;
////				}) //
////				.filter(c -> {
////					for (int literal : c.getLiterals()) {
////						dsa[Math.abs(literal)] = literal;
////					}
////					for (int literal : c.getLiterals()) {
////						for (Vertex strong : mig.getVertex(-literal).getStrongEdges()) {
////							if (dsa[Math.abs(strong.getVar())] == strong.getVar()) {
////								return false;
////							}
////						}
////					}
////					for (int literal : c.getLiterals()) {
////						dsa[Math.abs(literal)] = 0;
////					}
////					return true;
////				}) //
//					.filter(c -> !isRedundant(newSolver, c)) //
//					.peek(newSolver::addClause); //
//		} else {
//			stream = stream //
//					.distinct() //
//					.peek(c -> monitor.step());
//		}
//		stream.forEach(mig::addClause);
//		monitor.done();
//	}
//
//	protected void addWeakEdges2(boolean checkRedundancy, InternalMonitor monitor) {
//		monitor.setTotalWork(cleanedClausesList.size());
//		Stream<LiteralList> stream = cleanedClausesList.stream().filter(c -> c.size() > 2);
//		if (checkRedundancy) {
//			stream = stream //
//					.sorted(lengthComparator) //
//					.distinct() //
//					.peek(c -> monitor.step()) //
//			;
//		} else {
//			stream = stream //
//					.distinct() //
//					.peek(c -> monitor.step());
//		}
//		stream.forEach(mig::addClause);
//		monitor.done();
//	}

	protected void cleanClauses() {
		cleanedClausesList = new ArrayList<>(mig.getCnf().getClauses().size());
		mig.getCnf().getClauses().stream().map(c -> cleanClause(c, mig)).filter(Objects::nonNull)
				.forEach(cleanedClausesList::add);
	}

	protected LiteralList cleanClause(LiteralList clause, MIG mig) {
		final int[] literals = clause.getLiterals();
		final LinkedHashSet<Integer> literalSet = new LinkedHashSet<>(literals.length << 1);

		// Sort out dead and core features
		int childrenCount = clause.size();
		for (int i = 0; i < childrenCount; i++) {
			final int var = literals[i];
			mig.size();
			final Status status = mig.getVertex(var).getStatus();
			switch (status) {
			case Core:
				return null;
			case Dead:
				if (childrenCount <= 2) {
					return null;
				}
				childrenCount--;
				// Switch literals (faster than deletion within an array)
				literals[i] = literals[childrenCount];
				literals[childrenCount] = var;
				i--;
				break;
			case Normal:
				if (literalSet.contains(-var)) {
					return null;
				} else {
					literalSet.add(var);
				}
				break;
			default:
				throw new IllegalStateException(String.valueOf(status));
			}
		}
		final int[] literalArray = new int[literalSet.size()];
		int i = 0;
		for (final int lit : literalSet) {
			literalArray[i++] = lit;
		}
		return new LiteralList(literalArray, Order.NATURAL);
	}

	protected final boolean isRedundant(SatSolver solver, LiteralList curClause) {
		return solver.hasSolution(curClause.negate()) == SatResult.FALSE;
	}

	protected void bfsStrong(InternalMonitor monitor) {
		monitor.setTotalWork(mig.getVertices().size());
		final boolean[] mark = new boolean[mig.size() + 1];
		final ArrayDeque<Vertex> queue = new ArrayDeque<>();
		for (final Vertex vertex : mig.getVertices()) {
			Arrays.fill(mark, false);
			final Vertex complement = mig.getVertex(-vertex.getVar());

			mark[Math.abs(vertex.getVar())] = true;
			for (final Vertex stronglyConnetedVertex : vertex.getStrongEdges()) {
				mark[Math.abs(stronglyConnetedVertex.getVar())] = true;
				queue.add(stronglyConnetedVertex);
			}
			while (!queue.isEmpty()) {
				final Vertex curVertex = queue.removeFirst();
				for (final Vertex stronglyConnetedVertex : curVertex.getStrongEdges()) {
					final int index = Math.abs(stronglyConnetedVertex.getVar());
					if (!mark[index]) {
						mark[index] = true;
						queue.add(stronglyConnetedVertex);

						final Vertex stronglyConnetedComplement = mig.getVertex(-stronglyConnetedVertex.getVar());
						vertex.addStronglyConnected(stronglyConnetedVertex);
						stronglyConnetedComplement.addStronglyConnected(complement);
					}
				}
			}
			monitor.step();
		}
		monitor.done();
	}

	protected void bfsWeak(LiteralList affectedVariables, InternalMonitor monitor) {
		monitor.setTotalWork(mig.getVertices().size());
		final ArrayDeque<Vertex> queue = new ArrayDeque<>();
		ArrayList<Integer> literals = new ArrayList<>();
		final boolean[] mark = new boolean[mig.size() + 1];
		final int[] fixed = new int[mig.size() + 1];
		final int orgSize = solver.getAssignmentSize();
		solver.setSelectionStrategy(SelectionStrategy.ORG);
		for (final Vertex vertex : mig.getVertices()) {
			if (vertex.isNormal() && (affectedVariables == null
					|| affectedVariables.containsAnyVariable(Math.abs(vertex.getVar())))) {
				final int var = vertex.getVar();
				final int negVar = -var;
				Arrays.fill(mark, false);
				Arrays.fill(fixed, 0);
				int[] model = null;

				for (LiteralList solution : solver.getSolutionHistory()) {
					if (solution.containsAllLiterals(var)) {
						if (model == null) {
							model = Arrays.copyOf(solution.getLiterals(), solution.size());
						} else {
							LiteralList.resetConflicts(model, solution.getLiterals());
						}
					}
				}

				solver.assignmentPush(var);
				fixed[Math.abs(var)] = var;
				mark[Math.abs(var)] = true;
				for (final Vertex strongVertex : vertex.getStrongEdges()) {
					final int strongVar = strongVertex.getVar();
					solver.assignmentPush(strongVar);
					final int index = Math.abs(strongVar);
					fixed[index] = strongVar;
					mark[index] = true;
					strongVertex.getComplexClauses().stream().flatMapToInt(c -> IntStream.of(c.getLiterals())).forEach(literals::add);
				}
				
				vertex.getComplexClauses().stream().flatMapToInt(c -> IntStream.of(c.getLiterals())).forEach(literals::add);
				
				if (model == null) {
					final int[] solution = solver.findSolution();
					model = Arrays.copyOf(solution, solution.length);
				}
				solver.setSelectionStrategy(model, true, true);

				for (Integer literal : literals) {
					final int index = Math.abs(literal);
					if (!mark[index]) {
						mark[index] = true;
						queue.add(mig.getVertex(literal));
					}
				}
				literals.clear();

				while (!queue.isEmpty()) {
					Vertex curVertex = queue.removeFirst();

					final int varX = model[Math.abs(curVertex.getVar()) - 1];
					if (varX != 0) {
						curVertex = mig.getVertex(varX);
						solver.assignmentPush(-varX);
						switch (solver.hasSolution()) {
						case FALSE:
							solver.assignmentReplaceLast(varX);
							fixed[Math.abs(varX)] = varX;
							final LiteralList literalList = new LiteralList(negVar, varX);
							cleanedClausesList.add(literalList);
							mig.getDetectedStrong().add(literalList);
							for (final Vertex strongVertex : curVertex.getStrongEdges()) {
								final int index = Math.abs(strongVertex.getVar());
								mark[index] = true;
								if (fixed[index] == 0) {
									solver.assignmentPush(strongVertex.getVar());
									fixed[index] = strongVertex.getVar();
								}
								strongVertex.getComplexClauses().stream().flatMapToInt(c -> IntStream.of(c.getLiterals())).forEach(literals::add);
							}
							break;
						case TIMEOUT:
							solver.assignmentPop();
							curVertex.getStrongEdges().stream().map(Vertex::getVar).forEach(literals::add);
							break;
						case TRUE:
							solver.assignmentPop();
							LiteralList.resetConflicts(model, solver.getSolution());
							solver.shuffleOrder(random);
							curVertex.getStrongEdges().stream().map(Vertex::getVar).forEach(literals::add);
							
//							Vertex complement = mig.getVertex(-curVertex.getVar());
//							for (final Vertex strongVertex : complement.getStrongEdges()) {
//								literals.add(strongVertex.getVar());
//							}
							break;
						}
					} else {
						curVertex.getStrongEdges().stream().map(Vertex::getVar).forEach(literals::add);
						
//						Vertex complement = mig.getVertex(-curVertex.getVar());
//						for (final Vertex strongVertex : complement.getStrongEdges()) {
//							literals.add(strongVertex.getVar());
//						}
					}
					curVertex.getComplexClauses().stream().flatMapToInt(c -> IntStream.of(c.getLiterals())).forEach(literals::add);
					
//					Vertex complement = mig.getVertex(-curVertex.getVar());
//					for (final LiteralList complexClause : complement.getComplexClauses()) {
//						for (int literal : complexClause.getLiterals()) {
//							literals.add(literal);
//						}
//					}

					for (Integer literal : literals) {
						final int index = Math.abs(literal);
						if (!mark[index]) {
							mark[index] = true;
							queue.add(mig.getVertex(literal));
						}
					}
					literals.clear();
				}
			}
			solver.assignmentClear(orgSize);
			monitor.step();
		}
		for (Vertex vertex : mig.getVertices()) {
			vertex.getStrongEdges().clear();
			vertex.getComplexClauses().clear();
		}
		monitor.done();
	}

	protected void finish() {
		for (final Vertex vertex : mig.getVertices()) {
			vertex.finish();
		}
		mig.getDetectedStrong().trimToSize();
	}

	public boolean isCheckRedundancy() {
		return checkRedundancy;
	}

	public void setCheckRedundancy(boolean checkRedundancy) {
		this.checkRedundancy = checkRedundancy;
	}

	public boolean isDetectStrong() {
		return detectStrong;
	}

	public void setDetectStrong(boolean detectStrong) {
		this.detectStrong = detectStrong;
	}

}
