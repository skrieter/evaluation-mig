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

public class BuildStatistic {

	public static final int timeInitRegular = 0;
	public static final int timeCoreRegular = 1;
	public static final int timeCleanRegular = 2;
	public static final int timeFirstAddRegular = 3;
	public static final int timeFirstStrongBfsRegular = 4;
	public static final int timeWeakBfsRegular = 5;
	public static final int timeSecondAddRegular = 6;
	public static final int timeSecondStrongBfsRegular = 7;
	public static final int timeFinishRegular = 8;

	public static final int timeInitIncremental = 9;
	public static final int timeCoreIncremental = 10;
	public static final int timeCleanIncremental = 11;
	public static final int timeFirstAddIncremental = 12;
	public static final int timeFirstStrongBfsIncremental = 13;
	public static final int timeWeakBfsIncremental = 14;
	public static final int timeSecondAddIncremental = 15;
	public static final int timeSecondStrongBfsIncremental = 16;
	public static final int timeFinishIncremental = 17;

	public static final int addedClauses = 0;
	public static final int removedClauses = 1;
	public static final int sharedClauses = 2;
	public static final int addedVar = 3;
	public static final int removedVar = 4;
	public static final int sharedVar = 5;

	public static final int redundantRegular = 6;
	public static final int strongRegular = 7;
	public static final int weakRegular = 8;
	public static final int redundantIncremental = 9;
	public static final int strongIncremental = 10;
	public static final int weakIncremental = 11;
	public static final int coreIncremental = 12;
	public static final int coreRegular = 13;

	public long[] time = new long[18];
	public int[] data = new int[14];

	public BuildStatistic() {
	}

	public BuildStatistic(BuildStatistic otherStatistic) {
		System.arraycopy(otherStatistic.time, 0, time, 0, time.length);
		System.arraycopy(otherStatistic.data, 0, data, 0, data.length);
	}

}
