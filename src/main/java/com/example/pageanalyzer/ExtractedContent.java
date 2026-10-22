package com.example.pageanalyzer;

import java.util.List;

public record ExtractedContent(String title, String text, List<String> links, List<ImageAsset> images, String source) { }
