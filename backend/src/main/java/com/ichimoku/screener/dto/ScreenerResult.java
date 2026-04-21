package com.ichimoku.screener.dto;

import java.util.ArrayList;
import java.util.List;

public class ScreenerResult {
    private String symbol;
    private String market;
    private double lastClose;
    private double avgTurnover10d;
    private int daysUnderCloud;
    private int daysBelowOrInCloud;
    private int cloudBounces;
    private boolean underCloud4m;
    private boolean belowOrInCloud4m;
    private boolean twoBouncesDetected;
    private boolean chikouAbovePrice;
    private boolean kumoTwist;
    private int totalScore;
    private List<String> signals = new ArrayList<>();

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public double getLastClose() { return lastClose; }
    public void setLastClose(double lastClose) { this.lastClose = lastClose; }
    public double getAvgTurnover10d() { return avgTurnover10d; }
    public void setAvgTurnover10d(double avgTurnover10d) { this.avgTurnover10d = avgTurnover10d; }
    public int getDaysUnderCloud() { return daysUnderCloud; }
    public void setDaysUnderCloud(int daysUnderCloud) { this.daysUnderCloud = daysUnderCloud; }
    public int getDaysBelowOrInCloud() { return daysBelowOrInCloud; }
    public void setDaysBelowOrInCloud(int daysBelowOrInCloud) { this.daysBelowOrInCloud = daysBelowOrInCloud; }
    public int getCloudBounces() { return cloudBounces; }
    public void setCloudBounces(int cloudBounces) { this.cloudBounces = cloudBounces; }
    public boolean isUnderCloud4m() { return underCloud4m; }
    public void setUnderCloud4m(boolean underCloud4m) { this.underCloud4m = underCloud4m; }
    public boolean isBelowOrInCloud4m() { return belowOrInCloud4m; }
    public void setBelowOrInCloud4m(boolean belowOrInCloud4m) { this.belowOrInCloud4m = belowOrInCloud4m; }
    public boolean isTwoBouncesDetected() { return twoBouncesDetected; }
    public void setTwoBouncesDetected(boolean twoBouncesDetected) { this.twoBouncesDetected = twoBouncesDetected; }
    public boolean isChikouAbovePrice() { return chikouAbovePrice; }
    public void setChikouAbovePrice(boolean chikouAbovePrice) { this.chikouAbovePrice = chikouAbovePrice; }
    public boolean isKumoTwist() { return kumoTwist; }
    public void setKumoTwist(boolean kumoTwist) { this.kumoTwist = kumoTwist; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public List<String> getSignals() { return signals; }
    public void setSignals(List<String> signals) { this.signals = signals; }
}
