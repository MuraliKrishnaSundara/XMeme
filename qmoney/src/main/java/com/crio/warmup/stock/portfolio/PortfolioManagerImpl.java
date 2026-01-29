package com.crio.warmup.stock.portfolio;

import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuoteServiceFactory;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class PortfolioManagerImpl implements PortfolioManager {

  private RestTemplate restTemplate;
  private String provider;
  private StockQuotesService stockQuotesService;

  protected PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  protected PortfolioManagerImpl(String provider, RestTemplate restTemplate) {
    this.provider = provider;
  }

  protected PortfolioManagerImpl(StockQuotesService stockQuotesService) {
    this.stockQuotesService = stockQuotesService;
  }

  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate,PortfolioTrade trade,Double buyPrice,Double sellPrice) {
    double totalReturn = (sellPrice - buyPrice) / buyPrice;
    double totalYears = yearsBetween(trade.getPurchaseDate(), endDate);
    double annualizedReturn = Math.pow(1 + totalReturn, 1 / totalYears) - 1;
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturn, totalReturn);
  }

  public static double yearsBetween(LocalDate startDate, LocalDate endDate) {
    long total_num_days = ChronoUnit.DAYS.between(startDate, endDate);
    return total_num_days / 365.24;
  }

  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to) throws JsonProcessingException {
    TiingoCandle[] candles = restTemplate.getForObject(
      buildUri(symbol, from, to),
      TiingoCandle[].class
      );
     return Arrays.asList(candles);
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

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades, LocalDate endDate) {
    
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
 
    for (PortfolioTrade trade : portfolioTrades) {

      List<Candle> candles;
      try {
        //old
        // candles = getStockQuote(trade.getSymbol(), trade.getPurchaseDate(), endDate);

        //new - service
        if(stockQuotesService == null) stockQuotesService = StockQuoteServiceFactory.INSTANCE.getService(provider, restTemplate);
        candles = stockQuotesService.getStockQuote(trade.getSymbol(), trade.getPurchaseDate(), endDate);
        
      } catch (StockQuoteServiceException e) {
        throw new RuntimeException(e);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      
      if (candles == null || candles.isEmpty()) continue;

      //sort stock data based on date 
      candles.sort(Comparator.comparing(Candle::getDate));
      
      Candle firstCandle = candles.get(0);
      Candle lastCandle = candles.get(candles.size() - 1);
      
      double buyPrice = firstCandle.getOpen();
      double sellPrice = lastCandle.getClose();
      
      AnnualizedReturn annualizedReturn = calculateAnnualizedReturns(endDate, trade, buyPrice, sellPrice);
      annualizedReturns.add(annualizedReturn);

    }
    //sort annualized returns
    annualizedReturns.sort(getComparator());
    return annualizedReturns;
  }

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(
    List<PortfolioTrade> portfolioTrades,
    LocalDate endDate,
    int numThreads
  ) throws InterruptedException, StockQuoteServiceException {
    
    ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
    List<Future<AnnualizedReturn>> futures = new ArrayList<>();
  
    for (PortfolioTrade trade : portfolioTrades) {
      Callable<AnnualizedReturn> task = () -> {
        List<Candle> candles;
        try {
          if (stockQuotesService == null) {
            stockQuotesService = StockQuoteServiceFactory.INSTANCE.getService(provider, restTemplate);
          }
          candles = stockQuotesService.getStockQuote(
            trade.getSymbol(),
            trade.getPurchaseDate(),
            endDate
            );
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          if (candles == null || candles.isEmpty()) return null;
      
          // Sort candles by date
          candles.sort(Comparator.comparing(Candle::getDate));
          Candle firstCandle = candles.get(0);
          Candle lastCandle = candles.get(candles.size() - 1);
          double buyPrice = firstCandle.getOpen();
          double sellPrice = lastCandle.getClose();
          return calculateAnnualizedReturns(endDate, trade, buyPrice, sellPrice);
        };
        futures.add(executorService.submit(task));
      }
      List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
      for (Future<AnnualizedReturn> future : futures) {
        try {
          AnnualizedReturn result = future.get();
          if (result != null) {
            annualizedReturns.add(result);
          }
        } catch (ExecutionException e) {
          throw new RuntimeException(e.getCause());
        }
      }
      executorService.shutdown();
      executorService.awaitTermination(1, TimeUnit.MINUTES);
      // Sort final result
      annualizedReturns.sort(getComparator());
      return annualizedReturns;
    }
  
}