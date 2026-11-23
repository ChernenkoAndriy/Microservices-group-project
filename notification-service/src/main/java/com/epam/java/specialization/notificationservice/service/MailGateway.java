package com.epam.java.specialization.notificationservice.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends an HTML email over SMTP when {@code spring.mail.host} is configured. Without it (local cluster, no mail
 * server) the email is only logged here and the delivery log still records it as SENT.
 */
@Component
public class MailGateway {

    private static final Logger log = LoggerFactory.getLogger(MailGateway.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public MailGateway(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${notification.mail.from:no-reply@spotty.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String subject, String htmlBody) throws Exception {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("No SMTP server configured; email to {} not sent over the wire (subject: {})", to, subject);
            return;
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        sender.send(message);
    }
}
