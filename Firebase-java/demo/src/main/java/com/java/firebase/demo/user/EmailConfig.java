package com.java.firebase.demo.user;

import java.util.Properties;
import javax.mail.Session;

public class EmailConfig {
    public static Session getSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com"); 
        props.put("mail.smtp.port", "587"); 

        final String username = "magicarena148@gmail.com"; 
        final String password = "kpvs mqie zqxx rsqd"; 

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(username, password);
            }
        });

        return session;
    }
}
