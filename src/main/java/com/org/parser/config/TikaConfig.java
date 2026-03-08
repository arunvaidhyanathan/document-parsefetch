package com.org.parser.config;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TikaConfig {

    @Bean
    public AutoDetectParser autoDetectParser() {
        return new AutoDetectParser();
    }

    @Bean
    public TesseractOCRConfig tesseractOCRConfig() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        // Tika will check for Tesseract binary automatically.
        // If not found, OCR is skipped gracefully.
        config.setLanguage("eng");
        return config;
    }
}
