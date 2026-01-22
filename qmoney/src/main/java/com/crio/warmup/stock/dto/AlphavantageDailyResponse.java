package com.crio.warmup.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlphavantageDailyResponse {

  @JsonProperty(value = "Time Series (Daily)")
  private Map<LocalDate, AlphavantageCandle> candles;

  public Map<LocalDate, AlphavantageCandle> getCandles() {
    return candles;
  }

  public void setCandles(
      Map<LocalDate, AlphavantageCandle> candles) {
    this.candles = candles;
  }
  
  public List<AlphavantageCandle> toCandleList() {
    return candles.entrySet()
            .stream()
            .map(entry -> {
                AlphavantageCandle candle = entry.getValue();
                candle.setDate(entry.getKey());
                return candle;
            })
            .collect(Collectors.toList());
  }

}