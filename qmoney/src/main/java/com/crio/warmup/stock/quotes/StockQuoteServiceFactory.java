package com.crio.warmup.stock.quotes;

import org.springframework.web.client.RestTemplate;

public enum StockQuoteServiceFactory {

  INSTANCE;

  public StockQuotesService getService(String provider, RestTemplate restTemplate) {
    StockQuotesService stockQuotesService;
    switch (provider.toLowerCase()) {
      case "tiingo":
        stockQuotesService = new TiingoService(restTemplate);
        break;
      default:
        stockQuotesService = new AlphavantageService(restTemplate);
    }
    return stockQuotesService;
  }

}