package com.crio.warmup.stock.quotes;

import com.crio.warmup.stock.dto.AlphavantageDailyResponse;
import com.crio.warmup.stock.dto.Candle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class AlphavantageService implements StockQuotesService {

  private RestTemplate restTemplate;

  protected AlphavantageService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to) throws JsonProcessingException {
    AlphavantageDailyResponse response = restTemplate.getForObject(
      // buildUri(symbol, from, to),
      buildUri(symbol),
      AlphavantageDailyResponse.class
      );

    List<Candle> candles = new ArrayList<>(response.toCandleList());
    if (candles == null || candles.size() == 0) throw new JsonMappingException(null, "No data");

    return candles.stream()
          .filter(c ->
          !c.getDate().isBefore(from)
          && !c.getDate().isAfter(to))
          .sorted(Comparator.comparing(Candle::getDate))
          .collect(Collectors.toList());
  }

  // protected String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
  protected String buildUri(String symbol) {
    String baseUrl = "https://www.alphavantage.co/query";
    return UriComponentsBuilder
            .fromHttpUrl(baseUrl)
            .queryParam("function", "TIME_SERIES_DAILY")
            .queryParam("symbol", symbol)
            .queryParam("outputsize", "full")
            .queryParam("apikey", getToken())
            .toUriString();
  }

  private static String getToken() {
    // return "FQ8R93QIEBWV21DE";
    return "demo";
  }

}