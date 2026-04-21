package com.ichimoku.screener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public class TradeRecord {
    private String symbol;
    private String market;
    private String direction;

    private LocalDate entryDate;
    private double entryPrice;
    private double initialSl;
    private double cloudTopAtEntry;
    private double cloudBottomAtEntry;

    private LocalDate exitDate;
    private double exitPrice;
    private double finalSl;
    private String exitReason; // "SL_HIT", "MAX_PERIOD"

    private int holdingDays;
    private double returnPct;
    @JsonProperty("rMultiple")
    private double rMultiple;
    private int slTrailCount;
    private String stooqUrl;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    public double getInitialSl() { return initialSl; }
    public void setInitialSl(double initialSl) { this.initialSl = initialSl; }
    public double getCloudTopAtEntry() { return cloudTopAtEntry; }
    public void setCloudTopAtEntry(double cloudTopAtEntry) { this.cloudTopAtEntry = cloudTopAtEntry; }
    public double getCloudBottomAtEntry() { return cloudBottomAtEntry; }
    public void setCloudBottomAtEntry(double cloudBottomAtEntry) { this.cloudBottomAtEntry = cloudBottomAtEntry; }

    public LocalDate getExitDate() { return exitDate; }
    public void setExitDate(LocalDate exitDate) { this.exitDate = exitDate; }
    public double getExitPrice() { return exitPrice; }
    public void setExitPrice(double exitPrice) { this.exitPrice = exitPrice; }
    public double getFinalSl() { return finalSl; }
    public void setFinalSl(double finalSl) { this.finalSl = finalSl; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }

    public int getHoldingDays() { return holdingDays; }
    public void setHoldingDays(int holdingDays) { this.holdingDays = holdingDays; }
    public double getReturnPct() { return returnPct; }
    public void setReturnPct(double returnPct) { this.returnPct = returnPct; }
    public double getRMultiple() { return rMultiple; }
    public void setRMultiple(double rMultiple) { this.rMultiple = rMultiple; }
    public int getSlTrailCount() { return slTrailCount; }
    public void setSlTrailCount(int slTrailCount) { this.slTrailCount = slTrailCount; }
    public String getStooqUrl() { return stooqUrl; }
    public void setStooqUrl(String stooqUrl) { this.stooqUrl = stooqUrl; }
}
