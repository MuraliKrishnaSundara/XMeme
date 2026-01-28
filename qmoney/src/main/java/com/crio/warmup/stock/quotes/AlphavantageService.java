package com.crio.warmup.stock.quotes;

import com.crio.warmup.stock.dto.AlphavantageDailyResponse;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class AlphavantageService implements StockQuotesService {

  // TODO: CRIO_TASK_MODULE_EXCEPTIONS
  //   1. Update the method signature to match the signature change in the interface.
  //   2. Start throwing new StockQuoteServiceException when you get some invalid response from
  //      Alphavantage, or you encounter a runtime exception during Json parsing.
  //   3. Make sure that the exception propagates all the way from PortfolioManager, so that the
  //      external user's of our API are able to explicitly handle this exception upfront.
  private RestTemplate restTemplate;

  protected AlphavantageService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to) throws JsonProcessingException, StockQuoteServiceException {
    try {
      AlphavantageDailyResponse response = restTemplate.getForObject(
      // buildUri(symbol, from, to),
      buildUri(symbol),
      AlphavantageDailyResponse.class
      );

    List<Candle> candles = new ArrayList<>(response.toCandleList());
    if(candles == null || candles.size() == 0) throw new StockQuoteServiceException("No Stock Data Found");

    return candles.stream()
          .filter(c ->
          !c.getDate().isBefore(from)
          && !c.getDate().isAfter(to))
          .sorted(Comparator.comparing(Candle::getDate))
          .collect(Collectors.toList());
    } catch (Exception e) {
      e.printStackTrace();
      throw new StockQuoteServiceException("Error while processing request: " + e.getLocalizedMessage());
    }
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