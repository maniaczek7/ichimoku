package com.ichimoku.screener.dto;

import java.util.ArrayList;
import java.util.List;

public class Ichimoku2Entry {
    private String symbol, market, direction, token, slType, changeStatus;
    private double lastClose, avgTurnover10d, cloudTopValue, cloudBottomValue, chikouValue, priceAtChikou, suggestedStopLoss;
    private int cloudDays, score, previousColumn;
    private boolean kumoTwist, chikouConfirms, fullCloudPierce;
    private List<String> signals = new ArrayList<>();

    public String getSymbol() { return symbol; }
    public void setSymbol(String v) { symbol = v; }
    public String getMarket() { return market; }
    public void setMarket(String v) { market = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { direction = v; }
    public String getToken() { return token; }
    public void setToken(String v) { token = v; }
    public double getLastClose() { return lastClose; }
    public void setLastClose(double v) { lastClose = v; }
    public double getAvgTurnover10d() { return avgTurnover10d; }
    public void setAvgTurnover10d(double v) { avgTurnover10d = v; }
    public int getCloudDays() { return cloudDays; }
    public void setCloudDays(int v) { cloudDays = v; }
    public double getCloudTopValue() { return cloudTopValue; }
    public void setCloudTopValue(double v) { cloudTopValue = v; }
    public double getCloudBottomValue() { return cloudBottomValue; }
    public void setCloudBottomValue(double v) { cloudBottomValue = v; }
    public int getScore() { return score; }
    public void setScore(int v) { score = v; }
    public boolean isKumoTwist() { return kumoTwist; }
    public void setKumoTwist(boolean v) { kumoTwist = v; }
    public boolean isChikouConfirms() { return chikouConfirms; }
    public void setChikouConfirms(boolean v) { chikouConfirms = v; }
    public double getChikouValue() { return chikouValue; }
    public void setChikouValue(double v) { chikouValue = v; }
    public double getPriceAtChikou() { return priceAtChikou; }
    public void setPriceAtChikou(double v) { priceAtChikou = v; }
    public boolean isFullCloudPierce() { return fullCloudPierce; }
    public void setFullCloudPierce(boolean v) { fullCloudPierce = v; }
    public double getSuggestedStopLoss() { return suggestedStopLoss; }
    public void setSuggestedStopLoss(double v) { suggestedStopLoss = v; }
    public String getSlType() { return slType; }
    public void setSlType(String v) { slType = v; }
    public List<String> getSignals() { return signals; }
    public void setSignals(List<String> v) { signals = v; }
    public String getChangeStatus() { return changeStatus; }
    public void setChangeStatus(String v) { changeStatus = v; }
    public int getPreviousColumn() { return previousColumn; }
    public void setPreviousColumn(int v) { previousColumn = v; }
}
