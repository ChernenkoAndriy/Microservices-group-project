package com.epam.java.specialization.notificationservice.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fills double-brace placeholders; an unknown placeholder becomes empty. */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    public String renderSubject(String template, Map<String, String> variables) {
        return render(template, variables, false);
    }

    /** HTML body: variable values are escaped, so a display name cannot inject markup. */
    public String renderBody(String template, Map<String, String> variables) {
        return render(template, variables, true);
    }

    private static String render(String template, Map<String, String> variables, boolean escape) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = variables.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(escape ? HtmlUtils.htmlEscape(value) : value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
