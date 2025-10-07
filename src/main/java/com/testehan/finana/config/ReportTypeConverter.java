package com.testehan.finana.config;

import com.testehan.finana.model.ReportType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ReportTypeConverter implements Converter<String, ReportType> {

    @Override
    public ReportType convert(String source) {
        try {
            return ReportType.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Or handle it in a way that makes sense for your application
            // For example, by throwing a custom exception or returning a default value
            throw new IllegalArgumentException("Invalid report type: " + source);
        }
    }
}
