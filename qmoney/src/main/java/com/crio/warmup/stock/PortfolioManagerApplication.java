package com.crio.warmup.stock;

import com.crio.warmup.stock.dto.*;
import com.crio.warmup.stock.log.UncaughtExceptionHandler;
import com.crio.warmup.stock.portfolio.PortfolioManager;
import com.crio.warmup.stock.portfolio.PortfolioManagerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class PortfolioManagerApplication {

  //Module 1
  public static List<String> mainReadFile(String[] args) throws IOException, URISyntaxException {
    if(args.length == 0) throw new IllegalArgumentException("JSON File");
    PortfolioTrade[] trades = parseJsonFile(args[0]);
    List<String> symbols = new ArrayList<>();
    for(PortfolioTrade trade: trades) {
      symbols.add(trade.getSymbol());
    }
     return symbols;
  }

  private static PortfolioTrade[] parseJsonFile(String filePath) throws IOException, URISyntaxException {
    File jsonFile = resolveFileFromResources(filePath);
    return getObjectMapper().readValue(jsonFile, PortfolioTrade[].class);
  }
  
  //Module 3
  public static List<String> mainReadQuotes(String[] args) throws IOException, URISyntaxException {
    if (args.length < 2) {
        throw new IllegalArgumentException("Required Arguments Not Passed");
    }
    PortfolioTrade[] trades = parseJsonFile(args[0]);
    if (trades.length == 0) {
      return Collections.emptyList();
    }
  
    LocalDate endDate = LocalDate.parse(args[1]);
    RestTemplate restTemplate = new RestTemplate();

    Map<String, Double> symbolCloseMap = new HashMap<>();
 
    for (PortfolioTrade trade : trades) {
      TiingoCandle[] response = restTemplate.getForObject(
        prepareUrl(trade, endDate, "63eced04ffd850143a0d17746a655c466c9bfced"),
        TiingoCandle[].class
        );
        if (response == null || response.length == 0) {
          continue;
        }

        TiingoCandle endDateCandle = Arrays.stream(response)
        .filter(c -> c.getDate().equals(endDate))
        .max(Comparator.comparing(TiingoCandle::getDate))
        .orElse(null);

        if (endDateCandle != null) {
          symbolCloseMap.put(
            trade.getSymbol(),
            endDateCandle.getClose()
          );
        }

    }

    if (symbolCloseMap.isEmpty() || symbolCloseMap.size() < trades.length) {
      throw new RuntimeException("No valid dates found");
  }

    return symbolCloseMap.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
}

  public static List<PortfolioTrade> readTradesFromJson(String filename) throws IOException, URISyntaxException {
    File jsonFile = resolveFileFromResources(filename);
    PortfolioTrade[] trades = getObjectMapper().readValue(jsonFile, PortfolioTrade[].class);
    return Arrays.asList(trades);
  }

  public static String prepareUrl(PortfolioTrade trade, LocalDate endDate, String token) {
    String baseUrl = "https://api.tiingo.com/tiingo/daily";
    return UriComponentsBuilder
            .fromHttpUrl(baseUrl)
            .pathSegment(trade.getSymbol(), "prices")
            .queryParam("startDate", trade.getPurchaseDate())
            .queryParam("endDate", endDate)
            .queryParam("token", token)
            .toUriString();
  }

  static Double getOpeningPriceOnStartDate(List<Candle> candles) {
    return candles.get(0).getOpen();
  }

  public static Double getClosingPriceOnEndDate(List<Candle> candles) {
    return candles.get(candles.size() - 1).getClose();
  }

  public static List<Candle> fetchCandles(PortfolioTrade trade, LocalDate endDate, String token) {
    RestTemplate restTemplate = new RestTemplate();
    TiingoCandle[] response = restTemplate.getForObject(
        prepareUrl(trade, endDate, token),
        TiingoCandle[].class
        );
        if (response == null) {
          return Collections.emptyList();
        }
        return Arrays.asList(response);
  }

  public static List<AnnualizedReturn> mainCalculateSingleReturn(String[] args)
      throws IOException, URISyntaxException {
        if (args.length < 2) {
          throw new IllegalArgumentException("Required Arguments Not Passed");
        }
        PortfolioTrade[] trades = parseJsonFile(args[0]);
        if (trades.length == 0) {
          return Collections.emptyList();
        }
        LocalDate endDate = LocalDate.parse(args[1]);
        List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
        for (PortfolioTrade trade : trades) {
          List<Candle> candles = fetchCandles(trade, endDate, getToken());
          double buyPrice = getOpeningPriceOnStartDate(
            candles.stream()
               .filter(c -> c.getDate().equals(trade.getPurchaseDate()))
               .sorted(Comparator.comparing(Candle::getDate))
               .collect(Collectors.toList())
               );
               
          double sellPrice = getClosingPriceOnEndDate(
            candles.stream()
               .filter(c -> c.getDate().equals(endDate))
               .sorted(Comparator.comparing(Candle::getDate))
               .collect(Collectors.toList())
               );

          AnnualizedReturn annualizedReturn = calculateAnnualizedReturns(endDate, trade, buyPrice, sellPrice);
          annualizedReturns.add(annualizedReturn);
        }
        if(annualizedReturns.isEmpty()) Collections.emptyList();
        annualizedReturns.sort(Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed());
        return annualizedReturns;
  }

  public static String getToken() {
    return "63eced04ffd850143a0d17746a655c466c9bfced";
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


  private static void printJsonObject(Object object) throws IOException {
    Logger logger = Logger.getLogger(PortfolioManagerApplication.class.getCanonicalName());
    ObjectMapper mapper = new ObjectMapper();
    logger.info(mapper.writeValueAsString(object));
  }

  private static File resolveFileFromResources(String filename) throws URISyntaxException {
    return Paths.get(
        Thread.currentThread().getContextClassLoader().getResource(filename).toURI()).toFile();
  }

  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }

  public static List<String> debugOutputs() {

     String valueOfArgument0 = "trades.json";
     String resultOfResolveFilePathArgs0 = "trades.json";
     String toStringOfObjectMapper = "ObjectMapper";
     String functionNameFromTestFileInStackTrace = "mainReadFile";
     String lineNumberFromTestFileInStackTrace = "";

    return Arrays.asList(new String[]{valueOfArgument0, resultOfResolveFilePathArgs0,
        toStringOfObjectMapper, functionNameFromTestFileInStackTrace,
        lineNumberFromTestFileInStackTrace});
  }

  public static List<AnnualizedReturn> mainCalculateReturnsAfterRefactor(String[] args)throws Exception {
    
    if (args.length < 2) throw new IllegalArgumentException("Required Arguments Not Passed");
    
    String file = args[0];
    LocalDate endDate = LocalDate.parse(args[1]);
    
    PortfolioTrade[] portfolioTrades = parseJsonFile(file);
    if (portfolioTrades.length == 0) return Collections.emptyList();
    
    RestTemplate restTemplate = new RestTemplate();
    
    PortfolioManager portfolioManager = PortfolioManagerFactory.getPortfolioManager(restTemplate);
    
    return portfolioManager.calculateAnnualizedReturn(Arrays.asList(portfolioTrades), endDate);

  }

  public static void main(String[] args) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
    ThreadContext.put("runId", UUID.randomUUID().toString());

    printJsonObject(mainCalculateReturnsAfterRefactor(args));
    
  }

}