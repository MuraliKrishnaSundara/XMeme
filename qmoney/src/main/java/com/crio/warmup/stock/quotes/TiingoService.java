package com.crio.warmup.stock.quotes;

import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class TiingoService implements StockQuotesService {

  private RestTemplate restTemplate;

  protected TiingoService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to) throws JsonProcessingException {
  String url = buildUri(symbol, from, to);
  try {
    String response = restTemplate.getForObject(url, String.class);
    if (response == null || response.isEmpty()) {
      throw new RuntimeException("Empty response from API");
    }
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    TiingoCandle[] candles = mapper.readValue(response, TiingoCandle[].class);
    return Arrays.asList(candles);
  } catch (Exception e) {
    e.printStackTrace();
    throw e;
  }
}
  
  protected String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
    String baseUrl = "https://api.tiingo.com/tiingo/daily";
    return UriComponentsBuilder
    .fromHttpUrl(baseUrl)
    .pathSegment(symbol, "prices")
    .queryParam("startDate", startDate)
    .queryParam("endDate", endDate)
    .queryParam("token", getToken())
    .toUriString();
  }

  private static String getToken() {
    return "63eced04ffd850143a0d17746a655c466c9bfced";
  }

}