package com.example.pageanalyzer;

import java.util.List;

public record SummaryResult(String title, String source, String level, String summary,
                            String extractedText, List<String> links, List<ImageAsset> images, int originalWords,
                            int summaryWords, String summarizer) { }
