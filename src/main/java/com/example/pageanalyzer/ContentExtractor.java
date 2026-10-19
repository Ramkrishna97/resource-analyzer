package com.example.pageanalyzer;

public interface ContentExtractor<T> {
    ExtractedContent extract(T input) throws Exception;
}
