package com.ichimoku.screener.dto;

import java.util.ArrayList;
import java.util.List;

public class TrojpolowkaEntry {
    private String symbol;
    private String market;
    private double lastClose;
    private double avgTurnover10d;

    private String direction;
    private int cloudDays;
    private String cloudPosition;
    private double cloudTopValue;
    private double cloudBottomValue;

    private boolean tkCross;
    private String tkCrossDirection;
    private int daysSinceTkCross;
    private boolean tkSwapped;

    private boolean breakoutDetected;
    private int breakoutDaysAgo;

    private int score;
    private boolean kumoTwist;
    private boolean chikouConfirms;
    private double chikouValue;
    private double priceAtChikou;

    private boolean unplayableBreakout;
    private int retestCount;
    private int daysSinceOpposingSignal;
    private boolean retestConfirmsDirection;
    private boolean secondRetestPlayable;

    private boolean positiveRetest;
    private String detectedPattern;
    private String retestZone;

    private String col3Status;
    private boolean nearCloudEdge;

    private List<String> signals = new ArrayList<>();

    private String changeStatus;
    private int previousColumn;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public double getLastClose() { return lastClose; }
    public void setLastClose(double lastClose) { this.lastClose = lastClose; }
    public double getAvgTurnover10d() { return avgTurnover10d; }
    public void setAvgTurnover10d(double avgTurnover10d) { this.avgTurnover10d = avgTurnover10d; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public int getCloudDays() { return cloudDays; }
    public void setCloudDays(int cloudDays) { this.cloudDays = cloudDays; }
    public String getCloudPosition() { return cloudPosition; }
    public void setCloudPosition(String cloudPosition) { this.cloudPosition = cloudPosition; }
    public double getCloudTopValue() { return cloudTopValue; }
    public void setCloudTopValue(double cloudTopValue) { this.cloudTopValue = cloudTopValue; }
    public double getCloudBottomValue() { return cloudBottomValue; }
    public void setCloudBottomValue(double cloudBottomValue) { this.cloudBottomValue = cloudBottomValue; }

    public boolean isTkCross() { return tkCross; }
    public void setTkCross(boolean tkCross) { this.tkCross = tkCross; }
    public String getTkCrossDirection() { return tkCrossDirection; }
    public void setTkCrossDirection(String tkCrossDirection) { this.tkCrossDirection = tkCrossDirection; }
    public int getDaysSinceTkCross() { return daysSinceTkCross; }
    public void setDaysSinceTkCross(int daysSinceTkCross) { this.daysSinceTkCross = daysSinceTkCross; }
    public boolean isTkSwapped() { return tkSwapped; }
    public void setTkSwapped(boolean tkSwapped) { this.tkSwapped = tkSwapped; }

    public boolean isBreakoutDetected() { return breakoutDetected; }
    public void setBreakoutDetected(boolean breakoutDetected) { this.breakoutDetected = breakoutDetected; }
    public int getBreakoutDaysAgo() { return breakoutDaysAgo; }
    public void setBreakoutDaysAgo(int breakoutDaysAgo) { this.breakoutDaysAgo = breakoutDaysAgo; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public boolean isKumoTwist() { return kumoTwist; }
    public void setKumoTwist(boolean kumoTwist) { this.kumoTwist = kumoTwist; }
    public boolean isChikouConfirms() { return chikouConfirms; }
    public void setChikouConfirms(boolean chikouConfirms) { this.chikouConfirms = chikouConfirms; }
    public double getChikouValue() { return chikouValue; }
    public void setChikouValue(double chikouValue) { this.chikouValue = chikouValue; }
    public double getPriceAtChikou() { return priceAtChikou; }
    public void setPriceAtChikou(double priceAtChikou) { this.priceAtChikou = priceAtChikou; }

    public boolean isUnplayableBreakout() { return unplayableBreakout; }
    public void setUnplayableBreakout(boolean unplayableBreakout) { this.unplayableBreakout = unplayableBreakout; }
    public int getRetestCount() { return retestCount; }
    public void setRetestCount(int retestCount) { this.retestCount = retestCount; }
    public int getDaysSinceOpposingSignal() { return daysSinceOpposingSignal; }
    public void setDaysSinceOpposingSignal(int daysSinceOpposingSignal) { this.daysSinceOpposingSignal = daysSinceOpposingSignal; }
    public boolean isRetestConfirmsDirection() { return retestConfirmsDirection; }
    public void setRetestConfirmsDirection(boolean retestConfirmsDirection) { this.retestConfirmsDirection = retestConfirmsDirection; }
    public boolean isSecondRetestPlayable() { return secondRetestPlayable; }
    public void setSecondRetestPlayable(boolean secondRetestPlayable) { this.secondRetestPlayable = secondRetestPlayable; }

    public boolean isPositiveRetest() { return positiveRetest; }
    public void setPositiveRetest(boolean positiveRetest) { this.positiveRetest = positiveRetest; }
    public String getDetectedPattern() { return detectedPattern; }
    public void setDetectedPattern(String detectedPattern) { this.detectedPattern = detectedPattern; }
    public String getRetestZone() { return retestZone; }
    public void setRetestZone(String retestZone) { this.retestZone = retestZone; }

    public String getCol3Status() { return col3Status; }
    public void setCol3Status(String col3Status) { this.col3Status = col3Status; }
    public boolean isNearCloudEdge() { return nearCloudEdge; }
    public void setNearCloudEdge(boolean nearCloudEdge) { this.nearCloudEdge = nearCloudEdge; }

    public List<String> getSignals() { return signals; }
    public void setSignals(List<String> signals) { this.signals = signals; }

    public String getChangeStatus() { return changeStatus; }
    public void setChangeStatus(String changeStatus) { this.changeStatus = changeStatus; }
    public int getPreviousColumn() { return previousColumn; }
    public void setPreviousColumn(int previousColumn) { this.previousColumn = previousColumn; }
}
