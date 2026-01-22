package com.crio.warmup.stock.portfolio;

import com.crio.warmup.stock.quotes.StockQuotesService;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerFactory {
  
  public static PortfolioManager getPortfolioManager(RestTemplate restTemplate) {
    PortfolioManager portfolioManager = new PortfolioManagerImpl(restTemplate);
    return portfolioManager;
  }

  public static PortfolioManager getPortfolioManager(String provider, RestTemplate restTemplate) {
    PortfolioManager portfolioManager = new PortfolioManagerImpl(provider, restTemplate);
    return portfolioManager;
  }

  public static PortfolioManager getPortfolioManager(StockQuotesService stockQuotesService) {
    PortfolioManager portfolioManager = new PortfolioManagerImpl(stockQuotesService);
    return portfolioManager;
  }

}