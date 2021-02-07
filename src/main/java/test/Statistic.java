package test;

public class Statistic {
	public String name1;
	public String name2;
	public int addedClauses;
	public int removedClauses;
	public int sharedClauses;
	public int addedVar;
	public int removedVar;
	public int sharedVar;
	public int addedCore;
	public int removedCore;
	public int sharedCore;
	public int addedRedundant;
	public int removedRedundant;
	public int sharedRedundant;
	public long timeInitNew;
	public long timeCoreDeadNew;
	public long timeAddNew;
	public long timeBfsNew;
	public long timeFinishNew;
	public long timeInitIt;
	public long timeSatCoreDeadIt;
	public long timeOldCoreDeadIt;
	public long timeNewCoreDeadIt;
	public long timeAddIt;
	public long timeBfsIt;
	public long timeFinishIt;
	
	public Statistic() {
	}
	
	public Statistic(Statistic otherStatistic) {
		name1 = otherStatistic.name1;
		name2 = otherStatistic.name2;
		addedClauses = otherStatistic.addedClauses;
		removedClauses = otherStatistic.removedClauses;
		sharedClauses = otherStatistic.sharedClauses;
		addedVar = otherStatistic.addedVar;
		removedVar = otherStatistic.removedVar;
		sharedVar = otherStatistic.sharedVar;
		addedCore = otherStatistic.addedCore;
		removedCore = otherStatistic.removedCore;
		sharedCore = otherStatistic.sharedCore;
		addedRedundant = otherStatistic.addedRedundant;
		removedRedundant = otherStatistic.removedRedundant;
		sharedRedundant = otherStatistic.sharedRedundant;
		timeInitNew = otherStatistic.timeInitNew;
		timeCoreDeadNew = otherStatistic.timeCoreDeadNew;
		timeAddNew = otherStatistic.timeAddNew;
		timeBfsNew = otherStatistic.timeBfsNew;
		timeFinishNew = otherStatistic.timeFinishNew;
		timeInitIt = otherStatistic.timeInitIt;
		timeSatCoreDeadIt = otherStatistic.timeSatCoreDeadIt;
		timeOldCoreDeadIt = otherStatistic.timeOldCoreDeadIt;
		timeNewCoreDeadIt = otherStatistic.timeNewCoreDeadIt;
		timeAddIt = otherStatistic.timeAddIt;
		timeBfsIt = otherStatistic.timeBfsIt;
		timeFinishIt = otherStatistic.timeFinishIt;
	}

	@Override
	public String toString() {
		return "Statistic [name1=" + name1 + ", name2=" + name2 + ", addedClauses=" + addedClauses + ", removedClauses="
			+ removedClauses + ", sharedClauses=" + sharedClauses + ", addedVar=" + addedVar + ", removedVar="
			+ removedVar + ", sharedVar=" + sharedVar + ", addedCore=" + addedCore + ", removedCore=" + removedCore
			+ ", sharedCore=" + sharedCore + ", addedRedundant=" + addedRedundant + ", removedRedundant="
			+ removedRedundant + ", sharedRedundant=" + sharedRedundant + ", timeInitNew=" + timeInitNew
			+ ", timeCoreDeadNew=" + timeCoreDeadNew + ", timeAddNew=" + timeAddNew + ", timeBfsNew=" + timeBfsNew
			+ ", timeFinishNew=" + timeFinishNew + ", timeInitIt=" + timeInitIt + ", timeSatCoreDeadIt="
			+ timeSatCoreDeadIt + ", timeOldCoreDeadIt=" + timeOldCoreDeadIt + ", timeNewCoreDeadIt="
			+ timeNewCoreDeadIt + ", timeAddIt=" + timeAddIt + ", timeBfsIt=" + timeBfsIt + ", timeFinishIt="
			+ timeFinishIt + "]";
	}

}
