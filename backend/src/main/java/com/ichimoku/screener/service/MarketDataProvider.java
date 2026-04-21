package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.StockCandle;

import java.util.List;

public interface MarketDataProvider {

    List<StockCandle> downloadHistory(String symbol, int daysBack);

    String getName();
}
