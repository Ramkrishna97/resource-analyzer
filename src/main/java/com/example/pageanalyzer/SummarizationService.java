package com.example.pageanalyzer;

public interface SummarizationService {
    String summarize(String text, SummaryLevel level, String model) throws Exception;
    String name();
}
